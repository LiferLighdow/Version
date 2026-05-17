package com.liferlighdow.version;

import android.app.Activity;
import android.graphics.Outline;
import android.view.ViewOutlineProvider;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.StatFs;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.provider.Settings;
import android.content.SharedPreferences;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.net.Uri;

import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends Activity {

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    
    private Runnable timeUpdater, statsUpdater;
    private TextView timeText, dateText, cpuStat, ramStat, romStat;
    private View searchContainer;
    private EditText searchInput, homeSearch;
    private ListView searchResults;
    
    private final List<AppInfo> allApps = new ArrayList<>();
    private final List<AppInfo> filteredApps = new ArrayList<>();
    private AppAdapter adapter;
    private boolean isSearchVisible = false;
    private boolean isPrivileged = false;
    private static final int FLAG_PRIVILEGED = 1 << 30;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadApps();
            uiHandler.post(() -> {
                setupDock();
                if (adapter != null) adapter.notifyDataSetChanged();
            });
        }
    };

    private SharedPreferences prefs;
    private static final String PREF_DOCK_PREFIX = "dock_pkg_";
    private static final String PREF_BLACK_MODE = "black_mode";
    private static final int DOCK_COUNT = 4;

    // 斤斤計較優化：重複使用物件避免 GC
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, MMM d", Locale.getDefault());
    private final Date nowDate = new Date();
    private final StringBuilder stringBuilder = new StringBuilder(64);
    private final Random random = new Random();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 使用原生 API 實現全螢幕透明
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        );
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);

        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("launcher_prefs", MODE_PRIVATE);
        
        // 偵測是否為系統特權應用
        isPrivileged = (getApplicationInfo().flags & FLAG_PRIVILEGED) != 0;
        
        // 啟動後台執行緒處理 CPU 監測
        backgroundThread = new HandlerThread("StatsWorker");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        initViews();
        adjustUiToScreen(); // 根據螢幕動態調整大小
        loadApps();
        setupSearch();
        setupDock();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        registerReceiver(packageReceiver, filter);
    }

    private void adjustUiToScreen() {
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        
        // 計算理想的圖示大小：螢幕寬度的 16.5%
        int screenWidth = metrics.widthPixels;
        int iconSize = (int) (screenWidth * 0.165);
        int dockHeight = (int) (iconSize * 1.4);
        int searchHeight = (int) (metrics.density * 52); // 固定搜尋框高度感
        
        // 調整 Dock 高度
        View dock = findViewById(R.id.dock);
        ViewGroup.LayoutParams dockParams = dock.getLayoutParams();
        dockParams.height = dockHeight;
        dock.setLayoutParams(dockParams);
        
        // 調整 4 個 Dock 圖示的大小
        int[] dockIds = {R.id.dock_1, R.id.dock_2, R.id.dock_3, R.id.dock_4};
        final float cornerRadius = iconSize * 0.23f;
        
        for (int id : dockIds) {
            View v = findViewById(id);
            ViewGroup.LayoutParams p = v.getLayoutParams();
            p.width = iconSize;
            p.height = iconSize;
            v.setLayoutParams(p);
            
            // 使用 ViewOutlineProvider 實現原生高效圓角裁切 (不依賴庫)
            v.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadius);
                }
            });
            v.setClipToOutline(true);
        }
        
        // 調整搜尋框高度
        View search = findViewById(R.id.home_search);
        ViewGroup.LayoutParams searchParams = search.getLayoutParams();
        searchParams.height = searchHeight;
        search.setLayoutParams(searchParams);
    }

    private void initViews() {
        timeText = findViewById(R.id.time_text);
        dateText = findViewById(R.id.date_text);
        cpuStat = findViewById(R.id.cpu_stat);
        ramStat = findViewById(R.id.ram_stat);
        romStat = findViewById(R.id.rom_stat);
        searchContainer = findViewById(R.id.search_container);
        searchInput = findViewById(R.id.search_input);
        homeSearch = findViewById(R.id.home_search);
        searchResults = findViewById(R.id.search_results);

        // 時鐘點擊：開啟時鐘/鬧鐘
        timeText.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception e) {
                // 如果系統不支援標準 Action，嘗試通用開啟方式
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_APP_MESSAGING); // 這裡通常改為時鐘相關，但時鐘沒有通用 Category
                // 改用尋找特定 Package 的備案
                try {
                    startActivity(getPackageManager().getLaunchIntentForPackage("com.google.android.deskclock"));
                } catch (Exception ignored) {}
            }
        });

        // 日期點擊：開啟行事曆
        dateText.setOnClickListener(v -> {
            try {
                long startMillis = System.currentTimeMillis();
                Uri.Builder builder = Uri.parse("content://com.android.calendar/time").buildUpon();
                android.content.ContentUris.appendId(builder, startMillis);
                Intent intent = new Intent(Intent.ACTION_VIEW).setData(builder.build());
                startActivity(intent);
            } catch (Exception e) {
                try {
                    Intent intent = new Intent(Intent.ACTION_MAIN);
                    intent.addCategory(Intent.CATEGORY_APP_CALENDAR);
                    startActivity(intent);
                } catch (Exception ignored) {}
            }
        });

        // 監聽長按桌面空白處 (root_container)
        findViewById(R.id.root_container).setOnLongClickListener(v -> {
            showSettings();
            return true;
        });
        
        applyBackgroundMode();
    }

    private void showSettings() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Settings");
        
        boolean isBlackMode = prefs.getBoolean(PREF_BLACK_MODE, false);
        String blackModeText = isBlackMode ? "Switch to Wallpaper Mode" : "Switch to Black Mode (AMOLED Save)";
        
        String[] options = {blackModeText, "Set as Default Launcher"};
        
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                prefs.edit().putBoolean(PREF_BLACK_MODE, !isBlackMode).apply();
                applyBackgroundMode();
            } else if (which == 1) {
                try {
                    // 開啟系統的預設應用程式設定頁面
                    Intent intent = new Intent(Settings.ACTION_HOME_SETTINGS);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception e) {
                    try {
                        // 備案：開啟通用的應用程式設定
                        Intent intent = new Intent(Settings.ACTION_SETTINGS);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    } catch (Exception ignored) {}
                }
            }
        });
        builder.show();
    }

    private void applyBackgroundMode() {
        boolean isBlackMode = prefs.getBoolean(PREF_BLACK_MODE, false);
        View root = findViewById(R.id.root_container);
        if (isBlackMode) {
            root.setBackgroundColor(0xFF000000); // 純黑
        } else {
            root.setBackgroundColor(0x00000000); // 透明 (顯示壁紙)
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 移除 loadApps() 和 setupDock()，改為僅在必要時更新
        // 因為 packageReceiver 與 onCreate 已經涵蓋了初始化與變動監聽
        startUpdaters();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopUpdaters();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        backgroundThread.quit();
        try {
            unregisterReceiver(packageReceiver);
        } catch (Exception ignored) {}
    }

    private void startUpdaters() {
        if (timeUpdater == null) {
            timeUpdater = new Runnable() {
                @Override
                public void run() {
                    updateTime();
                    uiHandler.postDelayed(this, 1000);
                }
            };
        }
        if (statsUpdater == null) {
            statsUpdater = new Runnable() {
                private int romCounter = 0;
                @Override
                public void run() {
                    updateRam();
                    backgroundHandler.post(() -> {
                        final String usage = getCPUUsage();
                        uiHandler.post(() -> {
                            stringBuilder.setLength(0);
                            cpuStat.setText(stringBuilder.append("CPU: ").append(usage).append("%"));
                        });
                    });
                    
                    if (romCounter % 60 == 0) updateRom();
                    romCounter++;
                    uiHandler.postDelayed(this, 3000);
                }
            };
        }
        uiHandler.post(timeUpdater);
        uiHandler.post(statsUpdater);
    }

    private void stopUpdaters() {
        uiHandler.removeCallbacks(timeUpdater);
        uiHandler.removeCallbacks(statsUpdater);
        backgroundHandler.removeCallbacksAndMessages(null);
    }

    private void updateTime() {
        nowDate.setTime(System.currentTimeMillis());
        timeText.setText(timeFormat.format(nowDate));
        dateText.setText(dateFormat.format(nowDate));
    }

    private void updateRam() {
        stringBuilder.setLength(0);
        ramStat.setText(stringBuilder.append("RAM: ").append(getRamPercentage()).append("%"));
    }

    private void updateRom() {
        stringBuilder.setLength(0);
        romStat.setText(stringBuilder.append("ROM: ").append(getRomPercentage()).append("%"));
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN, null);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> availableActivities = pm.queryIntentActivities(i, 0);
        allApps.clear();
        for (ResolveInfo ri : availableActivities) {
            allApps.add(new AppInfo(
                ri.loadLabel(pm).toString(),
                ri.activityInfo.packageName,
                ri.loadIcon(pm)
            ));
        }
    }

    private void setupSearch() {
        adapter = new AppAdapter();
        searchResults.setAdapter(adapter);
        searchResults.setOnItemClickListener((parent, view, position, id) -> {
            launchApp(filteredApps.get(position).packageName);
            hideSearch();
        });

        TextWatcher commonTextWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0 && !isSearchVisible) {
                    showSearch();
                    searchInput.setText(s);
                    searchInput.setSelection(s.length());
                }
                filterApps(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        };

        // 點擊搜尋框即進入搜尋模式
        View.OnClickListener clickListener = v -> {
            if (!isSearchVisible) showSearch();
        };
        homeSearch.setOnClickListener(clickListener);
        homeSearch.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && !isSearchVisible) showSearch();
        });

        TextView.OnEditorActionListener searchListener = (v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                String query = v.getText().toString();
                if (!query.isEmpty()) {
                    if (filteredApps.isEmpty()) {
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + query));
                            startActivity(intent);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else {
                        launchApp(filteredApps.get(0).packageName);
                    }
                    hideSearch();
                }
                return true;
            }
            return false;
        };

        searchInput.addTextChangedListener(commonTextWatcher);
        homeSearch.addTextChangedListener(commonTextWatcher);

        searchInput.setOnEditorActionListener(searchListener);
        homeSearch.setOnEditorActionListener(searchListener);
    }

    private void filterApps(String query) {
        filteredApps.clear();
        if (query.isEmpty()) {
            // 當沒輸入文字時，顯示所有 APP (充當抽屜功能)
            filteredApps.addAll(allApps);
            Collections.sort(filteredApps, (a, b) -> a.label.compareToIgnoreCase(b.label));
        } else {
            String lowerQuery = query.toLowerCase();
            for (AppInfo app : allApps) {
                if (app.label.toLowerCase().contains(lowerQuery)) {
                    filteredApps.add(app);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void showSearch() {
        isSearchVisible = true;
        searchContainer.setVisibility(View.VISIBLE);
        filterApps(""); // 初始化時顯示完整列表
        searchInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
    }

    private void hideSearch() {
        isSearchVisible = false;
        searchContainer.setVisibility(View.GONE);
        searchInput.setText("");
        homeSearch.setText("");
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
    }

    private void setupDock() {
        String[] keywords = {"dialer", "mms", "browser", "camera"};
        for (int i = 0; i < DOCK_COUNT; i++) {
            final int index = i;
            String pkg = prefs.getString(PREF_DOCK_PREFIX + i, null);
            int viewId = getResources().getIdentifier("dock_" + (index + 1), "id", getPackageName());
            ImageView iv = findViewById(viewId);

            if (pkg == null || getPackageManager().getLaunchIntentForPackage(pkg) == null) {
                if (i < keywords.length) {
                    for (AppInfo app : allApps) {
                        if (app.packageName.toLowerCase().contains(keywords[i])) {
                            pkg = app.packageName;
                            break;
                        }
                    }
                }
                // 斤斤計較優化：避免使用 Collections.shuffle()
                if (pkg == null && !allApps.isEmpty()) {
                    pkg = allApps.get(random.nextInt(allApps.size())).packageName;
                }
                if (pkg != null) prefs.edit().putString(PREF_DOCK_PREFIX + i, pkg).apply();
            }

            if (pkg != null) {
                try {
                    iv.setImageDrawable(getPackageManager().getApplicationIcon(pkg));
                } catch (Exception e) {
                    iv.setImageResource(android.R.drawable.sym_def_app_icon);
                }
                final String finalPkg = pkg;
                iv.setOnClickListener(v -> launchApp(finalPkg));
                iv.setOnLongClickListener(v -> { showAppPickerForDock(index); return true; });
            }
        }
    }

    private void showAppPickerForDock(int dockIndex) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Pick an app");

        // 使用自定義 Adapter 顯示圖示
        android.widget.ListAdapter adapter = new android.widget.BaseAdapter() {
            @Override public int getCount() { return allApps.size(); }
            @Override public Object getItem(int position) { return allApps.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = getLayoutInflater().inflate(R.layout.item_app, parent, false);
                }
                AppInfo app = allApps.get(position);
                TextView label = convertView.findViewById(R.id.item_label);
                ImageView icon = convertView.findViewById(R.id.item_icon);
                
                label.setText(app.label);
                label.setTextColor(android.graphics.Color.BLACK); // 對話框背景通常是亮的，字改回黑色
                icon.setImageDrawable(app.icon);

                // 設定選單圖示的原生圓角
                icon.setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), view.getHeight() * 0.2f);
                    }
                });
                icon.setClipToOutline(true);

                return convertView;
            }
        };

        builder.setAdapter(adapter, (dialog, which) -> {
            prefs.edit().putString(PREF_DOCK_PREFIX + dockIndex, allApps.get(which).packageName).apply();
            setupDock();
        });
        builder.show();
    }

    private void launchApp(String pkg) {
        Intent intent = getPackageManager().getLaunchIntentForPackage(pkg);
        if (intent != null) startActivity(intent);
    }

    private int getRamPercentage() {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(mi);
        if (mi.totalMem <= 0) return 0;
        return (int) ((mi.totalMem - mi.availMem) * 100 / mi.totalMem);
    }

    private int getRomPercentage() {
        StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
        long total = stat.getBlockCountLong() * stat.getBlockSizeLong();
        long avail = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
        if (total <= 0) return 0;
        return (int) ((total - avail) * 100 / total);
    }

    private String getCPUUsage() {
        try (RandomAccessFile reader = new RandomAccessFile("/proc/stat", "r")) {
            String[] toks = reader.readLine().split(" +");
            long idle1 = Long.parseLong(toks[4]);
            long cpu1 = Long.parseLong(toks[1]) + Long.parseLong(toks[2]) + Long.parseLong(toks[3]) + Long.parseLong(toks[4]) + Long.parseLong(toks[5]) + Long.parseLong(toks[6]) + Long.parseLong(toks[7]);
            Thread.sleep(360);
            reader.seek(0);
            toks = reader.readLine().split(" +");
            long idle2 = Long.parseLong(toks[4]);
            long cpu2 = Long.parseLong(toks[1]) + Long.parseLong(toks[2]) + Long.parseLong(toks[3]) + Long.parseLong(toks[4]) + Long.parseLong(toks[5]) + Long.parseLong(toks[6]) + Long.parseLong(toks[7]);
            return String.valueOf((int)((cpu2 - cpu1 - (idle2 - idle1)) * 100 / (cpu2 - cpu1)));
        } catch (Exception e) { return "0"; }
    }

    private static class AppInfo {
        String label, packageName;
        Drawable icon;
        AppInfo(String l, String p, Drawable i) { label = l; packageName = p; icon = i; }
    }

    private class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return filteredApps.size(); }
        @Override public Object getItem(int p) { return filteredApps.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public View getView(int p, View v, ViewGroup parent) {
            if (v == null) {
                v = getLayoutInflater().inflate(R.layout.item_app, parent, false);
            }
            AppInfo app = filteredApps.get(p);
            TextView label = v.findViewById(R.id.item_label);
            ImageView icon = v.findViewById(R.id.item_icon);
            
            label.setText(app.label);
            icon.setImageDrawable(app.icon);

            // 搜尋列表中的圖示也套用原生圓角
            icon.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), view.getHeight() * 0.2f);
                }
            });
            icon.setClipToOutline(true);

            return v;
        }
    }

    @Override 
    public void onBackPressed() { 
        if (isSearchVisible) {
            hideSearch(); 
        } else {
            super.onBackPressed();
        }
    }
}
