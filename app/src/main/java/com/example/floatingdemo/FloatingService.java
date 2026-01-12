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
import android.os.VibrationEffect;
import android.os.Vibrator;
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

    private Handler burnInHandler = new Handler(Looper.getMainLooper());
    private Runnable burnInRunnable;

    private int initialHideY = 0;

    private static final int BURN_IN_INTERVAL = 1 * 60 * 1000;
    private static final int MAX_SHIFT_PIXELS = 15;

    // =================================================================================
    // 长按相关变量
    // =================================================================================
    private Handler longPressHandler = new Handler(Looper.getMainLooper());
    private Runnable longPressRunnable;
    private boolean isLongPressed = false;
    private static final int LONG_PRESS_DURATION = 1000; // 1秒
    private static final int TOUCH_SLOP = 15; // 手指防抖动范围

    private int screenWidth;
    private int screenHeight;
    private int ballSizePx;

    private static final float BALL_SIZE_RATIO = 0.105f;
    private static final int ICON_SIZE_DP = 34;
    private static final int ICON_MARGIN_DP = 8;
    private static final int CORNER_MARGIN_DP = 80;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundNotification();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        updateDimensions();

        hideRunnable = new Runnable() {
            @Override
            public void run() {
                toSideHiddenMode();
            }
        };

        burnInRunnable = new Runnable() {
            @Override
            public void run() {
                if (isHidden && floatingBallView != null) {
                    performPixelShift();
                    burnInHandler.postDelayed(this, BURN_IN_INTERVAL);
                }
            }
        };

        longPressRunnable = new Runnable() {
            @Override
            public void run() {
                isLongPressed = true;
                try {
                    Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                    if (v != null && v.hasVibrator()) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            v.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
                        } else {
                            v.vibrate(50);
                        }
                    }
                } catch (Exception e) {
                }

                openMainActivity();
                removeMenu();
            }
        };

        createFloatingBall();
    }

    private void updateDimensions() {
        screenWidth = getCurrentScreenWidth();
        screenHeight = getCurrentScreenHeight();
        int minDimension = Math.min(screenWidth, screenHeight);
        ballSizePx = (int) (minDimension * BALL_SIZE_RATIO);
    }

    private int dp2px(float dp) {
        final float scale = getResources().getDisplayMetrics().density;
        return (int) (dp * scale + 0.5f);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (isMenuVisible) {
            removeMenu();
        }

        boolean wasOnLeft = ballParams.x < 300;

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateDimensions();
                ballParams.width = ballSizePx;
                ballParams.height = ballSizePx;

                if (wasOnLeft) {
                    ballParams.x = 0;
                } else {
                    ballParams.x = screenWidth - ballParams.width;
                }
                ballParams.y = (screenHeight / 2) - (ballParams.height / 2);

                if (isHidden) {
                    floatingBallView.setBackground(createArcDrawable());
                    toSideHiddenMode();
                } else {
                    floatingBallView.setBackground(createNormalDrawable());
                    snapToEdge();
                }
                windowManager.updateViewLayout(floatingBallView, ballParams);
            }
        }, 300);
    }

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ballParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        ballParams.width = ballSizePx;
        ballParams.height = ballSizePx;
        ballParams.x = 0;
        ballParams.y = 500;

        initTouchListener();
        windowManager.addView(floatingBallView, ballParams);
        resetHideTimer();
    }

    // =================================================================================
    // 【修改点】 修复了隐藏模式下点击需要点两次的问题
    // =================================================================================
    private void initTouchListener() {
        floatingBallView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private long touchStartTime;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                handler.removeCallbacks(hideRunnable);
                stopBurnInProtection();

                // 【这里改了】之前这里有 return true 阻止了后续逻辑，现在删掉了，让它继续往下走

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // 如果是隐藏状态，先恢复，并让球归位
                        if (isHidden) {
                            toNormalMode();
                            // 注意：这里不用 return，让它继续初始化下面的坐标，这样 ACTION_UP 才能识别为点击
                        }

                        initialX = ballParams.x;
                        initialY = ballParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        touchStartTime = System.currentTimeMillis();

                        isLongPressed = false;
                        longPressHandler.postDelayed(longPressRunnable, LONG_PRESS_DURATION);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - initialTouchX;
                        float dy = event.getRawY() - initialTouchY;

                        if (Math.abs(dx) > TOUCH_SLOP || Math.abs(dy) > TOUCH_SLOP) {
                            longPressHandler.removeCallbacks(longPressRunnable);
                        }

                        if (!isLongPressed) {
                            ballParams.x = initialX + (int) dx;
                            ballParams.y = initialY + (int) dy;
                            windowManager.updateViewLayout(floatingBallView, ballParams);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        longPressHandler.removeCallbacks(longPressRunnable);

                        float movedDistance = Math.abs(event.getRawX() - initialTouchX) + Math.abs(event.getRawY() - initialTouchY);

                        if (isLongPressed) {
                            return true;
                        }

                        // 如果移动距离很小，且时间很短，就认为是点击
                        if (movedDistance < 10 && (System.currentTimeMillis() - touchStartTime) < 200) {
                            if (isMenuVisible) {
                                removeMenu();
                            } else {
                                showMenuNearBall();
                            }
                        } else {
                            snapToEdge();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void openMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private GradientDrawable createNormalDrawable() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(Color.parseColor("#80333333"));
        drawable.setStroke(8, Color.WHITE);
        drawable.setSize(ballSizePx, ballSizePx);
        return drawable;
    }

    private Drawable createArcDrawable() {
        GradientDrawable darkRing = new GradientDrawable();
        darkRing.setShape(GradientDrawable.OVAL);
        darkRing.setColor(Color.TRANSPARENT);
        darkRing.setStroke(dp2px(6), Color.parseColor("#33000000"));
        darkRing.setSize(ballSizePx, ballSizePx);

        GradientDrawable lightRing = new GradientDrawable();
        lightRing.setShape(GradientDrawable.OVAL);
        lightRing.setColor(Color.TRANSPARENT);
        lightRing.setStroke(dp2px(4), Color.parseColor("#80FFFFFF"));
        lightRing.setSize(ballSizePx, ballSizePx);

        LayerDrawable layerDrawable = new LayerDrawable(new Drawable[]{darkRing, lightRing});
        return layerDrawable;
    }

    private void snapToEdge() {
        int currentScreenWidth = getCurrentScreenWidth();
        int currentScreenHeight = getCurrentScreenHeight();

        int centerX = ballParams.x + ballParams.width / 2;
        if (centerX < currentScreenWidth / 2) {
            ballParams.x = 0;
        } else {
            ballParams.x = currentScreenWidth - ballParams.width;
        }

        int minSafeY = dp2px(CORNER_MARGIN_DP);
        int maxSafeY = currentScreenHeight - ballParams.height - dp2px(CORNER_MARGIN_DP);

        if (ballParams.y < minSafeY) {
            ballParams.y = minSafeY;
        } else if (ballParams.y > maxSafeY) {
            ballParams.y = maxSafeY;
        }

        windowManager.updateViewLayout(floatingBallView, ballParams);
        resetHideTimer();
    }

    private void toSideHiddenMode() {
        isHidden = true;
        floatingBallView.setBackground(createArcDrawable());
        floatingBallView.setAlpha(1.0f);

        int currentScreenWidth = getCurrentScreenWidth();
        int hideOffset = (int) (ballSizePx * 0.65f);

        if (ballParams.x <= 0) {
            ballParams.x = -hideOffset;
        } else {
            ballParams.x = currentScreenWidth - (ballSizePx - hideOffset);
        }

        int currentScreenHeight = getCurrentScreenHeight();
        int minSafeY = dp2px(CORNER_MARGIN_DP);
        int maxSafeY = currentScreenHeight - ballParams.height - dp2px(CORNER_MARGIN_DP);
        if (ballParams.y < minSafeY) ballParams.y = minSafeY;
        if (ballParams.y > maxSafeY) ballParams.y = maxSafeY;

        windowManager.updateViewLayout(floatingBallView, ballParams);
        initialHideY = ballParams.y;
        startBurnInProtection();
    }

    private void toNormalMode() {
        isHidden = false;
        floatingBallView.setBackground(createNormalDrawable());
        floatingBallView.setAlpha(1.0f);
        stopBurnInProtection();

        int currentScreenWidth = getCurrentScreenWidth();

        if (ballParams.x < 0) ballParams.x = 0;
        if (ballParams.x > currentScreenWidth - ballParams.width) {
            ballParams.x = currentScreenWidth - ballParams.width;
        }

        windowManager.updateViewLayout(floatingBallView, ballParams);
        resetHideTimer();
    }

    private void resetHideTimer() {
        handler.removeCallbacks(hideRunnable);
        handler.postDelayed(hideRunnable, 3000);
    }

    private void startBurnInProtection() {
        burnInHandler.removeCallbacks(burnInRunnable);
        burnInHandler.postDelayed(burnInRunnable, BURN_IN_INTERVAL);
    }

    private void stopBurnInProtection() {
        burnInHandler.removeCallbacks(burnInRunnable);
    }

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

        int iconSizePx = dp2px(ICON_SIZE_DP);
        int margin = dp2px(ICON_MARGIN_DP);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(iconSizePx, iconSizePx);
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