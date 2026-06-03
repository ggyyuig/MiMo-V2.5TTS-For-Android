package com.jiushuangchi.mimotts;

import android.Manifest;
import android.content.ContentValues;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.File;
import java.io.FileOutputStream;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

public class MainActivity extends Activity {
    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_CHOOSER_CODE = 1001;
    private static final int PERMISSION_CODE = 2001;

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);
        webView.setBackgroundColor(Color.TRANSPARENT);

        if (Build.VERSION.SDK_INT >= 23) {
            java.util.ArrayList<String> permissions = new java.util.ArrayList<>();
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.RECORD_AUDIO);
            }
            if (Build.VERSION.SDK_INT < 29 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            if (!permissions.isEmpty()) {
                requestPermissions(permissions.toArray(new String[0]), PERMISSION_CODE);
            }
        }

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        webView.addJavascriptInterface(new ApiBridge(), "AndroidBridge");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> request.grant(request.getResources()));
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                MainActivity.this.filePathCallback = filePathCallback;
                Intent intent = fileChooserParams.createIntent();
                try {
                    startActivityForResult(intent, FILE_CHOOSER_CODE);
                } catch (Exception e) {
                    Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
                    fallback.addCategory(Intent.CATEGORY_OPENABLE);
                    fallback.setType("audio/*");
                    startActivityForResult(Intent.createChooser(fallback, "选择音频样本"), FILE_CHOOSER_CODE);
                }
                return true;
            }
        });
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_CODE && filePathCallback != null) {
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) {
                Uri uri = data.getData();
                if (uri != null) results = new Uri[]{uri};
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    public class ApiBridge {
        @JavascriptInterface
        public boolean isSystemDark() {
            int nightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            return nightMode == Configuration.UI_MODE_NIGHT_YES;
        }

        @JavascriptInterface
        public String saveBase64Audio(String fileName, String mimeType, String base64, String subFolder) {
            JSONObject result = new JSONObject();
            try {
                String safeName = sanitizeFileName(fileName == null || fileName.trim().isEmpty() ? "mimo-tts.wav" : fileName.trim());
                if (!safeName.toLowerCase().endsWith(".wav") && !safeName.toLowerCase().endsWith(".mp3")) {
                    safeName += (mimeType != null && mimeType.contains("mpeg")) ? ".mp3" : ".wav";
                }
                String safeFolder = sanitizeFolderName(subFolder == null || subFolder.trim().isEmpty() ? "MiMoTTS" : subFolder.trim());
                byte[] data = Base64.decode(base64, Base64.DEFAULT);
                String displayPath;

                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, safeName);
                    values.put(MediaStore.Downloads.MIME_TYPE, mimeType == null || mimeType.isEmpty() ? "audio/wav" : mimeType);
                    values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + safeFolder);
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) throw new Exception("无法创建下载文件");
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                        if (os == null) throw new Exception("无法打开输出流");
                        os.write(data);
                    }
                    displayPath = "Download/" + safeFolder + "/" + safeName;
                } else {
                    File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), safeFolder);
                    if (!dir.exists() && !dir.mkdirs()) throw new Exception("无法创建下载目录：" + dir.getAbsolutePath());
                    File out = new File(dir, safeName);
                    try (FileOutputStream fos = new FileOutputStream(out)) { fos.write(data); }
                    displayPath = out.getAbsolutePath();
                }

                result.put("ok", true);
                result.put("path", displayPath);
            } catch (Exception e) {
                try {
                    result.put("ok", false);
                    result.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
                } catch (Exception ignored) {}
            }
            return result.toString();
        }

        @JavascriptInterface
        public void postJson(String requestId, String urlText, String headersJson, String bodyJson) {
            new Thread(() -> {
                int status = 0;
                String contentType = "";
                String base64Body = "";
                String error = "";
                try {
                    URL url = new URL(urlText);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setConnectTimeout(90000);
                    conn.setReadTimeout(90000);
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Accept", "application/json, audio/mpeg, audio/wav, */*");
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    JSONObject headers = new JSONObject(headersJson == null || headersJson.isEmpty() ? "{}" : headersJson);
                    Iterator<String> keys = headers.keys();
                    while (keys.hasNext()) {
                        String k = keys.next();
                        conn.setRequestProperty(k, headers.getString(k));
                    }
                    byte[] bytes = bodyJson.getBytes(StandardCharsets.UTF_8);
                    conn.setFixedLengthStreamingMode(bytes.length);
                    try (OutputStream os = conn.getOutputStream()) { os.write(bytes); }
                    status = conn.getResponseCode();
                    contentType = conn.getContentType() == null ? "" : conn.getContentType();
                    InputStream is = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
                    if (is != null) base64Body = Base64.encodeToString(readAll(is), Base64.NO_WRAP);
                    conn.disconnect();
                } catch (Exception e) {
                    error = e.getClass().getSimpleName() + ": " + e.getMessage();
                }
                final int fStatus = status;
                final String fContentType = contentType;
                final String fBase64Body = base64Body;
                final String fError = error;
                runOnUiThread(() -> {
                    try {
                        JSONObject obj = new JSONObject();
                        obj.put("id", requestId);
                        obj.put("status", fStatus);
                        obj.put("contentType", fContentType);
                        obj.put("base64", fBase64Body);
                        obj.put("error", fError);
                        webView.evaluateJavascript("window.NativeBridge && window.NativeBridge.onResponse(" + JSONObject.quote(obj.toString()) + ")", null);
                    } catch (Exception ignored) {}
                });
            }).start();
        }
    }

    private static String sanitizeFileName(String name) {
        return name.replaceAll("[\\/:*?\"<>|\n\r\t]", "_").trim();
    }

    private static String sanitizeFolderName(String name) {
        String cleaned = name.replaceAll("[\\:*?\"<>|\n\r\t]", "_").replaceAll("^/|/$", "").trim();
        if (cleaned.isEmpty()) return "MiMoTTS";
        if (cleaned.contains("..")) return "MiMoTTS";
        return cleaned;
    }

    private static byte[] readAll(InputStream input) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = input.read(buf)) != -1) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
