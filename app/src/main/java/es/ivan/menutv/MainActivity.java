package es.ivan.menutv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Base64;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {

    private WebView web;
    private final Map<String, String> iconCache = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hideSystemBars();

        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);
        s.setSupportZoom(false);
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "Android");
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
        web.requestFocus();
    }

    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();
        web.evaluateJavascript("window.refresh && window.refresh()", null);
        web.requestFocus();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Se pulsó Inicio estando ya dentro del launcher
        web.evaluateJavascript("window.onHome && window.onHome()", null);
    }

    @Override
    public void onBackPressed() {
        // Un launcher no se cierra con Atrás; la interfaz decide (cierra "Todas las apps")
        web.evaluateJavascript("window.onBack && window.onBack()", null);
    }

    /** Funciones que la interfaz HTML puede llamar. */
    private class Bridge {

        @JavascriptInterface
        public String getApps() {
            PackageManager pm = getPackageManager();
            Map<String, String> found = new LinkedHashMap<>();
            String[] cats = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
            for (String cat : cats) {
                Intent i = new Intent(Intent.ACTION_MAIN).addCategory(cat);
                List<ResolveInfo> list = pm.queryIntentActivities(i, 0);
                for (ResolveInfo ri : list) {
                    String pkg = ri.activityInfo.packageName;
                    if (pkg.equals(getPackageName()) || found.containsKey(pkg)) continue;
                    found.put(pkg, String.valueOf(ri.loadLabel(pm)));
                }
            }
            JSONArray arr = new JSONArray();
            try {
                for (Map.Entry<String, String> e : found.entrySet()) {
                    JSONObject o = new JSONObject();
                    o.put("pkg", e.getKey());
                    o.put("label", e.getValue());
                    arr.put(o);
                }
            } catch (Exception ignored) { }
            return arr.toString();
        }

        /** Devuelve {"u":"data:image/png;base64,...","banner":true|false} con el icono REAL de la app. */
        @JavascriptInterface
        public String getIcon(String pkg) {
            String cached = iconCache.get(pkg);
            if (cached != null) return cached;
            try {
                PackageManager pm = getPackageManager();
                Drawable d = null;
                boolean banner = false;
                Intent li = pm.getLeanbackLaunchIntentForPackage(pkg);
                if (li != null) {
                    d = pm.getActivityBanner(li);
                }
                if (d == null) {
                    d = pm.getApplicationBanner(pkg);
                }
                if (d != null) {
                    banner = true;
                } else {
                    d = pm.getApplicationIcon(pkg);
                }
                int w = banner ? 320 : 192;
                int h = banner ? 180 : 192;
                Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(bm);
                d.setBounds(0, 0, w, h);
                d.draw(c);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                bm.compress(Bitmap.CompressFormat.PNG, 100, out);
                bm.recycle();
                JSONObject o = new JSONObject();
                o.put("u", "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP));
                o.put("banner", banner);
                String res = o.toString();
                iconCache.put(pkg, res);
                return res;
            } catch (Throwable t) {
                return "";
            }
        }

        @JavascriptInterface
        public boolean launch(final String pkg) {
            PackageManager pm = getPackageManager();
            Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (i == null) i = pm.getLaunchIntentForPackage(pkg);
            if (i == null) return false;
            final Intent intent = i;
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    try { startActivity(intent); } catch (Throwable ignored) { }
                }
            });
            return true;
        }

        @JavascriptInterface
        public String getInputs() {
            JSONArray arr = new JSONArray();
            try {
                TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
                if (tim != null) {
                    for (TvInputInfo info : tim.getTvInputList()) {
                        if (!info.isPassthroughInput()) continue;
                        CharSequence label = info.loadCustomLabel(MainActivity.this);
                        if (label == null || label.length() == 0) label = info.loadLabel(MainActivity.this);
                        JSONObject o = new JSONObject();
                        o.put("id", info.getId());
                        o.put("label", String.valueOf(label));
                        arr.put(o);
                    }
                }
            } catch (Throwable ignored) { }
            return arr.toString();
        }

        @JavascriptInterface
        public boolean launchInput(final String inputId) {
            try {
                final Uri uri = TvContract.buildChannelUriForPassthroughInput(inputId);
                final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        try { startActivity(intent); } catch (Throwable ignored) { }
                    }
                });
                return true;
            } catch (Throwable t) {
                return false;
            }
        }

        @JavascriptInterface
        public String getIp() {
            try {
                java.util.Enumeration<java.net.NetworkInterface> nis = java.net.NetworkInterface.getNetworkInterfaces();
                while (nis != null && nis.hasMoreElements()) {
                    java.net.NetworkInterface ni = nis.nextElement();
                    if (!ni.isUp() || ni.isLoopback()) continue;
                    java.util.Enumeration<java.net.InetAddress> addrs = ni.getInetAddresses();
                    while (addrs.hasMoreElements()) {
                        java.net.InetAddress a = addrs.nextElement();
                        if (!a.isLoopbackAddress() && a instanceof java.net.Inet4Address) return a.getHostAddress();
                    }
                }
            } catch (Throwable ignored) { }
            return "";
        }

        @JavascriptInterface
        public void openSettings() {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    try {
                        Intent i = new Intent(Settings.ACTION_SETTINGS);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                    } catch (Throwable ignored) { }
                }
            });
        }
    }
}
