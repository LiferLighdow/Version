# VersionOS Launcher 🚀

**Inspire from KISS Launcher but LIGHTER.**

VersionOS is an ultra-minimalist, high-performance Android launcher designed for those who value speed, privacy, and technical purity. It delivers a modern smartphone experience within an impossible **45 KB** footprint.

## 📦 The 50KB Engineering Marvel
VersionOS is built with **zero external dependencies**. No AndroidX, no Material Components, no heavy libraries. By using pure Android SDK and hardware-accelerated native APIs, we've reduced the size by 99% compared to traditional launchers.

## ✨ Advanced Features
- **Hybrid Search/Drawer**: A single unified interface for searching apps. **Smart Fallback**: If no local app is found, pressing 'Enter' instantly launches a Google web search.
- **UI Themes**: Choose from **Default (Glass)**, **OLED Black**, **Snow White**, or **AOSP Style**. 
    - *AOSP Style* features a refined 5-icon layout and hides the Stats Widget for the ultimate clean look.
- **Icon Customization**: 
    - **Interactive Cropping**: Professional-grade manual pan and zoom to select your favorite image area.
    - **Legacy Adaptive Icons**: Brings modern **Circle/Rounded Square masking** to Android 5.0 - 7.1 devices, ensuring a unified look on older hardware.
    - **Memory Optimized**: Custom icons are pre-sampled and cached to prevent OOM.
- **App Renaming**: Personalize application labels for a cleaner dock or easier searching.
- **Smart Gestures**:
    - **Swipe Down**: Expand notifications panel (via Reflection).
    - **Double Tap**: Lock screen/Sleep (Requires Device Admin activation).
- **Privacy Management**: Hide sensitive apps from both the search results and dock with a full-featured selection menu.
- **Live Stats Widget**: Real-time monitoring of CPU, RAM, and ROM usage in a sleek capsule design.
- **AMOLED Save Mode**: Toggle pure black (#000000) background to eliminate pixel power consumption.

## 🛠 Shortcuts & Interaction
- **Clock**: Opens Alarms.
- **Date**: Opens Calendar.
- **Long Press Empty Space**: Access Settings (Theme selection, Black Mode, Hide Apps, and **Quick Set as Default Launcher**).
- **Long Press Dock Item**: 
    - **Change App**: Reassign the shortcut.
    - **Change Icon**: Launch the interactive cropper.
    - **Change Name**: Set a custom label.
    - **Reset**: Revert icon or name to system defaults.

## 🚀 Technical Philosophy: "Mechanical Sympathy"
VersionOS is designed to work *with* the Android OS, not on top of it:
- **Zero-Cost UI**: Uses `ViewOutlineProvider` for native corner clipping and `Matrix` transformations for high-speed image processing.
- **Zero AndroidX**: Built purely on the platform's `android.*` APIs to eliminate library overhead.
- **Memory Efficiency**: Manual `Bitmap` recycling and `inSampleSize` decoding to maintain a tiny RAM footprint.
- **O(1) Filtering**: App search and visibility logic performed with zero UI lag.
- **Pure SDK**: Compatible with Android 5.0 (API 21) and above.

## 📝 License
MIT License. Keep it light, keep it fast.

Developed with ❤️ by LiferLighdow