package com.nabbinator.destroy;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.google.android.gms.ads.MobileAds;
import android.webkit.JavascriptInterface;
import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

/**
 * Destroy: battili tutti! — contenitore Android del gioco.
 * Carica la versione online (sempre aggiornata, con account, PvP e classifiche);
 * senza connessione usa la copia inclusa nell'app.
 * La WebView è registrata presso Google Mobile Ads, così gli annunci AdMob del gioco funzionano.
 */
public class MainActivity extends Activity {
    private static final String ONLINE_URL = "https://airbnbcatania-ux.github.io/Destroy/?app=android";
    private static final String OFFLINE_URL = "file:///android_asset/index.html?app=android";
    private WebView web;
    private boolean usedOffline = false;

    // ---------- Acquisti in-app (Google Play Billing) ----------
    // ID dei prodotti: devono essere identici a quelli creati in Play Console -> Monetizza -> Prodotti in-app
    private static final String[] PRODUCTS = {"nuc_10", "nuc_60", "nuc_150"};
    private BillingClient billing;
    private final Map<String, ProductDetails> details = new HashMap<>();

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        setContentView(web);
        goFullscreen();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false); // musica e intro partono subito
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setAllowFileAccess(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(s.getUserAgentString() + " DestroyApp/Android");

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame() && !usedOffline) { usedOffline = true; view.loadUrl(OFFLINE_URL); }
            }
        });

        // Annunci: inizializza AdMob e registra la WebView (API WebView per gli annunci)
        MobileAds.initialize(this, status -> {});
        MobileAds.registerWebView(web);

        // Ponte JavaScript per gli acquisti: il gioco chiama DestroyBilling.buy("nuc_10")
        web.addJavascriptInterface(new BillingBridge(), "DestroyBilling");
        setupBilling();

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else if (isOnline()) web.loadUrl(ONLINE_URL);
        else { usedOffline = true; web.loadUrl(OFFLINE_URL); }
    }

    private boolean isOnline() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception e) { return true; }
    }

    private void goFullscreen() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) { super.onWindowFocusChanged(hasFocus); if (hasFocus) goFullscreen(); }
    @Override protected void onPause() { super.onPause(); web.onPause(); web.evaluateJavascript("try{R&&!R.over&&(R.paused=true)}catch(e){}", null); }
    @Override protected void onResume() { super.onResume(); web.onResume(); }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); web.saveState(out); }
    @Override protected void onDestroy() { if (billing != null) billing.endConnection(); if (web != null) web.destroy(); super.onDestroy(); }

    private void setupBilling() {
        billing = BillingClient.newBuilder(this)
            .setListener((result, purchases) -> {
                if (result.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
                    for (Purchase p : purchases) handlePurchase(p);
                } else if (result.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
                    js("try{iapEvent('cancel')}catch(e){}");
                } else {
                    js("try{iapEvent('error'," + JSONObject.quote(result.getDebugMessage()) + ")}catch(e){}");
                }
            })
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build();
        billing.startConnection(new BillingClientStateListener() {
            @Override public void onBillingSetupFinished(BillingResult r) {
                if (r.getResponseCode() != BillingClient.BillingResponseCode.OK) return;
                queryProducts();
                restorePurchases();
            }
            @Override public void onBillingServiceDisconnected() { }
        });
    }

    private void queryProducts() {
        List<QueryProductDetailsParams.Product> list = new ArrayList<>();
        for (String id : PRODUCTS) list.add(QueryProductDetailsParams.Product.newBuilder().setProductId(id).setProductType(BillingClient.ProductType.INAPP).build());
        billing.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(list).build(), (r, res) -> {
            if (r.getResponseCode() != BillingClient.BillingResponseCode.OK || res == null) return;
            for (ProductDetails d : res.getProductDetailsList()) details.put(d.getProductId(), d);
            js("try{iapEvent('prices')}catch(e){}");
        });
    }

    // acquisti pagati ma non ancora consegnati (es. app chiusa durante il pagamento)
    private void restorePurchases() {
        billing.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(), (r, purchases) -> {
            if (r.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) for (Purchase p : purchases) handlePurchase(p);
        });
    }

    private void handlePurchase(Purchase p) {
        if (p.getPurchaseState() == Purchase.PurchaseState.PENDING) { js("try{iapEvent('pending')}catch(e){}"); return; }
        if (p.getPurchaseState() != Purchase.PurchaseState.PURCHASED) return;
        for (String id : p.getProducts()) {
            // il gioco aggiunge i nuclei, salva e poi chiama DestroyBilling.consume(token)
            js("try{iapGrant(" + JSONObject.quote(id) + "," + JSONObject.quote(p.getPurchaseToken()) + ")}catch(e){}");
        }
    }

    private void js(String code) { runOnUiThread(() -> { if (web != null) web.evaluateJavascript(code, null); }); }

    private class BillingBridge {
        @JavascriptInterface public boolean ready() { return billing != null && billing.isReady() && !details.isEmpty(); }
        @JavascriptInterface public String prices() {
            JSONObject o = new JSONObject();
            try { for (ProductDetails d : details.values()) { ProductDetails.OneTimePurchaseOfferDetails of = d.getOneTimePurchaseOfferDetails(); if (of != null) o.put(d.getProductId(), of.getFormattedPrice()); } } catch (Exception e) { }
            return o.toString();
        }
        @JavascriptInterface public void buy(String id) {
            runOnUiThread(() -> {
                ProductDetails d = details.get(id);
                if (d == null || billing == null) { js("try{iapEvent('error','prodotto non disponibile')}catch(e){}"); return; }
                List<BillingFlowParams.ProductDetailsParams> l = new ArrayList<>();
                l.add(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build());
                billing.launchBillingFlow(MainActivity.this, BillingFlowParams.newBuilder().setProductDetailsParamsList(l).build());
            });
        }
        @JavascriptInterface public void consume(String token) {
            if (billing == null) return;
            billing.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(token).build(), (r, t) -> { });
        }
    }

    // Tasto Indietro: chiude una finestra aperta nel gioco o torna alla Home; altrimenti manda l'app in background
    @Override
    public void onBackPressed() {
        web.evaluateJavascript(
            "(function(){try{var m=document.getElementById('ov');if(m&&getComputedStyle(m).display!=='none'&&typeof hideModal==='function'){hideModal();return 1;}" +
            "if(window.R&&!R.over){R.paused=true;return 1;}if(typeof curScreen!=='undefined'&&curScreen!=='sHome'&&typeof go==='function'){go('sHome');return 1;}}catch(e){}return 0;})()",
            v -> { if (!"1".equals(v)) moveTaskToBack(true); });
    }
}
