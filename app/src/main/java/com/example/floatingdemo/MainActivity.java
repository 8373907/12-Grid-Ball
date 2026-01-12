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
        // 根布局：垂直线性布局
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(Color.WHITE);
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
        btn.setOnClickListener(v -> startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())), REQUEST_CODE));
        rootLayout.addView(btn);
    }

    // 【修改核心】 构建上下分屏布局
    private void showAppSelectionUI() {
        rootLayout.removeAllViews();
        rootLayout.setGravity(Gravity.TOP);

        // 1. 顶部标题 (固定)
        titleView = new TextView(this);
        updateTitle();
        titleView.setTextSize(18);
        titleView.setPadding(30, 30, 30, 10);
        titleView.setTextColor(Color.BLACK);
        titleView.setTypeface(null, Typeface.BOLD);
        rootLayout.addView(titleView);

        // 2. 上半部分：已选应用列表 (Weight 1)
        // 用一个 ScrollView 包裹，防止选多了屏幕不够
        LinearLayout topWrapper = new LinearLayout(this);
        topWrapper.setOrientation(LinearLayout.VERTICAL);
        // layout_weight = 1，分配一半空间
        LinearLayout.LayoutParams topParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        topWrapper.setLayoutParams(topParams);
        topWrapper.setPadding(20, 0, 20, 0);

        // 排序区提示文字
        TextView label = new TextView(this);
        label.setText("【排序区】点击箭头调整顺序 (越靠上，球里越靠前)");
        label.setTextColor(Color.parseColor("#FF5722"));
        label.setTextSize(12);
        label.setPadding(0, 5, 0, 10);
        topWrapper.addView(label);

        ScrollView topScroll = new ScrollView(this);
        selectedContainer = new LinearLayout(this);
        selectedContainer.setOrientation(LinearLayout.VERTICAL);
        selectedContainer.setBackgroundColor(Color.parseColor("#F0F8FF")); // 淡蓝背景
        selectedContainer.setPadding(10, 10, 10, 10);
        topScroll.addView(selectedContainer);
        topWrapper.addView(topScroll);

        rootLayout.addView(topWrapper);

        // 3. 中间部分：保存按钮 (固定)
        Button startButton = new Button(this);
        startButton.setText("保存排序 并 重启悬浮球");
        startButton.setBackgroundColor(Color.parseColor("#4CAF50"));
        startButton.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnParams.setMargins(40, 10, 40, 10);
        startButton.setLayoutParams(btnParams);
        startButton.setOnClickListener(v -> saveAndStartService());
        rootLayout.addView(startButton);

        // 4. 下半部分：所有应用列表 (Weight 1)
        LinearLayout bottomWrapper = new LinearLayout(this);
        bottomWrapper.setOrientation(LinearLayout.VERTICAL);
        // layout_weight = 1，分配另一半空间
        LinearLayout.LayoutParams bottomParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        bottomWrapper.setLayoutParams(bottomParams);
        bottomWrapper.setPadding(20, 0, 20, 0);

        // 搜索框
        searchInput = new EditText(this);
        searchInput.setHint("🔍 搜索应用...");
        searchInput.setBackgroundResource(android.R.drawable.edit_text);
        searchInput.setPadding(20, 20, 20, 20);
        searchInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { filterAppList(s.toString()); }
            public void afterTextChanged(Editable s) {}
        });
        bottomWrapper.addView(searchInput);

        // 列表容器
        loadingBar = new ProgressBar(this);
        bottomWrapper.addView(loadingBar);

        ScrollView bottomScroll = new ScrollView(this);
        appListContainer = new LinearLayout(this);
        appListContainer.setOrientation(LinearLayout.VERTICAL);
        bottomScroll.addView(appListContainer);
        bottomWrapper.addView(bottomScroll);

        rootLayout.addView(bottomWrapper);

        // 5. 底部版本号 (固定)
        TextView v = new TextView(this);
        v.setText("Version: " + getAppVersionName());
        v.setGravity(Gravity.CENTER);
        v.setPadding(0, 10, 0, 10);
        v.setTextColor(Color.LTGRAY);
        rootLayout.addView(v);

        loadInstalledApps();
    }

    // ... (后续逻辑代码几乎不变，为了完整性贴出) ...

    private void loadInstalledApps() {
        SharedPreferences prefs = getSharedPreferences("FloatingConfig", Context.MODE_PRIVATE);
        String savedString = prefs.getString("target_apps", "");
        selectedPackages.clear();
        if (!savedString.isEmpty()) {
            String[] split = savedString.split(",");
            for (String s : split) { if (!s.trim().isEmpty()) selectedPackages.add(s); }
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
            empty.setText("暂无选择，请在下方勾选");
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
        for (AppItem item : allInstalledApps) { if (item.pkg.equals(pkg)) return item; }
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

        int btnSize = 100;
        if (index > 0) {
            Button btnUp = new Button(this);
            btnUp.setText("⬆");
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(btnSize, btnSize);
            lp.setMargins(10, 0, 0, 0);
            btnUp.setLayoutParams(lp);
            btnUp.setOnClickListener(v -> moveItem(index, -1));
            row.addView(btnUp);
        } else {
            View p = new View(this);
            p.setLayoutParams(new LinearLayout.LayoutParams(btnSize, btnSize));
            row.addView(p);
        }

        if (index < selectedPackages.size() - 1) {
            Button btnDown = new Button(this);
            btnDown.setText("⬇");
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(btnSize, btnSize);
            lp.setMargins(10, 0, 0, 0);
            btnDown.setLayoutParams(lp);
            btnDown.setOnClickListener(v -> moveItem(index, 1));
            row.addView(btnDown);
        } else {
            View p = new View(this);
            p.setLayoutParams(new LinearLayout.LayoutParams(btnSize, btnSize));
            row.addView(p);
        }

        Button btnDel = new Button(this);
        btnDel.setText("✕");
        btnDel.setTextColor(Color.RED);
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
            rootLayout.requestFocus();
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
        if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void filterAppList(String query) {
        if (allInstalledApps.isEmpty()) return;
        List<AppItem> filtered = new ArrayList<>();
        String lower = query.toLowerCase();
        for (AppItem item : allInstalledApps) {
            if (item.name.toLowerCase().contains(lower)) filtered.add(item);
        }
        renderAppList(filtered);
    }

    private void saveAndStartService() {
        SharedPreferences prefs = getSharedPreferences("FloatingConfig", Context.MODE_PRIVATE);
        StringBuilder sb = new StringBuilder();
        for (String pkg : selectedPackages) sb.append(pkg).append(",");
        prefs.edit().putString("target_apps", sb.toString()).apply();
        Intent intent = new Intent(this, FloatingService.class);
        stopService(intent);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent);
        else startService(intent);
        Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show();
        moveTaskToBack(true);
    }

    private void updateTitle() {
        if (titleView != null) titleView.setText("已选: " + selectedPackages.size() + "/" + MAX_SELECTION);
    }

    private String getAppVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) { return "Unknown"; }
    }

    private static class AppItem {
        String pkg; String name; Drawable icon;
        AppItem(String p, String n, Drawable i) { pkg = p; name = n; icon = i; }
    }
}