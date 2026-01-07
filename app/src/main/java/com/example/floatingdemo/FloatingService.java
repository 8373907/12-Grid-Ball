package com.example.floatingdemo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class FloatingService extends Service {

    private WindowManager windowManager;
    private View floatingBallView;
    private WindowManager.LayoutParams ballParams;

    private LinearLayout menuLayout;
    private WindowManager.LayoutParams menuParams;
    private boolean isMenuVisible = false;

    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable hideRunnable;
    private boolean isHidden = false;

    // 【功能点4】自动抖动防止烧屏
    // 使用独立的 Handler 来处理防烧屏的定时任务
    private Handler burnInHandler = new Handler(Looper.getMainLooper());
    private Runnable burnInRunnable;

    // 记录隐藏时的基准Y坐标（“桩子”），确保抖动只在原位置附近小范围进行
    private int initialHideY = 0;

    // =================================================================================
    // 【功能点4】 防烧屏配置
    // =================================================================================
    // 抖动频率：每 1 分钟抖动一次
    private static final int BURN_IN_INTERVAL = 1 * 60 * 1000;
    // 最大位移距离：每次上下随机移动不超过 15 像素
    private static final int MAX_SHIFT_PIXELS = 15;

    private static final int BALL_SIZE = 110;

    // =================================================================================
    // 【功能点6】 解决2K屏幕图标过小问题
    // =================================================================================
    // 使用 dp (独立像素) 定义图标大小和间距，确保在 1K/2K/4K 屏幕上物理尺寸一致
    private static final int ICON_SIZE_DP = 34;
    private static final int ICON_MARGIN_DP = 8;

    // =================================================================================
    // 【功能点2】 禁止在四个角隐藏
    // =================================================================================
    // 定义转角禁区距离 (80dp)，防止悬浮球躲在屏幕圆角里按不到
    private static final int CORNER_MARGIN_DP = 80;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundNotification();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // 【功能点7】 3秒自动侧边隐藏
        // 初始化隐藏任务，3秒后执行 toSideHiddenMode
        hideRunnable = new Runnable() {
            @Override
            public void run() {
                toSideHiddenMode();
            }
        };

        // 【功能点4】 防烧屏定时任务逻辑
        burnInRunnable = new Runnable() {
            @Override
            public void run() {
                if (isHidden && floatingBallView != null) {
                    performPixelShift(); // 执行微小位移
                    burnInHandler.postDelayed(this, BURN_IN_INTERVAL); // 预约下一次
                }
            }
        };

        createFloatingBall();
    }

    // 【功能点6】 屏幕适配核心工具方法
    // 将 dp 转为 px，用于适配不同分辨率的屏幕密度
    private int dp2px(float dp) {
        final float scale = getResources().getDisplayMetrics().density;
        return (int) (dp * scale + 0.5f);
    }

    // =================================================================================
    // 【功能点1】 屏幕旋转自适应逻辑
    // =================================================================================
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (isMenuVisible) {
            removeMenu();
        }

        // 关键逻辑：直接通过 X 坐标判断球之前是在左边还是右边
        // 避免使用屏幕宽度计算，因为旋转瞬间屏幕宽度的值可能尚未更新或混乱
        boolean wasOnLeft = ballParams.x < 300;

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                int newScreenWidth = getCurrentScreenWidth();
                int newScreenHeight = getCurrentScreenHeight();

                // 1. 横屏/竖屏切换后，自动吸附到对应侧边
                if (wasOnLeft) {
                    ballParams.x = 0;
                } else {
                    ballParams.x = newScreenWidth - ballParams.width;
                }

                // 2. 强制垂直居中，确保用户能第一时间找到球
                ballParams.y = (newScreenHeight / 2) - (ballParams.height / 2);

                windowManager.updateViewLayout(floatingBallView, ballParams);

                // 3. 恢复之前的状态（隐藏或吸边）
                if (isHidden) {
                    toSideHiddenMode();
                } else {
                    snapToEdge();
                }
            }
        }, 300); // 延时 300ms 确保屏幕旋转动画完成，获取准确的屏幕尺寸
    }

    // 【功能点5】 修正刘海屏/挖孔屏隐藏不完美的问题
    // 使用 getRealMetrics 获取包含导航栏、刘海区的完整物理屏幕尺寸
    private int getCurrentScreenWidth() {
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        return metrics.widthPixels;
    }

    private int getCurrentScreenHeight() {
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        return metrics.heightPixels;
    }

    private void createFloatingBall() {
        ImageView imageView = new ImageView(this);
        imageView.setBackground(createNormalDrawable());
        floatingBallView = imageView;

        ballParams = new WindowManager.LayoutParams();
        ballParams.type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        ballParams.format = PixelFormat.TRANSLUCENT;

        ballParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

        ballParams.gravity = Gravity.TOP | Gravity.LEFT;

        // 【功能点5】 允许悬浮球延伸到刘海屏区域
        // LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES 确保隐藏时能完全贴边，不会被刘海挡住
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ballParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        ballParams.width = BALL_SIZE;
        ballParams.height = BALL_SIZE;
        ballParams.x = 0;
        ballParams.y = 500;

        initTouchListener();
        windowManager.addView(floatingBallView, ballParams);
        resetHideTimer();
    }

    private void initTouchListener() {
        floatingBallView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private long touchStartTime;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                handler.removeCallbacks(hideRunnable);
                // 触摸时停止防烧屏抖动，防止球乱跑
                stopBurnInProtection();

                if (isHidden) {
                    toNormalMode();
                    initialX = ballParams.x;
                    initialY = ballParams.y;
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                }

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = ballParams.x;
                        initialY = ballParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        touchStartTime = System.currentTimeMillis();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        ballParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        ballParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingBallView, ballParams);
                        return true;

                    case MotionEvent.ACTION_UP:
                        float movedDistance = Math.abs(event.getRawX() - initialTouchX) + Math.abs(event.getRawY() - initialTouchY);
                        // 点击判定：移动距离小且时间短
                        if (movedDistance < 10 && (System.currentTimeMillis() - touchStartTime) < 200) {
                            if (isMenuVisible) {
                                removeMenu();
                            } else {
                                showMenuNearBall();
                            }
                        } else {
                            // 拖拽结束：吸边
                            snapToEdge();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private GradientDrawable createNormalDrawable() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(Color.parseColor("#80333333"));
        drawable.setStroke(8, Color.WHITE);
        drawable.setSize(BALL_SIZE, BALL_SIZE);
        return drawable;
    }

    // =================================================================================
    // 【功能点3】 自动隐藏圆环双色设计 (仿小米悬浮球)
    // =================================================================================
    // 采用 LayerDrawable 叠加两层圆环，实现"白芯黑边"的高对比度设计
    private Drawable createArcDrawable() {
        // 1. 底层圆环（黑边）：适应浅色背景
        // 20% 黑色 (#33000000)，提供淡淡的阴影轮廓
        GradientDrawable darkRing = new GradientDrawable();
        darkRing.setShape(GradientDrawable.OVAL);
        darkRing.setColor(Color.TRANSPARENT);
        darkRing.setStroke(dp2px(6), Color.parseColor("#33000000"));

        // 2. 顶层圆环（白芯）：适应深色背景
        // 50% 白色 (#80FFFFFF)，半透明磨砂质感，不刺眼
        GradientDrawable lightRing = new GradientDrawable();
        lightRing.setShape(GradientDrawable.OVAL);
        lightRing.setColor(Color.TRANSPARENT);
        lightRing.setStroke(dp2px(4), Color.parseColor("#80FFFFFF"));

        LayerDrawable layerDrawable = new LayerDrawable(new Drawable[]{darkRing, lightRing});
        return layerDrawable;
    }

    private void snapToEdge() {
        int currentScreenWidth = getCurrentScreenWidth();
        int currentScreenHeight = getCurrentScreenHeight();

        // X轴逻辑：吸附到左右两侧
        int centerX = ballParams.x + ballParams.width / 2;
        if (centerX < currentScreenWidth / 2) {
            ballParams.x = 0;
        } else {
            ballParams.x = currentScreenWidth - ballParams.width;
        }

        // 【功能点2】 Y轴逻辑：禁止在四个角隐藏
        // 限制 Y 轴坐标在 [顶部安全线, 底部安全线] 范围内
        int minSafeY = dp2px(CORNER_MARGIN_DP);
        int maxSafeY = currentScreenHeight - ballParams.height - dp2px(CORNER_MARGIN_DP);

        if (ballParams.y < minSafeY) {
            ballParams.y = minSafeY;
        } else if (ballParams.y > maxSafeY) {
            ballParams.y = maxSafeY;
        }

        windowManager.updateViewLayout(floatingBallView, ballParams);

        // 【功能点7】 重置3秒隐藏计时器
        resetHideTimer();
    }

    private void toSideHiddenMode() {
        isHidden = true;

        // 切换为双色透明圆环样式
        floatingBallView.setBackground(createArcDrawable());
        floatingBallView.setAlpha(1.0f);

        int currentScreenWidth = getCurrentScreenWidth();

        // 隐藏逻辑：球体 65% 缩进屏幕，露出 35%
        int hideOffset = (int) (BALL_SIZE * 0.65f);

        if (ballParams.x <= 0) {
            ballParams.x = -hideOffset;
        } else {
            ballParams.x = currentScreenWidth - (BALL_SIZE - hideOffset);
        }

        // 再次检查转角限制，防止隐藏时滑入死角
        int currentScreenHeight = getCurrentScreenHeight();
        int minSafeY = dp2px(CORNER_MARGIN_DP);
        int maxSafeY = currentScreenHeight - ballParams.height - dp2px(CORNER_MARGIN_DP);
        if (ballParams.y < minSafeY) ballParams.y = minSafeY;
        if (ballParams.y > maxSafeY) ballParams.y = maxSafeY;

        windowManager.updateViewLayout(floatingBallView, ballParams);

        // 记录基准位置，启动防烧屏抖动
        initialHideY = ballParams.y;
        startBurnInProtection();
    }

    private void toNormalMode() {
        isHidden = false;
        floatingBallView.setBackground(createNormalDrawable());
        floatingBallView.setAlpha(1.0f);
        stopBurnInProtection();

        int currentScreenWidth = getCurrentScreenWidth();

        // 恢复正常模式时，确保球完全在屏幕内
        if (ballParams.x < 0) ballParams.x = 0;
        if (ballParams.x > currentScreenWidth - ballParams.width) {
            ballParams.x = currentScreenWidth - ballParams.width;
        }

        windowManager.updateViewLayout(floatingBallView, ballParams);
        resetHideTimer();
    }

    private void resetHideTimer() {
        handler.removeCallbacks(hideRunnable);
        // 【功能点7】 3000毫秒 (3秒) 后触发隐藏
        handler.postDelayed(hideRunnable, 3000);
    }

    private void startBurnInProtection() {
        burnInHandler.removeCallbacks(burnInRunnable);
        burnInHandler.postDelayed(burnInRunnable, BURN_IN_INTERVAL);
    }

    private void stopBurnInProtection() {
        burnInHandler.removeCallbacks(burnInRunnable);
    }

    // 【功能点4】 执行微小的像素偏移
    private void performPixelShift() {
        int offset = new Random().nextInt(MAX_SHIFT_PIXELS * 2) - MAX_SHIFT_PIXELS;
        ballParams.y = initialHideY + offset;
        try {
            windowManager.updateViewLayout(floatingBallView, ballParams);
        } catch (Exception e) {
        }
    }

    private void showMenuNearBall() {
        if (menuLayout != null) windowManager.removeView(menuLayout);

        List<String> targetPackages = getSavedPackages();
        if (targetPackages.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_setup_first), Toast.LENGTH_SHORT).show();
            targetPackages.add(getPackageName());
        }

        menuLayout = new LinearLayout(this);
        menuLayout.setOrientation(LinearLayout.VERTICAL);
        menuLayout.setBackgroundColor(Color.TRANSPARENT);

        PackageManager pm = getPackageManager();

        for (int i = 0; i < targetPackages.size(); i += 2) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);

            View leftIcon = createAppIconView(targetPackages.get(i), pm);
            rowLayout.addView(leftIcon);

            if (i + 1 < targetPackages.size()) {
                View rightIcon = createAppIconView(targetPackages.get(i+1), pm);
                rowLayout.addView(rightIcon);
            }
            menuLayout.addView(rowLayout);
        }

        menuParams = new WindowManager.LayoutParams();
        menuParams.type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        menuParams.format = PixelFormat.TRANSLUCENT;

        menuParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            menuParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        menuParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
        menuParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        menuParams.gravity = Gravity.TOP | Gravity.LEFT;

        int currentScreenWidth = getCurrentScreenWidth();

        // 动态计算菜单宽度和位置，防止菜单被遮挡
        int singleItemWidthPx = dp2px(ICON_SIZE_DP) + dp2px(ICON_MARGIN_DP) * 2;
        int menuTotalWidthPx = singleItemWidthPx * 2;
        int gapPx = dp2px(10);

        int menuX;
        if (ballParams.x < currentScreenWidth / 2) {
            menuX = ballParams.x + ballParams.width + gapPx;
        } else {
            menuX = ballParams.x - menuTotalWidthPx - gapPx;
        }

        int menuY = ballParams.y - dp2px(110);
        if (menuY < 0) menuY = 0;

        menuParams.x = menuX;
        menuParams.y = menuY;

        menuLayout.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                removeMenu();
                return true;
            }
            return false;
        });

        windowManager.addView(menuLayout, menuParams);
        isMenuVisible = true;
        handler.removeCallbacks(hideRunnable);
    }

    private List<String> getSavedPackages() {
        SharedPreferences prefs = getSharedPreferences("FloatingConfig", Context.MODE_PRIVATE);
        String savedString = prefs.getString("target_apps", "");
        List<String> list = new ArrayList<>();
        if (!savedString.isEmpty()) {
            String[] split = savedString.split(",");
            for (String s : split) {
                if (!s.trim().isEmpty()) list.add(s);
            }
        }
        return list;
    }

    private ImageView createAppIconView(String pkg, PackageManager pm) {
        ImageView icon = new ImageView(this);
        // 【功能点6】 使用 dp 动态计算图标大小，适配高清屏
        int iconSize = dp2px(ICON_SIZE_DP);
        int margin = dp2px(ICON_MARGIN_DP);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(iconSize, iconSize);
        lp.setMargins(margin, margin, margin, margin);
        icon.setLayoutParams(lp);

        try {
            icon.setImageDrawable(pm.getApplicationIcon(pkg));
            icon.setOnClickListener(v -> {
                launchApp(pkg);
                removeMenu();
            });
        } catch (Exception e) {
            icon.setImageResource(android.R.drawable.sym_def_app_icon);
        }
        return icon;
    }

    private void removeMenu() {
        if (isMenuVisible && menuLayout != null) {
            windowManager.removeView(menuLayout);
            menuLayout = null;
            isMenuVisible = false;
            resetHideTimer();
        }
    }

    private void startForegroundNotification() {
        String channelId = "floating_service";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(getString(R.string.notif_content))
                .setSmallIcon(android.R.drawable.ic_menu_rotate)
                .build();
        startForeground(1, notification);
    }

    private void launchApp(String pkg) {
        if (pkg.equals(getPackageName())) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return;
        }

        Intent intent = getPackageManager().getLaunchIntentForPackage(pkg);
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } else {
            Toast.makeText(this, getString(R.string.toast_not_installed) + ": " + pkg, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingBallView != null) windowManager.removeView(floatingBallView);
        if (menuLayout != null) windowManager.removeView(menuLayout);
    }
}