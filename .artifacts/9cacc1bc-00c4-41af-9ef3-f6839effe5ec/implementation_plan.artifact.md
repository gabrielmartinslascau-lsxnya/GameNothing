# Implementation Plan - Storage Optimization and Controller Profile Fixes

This plan addresses three main tasks: optimizing SAF transfer speeds, integrating a native system file manager shortcut, and fixing bugs in the controller mapping profile system.

## Proposed Changes

### 1. SAF Transfer Speed Optimization [Task 1]
Refactor the file copy logic to use `BufferedInputStream` and `BufferedOutputStream` with a 1 MB buffer for better performance during move operations across different storage volumes.

#### [MODIFY] [StorageUtils.kt](file:///C:/Users/sorin/StudioProjects/GameNative-/app/src/main/java/app/gamenative/utils/StorageUtils.kt)
- Refactor the fallback copy loop in `moveDirectory` to use `BufferedInputStream` and `BufferedOutputStream` instead of `FileChannel`.
- Set buffer size to `1024 * 1024` (1 MB).
- Ensure the copy loop yields to allow cancellation and remains strictly on `Dispatchers.IO`.

---

### 2. Native System File Manager Integration [Task 2]
Add a shortcut to open Android's native Files app (`com.android.documentsui`) at the game's directory.

#### [MODIFY] [StorageUtils.kt](file:///C:/Users/sorin/StudioProjects/GameNative-/app/src/main/java/app/gamenative/utils/StorageUtils.kt)
- Implement `openSystemFileManager(context: Context, folderPath: String)` using `Intent.ACTION_VIEW` and `DocumentsContract.EXTRA_INITIAL_URI`.
- Include fallback to `Intent.ACTION_OPEN_DOCUMENT_TREE`.

#### [MODIFY] [ContainerStorageManagerDialog.kt](file:///C:/Users/sorin/StudioProjects/GameNative-/app/src/main/java/app/gamenative/ui/screen/settings/ContainerStorageManagerDialog.kt)
- Add "Manage via System Files" button in `StorageEntryCard`.
- Bind `onClick` to `StorageUtils.openSystemFileManager`.
- Ensure styling matches existing action buttons.

---

### 3. Controller Mapping Profile Bug Fixes [Task 3]
Resolve keymap state loss and persistence bugs, ensure non-destructive profile activation, and improve save validation.

#### [MODIFY] [XServerScreen.kt](file:///C:/Users/sorin/StudioProjects/GameNative-/app/src/main/java/app/gamenative/ui/screen/xserver/XServerScreen.kt)
- **Selection Fix**: Update `onProfileSelected` to switch the active profile ID instead of overwriting the current profile's bindings, ensuring the saved profile remains intact and the UI state is preserved.
- **Save Validation**: Although `SaveProfileDialog` already has UI validation, ensure the `onSave` callback in `XServerScreen` doesn't proceed if the name is invalid (redundancy for safety).
- **Drawer Refresh**: Ensure `customVirtualProfiles` and `customPhysicalProfiles` are updated immediately after a successful save or delete operation.

#### [MODIFY] [SaveProfileDialog.kt](file:///C:/Users/sorin/StudioProjects/GameNative-/app/src/main/java/app/gamenative/ui/component/dialog/SaveProfileDialog.kt)
- Verify name uniqueness and non-emptiness checks are robust.

## Verification Plan

### Automated Tests
- N/A for UI and I/O logic in this scope, focus on manual verification.

### Manual Verification
- **Storage Move**: Perform "Move to External" and "Move to Internal" on a large container and verify speed improvements and progress bar accuracy.
- **System Files Shortcut**: Tap "Manage via System Files" for different games and verify it opens the correct folder in the native Android Files app.
- **Controller Profiles**:
    - Save a new profile and verify it appears immediately in the drawer.
    - Select a saved profile and verify the controls update immediately.
    - Check that the previously active profile's bindings were not lost or overwritten by the newly selected one.
    - Try to save a profile with an empty name or a duplicate name and verify it is blocked.
