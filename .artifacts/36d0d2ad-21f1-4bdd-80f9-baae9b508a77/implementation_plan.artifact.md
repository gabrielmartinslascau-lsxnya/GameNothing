# Implementation Plan - Remove Membership/Tip Buttons & Implement 5-Launch Membership Prompt with TikTok Redirect

This plan removes the deprecated "Become a member" button and membership offer toggle from settings, updates membership pitch triggers to appear every 5 app launches, and updates the prompt copy and options to target TikTok (`@lsxnothing`) with green "sure!" and red "i think.. later" buttons.

## User Review Required

> [!IMPORTANT]
> - **Removal of Settings Tile**: The "Become a member" button and membership offers toggle in `SettingsGroupInfo.kt` are completely removed.
> - **Prompt Trigger Frequency**: The membership prompt now shows every 5 app launches.
> - **New Dialog Text & Buttons**:
>   - **Message**: *"liking this lsx version? help it make better by becoming part of it! you will even get youre place in it and its development"*
>   - **Buttons**: **"sure!"** (green button, links to `https://www.tiktok.com/@lsxnothing`) and **"i think.. later"** (red button, dismisses).

## Proposed Changes

### Constants & Preferences
#### [MODIFY] [Constants.kt](file:///C:/Users/sorin/StudioProjects/GameNative-/app/src/main/java/app/gamenative/Constants.kt)
- Add `TIKTOK_LINK = "https://www.tiktok.com/@lsxnothing"`.

#### [MODIFY] [PrefManager.kt](file:///C:/Users/sorin/StudioProjects/GameNative-/app/src/main/java/app/gamenative/PrefManager.kt)
- Add launch count tracking (`app_launch_count`) to increment on each app start and trigger every 5 launches.

### Settings
#### [MODIFY] [SettingsGroupInfo.kt](file:///C:/Users/sorin/StudioProjects/GameNative-/app/src/main/java/app/gamenative/ui/screen/settings/SettingsGroupInfo.kt)
- Remove `SettingsMenuLink` for send tip ("Become a member") and `SettingsSwitch` for membership offers.

### Dialog & PluviaMain
#### [MODIFY] [MessageDialog.kt](file:///C:/Users/sorin/StudioProjects/GameNative-/app/src/main/java/app/gamenative/ui/component/dialog/MessageDialog.kt)
- Allow custom button background or text color overrides for confirm/action and dismiss buttons (green for "sure!", red for "i think.. later").

#### [MODIFY] [PluviaMain.kt](file:///C:/Users/sorin/StudioProjects/GameNative-/app/src/main/java/app/gamenative/ui/PluviaMain.kt)
- Increment launch count on Home screen display.
- Trigger membership prompt every 5 launches (`launchCount % 5 == 0`).
- Display the requested message and buttons with TikTok link integration.

## Verification Plan

### Automated Tests
- Gradle build verification (`app:assembleDebug`).

### Manual Verification
- Verify settings screen no longer displays membership buttons/offers.
- Test app launch counter triggers the TikTok membership prompt on every 5th launch.
