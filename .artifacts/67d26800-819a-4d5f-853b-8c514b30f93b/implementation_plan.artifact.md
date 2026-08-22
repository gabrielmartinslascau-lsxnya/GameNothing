# Fix Content Bleeding on Game Library Screen

This plan addresses the issue where game items in the library scroll behind the header/top bar and are visible through rounded edges or above the top bar.

## Proposed Changes

### Library UI Components

#### [MODIFY] [LibraryListPane.kt](file:///C:/Users/sorin/StudioProjects/GameNative-/app/src/main/java/app/gamenative/ui/screen/library/components/LibraryListPane.kt)
- Add `Modifier.clipToBounds()` to the `LazyVerticalGrid` to ensure items are clipped to the grid's bounds.
- Verify `contentPadding` has `top = 80.dp`. (Already present).
- Apply `clipToBounds()` to the skeleton grid as well.

#### [MODIFY] [LibraryCarouselPane.kt](file:///C:/Users/sorin/StudioProjects/GameNative-/app/src/main/java/app/gamenative/ui/screen/library/components/LibraryCarouselPane.kt)
- Update `carouselTopMargin` to be a consistent `80.dp` to match the header height.
- Add `Modifier.clipToBounds()` to the `LazyRow` to ensure scrolled items cut off cleanly.

## Verification Plan

### Automated Tests
- N/A (UI layout change)

### Manual Verification
- Deploy the app to a device/emulator.
- Navigate to the Game Library screen.
- Scroll the grid/carousel and verify that items disappear cleanly when reaching the bottom of the header.
- Verify that items are no longer visible through the rounded corners of the header or above the top bar area.
