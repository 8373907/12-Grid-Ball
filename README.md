# 12-Grid Floating Ball (安卓12宫格悬浮球)

🚀 一个轻量级、高效的 Android 悬浮球辅助工具。点击悬浮球即可弹出 12 宫格菜单，快速启动你最常用的 App。

## ✨ 核心功能 (Features)

* **快捷启动**：支持从已安装应用中选择最多 12 个 App 放入快捷菜单。
* **自定义排序**：支持在设置页通过“⬆️上移”和“⬇️下移”按钮自定义图标顺序（**v1.1 新增**）。
* **应用搜索**：内置搜索栏，快速查找并添加应用。
* **智能贴边隐藏**：
    * 悬浮球在不使用时会自动吸附到屏幕边缘。
    * 3秒无操作后自动变为半透明“扇形”隐藏模式，减少干扰。
* **防烧屏保护**：在隐藏模式下，悬浮球会每隔一分钟微调位置，防止 OLED 屏幕烧屏。
* **横竖屏适配**：完美适配横屏游戏/视频场景，悬浮球大小自动调整，不会变成“巨无霸”（**v1.1 修复**）。
* **交互优化**：
    * **短按**：打开/关闭 12 宫格菜单。
    * **长按 (1秒)**：打开本软件主设置界面。
    * **拖拽**：自由移动悬浮球位置。

## 📸 截图 (Screenshots)

| 设置界面 (排序) | 12宫格菜单 | 隐藏模式 |
|:---:|:---:|:---:|
| ![Settings](https://via.placeholder.com/200x400?text=Settings) | ![Menu](https://via.placeholder.com/200x400?text=Menu) | ![Hidden](https://via.placeholder.com/200x400?text=Hidden) |

## 🛠️ 更新日志 (Changelog)

### v1.1 (Current)
* 🔥 **新增**：应用排序功能，支持置顶显示常用 App。
* 🎨 **优化**：修复横屏下悬浮球尺寸过大的问题（改为基于屏幕短边计算）。
* 👆 **优化**：改进触摸算法，增加防抖动判断，解决点击列表自动滚屏的问题。
* ⚙️ **调整**：长按悬浮球改为直接打开主设置页面，取消繁琐的包名输入框。
* 🐛 **修复**：增加震动权限异常捕获，防止部分机型崩溃。

### v1.0
* 基础功能实现：悬浮球显示、拖拽、吸附。
* 12宫格菜单选择与启动。
* 半透明隐藏与防烧屏逻辑。

## 🚀 如何使用 (How to use)

1.  下载并安装 APK。
2.  首次打开，授予 **“显示在其他应用上层 (悬浮窗)”** 权限。
3.  在下方列表中勾选你需要放入悬浮球的 App（支持搜索）。
4.  在顶部 **“排序区”** 点击箭头调整图标顺序。
5.  点击底部的 **“保存排序 并 重启悬浮球”** 按钮。
6.  现在，悬浮球已经出现在屏幕上了！

## 📦 开发环境

* **Language**: Java
* **Min SDK**: Android 7.0 (API 24)
* **Target SDK**: Android 13 (API 33)
* **Permissions**: 
    * `SYSTEM_ALERT_WINDOW` (悬浮窗)
    * `VIBRATE` (震动反馈)
    * `QUERY_ALL_PACKAGES` (读取应用列表)

---

**Author**: [Your Name/GitHub ID]
**License**: MIT



----------------------------------
# 12-Grid Floating Ball

🚀 A lightweight and efficient Android floating assistant tool. Tap the floating ball to pop up a 12-grid menu and quickly launch your most frequently used apps.

## ✨ Core Features

* **Quick Launch**: Select up to 12 apps from your installed applications to add to the quick access menu.
* **Custom Sorting**: Customize the icon order in the settings page using "⬆️ Move Up" and "⬇️ Move Down" buttons (**New in v1.1**).
* **App Search**: Built-in search bar to quickly find and add apps.
* **Smart Edge Snapping**:
    * The floating ball automatically snaps to the screen edge when not in use.
    * After 3 seconds of inactivity, it automatically transforms into a semi-transparent "fan-shaped" hidden mode to minimize distraction.
* **Burn-in Protection**: In hidden mode, the floating ball slightly adjusts its position every minute to prevent OLED screen burn-in.
* **Landscape Adaptation**: Perfectly adapted for landscape games/videos. The floating ball size adjusts automatically and won't become oversized (**Fixed in v1.1**).
* **Optimized Interaction**:
    * **Short Press**: Open/Close the 12-grid menu.
    * **Long Press (1s)**: Open the main settings interface of this app.
    * **Drag**: Freely move the floating ball position.

## 📸 Screenshots

| Settings (Sorting) | 12-Grid Menu | Hidden Mode |
|:---:|:---:|:---:|
| ![Settings](https://via.placeholder.com/200x400?text=Settings) | ![Menu](https://via.placeholder.com/200x400?text=Menu) | ![Hidden](https://via.placeholder.com/200x400?text=Hidden) |

## 🛠️ Changelog

### v1.1 (Current)
* 🔥 **New**: App sorting function. Supports pinning and ordering favorite apps.
* 🎨 **Optimization**: Fixed the issue where the floating ball became too large in landscape mode (now calculated based on the screen's shorter side).
* 👆 **Optimization**: Improved touch algorithm with added jitter protection to solve the issue of the list scrolling automatically when clicking items.
* ⚙️ **Adjustment**: Changed the "Long Press" action to directly open the main settings page, removing the cumbersome package name input box.
* 🐛 **Fix**: Added exception handling for vibration permissions to prevent crashes on certain devices.

### v1.0
* Implemented basic functions: Floating ball display, dragging, and edge snapping.
* 12-grid menu selection and launching.
* Semi-transparent hiding and anti-burn-in logic.

## 🚀 How to Use

1.  Download and install the APK.
2.  Upon first launch, grant the **"Display over other apps"** permission.
3.  Check the apps you want to add to the floating ball from the list below (search supported).
4.  Use the arrows in the top **"Sorting Area"** to adjust the icon order.
5.  Click the **"Save Sorting & Restart Ball"** button at the bottom.
6.  The floating ball should now appear on your screen!

## 📦 Development Environment

* **Language**: Java
* **Min SDK**: Android 7.0 (API 24)
* **Target SDK**: Android 13 (API 33)
* **Permissions**:
    * `SYSTEM_ALERT_WINDOW` (Floating Window)
    * `VIBRATE` (Haptic Feedback)
    * `QUERY_ALL_PACKAGES` (Read App List)

---

**Author**: [Your Name/GitHub ID]
**License**: MIT