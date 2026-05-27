<p align="center">
  <img src="app/src/main/ic_launcher-playstore.png" width="100" height="100">
</p>

<h1 align="center">VersionOS Launcher</h1>

<p align="center">
  <strong>Inspire from KISS Launcher but LIGHTER.</strong><br>
  <em>Ultra-minimalist. High-performance. Zero-bloat.</em>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Language-Pure%20Java-orange" alt="Language">
  <img src="https://img.shields.io/badge/Android-5.0--17%2B-green" alt="Android">
  <img src="https://img.shields.io/badge/Size-60%20KB-blue" alt="Size">
  <img src="https://img.shields.io/badge/License-MIT-lightgrey" alt="License">
  <img src="https://img.shields.io/badge/Dependencies-Zero-red" alt="Dependencies">
</p>

---

VersionOS 2.0.0 is a high-performance Android launcher designed for those who value speed, privacy, and technical purity. It delivers a modern smartphone experience within an impossible **60 KB** footprint by stripping away all external libraries and returning to pure Android SDK development.

## ✨ Key Features (v2.0.0 "Widget & Privacy Update")

### 🖼️ Advanced Widget Hosting (NEW)
- **Full Widget Support**: Now includes a complete `AppWidgetHost` engine to run any system or 3rd-party widget.
- **Precision Layout**: Manually adjust Widget position (Y-offset), width scale, and height via a dedicated management menu.
- **Stability**: State-persistent storage ensures your widgets stay exactly where you left them after reboot.

### 🔒 Enhanced Privacy & Security (NEW)
- **PIN Protection**: Secure your hidden apps list with a numeric PIN code.
- **Hidden App Launcher**: A dedicated, password-protected portal to launch apps that are hidden from the main list.
- **Deep Filtering**: Automatically excludes the launcher itself from the app list for a cleaner experience.

### 🎨 Intelligent Theming
- **Four Distinct Modes**: Default (Glass), OLED Black, Snow White, and the all-new **AOSP Style**.
- **AOSP Style Evolution**: 
    - **Native Experience**: Features a 5-column grid layout that perfectly matches the 5-icon dock.
    - **Clean Desktop**: Automatically hides the main search bar and stats widget for a distraction-free home screen.
    - **Adaptive Grid**: 5xN grid layout for the App Drawer with optimized spacing for modern screen ratios.

### 🔍 Unified Search & App Drawer
- **Hybrid Interface**: A single, lightning-fast interface for searching apps.
- **Smart Fallback**: If no local app is found, pressing 'Enter' instantly performs a web search via your default browser.
- **Multi-Component Support**: Optimized indexing for apps with multiple entry points (clones/shortcuts).

### 🛠 Professional Customization
- **Icon Studio**: 
    - **Interactive Cropping**: Manual pan and zoom to perfectly frame your custom icons.
    - **Universal Adaptive Icons**: Retrofits **Circle/Rounded Square masking** for devices running Android 5.0+, ensuring a modern look on legacy hardware.
- **Label Editing**: Rename apps directly from the home screen for a personalized aesthetic.

### 👆 Intuitive Gestures
- **Smart Swipe (AOSP Mode)**:
    - **Swipe Up**: Instantly call the App Drawer.
- **System Gestures**:
    - **Fling Down**: Expand the system notification panel (via safe reflection).
    - **Double Tap**: Lock screen/Sleep (Requires Device Admin activation).

### 📊 Real-Time Telemetry
- **Hardware Stats**: Capsule-design widget monitoring **Battery Level**, **RAM usage**, and **Storage (ROM)** status.
- **Optimized Monitoring**: Replaced legacy CPU polling with high-efficiency Battery API calls to maximize device standby time.

## 🚀 Technical Philosophy: "Mechanical Sympathy"
- **Zero-Cost UI**: Employs `ViewOutlineProvider` for native hardware-layer corner clipping.
- **Memory Management**: Manual `Bitmap` recycling and `LruCache` sizing based on 1/8 of available system RAM.
- **High-Speed Indexing**: Transitioned from $O(N \cdot M)$ list iterations to $O(1)$ map lookups for all UI components.
- **Compatibility**: Supports Android 5.0 (API 21) up to the latest Android 17+ (Target SDK 37).

## 📝 Interaction Guide
- **Clock**: Tap to open Alarm/Clock.
- **Date**: Tap to open Calendar.
- **Desktop Long Press**: Access global settings (Theme, Hidden Apps, Default Launcher).
- **Dock Long Press**: Modify shortcuts, icons, or labels.

## 📜 License
**MIT License**. Keep it light, keep it fast. Stay in control.

---

**Developed with ❤️ by LiferLighdow**
