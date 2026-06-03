# MiMoTTS-MobileApp V2.2

Android Studio project for MiMo V2.5 TTS mobile app.

## V2.2 更新

- 修复 WebView 内“下载音频文件”点击无反应的问题。
- 下载音频时可以自定义文件名。
- 设置里新增下载位置，默认保存到 `Download/MiMoTTS`，可改成 Download 下面的自定义文件夹。
- 设置里新增深色模式：跟随系统 / 浅色 / 深色。
- 保留复刻音色管理：查看、试听、删除、清空。
- 版本号更新为 `2.0`。
- 包名保持新版独立包名：`com.jiushuangchi.mimotts.manager`。

## 打开方式

1. 解压项目。
2. Android Studio 选择 `Open`。
3. 选中 `MiMoTTS-MobileApp` 文件夹。
4. 等 Gradle Sync 完成。
5. 连接手机或模拟器运行。

## GitHub Actions

项目已包含：

`.github/workflows/android.yml`

推送到 GitHub 后会自动构建 Debug APK，构建成功后在 Actions 的 Artifacts 下载。


## V2.2 更新

- 修复部分按钮点击无反应的问题。
- 修复下载文件名清理函数导致的前端脚本异常。
- UI 改为液态玻璃 / 深色模式专属 UI风格。
- 保留深色模式、下载位置和复刻音色管理。
