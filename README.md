MiMo V2.5 TTS For Android

这是一个基于 MiMo V2.5 TTS 的安卓端语音合成项目仓库，主要用于在手机端调用 MiMo TTS 接口，将输入文本转换为语音音频。

本仓库目前同时保留两个版本，方便对比、回退和后续维护。

项目结构

MiMo-V2.5TTS-For-Android/
├─ MiMoTTS-MobileAppV1.0/   # 旧版本项目
├─ MiMoTTS-MobileAppV2.0/   # 新版本项目
└─ README.md                # 仓库说明文件

版本说明

MiMoTTS-MobileAppV1.0

旧版本项目，用于保留之前的代码和功能实现，方便后续查看、对比和回退。

MiMoTTS-MobileAppV2.0

当前主要维护版本，作为后续继续开发和打包 Android App 的主要项目。

主要目标：

- 适配安卓手机端使用
- 提供更完整的移动端 UI
- 支持文本转语音功能
- 支持 API Key 和接口地址配置
- 支持音色相关设置
- 支持语音预览和保存
- 页面布局更接近成品 App
- 方便后续使用 Android Studio 构建 APK

功能介绍

当前项目主要包含以下功能方向：

- 文本输入
- 文本转语音
- 音色配置
- API 设置
- 接口地址设置
- 移动端界面适配
- 安卓 WebView / App 打包支持

使用方式

使用 Android Studio 打开

1. 下载或克隆本仓库
2. 打开 Android Studio
3. 选择 "Open"
4. 进入 "MiMoTTS-MobileAppV2.0" 文件夹
5. 等待 Gradle 自动同步
6. 连接安卓手机或使用模拟器运行
7. 构建 APK 后安装到手机

Termux 上传参考

如果项目文件在手机目录：

/storage/emulated/0/MiMoTTS-MobileAppV2.0/

可以使用 Termux 配合 Git 上传到 GitHub。

常用初始化命令：

pkg update -y
pkg install git -y

git config --global user.name "ggyyuig"
git config --global user.email "2164211722@qq.com"

如果 Git 提示 "dubious ownership"，可以执行：

git config --global --add safe.directory /storage/emulated/0/MiMoTTS-MobileAppV2.0

API 设置说明

应用中需要配置 MiMo TTS 相关接口信息，一般包括：

- API Key
- API URL / Base URL
- 模型名称
- 音色参数
- 输入文本内容

请根据 MiMo 开放平台提供的真实接口文档填写参数。

注意：不要把自己的 API Key 直接写死并提交到公开仓库中，建议只在 App 设置页面中本地填写。

注意事项

- "MiMoTTS-MobileAppV1.0" 是旧版本
- "MiMoTTS-MobileAppV2.0" 是当前主要版本
- GitHub 仓库首页只会自动显示根目录下的 "README.md"
- 如果 README 没显示，请检查文件名是否写成了 "RADEME.md"
- 如果项目无法编译，请检查 Gradle、Android SDK、依赖下载和网络环境
- 如果上传 GitHub 失败，请检查 Token 权限和远程仓库地址

后续计划

- 优化移动端 UI
- 完善音色选择功能
- 增加音频预览功能
- 增加音频保存功能
- 增加错误提示
- 增加 API 配置保存功能
- 优化 Android 打包流程
- 提升手机端使用体验

作者

九霜迟
