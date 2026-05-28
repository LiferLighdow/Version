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
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.RelativeLayout;
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
import android.util.LruCache;
import android.graphics.Rect;

import android.os.BatteryManager;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;

public class MainActivity extends Activity {

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    
    private Runnable timeUpdater, statsUpdater;
    private TextView timeText, dateText, batteryStat, ramStat, romStat;
    private View searchContainer, clockContainer, widgetContainer;
    private EditText searchInput, homeSearch;
    private GridView searchResults;
    private LruCache<String, Drawable> iconCache;
    
    private final List<AppInfo> allApps = new ArrayList<>();
    private final Map<String, AppInfo> appMap = new HashMap<>();
    private final List<AppInfo> filteredApps = new ArrayList<>();
    private AppAdapter adapter;
    private int currentTextColor = 0xFFFFFFFF;
    private GestureDetector gestureDetector;
    private DevicePolicyManager devicePolicyManager;
    private ComponentName adminComponent;
    private AppWidgetManager mAppWidgetManager;
    private AppWidgetHost mAppWidgetHost;
    private boolean isSearchVisible = false;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (iconCache != null) iconCache.evictAll();
            loadApps(); 
        }
    };

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level != -1 && scale != -1) {
                int pct = (int) (level * 100 / (float) scale);
                stringBuilder.setLength(0);
                batteryStat.setText(stringBuilder.append("BAT: ").append(pct).append("%"));
            }
        }
    };

    private SharedPreferences prefs;
    private static final String PREF_DOCK_PREFIX = "dock_pkg_";
    private static final String PREF_BLACK_MODE = "black_mode";
    private static final String PREF_HIDDEN_APPS = "hidden_apps";
    private static final String PREF_THEME = "ui_theme";
    private static final String PREF_ADAPTIVE_ICON = "adaptive_icon";
    private static final String PREF_WIDGET_MODE = "widget_mode";
    private static final String PREF_WIDGET_ID = "widget_id";
    private static final String PREF_WIDGET_HEIGHT = "widget_height_val"; // 儲存 dp 值
    private static final String PREF_WIDGET_WIDTH_SCALE = "widget_width_scale"; // 比例 0-10
    private static final String PREF_WIDGET_Y_OFFSET = "widget_y_offset"; // Top margin dp
    private static final String PREF_HIDE_APPS_PIN = "hide_apps_pin";
    private static final String PREF_CUSTOM_ICON_PREFIX = "custom_icon_";
    private static final String PREF_CUSTOM_LABEL_PREFIX = "custom_label_";
    private static final int DOCK_COUNT = 5;
    private static final int REQUEST_PICK_IMAGE = 1001;
    private static final int REQUEST_PICK_APPWIDGET = 9;
    private static final int REQUEST_CREATE_APPWIDGET = 5;
    private static final int APPWIDGET_HOST_ID = 1024;
    private String pendingPackageName = null;
    private String pendingClassName = null;

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

        mAppWidgetManager = AppWidgetManager.getInstance(this);
        mAppWidgetHost = new AppWidgetHost(this, APPWIDGET_HOST_ID);

        // 初始化圖示快取 (使用可用記憶體的 1/8)
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 8;
        iconCache = new LruCache<String, Drawable>(cacheSize) {
            @Override
            protected int sizeOf(String key, Drawable drawable) {
                if (drawable instanceof android.graphics.drawable.BitmapDrawable) {
                    return ((android.graphics.drawable.BitmapDrawable) drawable).getBitmap().getByteCount() / 1024;
                }
                return 1;
            }
        };
        
        // 啟動後台執行緒處理 CPU 監測
        backgroundThread = new HandlerThread("StatsWorker");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        initViews();
        adjustUiToScreen(); // 根據螢幕動態調整大小
        loadApps();
        setupSearch();
        setupGestures();

        if (mAppWidgetHost != null) mAppWidgetHost.startListening();
        applyWidgetMode();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        registerReceiver(packageReceiver, filter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mAppWidgetHost != null) mAppWidgetHost.startListening();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mAppWidgetHost != null) mAppWidgetHost.stopListening();
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
        batteryStat = findViewById(R.id.battery_stat);
        ramStat = findViewById(R.id.ram_stat);
        romStat = findViewById(R.id.rom_stat);
        searchContainer = findViewById(R.id.search_container);
        searchInput = findViewById(R.id.search_input);
        homeSearch = findViewById(R.id.home_search);
        searchResults = findViewById(R.id.search_results);
        clockContainer = findViewById(R.id.clock_container);
        widgetContainer = findViewById(R.id.widget_container);

        widgetContainer.setOnLongClickListener(v -> {
            showWidgetSettingsDialog();
            return true;
        });

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
        View root = findViewById(R.id.root_container);
        View homeContent = findViewById(R.id.home_content);
        View.OnLongClickListener mainLongClick = v -> {
            showSettings();
            return true;
        };
        root.setOnLongClickListener(mainLongClick);
        homeContent.setOnLongClickListener(mainLongClick);
        
        applyBackgroundMode();
        applyTheme();
        applyWidgetMode();
    }

    private void applyWidgetMode() {
        boolean isWidgetMode = prefs.getBoolean(PREF_WIDGET_MODE, false);
        if (clockContainer != null) {
            clockContainer.setVisibility(isWidgetMode ? View.GONE : View.VISIBLE);
        }
        if (widgetContainer != null) {
            widgetContainer.setVisibility(isWidgetMode ? View.VISIBLE : View.GONE);
            if (isWidgetMode) {
                int widgetId = prefs.getInt(PREF_WIDGET_ID, -1);
                if (widgetId != -1) {
                    loadWidget(widgetId);
                }
            }
        }
    }

    private void setupGestures() {
        devicePolicyManager = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(this, AdminReceiver.class);
        
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (isTouchOnWidget(e)) return false;
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
                // 如果滑動是從 Widget 內部開始的，且該滑動很可能是為了操作 Widget，則不觸發 Launcher 功能
                // 這裡我們允許 AOSP 上滑手勢穿透「空的容器區域」，只在有實體 Widget 時進行判斷
                if (isTouchOnWidget(e1)) return false;
                
                // 下滑偵測 (向下速度大於門檻且為垂直向)
                if (velocityY > 500 && Math.abs(velocityY) > Math.abs(velocityX)) {
                    expandNotifications();
                    return true;
                }
                // 上滑偵測 (向上速度大於門檻且為垂直向) - 僅在 AOSP Style 開啟
                if (velocityY < -500 && Math.abs(velocityY) > Math.abs(velocityX)) {
                    if (prefs.getInt(PREF_THEME, 0) == 3) {
                        showSearch();
                        return true;
                    }
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

    private final Rect hitRect = new Rect();
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (!isSearchVisible) {
            gestureDetector.onTouchEvent(ev);
        }
        return super.dispatchTouchEvent(ev);
    }

    private boolean isTouchOnWidget(MotionEvent ev) {
        if (prefs.getBoolean(PREF_WIDGET_MODE, false) && widgetContainer != null && widgetContainer.getVisibility() == View.VISIBLE) {
            ViewGroup container = (ViewGroup) widgetContainer;
            if (container.getChildCount() > 0) {
                View widget = container.getChildAt(0);
                widget.getGlobalVisibleRect(hitRect);
                return hitRect.contains((int)ev.getRawX(), (int)ev.getRawY());
            }
        }
        return false;
    }

    public static class AdminReceiver extends android.app.admin.DeviceAdminReceiver {}

    private void showSettings() {
        android.app.AlertDialog.Builder builder = getDialogBuilder();
        builder.setTitle("Settings");
        
        boolean isBlackMode = prefs.getBoolean(PREF_BLACK_MODE, false);
        String blackModeText = isBlackMode ? "Switch to Wallpaper Mode" : "Switch to Black Mode (AMOLED Save)";
        
        // 僅在 Android 8.0 以前顯示 Adaptive Icon 選項
        boolean isPreOreo = android.os.Build.VERSION.SDK_INT < 26;
        int adaptiveMode = prefs.getInt(PREF_ADAPTIVE_ICON, 0); // 0: Off, 1: Circle, 2: Rounded
        String adaptiveText = (adaptiveMode == 0) ? "Enable Adaptive Icons" : "Disable Adaptive Icons";

        boolean isWidgetMode = prefs.getBoolean(PREF_WIDGET_MODE, false);
        String widgetModeText = isWidgetMode ? "None Widget Mode" : "Widget Mode";

        List<String> optionsList = new ArrayList<>();
        optionsList.add(blackModeText);
        optionsList.add(widgetModeText);
        if (isWidgetMode) {
            optionsList.add("Widget Settings");
        }
        optionsList.add("Themes");
        optionsList.add("Wallpaper");
        if (isPreOreo) optionsList.add(adaptiveText);
        optionsList.add("Hide Apps");
        optionsList.add("Hidden Apps");
        if (!isDefaultLauncher()) {
            optionsList.add("Set as Default Launcher");
        }
        
        String[] options = optionsList.toArray(new String[0]);
        
        builder.setItems(options, (dialog, which) -> {
            String selected = options[which];
            if (selected.equals(blackModeText)) {
                prefs.edit().putBoolean(PREF_BLACK_MODE, !isBlackMode).apply();
                applyBackgroundMode();
            } else if (selected.equals(widgetModeText)) {
                prefs.edit().putBoolean(PREF_WIDGET_MODE, !isWidgetMode).apply();
                applyWidgetMode();
            } else if (selected.equals("Widget Settings")) {
                showWidgetSettingsDialog();
            } else if (selected.equals("Themes")) {
                showThemeDialog();
            } else if (selected.equals("Wallpaper")) {
                try {
                    Intent intent = new Intent(Intent.ACTION_SET_WALLPAPER);
                    startActivity(Intent.createChooser(intent, "Select Wallpaper"));
                } catch (Exception e) {
                    // Fallback to general display settings if wallpaper picker fails
                    try {
                        startActivity(new Intent(Settings.ACTION_DISPLAY_SETTINGS));
                    } catch (Exception ignored) {}
                }
            } else if (selected.equals(adaptiveText)) {
                if (adaptiveMode == 0) {
                    showAdaptiveShapeDialog();
                } else {
                    prefs.edit().putInt(PREF_ADAPTIVE_ICON, 0).apply();
                    refreshIcons();
                }
            } else if (selected.equals("Hide Apps")) {
                handleHideAppsAccess();
            } else if (selected.equals("Hidden Apps")) {
                handleHiddenAppsLauncherAccess();
            } else if (selected.equals("Set as Default Launcher")) {
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

    private void showAdaptiveShapeDialog() {
        String[] shapes = {"Circle", "Rounded Square"};
        getDialogBuilder()
            .setTitle("Select Shape")
            .setItems(shapes, (dialog, which) -> {
                prefs.edit().putInt(PREF_ADAPTIVE_ICON, which + 1).apply();
                refreshIcons();
            })
            .show();
    }

    private void refreshIcons() {
        if (iconCache != null) iconCache.evictAll();
        setupDock();
        if (adapter != null) adapter.notifyDataSetChanged();
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
        batteryStat.setTextColor(textColor);
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
        ViewGroup.MarginLayoutParams clockParams = (ViewGroup.MarginLayoutParams) clockContainer.getLayoutParams();
        clockParams.topMargin = (int) (density * (theme == 3 ? 120 : 80));
        clockContainer.setLayoutParams(clockParams);

        // 如果目前是 Widget 模式，覆蓋時鐘的可見性
        applyWidgetMode();

        // Toggle Stats Widget
        LinearLayout statsWidget = findViewById(R.id.stats_widget);
        statsWidget.setVisibility(theme == 3 ? View.GONE : View.VISIBLE);
        
        // AOSP Style 隱藏主畫面搜尋框
        homeSearch.setVisibility(theme == 3 ? View.GONE : View.VISIBLE);

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
        searchResults.setNumColumns(theme == 3 ? 5 : 1);
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

    private Drawable getAppIcon(String pkg, String cls) {
        String cacheKey = (cls != null) ? pkg + "/" + cls : pkg;
        Drawable cached = iconCache.get(cacheKey);
        if (cached != null) return cached;

        Drawable finalIcon = null;
        String customPath = prefs.getString(PREF_CUSTOM_ICON_PREFIX + cacheKey, null);
        if (customPath == null) customPath = prefs.getString(PREF_CUSTOM_ICON_PREFIX + pkg, null); // 相容舊版僅包名儲存方式

        try {
            if (customPath != null && new File(customPath).exists()) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.RGB_565;
                Bitmap bmp = BitmapFactory.decodeFile(customPath, options);
                if (bmp != null) {
                    finalIcon = new android.graphics.drawable.BitmapDrawable(getResources(), bmp);
                }
            }

            if (finalIcon == null) {
                if (cls != null) {
                    finalIcon = getPackageManager().getActivityIcon(new ComponentName(pkg, cls));
                } else {
                    finalIcon = getPackageManager().getApplicationIcon(pkg);
                }
            }

            int adaptiveMode = prefs.getInt(PREF_ADAPTIVE_ICON, 0);
            if (adaptiveMode != 0) {
                finalIcon = wrapInAdaptive(finalIcon, adaptiveMode);
            }
        } catch (Exception e) {
            try { finalIcon = getPackageManager().getApplicationIcon(pkg); } catch (Exception ignored) {}
        }

        if (finalIcon == null) {
            finalIcon = getResources().getDrawable(android.R.drawable.sym_def_app_icon);
        }

        iconCache.put(cacheKey, finalIcon);
        return finalIcon;
    }

    private Drawable wrapInAdaptive(Drawable original, int mode) {
        if (original == null) return null;
        
        // 取得標準尺寸 (以 48dp 為基準，可隨螢幕密度調整)
        int size = (int) (getResources().getDisplayMetrics().density * 52);
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // 根據主題選擇底板顏色
        int theme = prefs.getInt(PREF_THEME, 0);
        int bgColor;
        if (theme == 1) bgColor = 0xFF222222;      // OLED: 深灰
        else if (theme == 2) bgColor = 0xFFF0F0F0; // Snow: 淺灰
        else if (theme == 3) bgColor = 0xFFFFFFFF; // AOSP: 純白
        else bgColor = 0x66FFFFFF;                 // Default: 磨砂白

        paint.setColor(bgColor);
        if (mode == 1) { // Circle
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
        } else { // Rounded Square (Squircle)
            float radius = size * 0.23f;
            canvas.drawRoundRect(new RectF(0, 0, size, size), radius, radius, paint);
        }

        // 縮放原始 Icon (約 62%) 並置中疊加
        int inset = (int) (size * 0.19f);
        original.setBounds(inset, inset, size - inset, size - inset);
        original.draw(canvas);

        return new android.graphics.drawable.BitmapDrawable(getResources(), output);
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

    private void handleHideAppsAccess() {
        String savedPin = prefs.getString(PREF_HIDE_APPS_PIN, null);
        if (savedPin == null) {
            // 未設定過密碼，先詢問是否設定
            getDialogBuilder()
                .setTitle("Privacy")
                .setMessage("Would you like to set a PIN to protect your hidden apps?")
                .setPositiveButton("Set PIN", (d, w) -> showPinInputDialog(true, false))
                .setNegativeButton("Not Now", (d, w) -> showHideAppsDialog())
                .show();
        } else {
            showPinInputDialog(false, false);
        }
    }

    private void handleHiddenAppsLauncherAccess() {
        String savedPin = prefs.getString(PREF_HIDE_APPS_PIN, null);
        if (savedPin == null) {
            showHiddenAppsLauncherDialog();
        } else {
            showPinInputDialog(false, true);
        }
    }

    private void showPinInputDialog(boolean isSettingNew, boolean forLauncher) {
        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());
        input.setGravity(android.view.Gravity.CENTER);
        
        String title = isSettingNew ? "Set PIN (Numbers only)" : "Enter PIN";
        
        getDialogBuilder()
            .setTitle(title)
            .setView(input)
            .setPositiveButton("OK", (d, w) -> {
                String pin = input.getText().toString();
                if (isSettingNew) {
                    if (!pin.isEmpty()) {
                        prefs.edit().putString(PREF_HIDE_APPS_PIN, pin).apply();
                        showHideAppsDialog();
                    }
                } else {
                    String savedPin = prefs.getString(PREF_HIDE_APPS_PIN, "");
                    if (savedPin.equals(pin)) {
                        if (forLauncher) showHiddenAppsLauncherDialog();
                        else showHideAppsDialog();
                    } else {
                        android.widget.Toast.makeText(this, "Wrong PIN", android.widget.Toast.LENGTH_SHORT).show();
                    }
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showHiddenAppsLauncherDialog() {
        Set<String> hidden = prefs.getStringSet(PREF_HIDDEN_APPS, new HashSet<>());
        if (hidden.isEmpty()) {
            android.widget.Toast.makeText(this, "No hidden apps", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> all = pm.queryIntentActivities(intent, 0);
        final List<AppInfo> hiddenApps = new ArrayList<>();
        for (ResolveInfo ri : all) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue; // 排除自身
            if (hidden.contains(pkg)) {
                hiddenApps.add(new AppInfo(ri.loadLabel(pm).toString(), pkg, ri.activityInfo.name));
            }
        }

        if (hiddenApps.isEmpty()) {
            android.widget.Toast.makeText(this, "No hidden apps found", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        Collections.sort(hiddenApps, (a, b) -> a.label.compareToIgnoreCase(b.label));

        android.widget.ListAdapter adapter = new android.widget.BaseAdapter() {
            @Override public int getCount() { return hiddenApps.size(); }
            @Override public Object getItem(int p) { return hiddenApps.get(p); }
            @Override public long getItemId(int p) { return p; }
            @Override public View getView(int p, View v, ViewGroup parent) {
                if (v == null) v = getLayoutInflater().inflate(R.layout.item_app, parent, false);
                AppInfo app = hiddenApps.get(p);
                TextView label = v.findViewById(R.id.item_label);
                label.setText(app.label);
                label.setTextColor(currentTextColor);
                ImageView icon = v.findViewById(R.id.item_icon);
                icon.setImageDrawable(getAppIcon(app.packageName, app.className));
                icon.setOutlineProvider(new ViewOutlineProvider() {
                    @Override public void getOutline(View view, Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), view.getHeight() * 0.2f);
                    }
                });
                icon.setClipToOutline(true);
                return v;
            }
        };

        getDialogBuilder()
            .setTitle("Hidden Launcher")
            .setAdapter(adapter, (d, which) -> {
                AppInfo app = hiddenApps.get(which);
                launchApp(app.packageName, app.className);
            })
            .show();
    }

    private void showHideAppsDialog() {
        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> allInstalledRI = pm.queryIntentActivities(intent, 0);
        Collections.sort(allInstalledRI, new ResolveInfo.DisplayNameComparator(pm));

        final List<AppInfo> appsForDialog = new ArrayList<>();
        for (ResolveInfo ri : allInstalledRI) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue; // 排除自身
            appsForDialog.add(new AppInfo(
                ri.loadLabel(pm).toString(),
                pkg,
                ri.activityInfo.name
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
                icon.setImageDrawable(getAppIcon(app.packageName, app.className));
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
            .setNeutralButton(prefs.getString(PREF_HIDE_APPS_PIN, null) == null ? "Set PIN" : "Remove PIN", (dialog, which) -> {
                if (prefs.getString(PREF_HIDE_APPS_PIN, null) == null) {
                    showPinInputDialog(true, false);
                } else {
                    prefs.edit().remove(PREF_HIDE_APPS_PIN).apply();
                    android.widget.Toast.makeText(this, "PIN Removed", android.widget.Toast.LENGTH_SHORT).show();
                }
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
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_PICK_APPWIDGET) {
                configureWidget(data);
            } else if (requestCode == REQUEST_CREATE_APPWIDGET) {
                createWidget(data);
            } else if (requestCode == REQUEST_PICK_IMAGE && data != null && pendingPackageName != null) {
                showCropDialog(data.getData(), pendingPackageName, pendingClassName);
                pendingPackageName = null;
                pendingClassName = null;
            }
        } else if (resultCode == RESULT_CANCELED && data != null) {
            int appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
            if (appWidgetId != -1) mAppWidgetHost.deleteAppWidgetId(appWidgetId);
        }
    }

    private void selectWidget() {
        int appWidgetId = mAppWidgetHost.allocateAppWidgetId();
        Intent pickIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK);
        pickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        startActivityForResult(pickIntent, REQUEST_PICK_APPWIDGET);
    }

    private void configureWidget(Intent data) {
        int appWidgetId = data.getExtras().getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        AppWidgetProviderInfo appWidgetInfo = mAppWidgetManager.getAppWidgetInfo(appWidgetId);
        if (appWidgetInfo.configure != null) {
            Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
            intent.setComponent(appWidgetInfo.configure);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            startActivityForResult(intent, REQUEST_CREATE_APPWIDGET);
        } else {
            createWidget(data);
        }
    }

    private void createWidget(Intent data) {
        int appWidgetId = data.getExtras().getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        prefs.edit().putInt(PREF_WIDGET_ID, appWidgetId).apply();
        loadWidget(appWidgetId);
    }

    private void showWidgetSettingsDialog() {
        int widgetId = prefs.getInt(PREF_WIDGET_ID, -1);
        String chooseOrChange = (widgetId == -1) ? "Choose Widget" : "Change Widget";

        List<String> optionsList = new ArrayList<>();
        optionsList.add(chooseOrChange);
        if (widgetId != -1) {
            optionsList.add("Set Size");
            optionsList.add("Set Coordinates");
            optionsList.add("Delete");
        }
        optionsList.add("Launcher Settings");
        
        String[] options = optionsList.toArray(new String[0]);

        getDialogBuilder()
            .setTitle("Widget Management")
            .setItems(options, (dialog, which) -> {
                String selected = options[which];
                if (selected.equals(chooseOrChange)) {
                    selectWidget();
                } else if (selected.equals("Set Size")) {
                    showWidgetSizeMenu();
                } else if (selected.equals("Set Coordinates")) {
                    showWidgetCoordinatesMenu();
                } else if (selected.equals("Delete")) {
                    mAppWidgetHost.deleteAppWidgetId(widgetId);
                    prefs.edit().remove(PREF_WIDGET_ID).apply();

                    // 核心修復：立即從畫面上移除 Widget 並恢復提示
                    ViewGroup container = (ViewGroup) widgetContainer;
                    container.removeAllViews();
                    TextView hint = new TextView(this);
                    hint.setText("Tap to add a widget");
                    hint.setTextColor(0x44FFFFFF);
                    hint.setGravity(android.view.Gravity.CENTER);
                    container.addView(hint);

                    applyWidgetMode();
                } else {
                    showSettings();
                }
            }).show();
    }

    private void showWidgetSizeMenu() {
        String[] options = {"Increase Height", "Decrease Height", "Increase Width", "Decrease Width"};
        getDialogBuilder()
            .setTitle("Set Size")
            .setItems(options, (dialog, which) -> {
                int h = prefs.getInt(PREF_WIDGET_HEIGHT, 250);
                int wScale = prefs.getInt(PREF_WIDGET_WIDTH_SCALE, 10);
                if (which == 0) prefs.edit().putInt(PREF_WIDGET_HEIGHT, Math.min(h + 50, 800)).apply();
                else if (which == 1) prefs.edit().putInt(PREF_WIDGET_HEIGHT, Math.max(h - 50, 50)).apply();
                else if (which == 2) prefs.edit().putInt(PREF_WIDGET_WIDTH_SCALE, Math.min(wScale + 1, 10)).apply();
                else if (which == 3) prefs.edit().putInt(PREF_WIDGET_WIDTH_SCALE, Math.max(wScale - 1, 3)).apply();
                applyWidgetMode();
                showWidgetSizeMenu(); // 連續調整
            })
            .setPositiveButton("Done", null)
            .show();
    }

    private void showWidgetCoordinatesMenu() {
        String[] options = {"Move Down", "Move Up"};
        getDialogBuilder()
            .setTitle("Set Coordinates")
            .setItems(options, (dialog, which) -> {
                int y = prefs.getInt(PREF_WIDGET_Y_OFFSET, 0);
                if (which == 0) {
                    // 防呆：限制設定上限 (大約 300dp)，避免使用者將 Widget 推得太深
                    if (y < 300) prefs.edit().putInt(PREF_WIDGET_Y_OFFSET, y + 20).apply();
                    else android.widget.Toast.makeText(this, "Maximum depth reached", android.widget.Toast.LENGTH_SHORT).show();
                }
                else if (which == 1) prefs.edit().putInt(PREF_WIDGET_Y_OFFSET, Math.max(y - 20, 0)).apply();
                applyWidgetMode();
                showWidgetCoordinatesMenu(); // 連續調整
            })
            .setPositiveButton("Done", null)
            .show();
    }

    private void loadWidget(int appWidgetId) {
        AppWidgetProviderInfo appWidgetInfo = mAppWidgetManager.getAppWidgetInfo(appWidgetId);
        if (appWidgetInfo == null) return;

        ViewGroup container = (ViewGroup) widgetContainer;
        AppWidgetHostView hostView = null;

        // 智慧複用：如果容器內已有同 ID 的 Widget，則不需要重新 createView
        if (container.getChildCount() > 0 && container.getChildAt(0) instanceof AppWidgetHostView) {
            AppWidgetHostView existing = (AppWidgetHostView) container.getChildAt(0);
            if (existing.getAppWidgetId() == appWidgetId) {
                hostView = existing;
            }
        }

        if (hostView == null) {
            hostView = mAppWidgetHost.createView(this, appWidgetId, appWidgetInfo);
            hostView.setAppWidget(appWidgetId, appWidgetInfo);
            container.removeAllViews();
            container.addView(hostView);
        }

        float density = getResources().getDisplayMetrics().density;
        int heightPx = (int)(prefs.getInt(PREF_WIDGET_HEIGHT, 250) * density);
        int yOffsetPx = (int)(prefs.getInt(PREF_WIDGET_Y_OFFSET, 0) * density);
        int wScale = prefs.getInt(PREF_WIDGET_WIDTH_SCALE, 10);

        // 套用大小與座標 (Margin)
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) container.getLayoutParams();

        // 安全限制：y_offset 不應超過螢幕總高度的 1/4，防止壓迫搜尋框
        int maxAllowedOffsetPx = (int)(getResources().getDisplayMetrics().heightPixels * 0.25f);
        int safeYOffsetPx = Math.min(yOffsetPx, maxAllowedOffsetPx);

        lp.topMargin = (int)(16 * density) + safeYOffsetPx;
        lp.width = (int)(getResources().getDisplayMetrics().widthPixels * (wScale / 10.0f));

        if (lp instanceof RelativeLayout.LayoutParams) {
            RelativeLayout.LayoutParams rlp = (RelativeLayout.LayoutParams) lp;
            rlp.addRule(RelativeLayout.CENTER_HORIZONTAL);
            rlp.addRule(RelativeLayout.ABOVE, R.id.home_search); // 始終保持在搜尋框上方，防止重疊
        }
        container.setLayoutParams(lp);

        ViewGroup.LayoutParams vlp = hostView.getLayoutParams();
        vlp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        vlp.height = heightPx;
        hostView.setLayoutParams(vlp);

        // 通知 Widget 它的可用尺寸
        Bundle options = new Bundle();
        int widthDp = (int)(lp.width / density);
        int heightDp = (int)(heightPx / density);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp);
        hostView.updateAppWidgetOptions(options);

        hostView.setOnLongClickListener(v -> {
            showWidgetSettingsDialog();
            return true;
        });
    }

    private void showCropDialog(Uri uri, final String pkg, final String cls) {
        try {
            // 1. 採樣縮放：讀取圖片尺寸
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(is, null, options);
            }
            
            // 2. 計算採樣率，防止 4K 等大圖導致 OOM
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            options.inSampleSize = 1;
            while (options.outWidth / options.inSampleSize > screenWidth * 2) options.inSampleSize *= 2;
            options.inJustDecodeBounds = false;
            options.inPreferredConfig = Bitmap.Config.RGB_565; // 節省記憶體
            
            Bitmap sourceBmp;
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                sourceBmp = BitmapFactory.decodeStream(is, null, options);
            }
            if (sourceBmp == null) return;

            final Bitmap bmp = sourceBmp;
            final Matrix matrix = new Matrix();
            final float[] lastTouch = new float[2];
            final float[] center = new float[2];
            
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
                    @Override public boolean onScaleBegin(ScaleGestureDetector detector) { return true; }
                    @Override public void onScaleEnd(ScaleGestureDetector detector) { }
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
                    int w = getWidth(); int h = getHeight();
                    int size = (int)(w * 0.7f);
                    int x = (w - size) / 2; int y = (h - size) / 2;
                    if (!initialCentered && w > 0) {
                        float scale = (float) size / Math.min(bmp.getWidth(), bmp.getHeight());
                        matrix.setScale(scale, scale);
                        matrix.postTranslate((w - bmp.getWidth() * scale) / 2f, (h - bmp.getHeight() * scale) / 2f);
                        initialCentered = true;
                    }
                    canvas.drawBitmap(bmp, matrix, null);
                    maskPaint.setColor(0xAA000000);
                    canvas.drawRect(0, 0, w, y, maskPaint);
                    canvas.drawRect(0, y + size, w, h, maskPaint);
                    canvas.drawRect(0, y, x, y + size, maskPaint);
                    canvas.drawRect(x + size, y, w, y + size, maskPaint);
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
                .setPositiveButton("Next", (dialog, which) -> showShapeSelection(bmp, matrix, (int)(cropView.getWidth() * 0.7f), center, pkg, cls))
                .setNegativeButton("Cancel", (dialog, which) -> bmp.recycle())
                .setOnCancelListener(dialog -> bmp.recycle())
                .show();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void showShapeSelection(final Bitmap source, final Matrix matrix, final int cropSize, final float[] center, final String pkg, final String cls) {
        String[] shapes = {"Square", "Circle", "Rounded Square"};
        getDialogBuilder()
            .setTitle("Select Shape")
            .setItems(shapes, (dialog, which) -> {
                performFinalCrop(source, matrix, cropSize, center, which, pkg, cls);
                setupDock();
                if (adapter != null) adapter.notifyDataSetChanged();
            })
            .setOnCancelListener(dialog -> source.recycle())
            .show();
    }

    private void performFinalCrop(Bitmap source, Matrix matrix, int cropSize, float[] center, int shapeType, String pkg, String cls) {
        try {
            Bitmap cropped = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(cropped);
            Matrix finalMatrix = new Matrix(matrix);
            finalMatrix.postTranslate(-center[0], -center[1]);
            canvas.drawBitmap(source, finalMatrix, new Paint(Paint.FILTER_BITMAP_FLAG));
            
            Bitmap output = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888);
            Canvas outputCanvas = new Canvas(output);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            RectF rect = new RectF(0, 0, cropSize, cropSize);
            
            if (shapeType == 1) outputCanvas.drawOval(rect, paint);
            else if (shapeType == 2) outputCanvas.drawRoundRect(rect, cropSize * 0.2f, cropSize * 0.2f, paint);
            else outputCanvas.drawRect(rect, paint);
            
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            outputCanvas.drawBitmap(cropped, 0, 0, paint);
            
            int targetSize = (int) (getResources().getDisplayMetrics().density * 192);
            Bitmap finalBmp = Bitmap.createScaledBitmap(output, targetSize, targetSize, true);
            
            String iconKey = pkg + (cls != null ? "_" + cls : "");
            File iconFile = new File(getFilesDir(), "icon_" + iconKey.hashCode() + ".png");
            try (FileOutputStream fos = new FileOutputStream(iconFile)) {
                finalBmp.compress(Bitmap.CompressFormat.PNG, 90, fos);
            }
            
            String cacheKey = (cls != null) ? pkg + "/" + cls : pkg;
            prefs.edit().putString(PREF_CUSTOM_ICON_PREFIX + cacheKey, iconFile.getAbsolutePath()).apply();
            if (iconCache != null) iconCache.remove(cacheKey);
            
            source.recycle();
            cropped.recycle();
            output.recycle();
            finalBmp.recycle();
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        startUpdaters();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(batteryReceiver);
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
        if (backgroundHandler == null) return;
        backgroundHandler.post(() -> {
            PackageManager pm = getPackageManager();
            Intent i = new Intent(Intent.ACTION_MAIN, null);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> availableActivities = pm.queryIntentActivities(i, 0);
            Set<String> hidden = prefs.getStringSet(PREF_HIDDEN_APPS, new HashSet<>());
            
            final List<AppInfo> newList = new ArrayList<>();
            for (ResolveInfo ri : availableActivities) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName())) continue; // 排除自身
                String cls = ri.activityInfo.name;
                String cacheKey = pkg + "/" + cls;
                if (hidden.contains(pkg) || hidden.contains(cacheKey)) continue;
                String label = prefs.getString(PREF_CUSTOM_LABEL_PREFIX + cacheKey, null);
                if (label == null) label = prefs.getString(PREF_CUSTOM_LABEL_PREFIX + pkg, null); // 退而求其次檢查舊版標籤
                if (label == null) label = ri.loadLabel(pm).toString();
                newList.add(new AppInfo(label, pkg, cls));
            }
            uiHandler.post(() -> {
                Collections.sort(newList, (a, b) -> a.label.compareToIgnoreCase(b.label));
                allApps.clear();
                allApps.addAll(newList);
                appMap.clear();
                for (AppInfo app : newList) {
                    String key = app.packageName + "/" + app.className;
                    appMap.put(key, app);
                }
                setupDock(); // 確保 App 載入完後才設定 Dock
                if (adapter != null) adapter.notifyDataSetChanged();
            });
        });
    }

    private void setupSearch() {
        adapter = new AppAdapter();
        searchResults.setAdapter(adapter);
        searchResults.setOnItemClickListener((parent, view, position, id) -> {
            AppInfo app = filteredApps.get(position);
            launchApp(app.packageName, app.className);
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
                        AppInfo app = filteredApps.get(0);
                        launchApp(app.packageName, app.className);
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
            String dockValue = prefs.getString(PREF_DOCK_PREFIX + i, null);
            String pkg = null, cls = null;

            if (dockValue != null) {
                if (dockValue.contains("/")) {
                    int sep = dockValue.indexOf('/');
                    pkg = dockValue.substring(0, sep);
                    cls = dockValue.substring(sep + 1);
                    if (cls.isEmpty()) cls = null;
                } else {
                    pkg = dockValue;
                }
            }

            // 檢查該 App 是否還存在於系統中
            String currentKey = (pkg != null) ? (cls != null ? pkg + "/" + cls : pkg) : null;
            AppInfo app = (currentKey != null) ? appMap.get(currentKey) : null;
            
            // 如果只有包名 (舊設定)，嘗試找一個預設 Activity
            if (app == null && pkg != null && cls == null) {
                for (AppInfo a : allApps) {
                    if (a.packageName.equals(pkg)) {
                        app = a;
                        cls = a.className;
                        break;
                    }
                }
            }

            // 如果 App 不存在或沒設定，則進行自動分配
            if (app == null) {
                pkg = null; cls = null;
                for (AppInfo a : allApps) {
                    if (a.packageName.toLowerCase().contains(keywords[i])) {
                        app = a;
                        pkg = a.packageName;
                        cls = a.className;
                        break;
                    }
                }
                
                if (app == null && !allApps.isEmpty()) {
                    app = allApps.get(random.nextInt(allApps.size()));
                    pkg = app.packageName;
                    cls = app.className;
                }
                
                if (pkg != null) {
                    prefs.edit().putString(PREF_DOCK_PREFIX + i, pkg + "/" + (cls != null ? cls : "")).apply();
                }
            } else {
                // 確保從 app 中更新 pkg/cls (特別是舊設定遷移過來的)
                pkg = app.packageName;
                cls = app.className;
            }

            int viewId = getResources().getIdentifier("dock_" + (index + 1), "id", getPackageName());
            ImageView iv = findViewById(viewId);

            if (app != null) {
                final String finalPkg = pkg;
                final String finalCls = cls;
                iv.setImageDrawable(getAppIcon(finalPkg, finalCls));
                iv.setOnClickListener(v -> launchApp(finalPkg, finalCls));
                iv.setOnLongClickListener(v -> { showDockMenu(index, finalPkg, finalCls); return true; });
            }
        }
    }

    private void showDockMenu(int index, String pkg, String cls) {
        String[] options = {"Change App", "Change Icon", "Reset Icon", "Change Name", "Reset Name"};
        getDialogBuilder()
            .setTitle("Dock Shortcut")
            .setItems(options, (dialog, which) -> {
                if (which == 0) showAppPickerForDock(index);
                else if (which == 1) {
                    pendingPackageName = pkg;
                    pendingClassName = cls;
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("image/*");
                    startActivityForResult(intent, REQUEST_PICK_IMAGE);
                } else if (which == 2) {
                    String cacheKey = (cls != null) ? pkg + "/" + cls : pkg;
                    prefs.edit().remove(PREF_CUSTOM_ICON_PREFIX + cacheKey).apply();
                    if (iconCache != null) iconCache.remove(cacheKey);
                    setupDock();
                    if (adapter != null) adapter.notifyDataSetChanged();
                } else if (which == 3) {
                    showChangeNameDialog(pkg, cls);
                } else if (which == 4) {
                    String cacheKey = (cls != null && !cls.isEmpty()) ? pkg + "/" + cls : pkg;
                    prefs.edit().remove(PREF_CUSTOM_LABEL_PREFIX + cacheKey).apply();
                    loadApps();
                    if (adapter != null) adapter.notifyDataSetChanged();
                }
            }).show();
    }

    private void showChangeNameDialog(String pkg, String cls) {
        final EditText input = new EditText(this);
        input.setSingleLine();
        String currentLabel = "";
        String cacheKey = (cls != null && !cls.isEmpty()) ? pkg + "/" + cls : pkg;
        
        for (AppInfo app : allApps) {
            if (app.packageName.equals(pkg) && (cls == null || cls.isEmpty() || app.className.equals(cls))) {
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
                    prefs.edit().remove(PREF_CUSTOM_LABEL_PREFIX + cacheKey).apply();
                } else {
                    prefs.edit().putString(PREF_CUSTOM_LABEL_PREFIX + cacheKey, newName).apply();
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
                icon.setImageDrawable(getAppIcon(app.packageName, app.className));

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
            AppInfo selected = allApps.get(which);
            prefs.edit().putString(PREF_DOCK_PREFIX + dockIndex, selected.packageName + "/" + selected.className).apply();
            setupDock();
        });
        builder.show();
    }

    private void launchApp(String pkg, String cls) {
        Intent intent;
        if (cls != null && !cls.isEmpty()) {
            intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setComponent(new ComponentName(pkg, cls));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        } else {
            intent = getPackageManager().getLaunchIntentForPackage(pkg);
        }
        if (intent != null) startActivity(intent);
    }

    private boolean isDefaultLauncher() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo res = getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return res != null && res.activityInfo != null && getPackageName().equals(res.activityInfo.packageName);
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

    private static class AppInfo {
        String label, packageName, className;
        AppInfo(String l, String p, String c) { label = l; packageName = p; className = c; }
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
            LinearLayout layout = (LinearLayout) v;
            
            int theme = prefs.getInt(PREF_THEME, 0);
            if (theme == 3) { // AOSP Grid Mode
                if (layout.getOrientation() != LinearLayout.VERTICAL) {
                    layout.setOrientation(LinearLayout.VERTICAL);
                    layout.setGravity(android.view.Gravity.CENTER);
                    int vPad = (int)(16 * getResources().getDisplayMetrics().density);
                    layout.setPadding(0, vPad, 0, vPad);
                    
                    LinearLayout.LayoutParams lpIcon = (LinearLayout.LayoutParams) icon.getLayoutParams();
                    lpIcon.setMargins(0, 0, 0, 0);
                    icon.setLayoutParams(lpIcon);
                    
                    LinearLayout.LayoutParams lpLabel = (LinearLayout.LayoutParams) label.getLayoutParams();
                    lpLabel.width = ViewGroup.LayoutParams.MATCH_PARENT;
                    lpLabel.weight = 0;
                    lpLabel.setMarginStart(0);
                    label.setLayoutParams(lpLabel);
                    label.setGravity(android.view.Gravity.CENTER);
                    label.setTextSize(12);
                    label.setSingleLine(true);
                    label.setEllipsize(android.text.TextUtils.TruncateAt.END);
                }
            } else { // List Mode
                if (layout.getOrientation() != LinearLayout.HORIZONTAL) {
                    layout.setOrientation(LinearLayout.HORIZONTAL);
                    layout.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    int pSize = (int)(12 * getResources().getDisplayMetrics().density);
                    layout.setPadding(pSize, pSize, pSize, pSize);
                    
                    LinearLayout.LayoutParams lpIcon = (LinearLayout.LayoutParams) icon.getLayoutParams();
                    icon.setLayoutParams(lpIcon);
                    
                    LinearLayout.LayoutParams lpLabel = (LinearLayout.LayoutParams) label.getLayoutParams();
                    lpLabel.width = 0;
                    lpLabel.weight = 1;
                    lpLabel.setMarginStart((int)(16 * getResources().getDisplayMetrics().density));
                    label.setLayoutParams(lpLabel);
                    label.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    label.setTextSize(16);
                    label.setSingleLine(false);
                    label.setEllipsize(null);
                }
            }
            
            label.setText(app.label);
            label.setTextColor(currentTextColor);

            icon.setImageDrawable(getAppIcon(app.packageName, app.className));

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
