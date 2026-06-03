# MiMo V2.5 TTS Android Web Tabs - Java Stable

这是一个基于 Web 端界面改造的安卓本地 App。新版去掉了 Kotlin 和 OkHttp，改为纯 Java + Android WebView + HttpURLConnection，目的是减少 Android Studio / Gradle / Kotlin 版本不匹配导致的构建错误。

> 说明：本项目是“安卓本地运行、无自建后端服务器”的 App。MiMo TTS / VoiceClone 模型仍然需要联网请求小米 MiMo API，不是离线语音合成。

## 功能

- 安卓本地 App，不需要 FastAPI 后端
- 底部三分页：生成 / 复刻 / 结果
- API Token 放到右上角设置里
- 支持 `api-key` 和 `Bearer Token` 两种认证方式
- 支持内置音色：`mimo_default`、`冰糖`、`茉莉`、`苏打`、`白桦`、`Mia`、`Chloe`、`Milo`、`Dean`
- 支持上传音频样本保存为复刻音色
- 支持本机录音并转为 WAV 样本
- 支持播放生成音频并保存到 `下载/MiMoTTS`

## 推荐环境

- Android Studio
- JDK 17，直接使用 Android Studio 自带的 Embedded JDK / jbr
- Android 7.0 及以上手机

## 打开方式

1. 解压项目。
2. Android Studio 选择 `Open`。
3. 打开项目根目录。
4. 等待 Gradle Sync。
5. 连接手机，打开 USB 调试。
6. 点击 Run。

## 如果 Gradle JDK 报错

Android Studio 里打开：

```text
File → Settings → Build, Execution, Deployment → Build Tools → Gradle
```

把 `Gradle JDK` 设置为：

```text
C:\Program Files\Android\Android Studio\jbr
```

或者选择 `Embedded JDK`。

## 如果旧缓存导致同步失败

关闭 Android Studio，PowerShell 运行：

```powershell
cd "C:\Users\21642\Desktop\mimo-tts-android-web-tabs-java-stable"

Remove-Item -Recurse -Force .gradle -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force .idea -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force build -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force app\build -ErrorAction SilentlyContinue
```

然后重新用 Android Studio 打开项目。

## 使用方法

1. 打开 App。
2. 点击右上角设置。
3. 填写 MiMo API Token。
4. 认证方式先选 `api-key`，如果 401 再换 `Bearer`。
5. 回到“生成”页输入文本，选择音色，点击生成。
6. 生成成功后会自动跳转到“结果”页播放。

## 复刻音色

1. 切到底部“复刻”页。
2. 上传音频样本，或点击录音。
3. 勾选授权确认。
4. 保存为复刻音色。
5. 回到“生成”页选择该复刻音色生成。

请只复刻你自己的声音，或已经取得授权的声音。
