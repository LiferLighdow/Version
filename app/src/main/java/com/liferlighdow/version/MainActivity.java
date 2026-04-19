package com.liferlighdow.version;

import android.app.Activity;
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
import android.content.SharedPreferences;

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

    private SharedPreferences prefs;
    private static final String PREF_DOCK_PREFIX = "dock_pkg_";
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
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("launcher_prefs", MODE_PRIVATE);
        
        // 偵測是否為系統特權應用
        isPrivileged = (getApplicationInfo().flags & FLAG_PRIVILEGED) != 0;
        
        // 啟動後台執行緒處理 CPU 監測
        backgroundThread = new HandlerThread("StatsWorker");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        initViews();
        loadApps();
        setupSearch();
        setupDock();
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
    }

    @Override
    protected void onResume() {
        super.onResume();
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
        ramStat.setText(stringBuilder.append("RAM: ").append(getUsedMemory()));
    }

    private void updateRom() {
        stringBuilder.setLength(0);
        romStat.setText(stringBuilder.append("ROM: ").append(getAvailableInternalMemorySize()));
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

        searchInput.addTextChangedListener(commonTextWatcher);
        homeSearch.addTextChangedListener(commonTextWatcher);

        homeSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                String query = v.getText().toString();
                if (!query.isEmpty()) {
                    if (filteredApps.isEmpty()) {
                        startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com/search?q=" + query)));
                    } else {
                        launchApp(filteredApps.get(0).packageName);
                    }
                    hideSearch();
                }
                return true;
            }
            return false;
        });
    }

    private void filterApps(String query) {
        filteredApps.clear();
        if (!query.isEmpty()) {
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
        String[] names = new String[allApps.size()];
        for (int i = 0; i < allApps.size(); i++) names[i] = allApps.get(i).label;
        builder.setItems(names, (dialog, which) -> {
            prefs.edit().putString(PREF_DOCK_PREFIX + dockIndex, allApps.get(which).packageName).apply();
            setupDock();
        });
        builder.show();
    }

    private void launchApp(String pkg) {
        Intent intent = getPackageManager().getLaunchIntentForPackage(pkg);
        if (intent != null) startActivity(intent);
    }

    private String getUsedMemory() {
        if (isPrivileged) {
            // 系統模式：直接讀取 /proc/meminfo，避免 Binder 調用消耗
            try (RandomAccessFile reader = new RandomAccessFile("/proc/meminfo", "r")) {
                long total = 0, avail = 0;
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("MemTotal:")) total = parseMeminfo(line);
                    else if (line.startsWith("MemAvailable:")) avail = parseMeminfo(line);
                    if (total > 0 && avail > 0) break;
                }
                if (total > 0) return (total - avail) / 1024 + "MB / " + total / 1024 + "MB";
            } catch (Exception ignored) {}
        }
        
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(mi);
        return (mi.totalMem - mi.availMem) / 1048576L + "MB / " + mi.totalMem / 1048576L + "MB";
    }

    private long parseMeminfo(String line) {
        // 快速提取數字字串，避免正則表達式的高開銷
        int start = -1, end = -1;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (Character.isDigit(c)) {
                if (start == -1) start = i;
            } else if (start != -1) {
                end = i;
                break;
            }
        }
        return (start != -1 && end != -1) ? Long.parseLong(line.substring(start, end)) : 0;
    }

    private String getAvailableInternalMemorySize() {
        StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
        long blockSize = stat.getBlockSizeLong();
        return (stat.getAvailableBlocksLong() * blockSize / 1073741824L) + "GB / " + (stat.getBlockCountLong() * blockSize / 1073741824L) + "GB";
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
                v = new TextView(MainActivity.this);
                ((TextView)v).setTextColor(0xFFFFFFFF);
                v.setPadding(20, 20, 20, 20);
            }
            ((TextView)v).setText(filteredApps.get(p).label);
            return v;
        }
    }

    @Override public void onBackPressed() { if (isSearchVisible) hideSearch(); }
}
