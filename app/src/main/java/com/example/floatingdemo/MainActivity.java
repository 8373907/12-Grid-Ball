package com.example.floatingdemo;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE = 100;
    private static final int MAX_SELECTION = 12;

    private LinearLayout rootLayout;
    private LinearLayout appListContainer;
    private Set<String> selectedPackages = new HashSet<>();
    private TextView titleView;
    private ProgressBar loadingBar;

    // 【新增】用来标记当前显示的是哪个界面，防止重复刷新
    private boolean isSelectionUIShowing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(Color.WHITE);
        rootLayout.setPadding(30, 30, 30, 30);
        setContentView(rootLayout);

        // 注意：onCreate 里我们不再直接做逻辑判断了
        // 把判断逻辑移到了 onResume 里，这样每次打开APP都会执行
    }

    // =================================================================================
    // 【关键修改】 onResume：每次APP回到前台（点开图标）都会执行这里
    // =================================================================================
    @Override
    protected void onResume() {
        super.onResume();
        checkPermissionAndRefreshUI();
    }

    // 检查权限并刷新界面的核心逻辑
    private void checkPermissionAndRefreshUI() {
        if (!Settings.canDrawOverlays(this)) {
            // 1. 如果没有权限 -> 强制显示权限申请界面
            showPermissionUI();
            isSelectionUIShowing = false; // 标记当前不是选择界面
        } else {
            // 2. 如果有权限
            // 只有当当前显示的 *不是* APP选择界面时，才去加载
            // (防止你已经在这个界面了，它还不停地刷新闪烁)
            if (!isSelectionUIShowing) {
                showAppSelectionUI();
                isSelectionUIShowing = true; // 标记当前已经是选择界面了
            }
        }
    }

    private void showPermissionUI() {
        rootLayout.removeAllViews();
        rootLayout.setGravity(Gravity.CENTER);

        TextView tipText = new TextView(this);
        tipText.setText(getString(R.string.perm_welcome));
        tipText.setTextSize(18);
        tipText.setTextColor(Color.BLACK);
        tipText.setGravity(Gravity.CENTER);
        tipText.setPadding(0, 0, 0, 50);
        rootLayout.addView(tipText);

        Button btnPermission = new Button(this);
        btnPermission.setText(getString(R.string.btn_open_perm));
        btnPermission.setBackgroundColor(Color.parseColor("#2196F3"));
        btnPermission.setTextColor(Color.WHITE);
        btnPermission.setPadding(40, 20, 40, 20);

        btnPermission.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_CODE);
        });
        rootLayout.addView(btnPermission);

        addVersionFooter();
    }

    private void showAppSelectionUI() {
        rootLayout.removeAllViews();
        rootLayout.setGravity(Gravity.TOP);

        titleView = new TextView(this);
        updateTitle();
        titleView.setTextSize(18);
        titleView.setPadding(0, 0, 0, 20);
        titleView.setTextColor(Color.BLACK);
        rootLayout.addView(titleView);

        loadingBar = new ProgressBar(this);
        rootLayout.addView(loadingBar);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        scrollView.setLayoutParams(scrollParams);

        appListContainer = new LinearLayout(this);
        appListContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(appListContainer);
        rootLayout.addView(scrollView);

        Button startButton = new Button(this);
        startButton.setText(getString(R.string.btn_save_start));
        startButton.setBackgroundColor(Color.parseColor("#4CAF50"));
        startButton.setTextColor(Color.WHITE);
        startButton.setPadding(20, 20, 20, 20);

        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnParams.setMargins(0, 20, 0, 20);
        startButton.setLayoutParams(btnParams);

        startButton.setOnClickListener(v -> saveAndStartService());
        rootLayout.addView(startButton);

        addVersionFooter();
        loadInstalledApps();
    }

    private void addVersionFooter() {
        TextView versionText = new TextView(this);
        versionText.setText(getString(R.string.version_prefix) + getAppVersionName());
        versionText.setTextSize(12);
        versionText.setTextColor(Color.GRAY);
        versionText.setGravity(Gravity.CENTER);
        versionText.setPadding(0, 10, 0, 10);
        rootLayout.addView(versionText);
    }

    private String getAppVersionName() {
        try {
            PackageManager pm = getPackageManager();
            PackageInfo pi = pm.getPackageInfo(getPackageName(), 0);
            return pi.versionName;
        } catch (Exception e) {
            return "1.0";
        }
    }

    private void loadInstalledApps() {
        SharedPreferences prefs = getSharedPreferences("FloatingConfig", Context.MODE_PRIVATE);
        String savedString = prefs.getString("target_apps", "");
        if (!savedString.isEmpty()) {
            String[] split = savedString.split(",");
            Collections.addAll(selectedPackages, split);
        }
        updateTitle();

        new Thread(() -> {
            PackageManager pm = getPackageManager();
            Intent intent = new Intent(Intent.ACTION_MAIN, null);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);

            List<ResolveInfo> apps = pm.queryIntentActivities(intent, 0);
            List<AppItem> loadedItems = new ArrayList<>();

            for (ResolveInfo info : apps) {
                String pkgName = info.activityInfo.packageName;
                if (pkgName.equals(getPackageName())) continue;

                String appLabel = info.loadLabel(pm).toString();
                Drawable icon = info.loadIcon(pm);
                loadedItems.add(new AppItem(pkgName, appLabel, icon));
            }

            runOnUiThread(() -> {
                if (loadingBar != null) rootLayout.removeView(loadingBar);
                for (AppItem item : loadedItems) {
                    addAppRow(item.pkg, item.name, item.icon);
                }
            });
        }).start();
    }

    private static class AppItem {
        String pkg;
        String name;
        Drawable icon;
        AppItem(String pkg, String name, Drawable icon) {
            this.pkg = pkg;
            this.name = name;
            this.icon = icon;
        }
    }

    private void addAppRow(String pkg, String name, Drawable icon) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 20, 0, 20);
        row.setBackgroundResource(android.R.drawable.list_selector_background);

        CheckBox checkBox = new CheckBox(this);
        checkBox.setChecked(selectedPackages.contains(pkg));
        checkBox.setClickable(false);
        checkBox.setFocusable(false);
        row.addView(checkBox);

        ImageView img = new ImageView(this);
        img.setImageDrawable(icon);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(90, 90);
        lp.setMargins(30, 0, 30, 0);
        img.setLayoutParams(lp);
        row.addView(img);

        TextView text = new TextView(this);
        text.setText(name);
        text.setTextColor(Color.BLACK);
        text.setTextSize(16);
        row.addView(text);

        row.setOnClickListener(v -> {
            boolean isCurrentlyChecked = checkBox.isChecked();
            if (!isCurrentlyChecked) {
                if (selectedPackages.size() >= MAX_SELECTION) {
                    Toast.makeText(MainActivity.this, getString(R.string.toast_max_limit, MAX_SELECTION), Toast.LENGTH_SHORT).show();
                    return;
                }
                selectedPackages.add(pkg);
                checkBox.setChecked(true);
            } else {
                selectedPackages.remove(pkg);
                checkBox.setChecked(false);
            }
            updateTitle();
        });

        appListContainer.addView(row);
    }

    private void updateTitle() {
        if (titleView != null) {
            titleView.setText(getString(R.string.title_select_apps, selectedPackages.size(), MAX_SELECTION));
        }
    }

    private void saveAndStartService() {
        SharedPreferences prefs = getSharedPreferences("FloatingConfig", Context.MODE_PRIVATE);
        StringBuilder sb = new StringBuilder();
        for (String pkg : selectedPackages) {
            sb.append(pkg).append(",");
        }
        prefs.edit().putString("target_apps", sb.toString()).apply();

        Intent intent = new Intent(this, FloatingService.class);
        stopService(intent);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        Toast.makeText(this, getString(R.string.toast_save_success), Toast.LENGTH_SHORT).show();
        moveTaskToBack(true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE) {
            // 这里其实不需要做什么了，因为从设置页回来会触发 onResume
            // onResume 会自动再次检查权限并刷新界面
        }
    }
}