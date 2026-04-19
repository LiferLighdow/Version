# Version Launcher (v1.0.0)

A high-performance, ultra-lightweight Android Launcher designed with a focus on "Invisible Personalization" and extreme resource efficiency. Created for the upcoming **VersionOS (AOSP Android 17)** but fully compatible with legacy hardware.

## 🚀 Highlights

- **Extreme Size:** Only **~186 KB** (smaller than most high-res wallpapers).
- **Minimal RAM Footprint:** Runs at **~27-28 MB** Pss Total on 2GB RAM devices.
- **Pure Engineering:** Zero dependencies. No `androidx`, no `Material` library—just pure Android Framework APIs.
- **Integrated Monitoring:** Real-time CPU, RAM, and ROM status displayed directly on the home screen.
- **Aesthetic Precision:** System-level rounded corners and translucent UI elements optimized for GPU performance.

## 🛠 System Features

- **Dual-Mode Architecture:** - **Standard APK:** Works on any Android device with minimal permissions.
  - **System Integration:** When placed in `/system/priv-app/`, it unlocks native gestures like "Double Tap to Sleep" via `PowerManager` and seamless window transition animations.
- **Low Overdraw:** Optimized view hierarchy (only 21 Views) to maintain a steady 60 FPS even on 10-year-old hardware.

## 📊 Benchmarks

| Metric | Result |
| :--- | :--- |
| **APK Size** | 186 KB |
| **Private Dirty RAM** | ~11.5 MB |
| **Cold Start Time** | < 100ms |
| **View Hierarchy Depth** | 2-3 Layers |

## 📦 Getting Started

1. Download the latest `app-release.apk` from the [Releases](https://github.com/liferlighdow/Version/releases) page.
2. Set as the default launcher.
3. **Recommended Bundle:** Best paired with **VersionOS Wallpaper (185 KB)** and our **[IME (44 KB)](https://github.com/liferlighdow/IME)** for the ultimate "Air-like" experience.

## 📜 Philosophy
> "Aesthetics should not be built on the waste of resources. Every byte is a choice." — *Lifer_Lighdow*
