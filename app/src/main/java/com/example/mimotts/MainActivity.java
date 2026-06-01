package com.example.mimotts;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private WebView webView;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ValueCallback<Uri[]> filePathCallback;

    private AudioRecord audioRecord;
    private volatile boolean isRecording = false;
    private ByteArrayOutputStream pcmBuffer = new ByteArrayOutputStream();
    private final int sampleRate = 16000;

    private static final int FILE_CHOOSER_REQUEST_CODE = 1001;
    private static final int RECORD_AUDIO_REQUEST_CODE = 2001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebView.setWebContentsDebuggingEnabled(true);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }
                MainActivity.this.filePathCallback = callback;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("audio/*");
                startActivityForResult(Intent.createChooser(intent, "选择音频样本"), FILE_CHOOSER_REQUEST_CODE);
                return true;
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && request != null) {
                    request.grant(request.getResources());
                }
            }
        });

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }

        webView.addJavascriptInterface(new MiMoBridge(), "AndroidBridge");
        webView.loadUrl("file:///android_asset/index.html");
        ensureRecordPermission();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isRecording = false;
        try {
            if (audioRecord != null) audioRecord.release();
        } catch (Exception ignored) {}
        executor.shutdownNow();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                result = new Uri[]{data.getData()};
            }
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(result);
                filePathCallback = null;
            }
        }
    }

    private boolean ensureRecordPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, RECORD_AUDIO_REQUEST_CODE);
            return false;
        }
        return true;
    }

    private String jsString(String value) {
        return JSONObject.quote(value == null ? "" : value);
    }

    private void runJs(String js) {
        runOnUiThread(() -> webView.evaluateJavascript(js, null));
    }

    private void callbackSuccess(String base64Audio, String mimeType, String filename) {
        runJs("window.MiMoNative && window.MiMoNative.onSynthesisSuccess(" +
                jsString(base64Audio) + "," +
                jsString(mimeType) + "," +
                jsString(filename) + ");");
    }

    private void callbackError(String message) {
        runJs("window.MiMoNative && window.MiMoNative.onSynthesisError(" + jsString(message) + ");");
    }

    private void callbackRecordReady(String dataUrl) {
        runJs("window.MiMoNative && window.MiMoNative.onNativeRecordingReady(" + jsString(dataUrl) + ");");
    }

    public class MiMoBridge {
        @JavascriptInterface
        public void synthesize(String payloadJson) {
            executor.execute(() -> {
                try {
                    JSONObject payload = new JSONObject(payloadJson);
                    String token = payload.optString("token", "").trim();
                    if (token.isEmpty()) throw new IOException("请先到设置里填写 MiMo API Token。");

                    String baseUrl = trimEnd(payload.optString("baseUrl", "https://api.xiaomimimo.com/v1").trim(), "/");
                    String endpoint = payload.optString("endpoint", "/chat/completions").trim();
                    String url = baseUrl + (endpoint.startsWith("/") ? endpoint : "/" + endpoint);
                    String authType = payload.optString("authType", "api-key");

                    String text = payload.optString("text", "").trim();
                    if (text.isEmpty()) throw new IOException("请输入要合成的文本。");
                    if (text.length() > 4096) throw new IOException("文本不能超过 4096 个字符。");

                    String voice = payload.optString("voice", "茉莉");
                    boolean isCloneVoice = voice.startsWith("data:audio/");
                    String model = isCloneVoice ? "mimo-v2.5-tts-voiceclone" : "mimo-v2.5-tts";
                    String format = payload.optString("format", "mp3");
                    double speed = payload.optDouble("speed", 1.0);
                    int volume = payload.optInt("volume", 80);
                    String stylePrompt = payload.optString("stylePrompt", "").trim();

                    StringBuilder instruction = new StringBuilder();
                    instruction.append("你是一个高质量语音合成引擎。请将后续文本合成为自然清晰的语音。");
                    instruction.append("语速参考：").append(speed).append("x。");
                    instruction.append("音量参考：").append(volume).append("/100。");
                    if (!stylePrompt.isEmpty()) instruction.append("风格要求：").append(stylePrompt).append("。");

                    JSONObject requestJson = new JSONObject();
                    requestJson.put("model", model);
                    JSONArray messages = new JSONArray();
                    messages.put(new JSONObject().put("role", "user").put("content", instruction.toString()));
                    messages.put(new JSONObject().put("role", "assistant").put("content", text));
                    requestJson.put("messages", messages);
                    requestJson.put("audio", new JSONObject().put("format", format).put("voice", voice));

                    byte[] responseBytes = postJson(url, requestJson.toString(), token, authType);
                    String mime = "wav".equalsIgnoreCase(format) ? "audio/wav" : "audio/mpeg";
                    String jsonText = new String(responseBytes, StandardCharsets.UTF_8);

                    String audioData;
                    try {
                        JSONObject json = new JSONObject(jsonText);
                        audioData = json.getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .getJSONObject("audio")
                                .getString("data");
                    } catch (Exception e) {
                        throw new IOException("MiMo 返回成功，但未找到 choices[0].message.audio.data。原始返回：" + take(jsonText, 800));
                    }
                    callbackSuccess(audioData, mime, createFileName(format));
                } catch (Exception e) {
                    callbackError(e.getMessage() == null ? "生成失败。" : e.getMessage());
                }
            });
        }

        @JavascriptInterface
        public void saveAudio(String base64Audio, String mimeType, String filename) {
            executor.execute(() -> {
                try {
                    String clean = base64Audio.contains("base64,") ? base64Audio.substring(base64Audio.indexOf("base64,") + 7) : base64Audio;
                    byte[] bytes = Base64.decode(clean, Base64.DEFAULT);
                    String savedName = (filename != null && !filename.trim().isEmpty())
                            ? filename.trim()
                            : createFileName(mimeType != null && mimeType.contains("wav") ? "wav" : "mp3");

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ContentValues values = new ContentValues();
                        values.put(MediaStore.Downloads.DISPLAY_NAME, savedName);
                        values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
                        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MiMoTTS");
                        Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                        if (uri == null) throw new IOException("无法创建下载文件。");
                        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                            if (out == null) throw new IOException("无法写入下载文件。");
                            out.write(bytes);
                        }
                    } else {
                        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MiMoTTS");
                        if (!dir.exists()) dir.mkdirs();
                        try (FileOutputStream out = new FileOutputStream(new File(dir, savedName))) {
                            out.write(bytes);
                        }
                    }
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "已保存到下载目录/MiMoTTS", Toast.LENGTH_LONG).show());
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, e.getMessage() == null ? "保存失败" : e.getMessage(), Toast.LENGTH_LONG).show());
                }
            });
        }

        @JavascriptInterface
        public void startRecording() {
            if (!ensureRecordPermission()) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "请允许录音权限后再试。", Toast.LENGTH_SHORT).show());
                return;
            }
            if (isRecording) return;

            executor.execute(() -> {
                try {
                    int minBufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                    int bufferSize = Math.max(minBufferSize, sampleRate * 2);
                    pcmBuffer = new ByteArrayOutputStream();

                    audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
                    audioRecord.startRecording();
                    isRecording = true;
                    runJs("window.MiMoNative && window.MiMoNative.onNativeRecordingStarted();");

                    byte[] buffer = new byte[bufferSize];
                    while (isRecording) {
                        int read = audioRecord.read(buffer, 0, buffer.length);
                        if (read > 0) pcmBuffer.write(buffer, 0, read);
                    }

                    try { audioRecord.stop(); } catch (Exception ignored) {}
                    audioRecord.release();
                    audioRecord = null;

                    byte[] wavBytes = pcmToWav(pcmBuffer.toByteArray(), sampleRate, 1, 16);
                    String base64 = Base64.encodeToString(wavBytes, Base64.NO_WRAP);
                    callbackRecordReady("data:audio/wav;base64," + base64);
                } catch (Exception e) {
                    isRecording = false;
                    callbackError("录音失败：" + (e.getMessage() == null ? "未知错误" : e.getMessage()));
                }
            });
        }

        @JavascriptInterface
        public void stopRecording() {
            isRecording = false;
        }
    }

    private byte[] postJson(String urlText, String json, String token, String authType) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlText).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(90000);
        conn.setReadTimeout(180000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Accept", "application/json, audio/mpeg, audio/wav, */*");
        if ("bearer".equalsIgnoreCase(authType)) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
        } else {
            conn.setRequestProperty("api-key", token);
        }
        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
        int code = conn.getResponseCode();
        String contentType = conn.getContentType() == null ? "" : conn.getContentType().toLowerCase(Locale.ROOT);
        InputStream raw = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        byte[] bytes = readAll(raw);
        if (code >= 400) {
            String err = new String(bytes, StandardCharsets.UTF_8);
            throw new IOException("MiMo API 请求失败：HTTP " + code + "，详情：" + take(err, 800));
        }
        if (contentType.startsWith("audio/")) {
            String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
            JSONObject fake = new JSONObject();
            try {
                JSONObject audio = new JSONObject().put("data", base64);
                JSONObject msg = new JSONObject().put("audio", audio);
                JSONObject choice = new JSONObject().put("message", msg);
                fake.put("choices", new JSONArray().put(choice));
            } catch (Exception ignored) {}
            return fake.toString().getBytes(StandardCharsets.UTF_8);
        }
        return bytes;
    }

    private byte[] readAll(InputStream input) throws IOException {
        if (input == null) return new byte[0];
        try (BufferedInputStream in = new BufferedInputStream(input); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) out.write(buffer, 0, len);
            return out.toByteArray();
        }
    }

    private String createFileName(String format) {
        String ext = "wav".equalsIgnoreCase(format) ? "wav" : "mp3";
        return "mimo_tts_" + System.currentTimeMillis() + "." + ext;
    }

    private byte[] pcmToWav(byte[] pcm, int sampleRate, int channels, int bitsPerSample) throws IOException {
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        int totalDataLen = pcm.length + 36;
        int totalAudioLen = pcm.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeAscii(out, "RIFF");
        writeIntLE(out, totalDataLen);
        writeAscii(out, "WAVE");
        writeAscii(out, "fmt ");
        writeIntLE(out, 16);
        writeShortLE(out, 1);
        writeShortLE(out, channels);
        writeIntLE(out, sampleRate);
        writeIntLE(out, byteRate);
        writeShortLE(out, channels * bitsPerSample / 8);
        writeShortLE(out, bitsPerSample);
        writeAscii(out, "data");
        writeIntLE(out, totalAudioLen);
        out.write(pcm);
        return out.toByteArray();
    }

    private void writeAscii(ByteArrayOutputStream out, String value) throws IOException {
        out.write(value.getBytes(StandardCharsets.US_ASCII));
    }

    private void writeIntLE(ByteArrayOutputStream out, int value) {
        out.write(value & 0xff);
        out.write((value >> 8) & 0xff);
        out.write((value >> 16) & 0xff);
        out.write((value >> 24) & 0xff);
    }

    private void writeShortLE(ByteArrayOutputStream out, int value) {
        out.write(value & 0xff);
        out.write((value >> 8) & 0xff);
    }

    private String trimEnd(String s, String suffix) {
        while (s.endsWith(suffix)) s = s.substring(0, s.length() - suffix.length());
        return s;
    }

    private String take(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max);
    }
}
