# Implementation Plan: Interactive First-Time Tutorial

This plan implements an interactive onboarding experience for first-time users, explaining key features and technical recommendations.

## User Review Required

> [!IMPORTANT]
> - **Tutorial Entry**: The tutorial will replace the Steam login screen on a fresh install.
> - **3 Interactive Steps**:
>   - **Step 1**: Explains how to access Settings (Menu icon or START button).
>   - **Step 2**: Interactive Glass Customization (Change colors in real-time).
>   - **Step 3**: Technical Recommendations (Proton 11 and DXVK 2.4.1).
> - **Navigation**: Users can Skip entirely, go Back, or proceed Step-by-Step.

## Proposed Changes

### [Preferences]

#### [MODIFY] [PrefManager.kt](file:///C:/Users/sorin/StudioProjects/GameNative-/app/src/main/java/app/gamenative/PrefManager.kt)
- Added `hasCompletedTutorial` preference to track if onboarding should be shown.
- Cleaning up duplicate declarations from previous turns.

### [Navigation]

#### [MODIFY] [PluviaScreen.kt](file:///C:/Users/sorin/StudioProjects/GameNative-/app/src/main/java/app/gamenative/ui/screen/PluviaScreen.kt)
- Added `Onboarding` destination.

#### [MODIFY] [PluviaMain.kt](file:///C:/Users/sorin/StudioProjects/GameNative-/app/src/main/java/app/gamenative/ui/PluviaMain.kt)
- Set `startDestination` to `Onboarding` if tutorial is incomplete.
- Added navigation logic to move from Onboarding to Login.

### [Tutorial UI]

#### [NEW] [TutorialScreen.kt](file:///C:/Users/sorin/StudioProjects/GameNative-/app/src/main/java/app/gamenative/ui/screen/TutorialScreen.kt)
- Implemented the 3-step interactive tutorial.
- Integrated `MainViewModel` for real-time theme preview in Step 2.

## Verification Plan

### Manual Verification
1.  **Fresh Install Simulation**:
    *   Clear app data or set `hasCompletedTutorial` to `false` in code.
    *   Verify the app opens to the Tutorial screen instead of the Login screen.
2.  **Interactive Step 2**:
    *   Navigate to the second page.
    *   Pick different colors for Main and Wallpaper.
    *   Verify the UI (glass panel in tutorial) updates instantly.
3.  **Completion**:
    *   Finish the tutorial or tap "Skip".
    *   Verify you land on the Steam Login screen.
    *   Verify the tutorial doesn't show again on subsequent launches.
