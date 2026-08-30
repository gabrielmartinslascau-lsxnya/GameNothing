# Fix Controller-Related Boot Crashes and UI Cleanup

Implement safeguards for controller disconnections, corrupted config files, and remove the thermal/throttle control option.

## User Review Required

> [!IMPORTANT]
> The thermal/throttle control option will be completely removed from the Quick Menu as requested.

## Proposed Changes

### [Safeguard 1] Config File Reads
Wrap external config file reads in try-catch blocks and delete corrupted files.

#### [MODIFY] [ContainerManager.java](file:///C:/Users/sorin/StudioProjects/GameNative-/app/src/main/java/com/winlator/container/ContainerManager.java)
- In `loadContainers()`, if a config file is corrupted (throws an exception or is empty), delete it.

#### [MODIFY] [InputControlsManager.java](file:///C:/Users/sorin/StudioProjects/GameNative-/app/src/main/java/com/winlator/inputcontrols/InputControlsManager.java)
- In `loadProfiles()`, delete corrupted profile files instead of just skipping them.

---

### [Safeguard 2] InputManager Device Loops
Add strict null-object checks inside `getInputDeviceIds()` loops.

#### [MODIFY] [ControllerManager.java](file:///C:/Users/sorin/StudioProjects/GameNative-/app/src/main/java/com/winlator/inputcontrols/ControllerManager.java)
- Add null and validity checks in `scanForDevices()` and `autoAssignConnectedDevices()`.

#### [MODIFY] [ExternalController.java](file:///C:/Users/sorin/StudioProjects/GameNative-/app/src/main/java/com/winlator/inputcontrols/ExternalController.java)
- Add null and validity checks in `getDeviceId()`, `isConnected()`, and `getControllers()`.

#### [MODIFY] [MainActivity.kt](file:///C:/Users/sorin/StudioProjects/GameNative-/app/src/main/java/app/gamenative/MainActivity.kt)
- Add null and validity checks in `isGamepadConnected()`.

---

### [Safeguard 3] Native JNI Input Thread
Update the native input thread to gracefully close when the backing storage vanishes.

#### [MODIFY] [evshim.c](file:///C:/Users/sorin/StudioProjects/GameNative-/app/src/main/cpp/evshim/evshim.c)
- Add checks in `vjoy_updater` and `waitForRumble` to handle missing shared memory or termination signals gracefully.

---

### [UI Cleanup] Remove Temperature/Throttle Control
Remove the Thermal tab from the Quick Menu.

#### [MODIFY] [QuickMenu.kt](file:///C:/Users/sorin/StudioProjects/GameNative-/app/src/main/java/app/gamenative/ui/component/QuickMenu.kt)
- Remove `QuickMenuTab.THERMAL` and its associated UI components.

## Verification Plan

### Automated Tests
- N/A (UI and hardware interaction fixes)

### Manual Verification
- Deploy the app and verify it boots without crashing even if controllers are disconnected.
- Verify the Thermal tab is gone from the Quick Menu.
- (Internal) Simulate corrupted config files and verify they are deleted on boot.
