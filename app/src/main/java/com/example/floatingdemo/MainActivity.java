package com.example.floatingdemo;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE = 100;
    private static final int MAX_SELECTION = 12;

    private LinearLayout rootLayout;

    // 两个容器
    private LinearLayout selectedContainer;
    private LinearLayout appListContainer;

    private List<String> selectedPackages = new ArrayList<>();

    private TextView titleView;
    private ProgressBar loadingBar;
    private EditText searchInput;

    private List<AppItem> allInstalledApps = new ArrayList<>();
    private boolean isSelectionUIShowing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(Color.WHITE);

        // 【修复点1】让根布局可以获取焦点，防止EditText自动抢焦点导致滚屏
        rootLayout.setFocusable(true);
        rootLayout.setFocusableInTouchMode(true);

        setContentView(rootLayout);
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkPermissionAndRefreshUI();
    }

    private void checkPermissionAndRefreshUI() {
        if (!Settings.canDrawOverlays(this)) {
            showPermissionUI();
            isSelectionUIShowing = false;
        } else {
            if (!isSelectionUIShowing) {
                showAppSelectionUI();
                isSelectionUIShowing = true;
            }
        }
    }

    private void showPermissionUI() {
        rootLayout.removeAllViews();
        rootLayout.setGravity(Gravity.CENTER);

        TextView tip = new TextView(this);
        tip.setText("请授予悬浮窗权限");
        tip.setTextSize(18);
        rootLayout.addView(tip);

        Button btn = new Button(this);
        btn.setText("去开启");
        btn.setOnClickListener(v -> startActivityForResult(
                new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())),
                REQUEST_CODE));
        rootLayout.addView(btn);
    }

    private void showAppSelectionUI() {
        rootLayout.removeAllViews();
        rootLayout.setGravity(Gravity.TOP);

        ScrollView mainScrollView = new ScrollView(this);
        mainScrollView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        rootLayout.addView(mainScrollView);

        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(30, 30, 30, 30);
        mainScrollView.addView(contentLayout);

        // 1. 标题
        titleView = new TextView(this);
        updateTitle();
        titleView.setTextSize(18);
        titleView.setPadding(0, 0, 0, 20);
        titleView.setTextColor(Color.BLACK);
        titleView.setTypeface(null, Typeface.BOLD);
        contentLayout.addView(titleView);

        // 2. 已选应用排序区
        addSortingSection(contentLayout);

        // 3. 保存按钮
        Button startButton = new Button(this);
        startButton.setText("保存排序 并 重启悬浮球");
        startButton.setBackgroundColor(Color.parseColor("#4CAF50"));
        startButton.setTextColor(Color.WHITE);
        startButton.setPadding(0, 25, 0, 25);
        startButton.setOnClickListener(v -> saveAndStartService());
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnParams.setMargins(0, 30, 0, 30);
        startButton.setLayoutParams(btnParams);
        contentLayout.addView(startButton);

        // 4. 下方搜索框
        searchInput = new EditText(this);
        searchInput.setHint("🔍 搜索下面的应用列表...");
        searchInput.setBackgroundResource(android.R.drawable.edit_text);
        searchInput.setPadding(20, 20, 20, 20);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterAppList(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
        contentLayout.addView(searchInput);

        // 5. 所有应用列表
        loadingBar = new ProgressBar(this);
        contentLayout.addView(loadingBar);

        appListContainer = new LinearLayout(this);
        appListContainer.setOrientation(LinearLayout.VERTICAL);
        contentLayout.addView(appListContainer);

        addVersionFooter(contentLayout);
        loadInstalledApps();
    }

    private void addSortingSection(LinearLayout parent) {
        TextView label = new TextView(this);
        label.setText("【排序区】点击箭头调整顺序 (越靠上，球里越靠前)");
        label.setTextColor(Color.parseColor("#FF5722"));
        label.setTextSize(14);
        label.setPadding(0, 0, 0, 15);
        parent.addView(label);

        selectedContainer = new LinearLayout(this);
        selectedContainer.setOrientation(LinearLayout.VERTICAL);
        selectedContainer.setBackgroundColor(Color.parseColor("#F0F8FF")); // 淡蓝背景
        selectedContainer.setPadding(15, 15, 15, 15);
        parent.addView(selectedContainer);
    }

    private void loadInstalledApps() {
        SharedPreferences prefs = getSharedPreferences("FloatingConfig", Context.MODE_PRIVATE);
        String savedString = prefs.getString("target_apps", "");
        selectedPackages.clear();
        if (!savedString.isEmpty()) {
            String[] split = savedString.split(",");
            for (String s : split) {
                if (!s.trim().isEmpty() && !selectedPackages.contains(s)) {
                    selectedPackages.add(s);
                }
            }
        }
        updateTitle();

        new Thread(() -> {
            PackageManager pm = getPackageManager();
            Intent intent = new Intent(Intent.ACTION_MAIN, null);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> apps = pm.queryIntentActivities(intent, 0);

            allInstalledApps.clear();
            for (ResolveInfo info : apps) {
                String pkg = info.activityInfo.packageName;
                if (pkg.equals(getPackageName())) continue;
                String name = info.loadLabel(pm).toString();
                Drawable icon = info.loadIcon(pm);
                allInstalledApps.add(new AppItem(pkg, name, icon));
            }

            Collections.sort(allInstalledApps, (o1, o2) -> o1.name.compareTo(o2.name));

            runOnUiThread(() -> {
                if (loadingBar != null) ((LinearLayout)loadingBar.getParent()).removeView(loadingBar);
                refreshSortingView();
                renderAppList(allInstalledApps);
            });
        }).start();
    }

    private void refreshSortingView() {
        selectedContainer.removeAllViews();
        if (selectedPackages.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("暂无选择，请在下方勾选应用");
            empty.setPadding(20, 20, 20, 20);
            empty.setTextColor(Color.GRAY);
            selectedContainer.addView(empty);
            return;
        }

        PackageManager pm = getPackageManager();
        for (int i = 0; i < selectedPackages.size(); i++) {
            String pkg = selectedPackages.get(i);
            AppItem item = findAppItem(pkg);
            String name = (item != null) ? item.name : pkg;
            Drawable icon = null;
            try { icon = pm.getApplicationIcon(pkg); } catch (Exception e) {}

            addSortingRow(i, pkg, name, icon);
        }
    }

    private AppItem findAppItem(String pkg) {
        for (AppItem item : allInstalledApps) {
            if (item.pkg.equals(pkg)) return item;
        }
        return null;
    }

    private void addSortingRow(int index, String pkg, String name, Drawable icon) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 10, 0, 10);

        ImageView img = new ImageView(this);
        if (icon != null) img.setImageDrawable(icon);
        LinearLayout.LayoutParams lpImg = new LinearLayout.LayoutParams(80, 80);
        lpImg.setMargins(0, 0, 20, 0);
        img.setLayoutParams(lpImg);
        row.addView(img);

        TextView txt = new TextView(this);
        txt.setText(name);
        txt.setTextColor(Color.BLACK);
        txt.setTextSize(15);
        LinearLayout.LayoutParams lpTxt = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        txt.setLayoutParams(lpTxt);
        row.addView(txt);

        // 统一按钮样式
        int btnSize = 100;

        if (index > 0) {
            Button btnUp = new Button(this);
            btnUp.setText("⬆");
            btnUp.setPadding(0,0,0,0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(btnSize, btnSize);
            lp.setMargins(10, 0, 0, 0);
            btnUp.setLayoutParams(lp);
            btnUp.setOnClickListener(v -> moveItem(index, -1));
            row.addView(btnUp);
        } else {
            View p = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(btnSize, btnSize);
            lp.setMargins(10, 0, 0, 0);
            p.setLayoutParams(lp);
            row.addView(p);
        }

        if (index < selectedPackages.size() - 1) {
            Button btnDown = new Button(this);
            btnDown.setText("⬇");
            btnDown.setPadding(0,0,0,0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(btnSize, btnSize);
            lp.setMargins(10, 0, 0, 0);
            btnDown.setLayoutParams(lp);
            btnDown.setOnClickListener(v -> moveItem(index, 1));
            row.addView(btnDown);
        } else {
            View p = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(btnSize, btnSize);
            lp.setMargins(10, 0, 0, 0);
            p.setLayoutParams(lp);
            row.addView(p);
        }

        Button btnDel = new Button(this);
        btnDel.setText("✕");
        btnDel.setTextColor(Color.RED);
        btnDel.setPadding(0,0,0,0);
        LinearLayout.LayoutParams lpDel = new LinearLayout.LayoutParams(btnSize, btnSize);
        lpDel.setMargins(10, 0, 0, 0);
        btnDel.setLayoutParams(lpDel);
        btnDel.setOnClickListener(v -> {
            selectedPackages.remove(index);
            updateTitle();
            refreshSortingView();
            filterAppList(searchInput.getText().toString());
        });
        row.addView(btnDel);

        selectedContainer.addView(row);
    }

    private void moveItem(int index, int offset) {
        int newIndex = index + offset;
        if (newIndex >= 0 && newIndex < selectedPackages.size()) {
            Collections.swap(selectedPackages, index, newIndex);
            refreshSortingView();
        }
    }

    private void renderAppList(List<AppItem> apps) {
        appListContainer.removeAllViews();
        for (AppItem item : apps) {
            boolean isChecked = selectedPackages.contains(item.pkg);
            addSelectableRow(item, isChecked);
        }
    }

    private void addSelectableRow(AppItem item, boolean isChecked) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 15, 0, 15);

        CheckBox cb = new CheckBox(this);
        cb.setChecked(isChecked);
        cb.setClickable(false);
        row.addView(cb);

        ImageView img = new ImageView(this);
        img.setImageDrawable(item.icon);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(80, 80);
        lp.setMargins(20, 0, 20, 0);
        img.setLayoutParams(lp);
        row.addView(img);

        TextView text = new TextView(this);
        text.setText(item.name);
        text.setTextColor(Color.BLACK);
        text.setTextSize(16);
        row.addView(text);

        row.setOnClickListener(v -> {
            // 【修复点2】点击列表时，强制将焦点抢回给根布局
            // 这样EditText就不会因为自动获取焦点而导致滚屏了
            rootLayout.requestFocus();

            // 顺便隐藏键盘，如果键盘是打开状态的话
            hideKeyboard(v);

            if (selectedPackages.contains(item.pkg)) {
                selectedPackages.remove(item.pkg);
                cb.setChecked(false);
            } else {
                if (selectedPackages.size() >= MAX_SELECTION) {
                    Toast.makeText(this, "最多选择 " + MAX_SELECTION + " 个", Toast.LENGTH_SHORT).show();
                    return;
                }
                selectedPackages.add(item.pkg);
                cb.setChecked(true);
            }
            updateTitle();
            refreshSortingView();
        });

        appListContainer.addView(row);
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void filterAppList(String query) {
        if (allInstalledApps.isEmpty()) return;
        List<AppItem> filtered = new ArrayList<>();
        String lower = query.toLowerCase();
        for (AppItem item : allInstalledApps) {
            if (item.name.toLowerCase().contains(lower)) {
                filtered.add(item);
            }
        }
        renderAppList(filtered);
    }

    private void saveAndStartService() {
        SharedPreferences prefs = getSharedPreferences("FloatingConfig", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        StringBuilder sb = new StringBuilder();
        for (String pkg : selectedPackages) {
            sb.append(pkg).append(",");
        }
        editor.putString("target_apps", sb.toString());
        editor.apply();

        Intent intent = new Intent(this, FloatingService.class);
        stopService(intent);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show();
        moveTaskToBack(true);
    }

    private void updateTitle() {
        if (titleView != null) titleView.setText("已选: " + selectedPackages.size() + "/" + MAX_SELECTION);
    }

    // =================================================================
    // 【修改点】 自动读取 build.gradle 中的版本号
    // =================================================================
    private void addVersionFooter(LinearLayout parent) {
        TextView v = new TextView(this);
        // 调用下面的 getAppVersionName() 方法
        v.setText("Version: " + getAppVersionName());
        v.setGravity(Gravity.CENTER);
        v.setPadding(0,30,0,30);
        v.setTextColor(Color.LTGRAY);
        parent.addView(v);
    }

    // 新增：读取系统版本号的方法
    private String getAppVersionName() {
        try {
            PackageManager pm = getPackageManager();
            PackageInfo pi = pm.getPackageInfo(getPackageName(), 0);
            // 这里返回的就是 build.gradle 里的 versionName
            return pi.versionName;
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private static class AppItem {
        String pkg; String name; Drawable icon;
        AppItem(String p, String n, Drawable i) { pkg = p; name = n; icon = i; }
    }
}