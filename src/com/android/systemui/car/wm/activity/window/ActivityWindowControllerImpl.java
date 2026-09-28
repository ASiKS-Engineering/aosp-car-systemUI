package com.android.systemui.car.wm.activity.window;

import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;

import android.app.ActivityOptions;
import android.annotation.NonNull;
import android.car.app.CarActivityManager;
import android.car.app.CarTaskViewController;
import android.car.app.CarTaskViewControllerCallback;
import android.car.app.CarTaskViewControllerHostLifecycle;
import android.car.app.RemoteCarDefaultRootTaskView;
import android.car.app.RemoteCarDefaultRootTaskViewCallback;
import android.car.app.RemoteCarDefaultRootTaskViewConfig;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.graphics.Rect;
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

    public static final String TAG =
            ActivityWindowController.class.getSimpleName();

    /*
     * Navigator application sends this broadcast through scalable_ui_actions.xml.
     */
    private static final String ACTION_NAVIGATION_UI_MODE_CHANGED =
            "com.example.campernavigator.action.NAVIGATION_UI_MODE_CHANGED";

    private static final String EXTRA_NAVIGATION_UI_MODE =
            "com.example.campernavigator.extra.NAVIGATION_UI_MODE";

    private static final String NAVIGATION_MODE_FULLSCREEN =
            "FULLSCREEN";

    private static final String NAVIGATION_MODE_HOME =
            "HOME";

    private static final String NAVIGATOR_PACKAGE =
            "com.example.campernavigator";

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

        /*
         * The RemoteCarDefaultRootTaskView is used only for fullscreen navigator hosting.
         * HOME stays visually launcher-owned and hides this root task view.
         */
    private RemoteCarDefaultRootTaskView mTaskView;

        /*
         * Current system bar insets.
         *
         * These are used to keep fullscreen content aligned with the visible system bars and to
         * preserve a sane hidden-home fallback state for the root task view.
         */
    private int mTopInset;
    private int mBottomInset;

        /*
         * Current navigation display mode.
         *
         * Default is HOME because the Launcher is the initial visible state.
         */
        private boolean mNavigationFullscreen = false;
        private boolean mNavigatorFullscreenTaskRequested;

    @NonNull
    private final BroadcastReceiver mNavigationUiModeReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (!ACTION_NAVIGATION_UI_MODE_CHANGED.equals(
                            intent.getAction())) {
                        return;
                    }

                    String mode = intent.getStringExtra(
                            EXTRA_NAVIGATION_UI_MODE);

                    if (NAVIGATION_MODE_FULLSCREEN.equals(mode)) {
                        Log.i(TAG, "Navigation UI mode: FULLSCREEN");
                        showNavigationFullscreen();
                    } else if (NAVIGATION_MODE_HOME.equals(mode)) {
                        Log.i(TAG, "Navigation UI mode: HOME");
                        showHomeMode();
                    } else {
                        Log.w(
                                TAG,
                                "Unknown navigation UI mode: " + mode);
                    }
                }
            };

    @NonNull
    @UiBackground
    private final CarServiceProvider.CarServiceOnConnectedListener
            mCarServiceLifecycleListener = car -> {

        mCarActivityManager =
                car.getCarManager(CarActivityManager.class);

        inflate();
        setupRemoteCarTaskView();
    };

    @Inject
    public ActivityWindowControllerImpl(
            Context context,
            WindowManager windowManager,
            CarServiceProvider carServiceProvider,
            CarTaskViewControllerHostLifecycle
                    carTaskViewControllerHostLifecycle) {

        mContext = context;
        mWindowManager = windowManager;
        mCarServiceProvider = carServiceProvider;
        mCarTaskViewControllerHostLifecycle =
                carTaskViewControllerHostLifecycle;
    }

    @MainThread
    @Override
    public void init() {
        /*
         * Listen for FULLSCREEN / HOME mode changes.
         */
        IntentFilter filter = new IntentFilter(
                ACTION_NAVIGATION_UI_MODE_CHANGED);

        mContext.registerReceiver(
                mNavigationUiModeReceiver,
                filter,
                Context.RECEIVER_EXPORTED);

        /*
         * Connect to CarService.
         */
        mCarServiceProvider.addListener(
                mCarServiceLifecycleListener);
    }

    @MainThread
    protected void inflate() {

        mLayout = (ViewGroup) LayoutInflater.from(mContext)
                .inflate(
                        R.layout.car_activity_window,
                        /* root= */ null);

        mWmLayoutParams = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);

        mWmLayoutParams.setTrustedOverlay();

        /*
         * ActivityWindow uses the full display.
         *
         * We handle the actual Task bounds ourselves with
         * RemoteCarTaskView.setWindowBounds().
         */
        mWmLayoutParams.setFitInsetsTypes(0);

        mWmLayoutParams.softInputMode =
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;

        mWmLayoutParams.token = new Binder();

        mWmLayoutParams.setTitle("ActivityWindow!");

        mWmLayoutParams.packageName =
                mContext.getPackageName();

        mWmLayoutParams.layoutInDisplayCutoutMode =
                LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;

        mWmLayoutParams.privateFlags |=
                WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS;

        /*
         * The ActivityWindow itself must exist because the
         * RemoteCarTaskView/SurfaceView needs a host window.
         *
         * The window must NOT consume touch input.
         *
         * This is important for:
         *
         * - CarLauncher
         * - Navigator
         * - IME / on-screen keyboard
         * - Top SystemUI
         * - Bottom SystemUI
         */
        mWindowManager.addView(
                mLayout,
                mWmLayoutParams);

        /*
         * Keep ActivityWindow completely non-touchable.
         *
         * Do NOT use a top/bottom touch region here.
         *
         * Top and Bottom SystemUI are separate windows, and the IME
         * can also occupy areas that overlap those coordinates.
         */
        mLayout.getViewTreeObserver()
                .addOnComputeInternalInsetsListener(
                        info -> {
                            info.touchableRegion.setEmpty();

                            info.setTouchableInsets(
                                    InternalInsetsInfo
                                            .TOUCHABLE_INSETS_REGION);
                        });

        /*
         * Remember system bar insets.
         *
         * These values are later used to calculate the HOME bounds.
         */
        mLayout.setOnApplyWindowInsetsListener(
                (view, insets) -> {

                    android.graphics.Insets systemBars =
                            insets.getInsets(
                                    WindowInsets.Type.systemBars());

                    mTopInset = systemBars.top;
                    mBottomInset = systemBars.bottom;

                    Log.d(
                            TAG,
                            "Window insets: top="
                                    + mTopInset
                                    + " bottom="
                                    + mBottomInset);

                    /*
                     * We intentionally do not apply the insets as
                     * padding to mLayout.
                     *
                     * The ActivityWindow stays fullscreen.
                     * The RemoteCarTaskView gets explicit screen
                     * coordinates instead.
                     */
                    return insets;
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
                            CarTaskViewController
                                    carTaskViewController) {

                        Log.d(
                                TAG,
                                "CarTaskViewController connected");

                        mCarTaskViewController =
                                carTaskViewController;

                        taskViewControllerReady();
                    }

                    @Override
                    public void onDisconnected(
                            CarTaskViewController
                                    carTaskViewController) {

                        Log.d(
                                TAG,
                                "CarTaskViewController disconnected");

                        mCarTaskViewController = null;
                        mTaskView = null;
                    }
                });
    }

    private void taskViewControllerReady() {

        mCarTaskViewController
                .createRemoteCarDefaultRootTaskView(
                        new RemoteCarDefaultRootTaskViewConfig.Builder()
                                .setDisplayId(
                                        mContext.getDisplayId())
                                .embedHomeTask(true)
                                .embedRecentsTask(true)
                                .build(),
                        mContext.getMainExecutor(),
                        new RemoteCarDefaultRootTaskViewCallback() {

                            @Override
                            public void onTaskViewCreated(
                                    @NonNull
                                    RemoteCarDefaultRootTaskView
                                            taskView) {

                                Log.d(
                                        TAG,
                                        "Root Task View is created");

                                mTaskView = taskView;

                                /*
                                 * Keep the task view above the
                                 * ActivityWindow background.
                                 */
                                taskView.setZOrderMediaOverlay(false);

                                ViewGroup layout =
                                        (ViewGroup) mLayout.findViewById(R.id.activity_area);

                                // The task view still needs a host surface in SystemUI even when
                                // HOME keeps it hidden behind the launcher-managed navigation.
                                taskView.setVisibility(View.INVISIBLE);
                                layout.addView(taskView);

                                if (mNavigationFullscreen) {
                                    applyFullscreenBounds();
                                } else {
                                    hideRootTaskViewForHome();
                                }

                                Log.d(
                                        TAG,
                                        "RemoteCarDefaultRootTaskView attached");
                            }

                            @Override
                            public void onTaskViewInitialized() {

                                                                Log.d(TAG, "Root Task View is ready");

                                                                if (mTaskView != null) {
                                                                        if (mNavigationFullscreen) {
                                                                                applyFullscreenBounds();
                                                                        } else {
                                                                                hideRootTaskViewForHome();
                                                                        }
                                                                }
                            }
                        });
    }

    /**
     * Switch to fullscreen navigation mode.
     *
     * Result:
     *
     *   +------------------------------------------+
     *   |                 NAVIGATION               |
     *   |                                          |
     *   |             FULL DISPLAY                 |
     *   |                                          |
     *   +------------------------------------------+
     *
     * Launcher/widget are behind the navigation task.
     */
    private void showNavigationFullscreen() {

        mNavigationFullscreen = true;
        if (!mNavigatorFullscreenTaskRequested && launchNavigatorTask()) {
            mNavigatorFullscreenTaskRequested = true;
        }

        if (mTaskView == null) {
            Log.w(
                    TAG,
                    "Cannot enter fullscreen navigation: "
                            + "TaskView is not ready");
            return;
        }

        applyFullscreenBounds();
    }

    /**
     * Switch back to normal HOME mode.
     *
     * Result:
     *
     *   +------------------------------------------+
     *   |              TOP SYSTEMUI                |
     *   +------------------------------------------+
     *   |                                          |
     *   |       Launcher + Navigation area        |
     *   |                                          |
     *   +------------------------------------------+
     *   |            BOTTOM SYSTEMUI               |
     *   +------------------------------------------+
     */
    private void showHomeMode() {

        mNavigationFullscreen = false;
                if (mNavigatorFullscreenTaskRequested) {
                        launchHomeTask();
                        mNavigatorFullscreenTaskRequested = false;
                }

                if (mTaskView == null) {
                        Log.w(TAG, "Cannot enter home mode: TaskView is not ready");
                        return;
                }

                hideRootTaskViewForHome();
    }

        private void hideRootTaskViewForHome() {
                if (mTaskView == null) {
                        return;
                }

                applyHomeBounds();
                mTaskView.setTaskVisibility(false);
        }

    /**
     * Makes the embedded task occupy the entire display.
     */
    private void applyFullscreenBounds() {

        if (mTaskView == null) {
            return;
        }

        int width = mLayout.getWidth();
        int height = mLayout.getHeight();

        /*
         * During very early startup the layout can still report zero.
         * Use the display metrics as fallback.
         */
        if (width <= 0) {
            width = mContext.getResources()
                    .getDisplayMetrics()
                    .widthPixels;
        }

        if (height <= 0) {
            height = mContext.getResources()
                    .getDisplayMetrics()
                    .heightPixels;
        }

        Rect bounds = new Rect(
                0,
                0,
                width,
                height);

        Log.d(
                TAG,
                "Applying FULLSCREEN bounds: "
                        + bounds);

        /*
         * RemoteCarTaskView.setWindowBounds() expects
         * screen coordinates.
         */
        mTaskView.setWindowBounds(bounds);

        /*
         * Ensure the embedded task is visible.
         *
         * We intentionally do not reorder the root task here.
         * The launch-root/task transition machinery is responsible
         * for task ordering.
         */
        mTaskView.showEmbeddedTask();
    }

    /**
     * Makes the embedded task occupy the normal application/content
     * area between the Top and Bottom SystemUI windows.
     */
    private void applyHomeBounds() {

        if (mTaskView == null) {
            return;
        }

        int width = mLayout.getWidth();
        int height = mLayout.getHeight();

        /*
         * During very early startup the layout can still report zero.
         * Use the display metrics as fallback.
         */
        if (width <= 0) {
            width = mContext.getResources()
                    .getDisplayMetrics()
                    .widthPixels;
        }

        if (height <= 0) {
            height = mContext.getResources()
                    .getDisplayMetrics()
                    .heightPixels;
        }

        /*
         * Normal application/content area:
         *
         * top    = below Top SystemUI
         * bottom = above Bottom SystemUI
         */
        int top = mTopInset;
        int bottom = height - mBottomInset;

        /*
         * Safety fallback if insets have not arrived yet.
         *
         * Our current display is 1024x600 with:
         *
         *   Top    = 76
         *   Bottom = 96
         *
         * But we do NOT hard-code those values.
         */
        if (bottom <= top) {
            top = 0;
            bottom = height;
        }

        Rect bounds = new Rect(
                0,
                top,
                width,
                bottom);

        Log.d(
                TAG,
                "Applying HOME bounds: "
                        + bounds
                        + " topInset="
                        + mTopInset
                        + " bottomInset="
                        + mBottomInset);

        mTaskView.setWindowBounds(bounds);
        mTaskView.showEmbeddedTask();
    }

    private boolean launchNavigatorTask() {
        Intent navigatorIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_APP_MAPS)
                .setPackage(NAVIGATOR_PACKAGE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        navigatorIntent.putExtra(EXTRA_NAVIGATION_UI_MODE, NAVIGATION_MODE_FULLSCREEN);

        PackageManager packageManager = mContext.getPackageManager();
        if (navigatorIntent.resolveActivity(packageManager) == null) {
            Log.w(TAG, "Navigator activity is not available for fullscreen launch");
            return false;
        }

        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(mContext.getDisplayId());
        mContext.startActivity(navigatorIntent, options.toBundle());
        return true;
    }

    private void launchHomeTask() {
        Intent homeIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(mContext.getDisplayId());
        mContext.startActivity(homeIntent, options.toBundle());
    }
}