# VersionOS Launcher 🚀

**Inspire from KISS Launcher but LIGHTER.**

VersionOS is an ultra-minimalist, high-performance Android launcher designed for those who value speed, privacy, and technical purity. It delivers a modern smartphone experience within an impossible **45 KB** footprint.

## 📦 The 45KB Engineering Marvel
VersionOS is built with **zero external dependencies**. No AndroidX, no Material Components, no heavy libraries. By using pure Android SDK and hardware-accelerated native APIs, we've reduced the size by 99% compared to traditional launchers.

## ✨ Advanced Features
- **Hybrid Search/Drawer**: A single unified interface for searching web/apps and browsing your full library.
- **Smart Gestures**:
    - **Swipe Down**: Expand notifications panel (via Reflection).
    - **Double Tap**: Lock screen/Sleep (via Device Admin).
- **Privacy Management**: Hide sensitive apps from search and dock with a full-featured, icon-supported selection menu.
- **Responsive Design**: Dynamically calculates icon sizes, margins, and corner radii based on screen DPI and aspect ratio.
- **Live Stats Widget**: Real-time monitoring of CPU, RAM, and ROM usage in a sleek, non-intrusive capsule design.
- **AMOLED Save Mode**: Toggle pure black (#000000) background to eliminate pixel power consumption.
- **Edge-to-Edge**: Fully transparent system bars for a truly immersive wallpaper experience.

## 🛠 Shortcuts & Interaction
- **Clock**: Opens Alarms.
- **Date**: Opens Calendar.
- **Long Press Empty Space**: Access Settings (Black Mode, Hide Apps, Default Launcher).
- **Long Press Dock**: Reassign app shortcuts.
- **App Search**: Empty search displays an A-Z sorted app library.

## 🚀 Technical Philosophy: "Mechanical Sympathy"
VersionOS is designed to work *with* the Android OS, not on top of it:
- **Zero-Cost UI**: Uses `ViewOutlineProvider` for native corner clipping.
- **O(1) Filtering**: App hiding and searching performed with zero UI lag.
- **Low Memory Footprint**: Persistent objects are reused to minimize Garbage Collection (GC) pauses.
- **Pure SDK**: Compatible with Android 5.0 (API 21) and above.

## 📝 License
MIT License. Keep it light, keep it fast.

Developed with ❤️ by LiferLighdow