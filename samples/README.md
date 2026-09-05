# AAOS SystemUI Sample RRO's

Each sample demonstrates the effects of overriding some configs in AAOS SystemUI.

## How to build and install RRO

```bash
# Enable RRO for the user 0
adb shell cmd overlay enable --user 0 com.android.systemui.rro.bottom
adb shell cmd overlay enable --user 0 com.android.systemui.rro.bottom.rounded
adb shell cmd overlay enable --user 0 com.android.systemui.rro.right
adb shell cmd overlay enable --user 0 com.android.systemui.rro.left
db shell cmd overlay enable --user 0 com.android.car.systemui.systembar.transparency.navbar.translucent
adb shell cmd overlay enable --user 0 com.android.car.systemui.systembar.transparency.statusbar.translucent

# To make system bar persistent, apply below RRO to both user 10 and user 0
adb shell cmd overlay enable --user 0 com.android.systemui.controls.systembar.insets.rro
adb shell cmd overlay enable --user 10 com.android.systemui.controls.systembar.insets.rro
# Verify with
adb shell dumpsys window | grep mRemoteInsetsControllerControlsSystemBars
# then adjust with
adb shell cmd overlay enable --user 0 com.android.car.systemui.systembar.persistency.immersive_with_nav
adb shell cmd overlay enable --user 0 com.android.car.systemui.systembar.persistency.non_immersive
adb shell cmd overlay enable --user 0 com.android.car.systemui.systembar.persistency.immersive
# Crash the systemUI if necessary
adb shell am crash com.android.systemui

# Build all sample RRO's
mmma {path to the samples directory}
# Install one of the sample RRO's
adb install {path to the RRO apk}
# Restart SystemUI
adb shell pkill -TERM -f com.android.systemui
```