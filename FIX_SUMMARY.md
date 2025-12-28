# Fix for ResourceResolutionException Crash

## Problem
The app was crashing with the following error:
```
Fatal Exception: androidx.compose.ui.res.ResourceResolutionException: 
Error attempting to load resource: res/drawable-xxhdpi-v4/top_banner.9.png
    at androidx.compose.ui.res.PainterResources_androidKt.loadImageBitmapResource
    at androidx.compose.ui.res.PainterResources_androidKt.painterResource
    at com.machi.memoiz.ui.screens.MainScreenKt$MainScreen$3.invoke(MainScreen.kt:116)
```

## Root Cause
The file `app/src/main/res/drawable-xxhdpi/top_banner.9.png` was named with the `.9.png` extension, which indicates it should be a [9-patch PNG](https://developer.android.com/develop/ui/views/graphics/drawables#nine-patch) - a special PNG format with stretchable regions.

However, the file was missing the required `npTc` (nine-patch chunk) in its PNG metadata. This chunk contains the information about which parts of the image should stretch. Without it, Android's resource loader fails to properly process the image as a 9-patch drawable, resulting in a crash.

## Solution
The fix was simple: rename the file from `top_banner.9.png` to `top_banner.png` to treat it as a regular PNG image instead of a 9-patch.

This is appropriate because:
1. The image is only used in `MainScreen.kt` line 116 with `ContentScale.FillBounds`
2. It doesn't actually need 9-patch stretching functionality
3. The resource ID `R.drawable.top_banner` remains valid after the rename

## Changes Made
- Renamed: `app/src/main/res/drawable-xxhdpi/top_banner.9.png` → `app/src/main/res/drawable-xxhdpi/top_banner.png`

## Technical Details

### What is a 9-Patch PNG?
A 9-patch PNG is a special PNG format used in Android that defines stretchable regions. The file format requirements are:
- Must have `.9.png` extension
- Must include black pixel markers on the 1-pixel border indicating stretch regions
- Must include an `npTc` chunk in the PNG data with encoded stretch/pad information

### Why Did This Crash?
When Android's resource system sees a `.9.png` file, it expects to find the `npTc` chunk and stretch region data. The absence of this data causes the resource loader to throw a `ResourceResolutionException`.

### Alternative Fixes
If 9-patch functionality were actually needed, the proper fix would be to:
1. Use Android Studio's Draw 9-patch tool to add stretch markers
2. Or use `aapt` to compile the image properly with stretch regions

However, since the image is used with `FillBounds` scaling (which stretches the entire image), 9-patch functionality is unnecessary.

## Testing
To verify the fix:
1. Build the app: `./gradlew assembleDebug`
2. Run the app and navigate to the Main Screen
3. Verify the top banner image displays correctly
4. No crash should occur when loading the resource

## References
- [Android 9-patch Documentation](https://developer.android.com/develop/ui/views/graphics/drawables#nine-patch)
- [Draw 9-patch Tool](https://developer.android.com/studio/write/draw9patch)
