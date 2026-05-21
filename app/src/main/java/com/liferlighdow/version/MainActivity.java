package com.liferlighdow.version;

import android.app.Activity;
import android.graphics.Outline;
import android.view.ViewOutlineProvider;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Matrix;
import android.view.ScaleGestureDetector;
import android.os.Bundle;
import android.os.Environment;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
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
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.view.GestureDetector;
import android.view.MotionEvent;
import java.lang.reflect.Method;
import android.provider.Settings;
import android.content.SharedPreferences;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.net.Uri;

import java.util.HashSet;
import java.util.Set;
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
    private int currentTextColor = 0xFFFFFFFF;
    private GestureDetector gestureDetector;
    private DevicePolicyManager devicePolicyManager;
    private ComponentName adminComponent;
    private boolean isSearchVisible = false;

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
    private static final String PREF_HIDDEN_APPS = "hidden_apps";
    private static final String PREF_THEME = "ui_theme";
    private static final String PREF_CUSTOM_ICON_PREFIX = "custom_icon_";
    private static final String PREF_CUSTOM_LABEL_PREFIX = "custom_label_";
    private static final int DOCK_COUNT = 5;
    private static final int REQUEST_PICK_IMAGE = 1001;
    private String pendingPackageName = null;

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
        
        // 啟動後台執行緒處理 CPU 監測
        backgroundThread = new HandlerThread("StatsWorker");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        initViews();
        adjustUiToScreen(); // 根據螢幕動態調整大小
        loadApps();
        setupSearch();
        setupDock();
        setupGestures();

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
        
        int theme = prefs.getInt(PREF_THEME, 0);
        // 計算理想的圖示大小：AOSP 風格更小一些 (13% vs 16.5%)
        int screenWidth = metrics.widthPixels;
        float iconScale = (theme == 3) ? 0.13f : 0.165f;
        int iconSize = (int) (screenWidth * iconScale);
        int dockHeight = (int) (iconSize * 1.4);
        int searchHeight = (int) (metrics.density * 52); 
        
        // 調整 Dock 高度
        View dock = findViewById(R.id.dock);
        ViewGroup.LayoutParams dockParams = dock.getLayoutParams();
        dockParams.height = dockHeight;
        dock.setLayoutParams(dockParams);
        
        // 調整 Dock 圖示的大小
        int[] dockIds = {R.id.dock_1, R.id.dock_2, R.id.dock_3, R.id.dock_4, R.id.dock_5};
        final float cornerRadius = iconSize * 0.23f;
        
        for (int id : dockIds) {
            View v = findViewById(id);
            if (v == null) continue;
            ViewGroup.LayoutParams p = v.getLayoutParams();
            p.width = iconSize;
            p.height = iconSize;
            v.setLayoutParams(p);
            
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
        applyTheme();
    }

    private void setupGestures() {
        devicePolicyManager = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(this, AdminReceiver.class);
        
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (devicePolicyManager.isAdminActive(adminComponent)) {
                    devicePolicyManager.lockNow();
                } else {
                    // 導向開啟權限
                    Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                    intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
                    intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Needed for Double Tap to Sleep");
                    startActivity(intent);
                }
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                // 下滑偵測 (向下速度大於門檻且為垂直向)
                if (velocityY > 500 && Math.abs(velocityY) > Math.abs(velocityX)) {
                    expandNotifications();
                    return true;
                }
                return false;
            }
        });
    }

    private void expandNotifications() {
        try {
            Object sbservice = getSystemService("statusbar");
            Class<?> statusbarManager = Class.forName("android.app.StatusBarManager");
            Method expand = statusbarManager.getMethod("expandNotificationsPanel");
            expand.invoke(sbservice);
        } catch (Exception ignored) {}
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (!isSearchVisible) {
            gestureDetector.onTouchEvent(ev);
        }
        return super.dispatchTouchEvent(ev);
    }

    public static class AdminReceiver extends android.app.admin.DeviceAdminReceiver {}

    private void showSettings() {
        android.app.AlertDialog.Builder builder = getDialogBuilder();
        builder.setTitle("Settings");
        
        boolean isBlackMode = prefs.getBoolean(PREF_BLACK_MODE, false);
        String blackModeText = isBlackMode ? "Switch to Wallpaper Mode" : "Switch to Black Mode (AMOLED Save)";
        
        String[] options = {blackModeText, "Themes", "Hide Apps", "Set as Default Launcher"};
        
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                prefs.edit().putBoolean(PREF_BLACK_MODE, !isBlackMode).apply();
                applyBackgroundMode();
            } else if (which == 1) {
                showThemeDialog();
            } else if (which == 2) {
                showHideAppsDialog();
            } else if (which == 3) {
                try {
                    Intent intent;
                    if (android.os.Build.VERSION.SDK_INT >= 24) {
                        intent = new Intent(Settings.ACTION_HOME_SETTINGS);
                    } else {
                        intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                    }
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception e) {
                    try {
                        Intent intent = new Intent(Settings.ACTION_SETTINGS);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    } catch (Exception ignored) {}
                }
            }
        });
        builder.show();
    }

    private void showThemeDialog() {
        String[] themes = {"Default (Modern Glass)", "OLED Black", "Snow White", "AOSP Style"};
        getDialogBuilder()
                .setTitle("Select Theme")
                .setItems(themes, (dialog, which) -> {
                    prefs.edit().putInt(PREF_THEME, which).apply();
                    applyTheme();
                })
                .show();
    }

    private void applyTheme() {
        int theme = prefs.getInt(PREF_THEME, 0);
        int textColor, secondaryTextColor, containerColor, strokeColor, searchStrokeColor, overlayColor;
        float density = getResources().getDisplayMetrics().density;

        if (theme == 3) { // AOSP Style
            textColor = 0xFFFFFFFF;
            secondaryTextColor = 0xFFCCCCCC;
            containerColor = 0x44000000; // 淺黑色
            strokeColor = 0x00000000; // AOSP Dock 無背景/邊框
            searchStrokeColor = 0x00000000;
            overlayColor = 0xEE000000;
        } else if (theme == 1) { // OLED Black
            textColor = 0xFFFFFFFF;
            secondaryTextColor = 0xFF888888;
            containerColor = 0xFF000000;
            strokeColor = 0xFF333333;
            searchStrokeColor = 0xFF444444;
            overlayColor = 0xFF000000;
        } else if (theme == 2) { // Snow White
            textColor = 0xFF222222;
            secondaryTextColor = 0xFF666666;
            containerColor = 0xCCFFFFFF;
            strokeColor = 0x44AAAAAA;
            searchStrokeColor = 0x66888888;
            overlayColor = 0xEEFFFFFF;
        } else { // Default (0)
            textColor = 0xFFFFFFFF;
            secondaryTextColor = 0xFFCCCCCC;
            containerColor = 0x22FFFFFF;
            strokeColor = 0x44FFFFFF;
            searchStrokeColor = 0x88FFFFFF;
            overlayColor = 0xEE000000;
        }

        // Apply to TextViews
        currentTextColor = textColor;
        timeText.setTextColor(textColor);
        dateText.setTextColor(textColor);
        cpuStat.setTextColor(textColor);
        ramStat.setTextColor(textColor);
        romStat.setTextColor(textColor);
        searchInput.setTextColor(textColor);
        searchInput.setHintTextColor(secondaryTextColor);
        homeSearch.setTextColor(textColor);
        homeSearch.setHintTextColor(secondaryTextColor);

        // Update Search Icon Tint
        Drawable[] drawables = homeSearch.getCompoundDrawables();
        if (drawables[0] != null) {
            drawables[0].mutate().setTint(textColor);
        }
        
        // Search Container Overlay
        searchContainer.setBackgroundColor(overlayColor);

        // Adjust Clock Container Position
        View clockContainer = findViewById(R.id.clock_container);
        ViewGroup.MarginLayoutParams clockParams = (ViewGroup.MarginLayoutParams) clockContainer.getLayoutParams();
        clockParams.topMargin = (int) (density * (theme == 3 ? 120 : 80));
        clockContainer.setLayoutParams(clockParams);

        // Toggle Stats Widget
        LinearLayout statsWidget = findViewById(R.id.stats_widget);
        statsWidget.setVisibility(theme == 3 ? View.GONE : View.VISIBLE);

        // Apply to Containers
        applyViewStyle(statsWidget, containerColor, strokeColor, (int)(40 * density), (int)(1 * density));
        applyViewStyle(homeSearch, containerColor, searchStrokeColor, (int)(25 * density), (int)(1.5 * density));
        applyViewStyle(searchInput, containerColor, searchStrokeColor, (int)(25 * density), (int)(1.5 * density));
        
        // Dock handled specially for AOSP (no background)
        View dockView = findViewById(R.id.dock);
        if (theme == 3) {
            dockView.setBackgroundColor(0x00000000);
        } else {
            applyViewStyle(dockView, containerColor, strokeColor, (int)(40 * density), (int)(1 * density));
        }

        // Toggle 5th icon for AOSP
        findViewById(R.id.dock_5).setVisibility(theme == 3 ? View.VISIBLE : View.GONE);
        findViewById(R.id.dock_spacer_4).setVisibility(theme == 3 ? View.VISIBLE : View.GONE);

        // Update Stats Widget Separators
        int separatorColor = (theme == 2) ? 0x44000000 : 0x44FFFFFF;
        for (int i = 0; i < statsWidget.getChildCount(); i++) {
            View child = statsWidget.getChildAt(i);
            if (!(child instanceof TextView)) {
                child.setBackgroundColor(separatorColor);
            }
        }

        // Refresh layouts
        adjustUiToScreen();
        setupDock();
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void applyViewStyle(View v, int bgColor, int strokeColor, int radius, int strokeWidth) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(bgColor);
        gd.setCornerRadius(radius);
        gd.setStroke(strokeWidth, strokeColor);
        v.setBackground(gd);
    }

    private android.app.AlertDialog.Builder getDialogBuilder() {
        int theme = prefs.getInt(PREF_THEME, 0);
        int dialogTheme;
        if (android.os.Build.VERSION.SDK_INT >= 22) {
            dialogTheme = (theme == 2) ? android.R.style.Theme_DeviceDefault_Light_Dialog_Alert : android.R.style.Theme_DeviceDefault_Dialog_Alert;
        } else {
            // API 21 Fallback
            dialogTheme = (theme == 2) ? android.R.style.Theme_Material_Light_Dialog_Alert : android.R.style.Theme_Material_Dialog_Alert;
        }
        return new android.app.AlertDialog.Builder(this, dialogTheme);
    }

    private void showHideAppsDialog() {
        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> allInstalledRI = pm.queryIntentActivities(intent, 0);
        Collections.sort(allInstalledRI, new ResolveInfo.DisplayNameComparator(pm));

        final List<AppInfo> appsForDialog = new ArrayList<>();
        for (ResolveInfo ri : allInstalledRI) {
            appsForDialog.add(new AppInfo(
                ri.loadLabel(pm).toString(),
                ri.activityInfo.packageName,
                ri.loadIcon(pm)
            ));
        }

        Set<String> hidden = prefs.getStringSet(PREF_HIDDEN_APPS, new HashSet<>());
        final boolean[] checked = new boolean[appsForDialog.size()];
        for (int i = 0; i < appsForDialog.size(); i++) {
            checked[i] = hidden.contains(appsForDialog.get(i).packageName);
        }

        ListView listView = new ListView(this);
        BaseAdapter adapter = new BaseAdapter() {
            @Override public int getCount() { return appsForDialog.size(); }
            @Override public Object getItem(int p) { return appsForDialog.get(p); }
            @Override public long getItemId(int p) { return p; }
            @Override public View getView(int p, View v, ViewGroup parent) {
                if (v == null) v = getLayoutInflater().inflate(R.layout.item_app, parent, false);
                AppInfo app = appsForDialog.get(p);
                TextView label = v.findViewById(R.id.item_label);
                label.setText(app.label);
                label.setTextColor(currentTextColor);
                ImageView icon = v.findViewById(R.id.item_icon);
                icon.setImageDrawable(app.icon);
                icon.setOutlineProvider(new ViewOutlineProvider() {
                    @Override public void getOutline(View view, Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), view.getHeight() * 0.2f);
                    }
                });
                icon.setClipToOutline(true);
                
                android.widget.CheckBox cb = v.findViewById(R.id.item_check);
                cb.setVisibility(View.VISIBLE);
                cb.setChecked(checked[p]);
                return v;
            }
        };
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            checked[position] = !checked[position];
            adapter.notifyDataSetChanged();
        });

        getDialogBuilder()
            .setTitle("Hide Apps")
            .setView(listView)
            .setPositiveButton("Done", (dialog, which) -> {
                Set<String> newHidden = new HashSet<>();
                for (int i = 0; i < appsForDialog.size(); i++) {
                    if (checked[i]) newHidden.add(appsForDialog.get(i).packageName);
                }
                prefs.edit().putStringSet(PREF_HIDDEN_APPS, newHidden).apply();
                loadApps();
                setupDock();
                if (isSearchVisible) filterApps(searchInput.getText().toString());
            })
            .show();
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK && data != null && pendingPackageName != null) {
            showCropDialog(data.getData(), pendingPackageName);
            pendingPackageName = null;
        }
    }

    private void showCropDialog(Uri uri, String pkg) {
        try {
            // 預先載入圖片（縮放至螢幕大小以防 OOM）
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(is, null, options);
            }
            
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            options.inSampleSize = 1;
            while (options.outWidth / options.inSampleSize > screenWidth * 2) options.inSampleSize *= 2;
            options.inJustDecodeBounds = false;
            
            Bitmap sourceBmp;
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                sourceBmp = BitmapFactory.decodeStream(is, null, options);
            }
            if (sourceBmp == null) return;

            final Bitmap bmp = sourceBmp;
            final Matrix matrix = new Matrix();
            final float[] lastTouch = new float[2];
            final float[] center = new float[2];
            
            // 建立一個互動式 View
            View cropView = new View(this) {
                private final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                private boolean initialCentered = false;

                ScaleGestureDetector scaleDetector = new ScaleGestureDetector(MainActivity.this, new ScaleGestureDetector.OnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        matrix.postScale(detector.getScaleFactor(), detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
                        invalidate();
                        return true;
                    }

                    @Override
                    public boolean onScaleBegin(ScaleGestureDetector detector) { return true; }
                    @Override
                    public void onScaleEnd(ScaleGestureDetector detector) { }
                });

                @Override
                public boolean onTouchEvent(MotionEvent event) {
                    scaleDetector.onTouchEvent(event);
                    if (scaleDetector.isInProgress()) return true;
                    
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            lastTouch[0] = event.getX();
                            lastTouch[1] = event.getY();
                            break;
                        case MotionEvent.ACTION_MOVE:
                            matrix.postTranslate(event.getX() - lastTouch[0], event.getY() - lastTouch[1]);
                            lastTouch[0] = event.getX();
                            lastTouch[1] = event.getY();
                            invalidate();
                            break;
                    }
                    return true;
                }

                @Override
                protected void onDraw(Canvas canvas) {
                    int w = getWidth();
                    int h = getHeight();
                    int size = (int)(w * 0.7f);
                    int x = (w - size) / 2;
                    int y = (h - size) / 2;

                    // 初始化圖片位置：居中並縮放至適合大小
                    if (!initialCentered && w > 0) {
                        float scale = (float) size / Math.min(bmp.getWidth(), bmp.getHeight());
                        matrix.setScale(scale, scale);
                        matrix.postTranslate((w - bmp.getWidth() * scale) / 2f, (h - bmp.getHeight() * scale) / 2f);
                        initialCentered = true;
                    }

                    canvas.drawBitmap(bmp, matrix, null);
                    
                    // 繪製遮罩 (上下左右四個矩形)
                    maskPaint.setColor(0xAA000000);
                    maskPaint.setStyle(Paint.Style.FILL);
                    canvas.drawRect(0, 0, w, y, maskPaint); // 上
                    canvas.drawRect(0, y + size, w, h, maskPaint); // 下
                    canvas.drawRect(0, y, x, y + size, maskPaint); // 左
                    canvas.drawRect(x + size, y, w, y + size, maskPaint); // 右
                    
                    // 繪製裁切框邊界
                    borderPaint.setStyle(Paint.Style.STROKE);
                    borderPaint.setColor(Color.WHITE);
                    borderPaint.setStrokeWidth(3);
                    canvas.drawRect(x, y, x + size, y + size, borderPaint);
                    
                    center[0] = x; center[1] = y;
                }
            };

            getDialogBuilder()
                .setTitle("Pan & Zoom to Crop")
                .setView(cropView)
                .setPositiveButton("Next", (dialog, which) -> {
                    // 根據目前 Matrix 提取圖片
                    showShapeSelection(bmp, matrix, (int)(cropView.getWidth() * 0.7f), center, pkg);
                })
                .setNegativeButton("Cancel", (dialog, which) -> bmp.recycle())
                .show();
                
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void showShapeSelection(Bitmap source, Matrix matrix, int cropSize, float[] center, String pkg) {
        String[] shapes = {"Square", "Circle", "Rounded Square"};
        getDialogBuilder()
            .setTitle("Select Shape")
            .setItems(shapes, (dialog, which) -> {
                performFinalCrop(source, matrix, cropSize, center, which, pkg);
                setupDock();
                if (adapter != null) adapter.notifyDataSetChanged();
            })
            .show();
    }

    private void performFinalCrop(Bitmap source, Matrix matrix, int cropSize, float[] center, int shapeType, String pkg) {
        try {
            // 1. 建立裁切後的 Bitmap
            Bitmap cropped = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(cropped);
            
            // 2. 套用 Matrix 繪製圖片到 cropped (需補償對話框中的偏移)
            Matrix finalMatrix = new Matrix(matrix);
            finalMatrix.postTranslate(-center[0], -center[1]);
            canvas.drawBitmap(source, finalMatrix, new Paint(Paint.FILTER_BITMAP_FLAG));
            
            // 3. 套用形狀遮罩
            Bitmap output = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888);
            Canvas outputCanvas = new Canvas(output);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            RectF rect = new RectF(0, 0, cropSize, cropSize);
            
            if (shapeType == 1) outputCanvas.drawOval(rect, paint);
            else if (shapeType == 2) outputCanvas.drawRoundRect(rect, cropSize * 0.2f, cropSize * 0.2f, paint);
            else outputCanvas.drawRect(rect, paint);
            
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            outputCanvas.drawBitmap(cropped, 0, 0, paint);
            
            // 4. 縮放至 192px 儲存
            int targetSize = (int) (getResources().getDisplayMetrics().density * 192);
            Bitmap finalBmp = Bitmap.createScaledBitmap(output, targetSize, targetSize, true);
            
            File iconFile = new File(getFilesDir(), "icon_" + pkg.hashCode() + ".png");
            try (FileOutputStream fos = new FileOutputStream(iconFile)) {
                finalBmp.compress(Bitmap.CompressFormat.PNG, 90, fos);
            }
            
            prefs.edit().putString(PREF_CUSTOM_ICON_PREFIX + pkg, iconFile.getAbsolutePath()).apply();
            
            // 釋放資源
            source.recycle();
            cropped.recycle();
            output.recycle();
            finalBmp.recycle();
            
        } catch (Exception e) { e.printStackTrace(); }
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
        
        Set<String> hidden = prefs.getStringSet(PREF_HIDDEN_APPS, new HashSet<>());
        
        allApps.clear();
        for (ResolveInfo ri : availableActivities) {
            String pkg = ri.activityInfo.packageName;
            if (hidden.contains(pkg)) continue;
            
            String label = prefs.getString(PREF_CUSTOM_LABEL_PREFIX + pkg, null);
            if (label == null) label = ri.loadLabel(pm).toString();
            
            allApps.add(new AppInfo(
                label,
                pkg,
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
            if (hasFocus && !isSearchVisible) {
                showSearch();
                v.clearFocus(); // 轉移焦點到 searchInput 後，清空主畫面的焦點
            }
        });

        TextView.OnEditorActionListener searchListener = (v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                String query = v.getText().toString();
                if (!query.isEmpty()) {
                    if (filteredApps.isEmpty()) {
                        try {
                            // 使用 Uri.encode 確保中文與空格能正確跳轉至瀏覽器
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)));
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
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
        String[] keywords = {"dialer", "mms", "browser", "camera", "contacts"};
        for (int i = 0; i < DOCK_COUNT; i++) {
            final int index = i;
            String pkg = prefs.getString(PREF_DOCK_PREFIX + i, null);
            int viewId = getResources().getIdentifier("dock_" + (index + 1), "id", getPackageName());
            ImageView iv = findViewById(viewId);

            if (pkg == null || getPackageManager().getLaunchIntentForPackage(pkg) == null) {
                // 優先使用關鍵字匹配預設 App
                if (i < keywords.length) {
                    for (AppInfo app : allApps) {
                        if (app.packageName.toLowerCase().contains(keywords[i])) {
                            pkg = app.packageName;
                            break;
                        }
                    }
                }
                
                // 如果沒匹配到，隨機選一個
                if (pkg == null && !allApps.isEmpty()) {
                    pkg = allApps.get(random.nextInt(allApps.size())).packageName;
                }
                if (pkg != null) prefs.edit().putString(PREF_DOCK_PREFIX + i, pkg).apply();
            }

            if (pkg != null) {
                String customPath = prefs.getString(PREF_CUSTOM_ICON_PREFIX + pkg, null);
                if (customPath != null && new File(customPath).exists()) {
                    iv.setImageBitmap(BitmapFactory.decodeFile(customPath));
                } else {
                    try {
                        iv.setImageDrawable(getPackageManager().getApplicationIcon(pkg));
                    } catch (Exception e) {
                        iv.setImageResource(android.R.drawable.sym_def_app_icon);
                    }
                }
                final String finalPkg = pkg;
                iv.setOnClickListener(v -> launchApp(finalPkg));
                iv.setOnLongClickListener(v -> { showDockMenu(index, finalPkg); return true; });
            }
        }
    }

    private void showDockMenu(int index, String pkg) {
        String[] options = {"Change App", "Change Icon", "Reset Icon", "Change Name", "Reset Name"};
        getDialogBuilder()
            .setTitle("Dock Shortcut")
            .setItems(options, (dialog, which) -> {
                if (which == 0) showAppPickerForDock(index);
                else if (which == 1) {
                    pendingPackageName = pkg;
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("image/*");
                    startActivityForResult(intent, REQUEST_PICK_IMAGE);
                } else if (which == 2) {
                    prefs.edit().remove(PREF_CUSTOM_ICON_PREFIX + pkg).apply();
                    setupDock();
                    if (adapter != null) adapter.notifyDataSetChanged();
                } else if (which == 3) {
                    showChangeNameDialog(pkg);
                } else if (which == 4) {
                    prefs.edit().remove(PREF_CUSTOM_LABEL_PREFIX + pkg).apply();
                    loadApps();
                    if (adapter != null) adapter.notifyDataSetChanged();
                }
            }).show();
    }

    private void showChangeNameDialog(String pkg) {
        final EditText input = new EditText(this);
        input.setSingleLine();
        String currentLabel = "";
        for (AppInfo app : allApps) {
            if (app.packageName.equals(pkg)) {
                currentLabel = app.label;
                break;
            }
        }
        input.setText(currentLabel);
        input.setSelection(input.getText().length());
        
        // 增加一點邊距
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int p = (int)(20 * getResources().getDisplayMetrics().density);
        container.setPadding(p, p/2, p, 0);
        container.addView(input);

        getDialogBuilder()
            .setTitle("Change Name")
            .setView(container)
            .setPositiveButton("OK", (dialog, which) -> {
                String newName = input.getText().toString().trim();
                if (newName.isEmpty()) {
                    prefs.edit().remove(PREF_CUSTOM_LABEL_PREFIX + pkg).apply();
                } else {
                    prefs.edit().putString(PREF_CUSTOM_LABEL_PREFIX + pkg, newName).apply();
                }
                loadApps();
                if (adapter != null) adapter.notifyDataSetChanged();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showAppPickerForDock(int dockIndex) {
        android.app.AlertDialog.Builder builder = getDialogBuilder();
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
                label.setTextColor(currentTextColor);
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
            label.setTextColor(currentTextColor);

            String customPath = prefs.getString(PREF_CUSTOM_ICON_PREFIX + app.packageName, null);
            if (customPath != null && new File(customPath).exists()) {
                icon.setImageBitmap(BitmapFactory.decodeFile(customPath));
            } else {
                icon.setImageDrawable(app.icon);
            }

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
