package com.android.systemui.car.wm.activity.window;

import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;

import android.annotation.NonNull;
import android.car.app.CarActivityManager;
import android.car.app.CarTaskViewController;
import android.car.app.CarTaskViewControllerCallback;
import android.car.app.CarTaskViewControllerHostLifecycle;
import android.car.app.RemoteCarDefaultRootTaskView;
import android.car.app.RemoteCarDefaultRootTaskViewCallback;
import android.car.app.RemoteCarDefaultRootTaskViewConfig;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Binder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.InternalInsetsInfo;
import android.view.WindowInsets;
import android.view.WindowManager;

import androidx.annotation.MainThread;

import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.shared.R;
import com.android.systemui.dagger.qualifiers.UiBackground;

import javax.inject.Inject;

public class ActivityWindowControllerImpl implements ActivityWindowController {
    public static final String TAG = ActivityWindowController.class.getSimpleName();

    @NonNull
    private final Context mContext;

    @NonNull
    private final WindowManager mWindowManager;

    @NonNull
    private final CarServiceProvider mCarServiceProvider;

    @NonNull
    private ViewGroup mLayout;

    @NonNull
    private WindowManager.LayoutParams mWmLayoutParams;

    @NonNull
    private CarTaskViewController mCarTaskViewController;

    @NonNull
    private CarTaskViewControllerHostLifecycle mCarTaskViewControllerHostLifecycle;

    @NonNull
    private CarActivityManager mCarActivityManager;

    @NonNull
    @UiBackground
    private final CarServiceProvider.CarServiceOnConnectedListener mCarServiceLifecycleListener =
            car -> {
                mCarActivityManager = car.getCarManager(CarActivityManager.class);

                inflate();
                setupRemoteCarTaskView();
            };

    @Inject
    public ActivityWindowControllerImpl(
            Context context,
            WindowManager windowManager,
            CarServiceProvider carServiceProvider,
            CarTaskViewControllerHostLifecycle carTaskViewControllerHostLifecycle) {
        mContext = context;
        mWindowManager = windowManager;
        mCarServiceProvider = carServiceProvider;
        mCarTaskViewControllerHostLifecycle = carTaskViewControllerHostLifecycle;
    }

    @MainThread
    @Override
    public void init() {
        mCarServiceProvider.addListener(mCarServiceLifecycleListener);
    }

    @MainThread
    protected void inflate() {
        mLayout = (ViewGroup) LayoutInflater.from(mContext)
                .inflate(R.layout.car_activity_window, /* root= */ null);

        mWmLayoutParams = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);

        mWmLayoutParams.setTrustedOverlay();
        mWmLayoutParams.setFitInsetsTypes(0);
        mWmLayoutParams.softInputMode =
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        mWmLayoutParams.token = new Binder();
        mWmLayoutParams.setTitle("ActivityWindow!");
        mWmLayoutParams.packageName = mContext.getPackageName();
        mWmLayoutParams.layoutInDisplayCutoutMode =
                LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        mWmLayoutParams.privateFlags |=
                WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS;

        /*
         * ActivityWindow muss vorhanden sein, damit der Activity-/TaskView-
         * Mechanismus funktioniert.
         *
         * Das Window selbst soll aber keine Touches abfangen.
         *
         * Top- und Bottom-SystemUI besitzen eigene Windows und sollen
         * ihre Touches selbst behandeln.
         *
         * Ebenso sollen Launcher, Navigation, IME und andere darüberliegende
         * Fenster ihre Touches bekommen können.
         */
        mWindowManager.addView(mLayout, mWmLayoutParams);

        /*
         * ActivityWindow bekommt eine leere Touch-Region.
         *
         * Dadurch wird verhindert, dass dieses fullscreen Window Touches
         * aus anderen Bereichen abfängt, insbesondere:
         *
         * - CarLauncher
         * - Navigation
         * - On-Screen-Keyboard / IME
         * - andere überlagernde Windows
         */
        mLayout.getViewTreeObserver().addOnComputeInternalInsetsListener(
                info -> {
                    info.touchableRegion.setEmpty();
                    info.setTouchableInsets(
                            InternalInsetsInfo.TOUCHABLE_INSETS_REGION);
                });
    }

    private void setupRemoteCarTaskView() {
        mCarActivityManager.getCarTaskViewController(
                mContext,
                mCarTaskViewControllerHostLifecycle,
                mContext.getMainExecutor(),
                new CarTaskViewControllerCallback() {
                    @Override
                    public void onConnected(
                            CarTaskViewController carTaskViewController) {
                        mCarTaskViewController = carTaskViewController;
                        taskViewControllerReady();
                    }

                    @Override
                    public void onDisconnected(
                            CarTaskViewController carTaskViewController) {
                    }
                });
    }

    private void taskViewControllerReady() {
        mCarTaskViewController.createRemoteCarDefaultRootTaskView(
                new RemoteCarDefaultRootTaskViewConfig.Builder()
                        .setDisplayId(mContext.getDisplayId())
                        .embedHomeTask(true)
                        .embedRecentsTask(true)
                        .build(),
                mContext.getMainExecutor(),
                new RemoteCarDefaultRootTaskViewCallback() {
                    @Override
                    public void onTaskViewCreated(
                            @NonNull RemoteCarDefaultRootTaskView taskView) {

                        Log.d(TAG, "Root Task View is created");

                        taskView.setZOrderMediaOverlay(true);

                        mLayout.setOnApplyWindowInsetsListener(
                                new View.OnApplyWindowInsetsListener() {
                                    @Override
                                    public WindowInsets onApplyWindowInsets(
                                            View view,
                                            WindowInsets insets) {

                                        mLayout.setPadding(
                                                insets.getSystemWindowInsetLeft(),
                                                insets.getSystemWindowInsetTop(),
                                                insets.getSystemWindowInsetRight(),
                                                insets.getSystemWindowInsetBottom());

                                        return insets.replaceSystemWindowInsets(
                                                /* left */ 0,
                                                /* top */ 0,
                                                /* right */ 0,
                                                /* bottom */ 0);
                                    }
                                });

                        ViewGroup layout =
                                (ViewGroup) mLayout.findViewById(R.id.activity_area);

                        /*
                         * Diagnosemodus:
                         *
                         * RemoteCarDefaultRootTaskView wird weiterhin NICHT
                         * in ActivityWindow eingefügt.
                         *
                         * Damit testen wir zunächst ausschließlich das
                         * Window-/Input-Verhalten von ActivityWindow.
                         */
                        // layout.addView(taskView);

                        Log.d(
                                TAG,
                                "Diagnostic: RemoteCarDefaultRootTaskView NOT attached");
                    }

                    @Override
                    public void onTaskViewInitialized() {
                        Log.d(TAG, "Root Task View is ready");
                    }
                });
    }
}

