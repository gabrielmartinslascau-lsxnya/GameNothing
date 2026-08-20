# Rebrand to GameNative-LSX and Add TikTok Membership Pitch

This plan covers rebranding the application, adding a new Legion-style logo option, removing legacy membership settings, and implementing a recurring TikTok promotion popup triggered by the launch counter.

## User Review Required

> [!IMPORTANT]
> - **Branding Change**: All hardcoded "GameNative" strings in the splash screen will be changed to "GameNative-LSX".
> - **Membership Removal**: The "Become a member" and "Membership offers" sections will be completely removed from the Settings screen.
> - **TikTok Promotion**: A new popup will appear every 5th launch promoting @lsxnothing on TikTok, with a "Sure!" (Green) and "Later" (Red) button.

## Proposed Changes

### [Core/Branding]

#### [MODIFY] [BootingSplash.kt](file:///C:/Users/sorin/StudioProjects/GameNative-/app/src/main/java/app/gamenative/ui/components/BootingSplash.kt)
- Update text from "GameNative" to "GameNative-LSX" in the logo display section (lines 183, 200).

#### [MODIFY] [Constants.kt](file:///C:/Users/sorin/StudioProjects/GameNative-/app/src/main/java/app/gamenative/Constants.kt)
- Add `TIKTOK_LINK = "https://www.tiktok.com/@lsxnothing"` to `Constants.Misc`.

### [Preferences & Launch Tracking]

#### [MODIFY] [PrefManager.kt](file:///C:/Users/sorin/StudioProjects/GameNative-/app/src/main/java/app/gamenative/PrefManager.kt)
- Add `launchCount` property (intPreferencesKey).
- Replace `useAltLauncherIcon` and `useAltNotificationIcon` with `launcherIconVariant` (0=Default, 1=Alternate, 2=Legion).

#### [MODIFY] [MainActivity.kt](file:///C:/Users/sorin/StudioProjects/GameNative-/app/src/main/java/app/gamenative/MainActivity.kt)
- Increment `PrefManager.launchCount` in `onCreate`.
- Trigger `MainUiEvent.ShowMembershipPitch` (to be repurposed for TikTok) if `launchCount % 5 == 0`.

### [Settings & UI]

#### [MODIFY] [SettingsGroupInfo.kt](file:///C:/Users/sorin/StudioProjects/GameNative-/app/src/main/java/app/gamenative/ui/screen/settings/SettingsGroupInfo.kt)
- Remove the membership-related `SettingsMenuLink` and `SettingsSwitch`.

#### [MODIFY] [SettingsGroupInterface.kt](file:///C:/Users/sorin/StudioProjects/GameNative-/app/src/main/java/app/gamenative/ui/screen/settings/SettingsGroupInterface.kt)
- Add a 3rd `IconVariantCard` for the "Legion" style logo.
- Update `selectedVariant` logic to handle 3 options.

#### [MODIFY] [PluviaMain.kt](file:///C:/Users/sorin/StudioProjects/GameNative-/app/src/main/java/app/gamenative/ui/PluviaMain.kt)
- Repurpose `ShowMembershipPitch` handler to show the TikTok promotion.
- Update `MessageDialogState` to use new strings and support custom button colors.

#### [MODIFY] [MessageDialog.kt](file:///C:/Users/sorin/StudioProjects/GameNative-/app/src/main/java/app/gamenative/ui/component/dialog/MessageDialog.kt)
- Update `MessageDialog` and `MessageDialogState` to support `confirmBtnColor` and `dismissBtnColor`.

### [Resources]

#### [MODIFY] [strings.xml](file:///C:/Users/sorin/StudioProjects/GameNative-/app/src/main/res/values/strings.xml)
- Add strings for TikTok popup: `tiktok_pitch_message`, `tiktok_pitch_confirm`, `tiktok_pitch_dismiss`.
- Remove legacy membership strings.

#### [NEW] [ic_logo_legion.xml](file:///C:/Users/sorin/StudioProjects/GameNative-/app/src/main/res/drawable/ic_logo_legion.xml)
- Vector drawable for the new Legion-style logo.

#### [NEW] [ic_launcher_legion.xml](file:///C:/Users/sorin/StudioProjects/GameNative-/app/src/main/res/mipmap-anydpi-v26/ic_launcher_legion.xml)
- Adaptive icon for the Legion variant.

#### [MODIFY] [AndroidManifest.xml](file:///C:/Users/sorin/StudioProjects/GameNative-/app/src/main/AndroidManifest.xml)
- Add `MainActivityAliasLegion` with `ic_launcher_legion`.

#### [MODIFY] [IconSwitcher.kt](file:///C:/Users/sorin/StudioProjects/GameNative-/app/src/main/java/app/gamenative/utils/IconSwitcher.kt)
- Update to support switching between 3 aliases.

## Verification Plan

### Automated Tests
- N/A (UI and Preference changes)

### Manual Verification
1. Launch the app and verify the splash screen shows "GameNative-LSX".
2. Go to Settings > Interface and verify the 3rd logo option (Legion) is available and works.
3. Go to Settings > Info and verify membership options are gone.
4. Close and launch the app 5 times to verify the TikTok popup appears.
5. Verify "Sure!" button opens TikTok and "Later" button dismisses it.
6. Verify the popup buttons have the correct colors (Green/Red).
