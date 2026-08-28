package com.nadaye.beheshti;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.MotionEvent;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements SensorEventListener {
    private static final String APP_BASE_URL = "https://app-hs2thc.v2.appdeploy.ai/";
    private static final String TRUSTED_HOST = "app-hs2thc.v2.appdeploy.ai";
    private static final int WEB_BUILD = 51;
    private static final int FILE_REQUEST = 4041;
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private volatile float heading;
    private int ownerTapCount = 0;
    private long ownerLastTap = 0L;

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface", "ClickableViewAccessibility"})
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestRuntimePermissions();
        sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setCacheMode(isOnline() ? WebSettings.LOAD_NO_CACHE : WebSettings.LOAD_CACHE_ELSE_NETWORK);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) s.setSafeBrowsingEnabled(true);

        int previousBuild = getSharedPreferences("nadaye", MODE_PRIVATE).getInt("web_build", 0);
        if (previousBuild != WEB_BUILD) {
            webView.clearCache(true);
            getSharedPreferences("nadaye", MODE_PRIVATE).edit().putInt("web_build", WEB_BUILD).apply();
        }

        webView.addJavascriptInterface(new NativeBridge(), "NedayeNative");
        webView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP && webView.getHeight() > 0) {
                float yRatio = event.getY() / webView.getHeight();
                float xRatio = event.getX() / webView.getWidth();
                if (yRatio <= 0.22f && xRatio >= 0.20f && xRatio <= 0.80f) {
                    long now = System.currentTimeMillis();
                    ownerTapCount = now - ownerLastTap < 700L ? ownerTapCount + 1 : 1;
                    ownerLastTap = now;
                    if (ownerTapCount >= 7) {
                        ownerTapCount = 0;
                        ownerLastTap = 0L;
                        webView.loadUrl(freshUrl(true));
                    }
                } else {
                    ownerTapCount = 0;
                    ownerLastTap = 0L;
                }
            }
            return false;
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (TRUSTED_HOST.equalsIgnoreCase(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
                return true;
            }
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.getSettings().setCacheMode(isOnline() ? WebSettings.LOAD_NO_CACHE : WebSettings.LOAD_CACHE_ELSE_NETWORK);
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                boolean trusted = origin != null && origin.startsWith("https://" + TRUSTED_HOST);
                boolean allowed = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                callback.invoke(origin, trusted && allowed, false);
            }
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    Intent i = params.createIntent();
                    startActivityForResult(i, FILE_REQUEST);
                    return true;
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
            }
        });
        setContentView(webView);
        webView.loadUrl(freshUrl(false));
    }

    private String freshUrl(boolean owner) {
        String q = "?native=1&build=" + WEB_BUILD + "&t=" + System.currentTimeMillis();
        if (owner) q += "&owner=1";
        return APP_BASE_URL + q;
    }

    private void requestRuntimePermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        List<String> wanted = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) wanted.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) wanted.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!wanted.isEmpty()) requestPermissions(wanted.toArray(new String[0]), 4042);
    }

    private boolean isOnline() {
        try {
            ConnectivityManager cm = (ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
            if (Build.VERSION.SDK_INT >= 23) {
                android.net.Network n = cm.getActiveNetwork();
                if (n == null) return false;
                NetworkCapabilities c = cm.getNetworkCapabilities(n);
                return c != null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            }
            android.net.NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (Exception e) { return true; }
    }

    @Override protected void onResume() {
        super.onResume();
        if (rotationSensor != null) sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI);
    }

    @Override protected void onPause() {
        sensorManager.unregisterListener(this);
        super.onPause();
    }

    @Override public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) return;
        float[] r = new float[9];
        float[] o = new float[3];
        SensorManager.getRotationMatrixFromVector(r, event.values);
        SensorManager.getOrientation(r, o);
        float d = (float)Math.toDegrees(o[0]);
        if (d < 0) d += 360f;
        heading = d;
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_REQUEST || fileCallback == null) return;
        Uri[] result = null;
        if (resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) result = new Uri[]{data.getData()};
            else if (data != null && data.getClipData() != null) {
                int n = data.getClipData().getItemCount();
                result = new Uri[n];
                for (int i=0;i<n;i++) result[i] = data.getClipData().getItemAt(i).getUri();
            }
        }
        fileCallback.onReceiveValue(result);
        fileCallback = null;
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }

    public class NativeBridge {
        @JavascriptInterface public void notify(String title, String body) {
            AlarmReceiver.showNotification(MainActivity.this, title, body, (title + body).hashCode(), true);
        }
        @JavascriptInterface public float getHeading() { return heading; }
        @JavascriptInterface public int getBuildVersion() { return WEB_BUILD; }
        @JavascriptInterface public void refreshCentral() { runOnUiThread(() -> webView.loadUrl(freshUrl(false))); }
        @JavascriptInterface public void openOwner() { runOnUiThread(() -> webView.loadUrl(freshUrl(true))); }
        @JavascriptInterface public void setAdhanUrl(String url) { getSharedPreferences("nadaye", MODE_PRIVATE).edit().putString("adhan_url", url == null ? "" : url).apply(); }
        @JavascriptInterface public void setFajrAdhanUrl(String url) { getSharedPreferences("nadaye", MODE_PRIVATE).edit().putString("fajr_adhan_url", url == null ? "" : url).apply(); }
        @JavascriptInterface public void setVibration(boolean enabled) { getSharedPreferences("nadaye", MODE_PRIVATE).edit().putBoolean("vibration", enabled).apply(); }
        @JavascriptInterface public void cancelPrayerAlarms() { NativeScheduler.cancelPrayerAlarms(MainActivity.this); }
        @JavascriptInterface public void schedulePrayer(String id, String label, double atMillis) { NativeScheduler.schedule(MainActivity.this, id, label, (long)atMillis, "prayer", true); }
        @JavascriptInterface public void scheduleAlert(String id, String type, String title, double atMillis, boolean vibrate) { NativeScheduler.schedule(MainActivity.this, id, title, (long)atMillis, type == null ? "event" : type, vibrate); }
        @JavascriptInterface public void requestExactAlarmPermission() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                AlarmManager am = (AlarmManager)getSystemService(ALARM_SERVICE);
                if (!am.canScheduleExactAlarms()) runOnUiThread(() -> {
                    try { startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()))); } catch (Exception ignored) {}
                });
            }
        }
        @JavascriptInterface public void downloadAdhan(String source) {
            if (source == null || !source.startsWith("https://")) return;
            new Thread(() -> downloadAdhanFile(source)).start();
        }
    }

    private void downloadAdhanFile(String source) {
        HttpURLConnection c = null;
        try {
            URL url = new URL(source);
            c = (HttpURLConnection)url.openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setInstanceFollowRedirects(true);
            c.connect();
            if (c.getResponseCode() < 200 || c.getResponseCode() >= 300) return;
            String key = Integer.toHexString(source.hashCode());
            File out = new File(getFilesDir(), "adhan_" + key + ".audio");
            try (InputStream in = c.getInputStream(); FileOutputStream fos = new FileOutputStream(out)) {
                byte[] buf = new byte[8192]; int n; long total = 0;
                while ((n = in.read(buf)) > 0) { total += n; if (total > 25L * 1024L * 1024L) throw new Exception("too_large"); fos.write(buf,0,n); }
            }
            getSharedPreferences("nadaye", MODE_PRIVATE).edit().putString("offline_adhan_" + key, out.getAbsolutePath()).apply();
        } catch (Exception ignored) {
        } finally { if (c != null) c.disconnect(); }
    }
}
