package com.yang.player.online;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Handles Google / YouTube Music WebView authentication and InnerTube authorization headers. */
public final class YouTubeAuth {
    private static final String PREF_NAME = "youtube_auth";
    private static final String KEY_COOKIE = "cookie";
    private static final String KEY_NAME = "account_name";
    private static String cachedCiphertext;
    private static String cachedCookie;

    public static boolean isLoggedIn(Context c) {
        return getCookie(c)!=null&&c.getSharedPreferences(PREF_NAME,0).getBoolean("server_verified",false);
    }
    public static boolean hasSavedSession(Context c){return getCookie(c)!=null;}
    public static synchronized String getCookie(Context c){
        String encrypted=c.getSharedPreferences(PREF_NAME,0).getString(KEY_COOKIE,null);
        if(encrypted==null){cachedCiphertext=null;cachedCookie=null;return null;}
        if(!encrypted.equals(cachedCiphertext)){cachedCookie=SessionSecrets.decrypt(encrypted);cachedCiphertext=encrypted;}
        return cachedCookie;
    }
    public static boolean verifySaved(Context c)throws Exception{
        String cookie=getCookie(c);if(cookie==null)return false;
        boolean valid=YouTubeMusicClient.sessionValid(c,cookie);
        c.getSharedPreferences(PREF_NAME,0).edit().putBoolean("server_verified",valid).apply();return valid;
    }
    static String cookieValue(String cookies,String name){
        if(cookies==null)return null;for(String part:cookies.split(";")){String item=part.trim();int equals=item.indexOf('=');if(equals>0&&name.equals(item.substring(0,equals)))return item.substring(equals+1);}return null;
    }
    public static String getAccountName(Context c) {
        return c.getSharedPreferences(PREF_NAME, 0).getString(KEY_NAME, "Cuenta de Google");
    }

    public static void setAccountName(Context c, String name) {
        c.getSharedPreferences(PREF_NAME, 0).edit().putString(KEY_NAME, name).apply();
    }

    public static String getSapisid(Context c){return sapisid(getCookie(c));}
    public static String sapisid(String cookies){String value=cookieValue(cookies,"SAPISID");return value!=null?value:cookieValue(cookies,"__Secure-3PAPISID");}
    public static String sapisidHash(String sapisid) {
        if (sapisid == null) return null;
        long time = System.currentTimeMillis() / 1000;
        String raw = time + " " + sapisid + " https://music.youtube.com";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b));
            return time + "_" + hex;
        } catch (Exception e) {
            return null;
        }
    }

    private static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static void startLogin(Activity a, Runnable onComplete) {
        Dialog dialog = new Dialog(a, android.R.style.Theme_DeviceDefault_NoActionBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0E0E13);
        root.setOnApplyWindowInsetsListener((view,insets)->{
            if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout());root.setPadding(bars.left,bars.top,bars.right,bars.bottom);}else{root.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());}
            return insets;
        });

        LinearLayout toolbar = new LinearLayout(a);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(a, 14), dp(a, 10), dp(a, 18), dp(a, 10));

        Button close = new Button(a);
        close.setText("✕");
        close.setTextColor(Color.WHITE);
        close.setBackgroundColor(Color.TRANSPARENT);
        toolbar.addView(close, new LinearLayout.LayoutParams(dp(a, 42), dp(a, 42)));
        close.setOnClickListener(v -> dialog.dismiss());

        TextView title = new TextView(a);
        title.setText("YouTube Music");
        title.setTextSize(18);
        title.setTextColor(Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setPadding(dp(a, 10), 0, 0, 0);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        Button doneBtn = new Button(a);
        doneBtn.setText("Comprobar");
        doneBtn.setAllCaps(false);
        doneBtn.setTextSize(13);
        doneBtn.setTextColor(Color.WHITE);
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(0xFFFF2D55);
        pill.setCornerRadius(dp(a, 18));
        doneBtn.setBackground(pill);
        doneBtn.setPadding(dp(a, 16), 0, dp(a, 16), 0);
        toolbar.addView(doneBtn, new LinearLayout.LayoutParams(-2, dp(a, 36)));
        root.addView(toolbar);

        ProgressBar progress = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(a, 3)));

        WebView webView = new WebView(a);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progress.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
                if (url != null && (url.contains("myaccount.google.com") || url.contains("accounts.google.com/CheckCookie"))) {
                    view.loadUrl("https://music.youtube.com/");
                }
            }
        });

        doneBtn.setOnClickListener(v -> {
            String candidate=getAllMergedCookies();
            if(sapisid(candidate)==null){Toast.makeText(a,"Termina de iniciar sesión dentro de YouTube Music y vuelve a comprobar.",Toast.LENGTH_LONG).show();return;}
            doneBtn.setEnabled(false);doneBtn.setText("Comprobando…");progress.setVisibility(View.VISIBLE);
            new Thread(()->{String error=null;boolean saved=false;
                try{if(!YouTubeMusicClient.sessionValid(a,candidate))error="YouTube todavía no reconoce esta sesión. Abre YouTube Music, elige tu cuenta y vuelve a comprobar.";
                    else{saved=a.getSharedPreferences(PREF_NAME,0).edit().putString(KEY_COOKIE,SessionSecrets.encrypt(candidate)).putBoolean("server_verified",true).putLong("login_time",System.currentTimeMillis()).commit();if(!saved)error="No se pudo guardar la sesión. Inténtalo de nuevo.";}}
                catch(Exception e){error=e.getMessage()==null?"No se pudo verificar la cuenta. Revisa la conexión.":e.getMessage();}
                final String message=error;final boolean complete=saved;
                a.runOnUiThread(()->{if(!dialog.isShowing()||a.isDestroyed())return;doneBtn.setEnabled(true);doneBtn.setText("Comprobar");progress.setVisibility(View.GONE);if(complete){dialog.dismiss();Toast.makeText(a,"Sesión de YouTube verificada",Toast.LENGTH_SHORT).show();if(onComplete!=null)onComplete.run();}else Toast.makeText(a,message,Toast.LENGTH_LONG).show();});
            },"YouTube-session-check").start();
        });

        root.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1));
        dialog.setContentView(root);
        dialog.setOnDismissListener(v->{webView.stopLoading();webView.destroy();});
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(-1, -1);
        }
        webView.loadUrl("https://music.youtube.com/");
    }

    public static String getAllMergedCookies() {
        CookieManager manager=CookieManager.getInstance();manager.flush();
        String cookie=manager.getCookie("https://music.youtube.com/");return cookie==null?"":cookie;
    }

    public static void logout(Context c, Runnable onComplete) {
        c.getSharedPreferences("youtube_profile",0).edit().clear().apply();
        c.getSharedPreferences(PREF_NAME, 0).edit().clear().apply();
        CookieManager.getInstance().removeAllCookies(v -> {
            if (onComplete != null) onComplete.run();
        });
    }
}

