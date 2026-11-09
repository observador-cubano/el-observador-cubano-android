package cu.observadorcubano.app;

import android.annotation.SuppressLint;
import android.Manifest;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pantalla principal: interfaz nativa + WebView que muestra el sitio original
 * de Blogger. No se expone ningún puente JavaScript-nativo al contenido remoto.
 */
public class MainActivity extends AppCompatActivity {

    private static final String HOME_URL = "https://observadorcubano.blogspot.com/";
    private static final String FEED_URL =
        HOME_URL + "feeds/posts/default?alt=json&max-results=150";
    public static final String EXTRA_ARTICLE_URL = "article_url";
    private static final long EXIT_WINDOW_MS = 2000L;
    private static final long POPUP_TIMEOUT_MS = 8000L;
    private static final long SPLASH_FALLBACK_MS = 12000L;
    private static final long UPDATE_FIRST_CHECK_DELAY_MS = 1800L;
    private static final int REQUEST_NOTIFICATIONS = 4301;
    private static final byte[] TRANSPARENT_PNG = Base64.decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
        Base64.DEFAULT
    );

    private static final String SECTIONS_PREFS = "eoc_sections_cache";
    private static final String KEY_SECTIONS = "sections";
    private static final String KEY_SECTIONS_AT = "sections_at";
    private static final long SECTIONS_TTL_MS = 6L * 60L * 60L * 1000L;
    private static final int MAX_SECTIONS = 8;

    /**
     * Secciones reales del sitio, usadas solo mientras no hay datos en caché
     * o cuando el dispositivo está sin conexión.
     */
    private static final String[][] DEFAULT_SECTIONS = {
        {"CUBA", "8"},
        {"OPOSITORES CUBANOS", "3"},
        {"ESTADOS UNIDOS", "3"},
        {"HOMOFÓBIA EN CUBA", "1"},
        {"VIDAS", "1"}
    };

    private static final String LINK_TARGET_FIX_JS =
        "(function(){try{var l=document.querySelectorAll('a[target]');"
            + "for(var i=0;i<l.length;i++){var t=(l[i].getAttribute('target')||'').toLowerCase();"
            + "if(t==='_blank'||t==='_new'){l[i].removeAttribute('target');}}}catch(e){}})()";

    /**
     * Ajustes de lectura mínimos y no editoriales: evitan imágenes que se
     * salgan del ancho y mejoran el corte de palabras largas. No oculta ni
     * reordena contenido de Blogger.
     */
    private static final String READER_CSS_JS =
        "(function(){try{if(document.getElementById('eoc-reader-style'))return;"
            + "var s=document.createElement('style');s.id='eoc-reader-style';"
            + "s.textContent='html{-webkit-text-size-adjust:100%}"
            + "body{overflow-x:hidden}"
            + "img{max-width:100%;height:auto}"
            + "article,.post-body,.post{overflow-wrap:break-word}';"
            + "document.head.appendChild(s);}catch(e){}})()";

    private static final String SHARE_TITLE_JS =
        "(function(){try{var s=['article#eoc-salud h1','article h1','article h2',"
            + "'.post-body h1','[itemprop=\"headline\"]','.post-title'];"
            + "for(var i=0;i<s.length;i++){var e=document.querySelector(s[i]);"
            + "if(e){var t=(e.innerText||e.textContent||'').trim();if(t){return t;}}}"
            + "return document.title||'';}catch(e){return '';}})()";

    private View root;
    private View appBar;
    private View appBarRow;
    private ImageView logoWordmark;
    private View contentContainer;
    private View bottomBar;
    private View bottomBarRow;
    private ImageButton navBack;
    private ImageButton shareButton;
    private ProgressBar progressBar;
    private LinearLayout offlineBanner;
    private Button bannerRetry;
    private View updateBanner;
    private TextView updateBannerText;
    private SwipeRefreshLayout swipeRefresh;
    private WebView webView;
    private LinearLayout stateOverlay;
    private ImageView stateIcon;
    private ProgressBar stateProgress;
    private TextView stateTitle;
    private TextView stateMessage;
    private Button stateRetry;
    private Button stateHome;
    private FrameLayout splashOverlay;
    private ProgressBar splashProgress;
    private View navHome;
    private View navSections;
    private View navSearch;
    private View navInfo;
    private ImageButton readerButton;
    private ImageButton savedButton;
    private LinearLayout dataSaverBanner;
    private Button dataSaverSettings;

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private ExecutorService ioExecutor;
    private UpdateManager updateManager;
    private ReleaseInfo pendingUpdateRelease;
    private boolean notificationPermissionRequested = false;

    private Dialog sectionsDialog;
    private Dialog searchDialog;
    private Dialog savedDialog;
    private Dialog savingDialog;
    private boolean sectionsFetchInFlight = false;

    private SavedArticlesStore savedStore;
    private boolean pageIsArticle = false;
    private boolean readerModeActive = false;
    private boolean savingArticle = false;
    private volatile boolean dataSaverBlocking = false;
    private boolean cellularNetwork = false;
    private int pageInfoRequest = 0;
    private String currentArticleUrl = "";
    private String currentArticleTitle = "";

    private boolean isOnline = true;
    private boolean contentVisible = false;
    private boolean pageHadError = false;
    private boolean stateIsError = false;
    private boolean rendererRecreated = false;
    private boolean splashHidden = false;
    private boolean firstFrameDrawn = false;
    private boolean stopSplashRequested = false;
    private String currentTitle = "";
    private long lastBackPress = 0L;

    private View fullscreenView;
    private FrameLayout fullscreenContainer;
    private WebChromeClient.CustomViewCallback customViewCallback;

    private final Runnable splashFallback = this::stopSplash;
    private final Runnable periodicUpdateCheck = () -> runUpdateCheck(false);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        ioExecutor = Executors.newSingleThreadExecutor();
        savedStore = new SavedArticlesStore(this);

        bindViews();
        observeSplashFirstFrame();
        configureSystemBars();
        configureInsets();
        applyResponsiveLayout();
        configureWebView();
        configureSwipeRefresh();
        configureActions();
        configureStateOverlay();
        configureBackNavigation();
        configureNetworkMonitoring();

        updateManager = new UpdateManager(this);
        UpdateNotifier.ensureChannel(this);
        root.postDelayed(() -> runUpdateCheck(false), UPDATE_FIRST_CHECK_DELAY_MS);

        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        isOnline = checkOnline();
        cellularNetwork = checkCellular();
        updateOfflineUi();
        updateDataSaverUi();
        updateArticleActions();

        root.postDelayed(splashFallback, SPLASH_FALLBACK_MS);

        String deepLink = getIntent() != null ? getIntent().getStringExtra(EXTRA_ARTICLE_URL) : null;
        if (deepLink != null && UrlPolicy.classify(deepLink) == UrlPolicy.Action.INTERNAL) {
            String normalized = UrlPolicy.normalize(deepLink);
            loadUrl(normalized != null ? normalized : HOME_URL, true);
        } else {
            loadHome(true);
        }
    }

    private void bindViews() {
        root = findViewById(R.id.root);
        appBar = findViewById(R.id.appBar);
        appBarRow = findViewById(R.id.appBarRow);
        logoWordmark = findViewById(R.id.logoWordmark);
        contentContainer = findViewById(R.id.contentContainer);
        bottomBar = findViewById(R.id.bottomBar);
        bottomBarRow = findViewById(R.id.bottomBarRow);
        navBack = findViewById(R.id.btnBack);
        shareButton = findViewById(R.id.btnShareTop);
        progressBar = findViewById(R.id.progressBar);
        offlineBanner = findViewById(R.id.offlineBanner);
        bannerRetry = findViewById(R.id.bannerRetry);
        updateBanner = findViewById(R.id.updateBanner);
        updateBannerText = findViewById(R.id.updateBannerText);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        webView = findViewById(R.id.webView);
        stateOverlay = findViewById(R.id.stateOverlay);
        stateIcon = findViewById(R.id.stateIcon);
        stateProgress = findViewById(R.id.stateProgress);
        stateTitle = findViewById(R.id.stateTitle);
        stateMessage = findViewById(R.id.stateMessage);
        stateRetry = findViewById(R.id.stateRetry);
        stateHome = findViewById(R.id.stateHome);
        splashOverlay = findViewById(R.id.splashOverlay);
        splashProgress = findViewById(R.id.splashProgress);
        navHome = findViewById(R.id.navHome);
        navSections = findViewById(R.id.navSections);
        navSearch = findViewById(R.id.navSearch);
        navInfo = findViewById(R.id.navInfo);
        readerButton = findViewById(R.id.btnReader);
        savedButton = findViewById(R.id.btnSaved);
        dataSaverBanner = findViewById(R.id.dataSaverBanner);
        dataSaverSettings = findViewById(R.id.dataSaverSettings);
    }

    private void configureSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);

        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
            window,
            window.getDecorView()
        );

        if (splashHidden) {
            applyContentSystemBars(window, controller);
        } else {
            // Mientras se muestra la splash, la barra de navegación continúa el
            // color del fondo para que la imagen se vea a pantalla completa.
            if (Build.VERSION.SDK_INT < 35) {
                window.setNavigationBarColor(
                    ContextCompat.getColor(this, R.color.splash_system_background)
                );
            }
            if (controller != null) {
                controller.setAppearanceLightStatusBars(false);
                controller.setAppearanceLightNavigationBars(false);
            }
        }
    }

    private void applyContentSystemBars(
        Window window,
        WindowInsetsControllerCompat controller
    ) {
        boolean dark = AppPrefs.isDarkMode(this);
        if (Build.VERSION.SDK_INT < 35) {
            window.setNavigationBarColor(ContextCompat.getColor(this, R.color.surface));
        }
        if (controller != null) {
            controller.setAppearanceLightStatusBars(false);
            controller.setAppearanceLightNavigationBars(!dark);
        }
    }

    private void configureInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            boolean keyboardVisible = ime.bottom > 0;

            appBar.setPadding(bars.left, bars.top, bars.right, 0);
            bottomBar.setPadding(bars.left, 0, bars.right, keyboardVisible ? 0 : bars.bottom);
            offlineBanner.setPadding(
                bars.left + dp(16),
                dp(7),
                bars.right + dp(8),
                dp(7)
            );
            updateBanner.setPadding(
                bars.left + dp(16),
                dp(6),
                bars.right + dp(4),
                dp(6)
            );
            dataSaverBanner.setPadding(
                bars.left + dp(16),
                dp(6),
                bars.right + dp(4),
                dp(6)
            );
            contentContainer.setPadding(
                bars.left,
                0,
                bars.right,
                keyboardVisible ? ime.bottom : 0
            );
            bottomBar.setVisibility(keyboardVisible ? View.GONE : View.VISIBLE);

            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private boolean isLandscape() {
        return getResources().getConfiguration().orientation
            == Configuration.ORIENTATION_LANDSCAPE;
    }

    /**
     * Adapta cabecera y barra inferior a la orientación y a pantallas anchas.
     * En apaisado la cabecera se compacta y el wordmark se separa de la barra
     * de estado; en tabletas el contenido de las barras se centra con un
     * ancho máximo para que no quede desproporcionado.
     */
    private void applyResponsiveLayout() {
        if (appBarRow == null || bottomBarRow == null || logoWordmark == null) {
            return;
        }

        int screenWidthDp = getResources().getConfiguration().screenWidthDp;
        boolean landscape = isLandscape();

        ViewGroup.LayoutParams barParams = appBarRow.getLayoutParams();
        barParams.width = dp(Math.min(screenWidthDp, 720));
        barParams.height = dp(landscape ? 60 : 64);
        if (barParams instanceof LinearLayout.LayoutParams) {
            ((LinearLayout.LayoutParams) barParams).gravity = Gravity.CENTER_HORIZONTAL;
        }
        appBarRow.setLayoutParams(barParams);

        ViewGroup.LayoutParams logoParams = logoWordmark.getLayoutParams();
        logoParams.height = dp(landscape ? 36 : 46);
        logoWordmark.setLayoutParams(logoParams);

        ViewGroup.LayoutParams navParams = bottomBarRow.getLayoutParams();
        navParams.width = dp(Math.min(screenWidthDp, 600));
        if (navParams instanceof LinearLayout.LayoutParams) {
            ((LinearLayout.LayoutParams) navParams).gravity = Gravity.CENTER_HORIZONTAL;
        }
        bottomBarRow.setLayoutParams(navParams);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyResponsiveLayout();
        applyPreferences();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setGeolocationEnabled(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            settings.setAlgorithmicDarkeningAllowed(false);
        }

        webView.setBackgroundColor(ContextCompat.getColor(this, R.color.surface));
        webView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        webView.getSettings().setTextZoom(AppPrefs.getTextZoom(this));
        webView.setWebViewClient(new EocWebViewClient());
        webView.setWebChromeClient(new EocChromeClient());
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            String safe = UrlPolicy.normalize(url);
            if (safe != null) {
                openExternalWeb(safe);
            } else {
                Toast.makeText(this, R.string.download_unsupported, Toast.LENGTH_SHORT).show();
            }
        });

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, false);
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
    }

    private void configureSwipeRefresh() {
        swipeRefresh.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.brand_blue),
            ContextCompat.getColor(this, R.color.brand_blue_dark),
            ContextCompat.getColor(this, R.color.brand_blue)
        );
        swipeRefresh.setProgressBackgroundColorSchemeColor(
            ContextCompat.getColor(this, R.color.surface)
        );
        swipeRefresh.setDistanceToTriggerSync(dp(76));
        swipeRefresh.setOnChildScrollUpCallback(
            (parent, child) -> webView != null && webView.getScrollY() > 0
        );
        swipeRefresh.setOnRefreshListener(this::refresh);
    }

    private void configureActions() {
        navBack.setOnClickListener(v -> goBackFromUi());
        shareButton.setOnClickListener(v -> shareCurrentPage());
        readerButton.setOnClickListener(v -> toggleReaderMode());
        savedButton.setOnClickListener(v -> showSavedSheet());
        dataSaverSettings.setOnClickListener(v -> openInfo());
        findViewById(R.id.logoWordmark).setOnClickListener(v -> loadHome(false));
        navHome.setOnClickListener(v -> loadHome(false));
        navSections.setOnClickListener(v -> showSections());
        navSearch.setOnClickListener(v -> showSearch());
        navInfo.setOnClickListener(v -> openInfo());
        bannerRetry.setOnClickListener(v -> refresh());
        findViewById(R.id.updateBannerAction).setOnClickListener(v -> openUpdates());
        findViewById(R.id.updateBannerClose).setOnClickListener(v -> dismissUpdateBanner());
    }

    private void configureStateOverlay() {
        stateRetry.setOnClickListener(v -> retryFromError());
        stateHome.setOnClickListener(v -> loadHome(true));
        updateNavState();
    }

    private void configureBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (fullscreenView != null) {
                    hideCustomView();
                    return;
                }
                if (readerModeActive) {
                    setReaderMode(false);
                    return;
                }
                if (savedDialog != null && savedDialog.isShowing()) {
                    savedDialog.dismiss();
                    return;
                }
                if (sectionsDialog != null && sectionsDialog.isShowing()) {
                    sectionsDialog.dismiss();
                    return;
                }
                if (searchDialog != null && searchDialog.isShowing()) {
                    searchDialog.dismiss();
                    return;
                }
                if (webView.canGoBack()) {
                    webView.goBack();
                    return;
                }
                long now = System.currentTimeMillis();
                if (now - lastBackPress <= EXIT_WINDOW_MS) {
                    finish();
                    return;
                }
                lastBackPress = now;
                Toast.makeText(MainActivity.this, R.string.exit_hint, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void configureNetworkMonitoring() {
        if (connectivityManager == null) {
            connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        }
        if (connectivityManager == null) {
            return;
        }

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                runOnUiThread(() -> {
                    setOnline(true);
                    refreshDataSaverState();
                });
            }

            @Override
            public void onLost(@NonNull Network network) {
                runOnUiThread(() -> {
                    setOnline(checkOnline());
                    refreshDataSaverState();
                });
            }

            @Override
            public void onCapabilitiesChanged(
                @NonNull Network network,
                @NonNull NetworkCapabilities capabilities
            ) {
                boolean online = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                runOnUiThread(() -> {
                    setOnline(online);
                    refreshDataSaverState();
                });
            }
        };

        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
        } catch (RuntimeException e) {
            networkCallback = null;
        }
    }

    private boolean checkOnline() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) {
            return true;
        }
        Network network = manager.getActiveNetwork();
        if (network == null) {
            return false;
        }
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
        return capabilities != null
            && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private boolean checkCellular() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) {
            return false;
        }
        Network network = manager.getActiveNetwork();
        if (network == null) {
            return false;
        }
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
        return capabilities != null
            && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
    }

    private boolean isImageRequest(WebResourceRequest request) {
        String accept = request.getRequestHeaders().get("Accept");
        if (accept != null && accept.toLowerCase(Locale.ROOT).contains("image")) {
            return true;
        }
        String destination = request.getRequestHeaders().get("Sec-Fetch-Dest");
        if ("image".equalsIgnoreCase(destination)) {
            return true;
        }
        String path = request.getUrl().getPath();
        return path != null && path.toLowerCase(Locale.ROOT).matches(
            ".*\\.(png|jpe?g|gif|webp|bmp|avif|svg)$"
        );
    }

    private void refreshDataSaverState() {
        cellularNetwork = checkCellular();
        updateDataSaverUi();
    }

    private void updateDataSaverUi() {
        boolean enabled = AppPrefs.isDataSaverEnabled(this);
        dataSaverBlocking = enabled && cellularNetwork;
        boolean show = dataSaverBlocking && isOnline && contentVisible;
        dataSaverBanner.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void setOnline(boolean online) {
        if (isOnline == online) {
            updateDataSaverUi();
            return;
        }
        isOnline = online;
        updateOfflineUi();
        updateDataSaverUi();

        if (online) {
            if (stateIsError && stateOverlay.getVisibility() == View.VISIBLE) {
                retryFromError();
            }
        } else if (!contentVisible) {
            showError(true);
        }
    }

    private void updateOfflineUi() {
        boolean showBanner = !isOnline && contentVisible;
        offlineBanner.setVisibility(showBanner ? View.VISIBLE : View.GONE);
    }

    private void loadHome(boolean initial) {
        if (!checkOnline()) {
            isOnline = false;
            updateOfflineUi();
            showError(true);
            return;
        }

        String current = webView.getUrl();
        if (!initial && isHomeUrl(current)) {
            webView.scrollTo(0, 0);
            return;
        }

        loadUrl(HOME_URL, initial || !contentVisible);
    }

    private void loadUrl(String url, boolean showWelcome) {
        if (!checkOnline()) {
            isOnline = false;
            updateOfflineUi();
            showError(true);
            return;
        }

        pageHadError = false;
        progressBar.setProgress(0);
        if (showWelcome || !contentVisible) {
            showWelcome();
        }
        webView.loadUrl(url);
    }

    private void refresh() {
        if (!checkOnline()) {
            isOnline = false;
            swipeRefresh.setRefreshing(false);
            updateOfflineUi();
            showError(true);
            return;
        }

        pageHadError = false;
        progressBar.setProgress(0);
        if (stateOverlay.getVisibility() == View.VISIBLE) {
            showWelcome();
        }

        String current = webView.getUrl();
        if (current == null || current.startsWith("about:")) {
            webView.loadUrl(HOME_URL);
        } else {
            webView.reload();
        }
    }

    private void retryFromError() {
        if (!checkOnline()) {
            isOnline = false;
            updateOfflineUi();
            showError(true);
            return;
        }
        showWelcome();
        refresh();
    }

    private void goBackFromUi() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            Toast.makeText(this, R.string.at_home, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Comprueba si hay una Release estable más reciente. El resultado se
     * muestra de forma discreta: aviso en la cabecera y notificación local.
     */
    private void runUpdateCheck(boolean manual) {
        if (updateManager == null) {
            return;
        }
        updateManager.check(manual, (release, available, error) -> {
            if (isFinishing() || updateManager == null) {
                return;
            }
            if (!available || release == null) {
                updateBanner.setVisibility(View.GONE);
                pendingUpdateRelease = null;
                return;
            }
            if (updateManager.getStore().isDismissed(release.getVersion())) {
                updateBanner.setVisibility(View.GONE);
                return;
            }
            pendingUpdateRelease = release;
            updateBannerText.setText(
                getString(R.string.update_banner_text, release.getVersion())
            );
            updateBanner.setVisibility(View.VISIBLE);
            maybeNotifyUpdate(release);
        });
    }

    private void maybeNotifyUpdate(ReleaseInfo release) {
        if (updateManager == null) {
            return;
        }
        if (UpdateNotifier.canPostNotifications(this)) {
            if (UpdatePolicy.shouldNotify(
                release.getVersion(),
                updateManager.getStore().getNotifiedVersion(),
                updateManager.getStore().getDismissedVersion()
            )) {
                updateManager.getStore().setNotifiedVersion(release.getVersion());
                UpdateNotifier.show(this, release);
            }
            return;
        }

        // Android 13+: el permiso se pide solo cuando existe una actualización.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && !notificationPermissionRequested) {
            notificationPermissionRequested = true;
            ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                REQUEST_NOTIFICATIONS
            );
        }
    }

    private void dismissUpdateBanner() {
        if (updateManager != null) {
            ReleaseInfo cached = updateManager.getCachedRelease();
            if (cached != null) {
                updateManager.getStore().dismiss(cached.getVersion());
            }
        }
        updateBanner.setVisibility(View.GONE);
    }

    private void openUpdates() {
        startActivity(new Intent(this, UpdateActivity.class));
    }

    @Override
    public void onRequestPermissionsResult(
        int requestCode,
        @NonNull String[] permissions,
        @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_NOTIFICATIONS) {
            return;
        }
        if (pendingUpdateRelease != null
            && grantResults.length > 0
            && grantResults[0] == PackageManager.PERMISSION_GRANTED
            && updateManager != null) {
            updateManager.getStore().setNotifiedVersion(pendingUpdateRelease.getVersion());
            UpdateNotifier.show(this, pendingUpdateRelease);
        }
    }

    private void openInfo() {
        startActivity(new Intent(this, InfoActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void openSavedArticles() {
        startActivity(new Intent(this, SavedArticlesActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void updateArticleActions() {
        if (readerButton == null || savedButton == null) {
            return;
        }
        boolean showReader = pageIsArticle && contentVisible && !stateIsError;
        readerButton.setVisibility(showReader ? View.VISIBLE : View.GONE);
        readerButton.setSelected(readerModeActive);
        readerButton.setContentDescription(
            getString(readerModeActive ? R.string.reader_close : R.string.cd_reader)
        );

        boolean hasSaved = savedStore != null && savedStore.count() > 0;
        boolean showSaved = pageIsArticle ? (!stateIsError || hasSaved) : hasSaved;
        savedButton.setVisibility(showSaved ? View.VISIBLE : View.GONE);
        updateSavedIcon();
    }

    private void updateSavedIcon() {
        if (savedButton == null) {
            return;
        }
        boolean saved = pageIsArticle && savedStore != null && savedStore.isSaved(currentArticleUrl);
        savedButton.setImageResource(saved ? R.drawable.ic_bookmark_filled : R.drawable.ic_bookmark);
    }

    private void showSavedSheet() {
        if (savedDialog != null && savedDialog.isShowing()) {
            return;
        }

        final Dialog dialog = createSheet(R.layout.sheet_saved);
        savedDialog = dialog;

        LinearLayout list = dialog.findViewById(R.id.savedSheetList);
        TextView status = dialog.findViewById(R.id.savedSheetStatus);
        list.removeAllViews();

        SavedArticle existing = pageIsArticle && savedStore != null
            ? savedStore.findByUrl(currentArticleUrl)
            : null;

        if (pageIsArticle) {
            if (existing == null) {
                addSheetRow(
                    list,
                    R.drawable.ic_bookmark,
                    getString(R.string.saved_save_title),
                    getString(R.string.saved_save_sub),
                    v -> {
                        dialog.dismiss();
                        saveCurrentArticle();
                    }
                );
            } else {
                addSheetRow(
                    list,
                    R.drawable.ic_bookmark_filled,
                    getString(R.string.saved_update_title),
                    getString(R.string.saved_update_sub),
                    v -> {
                        dialog.dismiss();
                        saveCurrentArticle();
                    }
                );
                addSheetRow(
                    list,
                    R.drawable.ic_delete,
                    getString(R.string.saved_remove_title),
                    getString(R.string.saved_remove_sub),
                    v -> {
                        dialog.dismiss();
                        confirmRemoveSaved(existing);
                    }
                );
            }
            if (status != null) {
                status.setVisibility(View.GONE);
            }
        } else {
            if (status != null) {
                status.setText(R.string.saved_none_hint);
                status.setVisibility(View.VISIBLE);
            }
        }

        int count = savedStore != null ? savedStore.count() : 0;
        String countText = count > 0
            ? getResources().getQuantityString(R.plurals.saved_count, count, count)
            : getString(R.string.saved_empty_title);
        addSheetRow(
            list,
            R.drawable.ic_library,
            getString(R.string.saved_view_title),
            countText,
            v -> {
                dialog.dismiss();
                openSavedArticles();
            }
        );

        dialog.setOnDismissListener(d -> {
            if (savedDialog == dialog) {
                savedDialog = null;
            }
        });

        dialog.show();
        sizeSheet(dialog);
    }

    private void saveCurrentArticle() {
        if (savingArticle || webView == null) {
            return;
        }
        if (!pageIsArticle
            || TextUtils.isEmpty(currentArticleUrl)
            || !UrlPolicy.isInternal(currentArticleUrl)) {
            Toast.makeText(this, R.string.saved_not_article, Toast.LENGTH_SHORT).show();
            return;
        }

        savingArticle = true;
        showSavingDialog();

        webView.evaluateJavascript(ArticleScripts.EXTRACT_JS, raw -> {
            if (isFinishing()) {
                savingArticle = false;
                dismissSavingDialog();
                return;
            }
            final JSONObject data = decodeJsObject(raw);
            if (data == null) {
                savingArticle = false;
                dismissSavingDialog();
                Toast.makeText(this, R.string.saved_not_article, Toast.LENGTH_SHORT).show();
                return;
            }

            final String html = data.optString("html", "");
            final String extractedTitle = data.optString("title", currentArticleTitle);
            final String byline = data.optString("byline", "");
            final String url = data.optString("url", currentArticleUrl);
            if (TextUtils.isEmpty(html) || html.trim().length() < 150 || !UrlPolicy.isInternal(url)) {
                savingArticle = false;
                dismissSavingDialog();
                Toast.makeText(this, R.string.saved_not_article, Toast.LENGTH_SHORT).show();
                return;
            }

            final boolean wasSaved = savedStore != null && savedStore.isSaved(url);
            final SavedArticlesStore store = savedStore;
            if (store == null || ioExecutor == null) {
                savingArticle = false;
                dismissSavingDialog();
                return;
            }

            try {
                ioExecutor.execute(() -> {
                    SavedArticle article = null;
                    try {
                        article = store.save(url, extractedTitle, byline, html);
                    } catch (Exception ignored) {
                        // Se informa abajo con un mensaje claro.
                    }
                    final SavedArticle result = article;
                    runOnUiThread(() -> {
                        if (isFinishing()) {
                            savingArticle = false;
                            dismissSavingDialog();
                            return;
                        }
                        savingArticle = false;
                        dismissSavingDialog();
                        if (result == null) {
                            Toast.makeText(
                                MainActivity.this,
                                R.string.saved_failed,
                                Toast.LENGTH_SHORT
                            ).show();
                        } else {
                            Toast.makeText(
                                MainActivity.this,
                                wasSaved ? R.string.saved_updated : R.string.saved_saved,
                                Toast.LENGTH_SHORT
                            ).show();
                        }
                        updateArticleActions();
                    });
                });
            } catch (RuntimeException e) {
                savingArticle = false;
                dismissSavingDialog();
                Toast.makeText(this, R.string.saved_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void confirmRemoveSaved(SavedArticle article) {
        if (article == null) {
            return;
        }
        String name = TextUtils.isEmpty(article.title)
            ? getString(R.string.saved_activity_title)
            : article.title;
        new AlertDialog.Builder(this)
            .setTitle(R.string.saved_delete_title)
            .setMessage(getString(R.string.saved_delete_message, name))
            .setNegativeButton(R.string.saved_delete_cancel, null)
            .setPositiveButton(
                R.string.saved_delete_confirm,
                (dialog, which) -> removeSavedArticle(article.id)
            )
            .show();
    }

    private void removeSavedArticle(String id) {
        if (savedStore == null || ioExecutor == null || TextUtils.isEmpty(id)) {
            return;
        }
        final SavedArticlesStore store = savedStore;
        try {
            ioExecutor.execute(() -> {
                store.remove(id);
                runOnUiThread(() -> {
                    if (isFinishing()) {
                        return;
                    }
                    Toast.makeText(
                        MainActivity.this,
                        R.string.saved_removed,
                        Toast.LENGTH_SHORT
                    ).show();
                    updateArticleActions();
                });
            });
        } catch (RuntimeException e) {
            Toast.makeText(this, R.string.saved_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void showSavingDialog() {
        dismissSavingDialog();
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_progress);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.42f);
        }
        savingDialog = dialog;
        dialog.show();
    }

    private void dismissSavingDialog() {
        if (savingDialog != null) {
            if (savingDialog.isShowing()) {
                savingDialog.dismiss();
            }
            savingDialog = null;
        }
    }

    private void toggleReaderMode() {
        if (!pageIsArticle) {
            Toast.makeText(this, R.string.reader_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        if (readerModeActive) {
            setReaderMode(false);
            return;
        }

        if (webView == null) {
            return;
        }
        final boolean dark = AppPrefs.isDarkMode(this);
        webView.evaluateJavascript(ArticleScripts.openReader(dark), value -> {
            boolean opened = "true".equals(value);
            readerModeActive = opened;
            updateArticleActions();
            if (!opened) {
                Toast.makeText(MainActivity.this, R.string.reader_failed, Toast.LENGTH_SHORT).show();
            }
        });
        readerModeActive = true;
        updateArticleActions();
    }

    private void setReaderMode(boolean enabled) {
        if (!enabled) {
            if (webView != null) {
                webView.evaluateJavascript(ArticleScripts.closeReader(), null);
            }
            if (readerModeActive) {
                Toast.makeText(this, R.string.reader_off, Toast.LENGTH_SHORT).show();
            }
            readerModeActive = false;
            updateArticleActions();
            return;
        }
        toggleReaderMode();
    }

    private JSONObject decodeJsObject(String raw) {
        if (raw == null || "null".equals(raw)) {
            return null;
        }
        try {
            Object value = new JSONTokener(raw).nextValue();
            if (value instanceof JSONObject) {
                return (JSONObject) value;
            }
            if (value instanceof String) {
                Object inner = new JSONTokener((String) value).nextValue();
                if (inner instanceof JSONObject) {
                    return (JSONObject) inner;
                }
            }
        } catch (Exception ignored) {
            // La respuesta no es un objeto JSON válido.
        }
        return null;
    }

    private void detectPageInfo(WebView view, String url) {
        final int request = ++pageInfoRequest;
        final String finalUrl = url;
        view.evaluateJavascript(ArticleScripts.PAGE_INFO_JS, raw -> {
            if (request != pageInfoRequest || isFinishing()) {
                return;
            }
            JSONObject info = decodeJsObject(raw);
            boolean article = info != null && info.optBoolean("article", false);
            String title = info != null ? info.optString("title", "") : "";
            pageIsArticle = article && UrlPolicy.isInternal(finalUrl);
            currentArticleUrl = finalUrl == null ? "" : finalUrl;
            currentArticleTitle = cleanTitle(
                TextUtils.isEmpty(title) ? currentTitle : title
            );
            updateArticleActions();
        });
    }

    private void applyReadingTheme() {
        if (webView == null) {
            return;
        }
        webView.setBackgroundColor(ContextCompat.getColor(this, R.color.surface));
        swipeRefresh.setProgressBackgroundColorSchemeColor(
            ContextCompat.getColor(this, R.color.surface)
        );
        applyContentSystemBars(
            getWindow(),
            WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
        );
        String url = webView.getUrl();
        if (url != null && !url.startsWith("about:")) {
            boolean dark = AppPrefs.isDarkMode(this);
            webView.evaluateJavascript(ArticleScripts.applyTheme(dark), null);
            if (readerModeActive && pageIsArticle) {
                // El modo lectura se dibuja con su propia hoja de estilos: se
                // vuelve a generar para que tome el tema recién elegido.
                webView.evaluateJavascript(ArticleScripts.openReader(dark), null);
            }
        }
    }

    private void shareCurrentPage() {
        String safeUrl = UrlPolicy.normalize(webView.getUrl());
        if (safeUrl == null) {
            safeUrl = HOME_URL;
        }
        final String shareUrl = safeUrl;

        webView.evaluateJavascript(SHARE_TITLE_JS, value -> {
            String title = decodeJsString(value);
            if (TextUtils.isEmpty(title)) {
                title = currentTitle;
            }
            shareText(cleanTitle(title), shareUrl);
        });
    }

    private void shareText(String title, String url) {
        String text = title + "\n\n" + url;

        Intent send = new Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, title)
            .putExtra(Intent.EXTRA_TEXT, text);

        try {
            startActivity(Intent.createChooser(send, getString(R.string.share_chooser)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_app, Toast.LENGTH_SHORT).show();
        }
    }

    private String decodeJsString(String raw) {
        if (raw == null || "null".equals(raw)) {
            return null;
        }
        try {
            Object value = new JSONTokener(raw).nextValue();
            if (value instanceof String) {
                String text = ((String) value).trim();
                return text.isEmpty() ? null : text;
            }
        } catch (Exception ignored) {
            // Si la respuesta no es válida se usa el título de respaldo.
        }
        return null;
    }

    private String cleanTitle(String title) {
        if (TextUtils.isEmpty(title)) {
            return getString(R.string.app_name);
        }
        String clean = title.trim();
        clean = clean.replaceAll("(?i)\\s*[|\\-–—]\\s*EL OBSERVADOR CUBANO\\s*$", "");
        clean = clean.replaceAll("(?i)^EL OBSERVADOR CUBANO\\s*[|\\-–—]\\s*", "");
        return clean.trim().isEmpty() ? getString(R.string.app_name) : clean.trim();
    }

    private void openExternal(String url, UrlPolicy.Action action) {
        if (action == UrlPolicy.Action.BLOCK) {
            return;
        }

        if (action == UrlPolicy.Action.EXTERNAL_APP) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, R.string.no_app, Toast.LENGTH_SHORT).show();
            }
            return;
        }

        String safe = UrlPolicy.normalize(url);
        if (safe == null) {
            Toast.makeText(this, R.string.unsafe_link, Toast.LENGTH_SHORT).show();
            return;
        }
        openExternalWeb(safe);
    }

    private void openExternalWeb(String safeUrl) {
        if (AppPrefs.opensLinksInApp(this)) {
            try {
                CustomTabsIntent tabs = new CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .setUrlBarHidingEnabled(true)
                    .build();
                tabs.launchUrl(this, Uri.parse(safeUrl));
                return;
            } catch (ActivityNotFoundException ignored) {
                // Si no hay navegador compatible con Custom Tabs, se usa el sistema.
            }
        }

        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_app, Toast.LENGTH_SHORT).show();
        }
    }

    private boolean handleNavigation(WebView view, String url, String method) {
        if (url != null && url.startsWith("eoc-reader://close")) {
            setReaderMode(false);
            return true;
        }

        UrlPolicy.Action action = UrlPolicy.classify(url);

        if (action == UrlPolicy.Action.INTERNAL) {
            String normalized = UrlPolicy.normalize(url);
            if (normalized != null && !normalized.equals(url)) {
                view.loadUrl(normalized);
                return true;
            }
            return false;
        }

        if (action == UrlPolicy.Action.EXTERNAL_WEB || action == UrlPolicy.Action.EXTERNAL_APP) {
            if ("POST".equalsIgnoreCase(method)) {
                // Conserva el envío de formularios (comentarios, búsquedas) dentro
                // del WebView en lugar de convertirlo en una URL GET externa.
                return false;
            }
            openExternal(url, action);
            return true;
        }

        return true;
    }

    private boolean isHomeUrl(String url) {
        if (url == null) {
            return false;
        }
        String value = url;
        int hash = value.indexOf('#');
        if (hash >= 0) {
            value = value.substring(0, hash);
        }
        int query = value.indexOf('?');
        if (query >= 0) {
            value = value.substring(0, query);
        }
        if (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return "https://observadorcubano.blogspot.com".equalsIgnoreCase(value)
            || "https://www.observadorcubano.blogspot.com".equalsIgnoreCase(value);
    }

    private void updateNavState() {
        if (webView == null || navBack == null) {
            return;
        }

        boolean canGoBack = webView.canGoBack();
        if (canGoBack && navBack.getVisibility() != View.VISIBLE) {
            navBack.animate().cancel();
            navBack.setAlpha(0f);
            navBack.setVisibility(View.VISIBLE);
            navBack.animate().alpha(1f).setDuration(160L).start();
        } else if (!canGoBack && navBack.getVisibility() == View.VISIBLE) {
            navBack.animate().cancel();
            navBack.animate()
                .alpha(0f)
                .setDuration(120L)
                .withEndAction(() -> navBack.setVisibility(View.GONE))
                .start();
        }

        String url = webView.getUrl();
        boolean onLabel = url != null && url.contains("/search/label/");
        boolean onSearch = url != null && url.contains("/search?q=");
        boolean onHome = !onLabel && !onSearch;

        navHome.setSelected(onHome);
        navSections.setSelected(onLabel);
        navSearch.setSelected(onSearch);
        navInfo.setSelected(false);
    }

    private void observeSplashFirstFrame() {
        splashOverlay.getViewTreeObserver().addOnPreDrawListener(
            new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    ViewTreeObserver observer = splashOverlay.getViewTreeObserver();
                    if (observer.isAlive()) {
                        observer.removeOnPreDrawListener(this);
                    }
                    firstFrameDrawn = true;
                    if (stopSplashRequested) {
                        stopSplash();
                    }
                    return true;
                }
            }
        );
    }

    private void stopSplash() {
        if (splashHidden || splashOverlay == null) {
            return;
        }
        // Si la página termina de cargar antes de que la actividad dibuje su
        // primer fotograma, se espera a ese fotograma para que la splash se
        // vea al menos una vez. No se añade ninguna espera artificial.
        if (!firstFrameDrawn) {
            stopSplashRequested = true;
            return;
        }
        splashHidden = true;
        root.removeCallbacks(splashFallback);
        splashProgress.setVisibility(View.GONE);
        splashOverlay.animate().cancel();
        splashOverlay.animate()
            .alpha(0f)
            .setDuration(320L)
            .withEndAction(() -> {
                splashOverlay.setVisibility(View.GONE);
                applyContentSystemBars(
                    getWindow(),
                    WindowCompat.getInsetsController(
                        getWindow(),
                        getWindow().getDecorView()
                    )
                );
            })
            .start();
    }

    private void showWelcome() {
        stateIsError = false;
        stateOverlay.animate().cancel();
        stateOverlay.setAlpha(1f);
        stateOverlay.setVisibility(View.VISIBLE);
        stateIcon.setVisibility(View.GONE);
        stateProgress.setVisibility(View.VISIBLE);
        stateTitle.setText(R.string.welcome_title);
        stateMessage.setText(R.string.welcome_message);
        stateRetry.setVisibility(View.GONE);
        stateHome.setVisibility(View.GONE);
    }

    private void showError(boolean offline) {
        stopSplash();
        stateIsError = true;
        stateOverlay.animate().cancel();
        stateOverlay.setAlpha(1f);
        stateOverlay.setVisibility(View.VISIBLE);
        stateProgress.setVisibility(View.GONE);
        stateRetry.setVisibility(View.VISIBLE);

        boolean atHome = webView.getUrl() == null || isHomeUrl(webView.getUrl());
        stateHome.setVisibility(atHome ? View.GONE : View.VISIBLE);

        if (offline) {
            stateTitle.setText(R.string.offline_title);
            stateMessage.setText(R.string.offline_message);
            setStateIcon(R.drawable.ic_cloud_off, R.color.brand_blue_dark);
        } else {
            stateTitle.setText(R.string.error_title);
            stateMessage.setText(R.string.error_message);
            setStateIcon(R.drawable.ic_error, R.color.error_text);
        }
    }

    private void showSecurityError() {
        stopSplash();
        stateIsError = true;
        stateOverlay.animate().cancel();
        stateOverlay.setAlpha(1f);
        stateOverlay.setVisibility(View.VISIBLE);
        stateProgress.setVisibility(View.GONE);
        stateRetry.setVisibility(View.VISIBLE);
        stateHome.setVisibility(
            webView.getUrl() == null || isHomeUrl(webView.getUrl())
                ? View.GONE
                : View.VISIBLE
        );
        stateTitle.setText(R.string.security_error_title);
        stateMessage.setText(R.string.security_error_message);
        setStateIcon(R.drawable.ic_error, R.color.error_text);
    }

    private void setStateIcon(int resId, int colorRes) {
        stateIcon.setVisibility(View.VISIBLE);
        stateIcon.setImageResource(resId);
        stateIcon.setImageTintList(
            ColorStateList.valueOf(ContextCompat.getColor(this, colorRes))
        );
    }

    private void hideState() {
        if (stateOverlay.getVisibility() != View.VISIBLE) {
            return;
        }
        stateOverlay.animate()
            .alpha(0f)
            .setDuration(180L)
            .withEndAction(() -> {
                stateOverlay.setVisibility(View.GONE);
                stateOverlay.setAlpha(1f);
            })
            .start();
    }

    private void applyPreferences() {
        if (webView == null) {
            return;
        }
        int zoom = AppPrefs.getTextZoom(this);
        if (webView.getSettings().getTextZoom() != zoom) {
            webView.getSettings().setTextZoom(zoom);
        }
        applyReadingTheme();
        updateDataSaverUi();
        updateArticleActions();
    }

    // ------------------------------------------------------------------
    // Secciones y búsqueda
    // ------------------------------------------------------------------

    private void showSections() {
        if (sectionsDialog != null && sectionsDialog.isShowing()) {
            return;
        }

        final Dialog dialog = createSheet(R.layout.sheet_sections);
        sectionsDialog = dialog;

        LinearLayout list = dialog.findViewById(R.id.sectionsList);
        ProgressBar progress = dialog.findViewById(R.id.sectionsProgress);
        TextView status = dialog.findViewById(R.id.sectionsStatus);
        MaxHeightScrollView scroll = dialog.findViewById(R.id.sectionsScroll);

        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int reserved = isLandscape() ? 170 : 300;
        scroll.setMaxHeightPx(Math.max(dp(120), screenHeight - dp(reserved)));

        renderSections(list, loadCachedSections());
        progress.setVisibility(View.GONE);
        status.setVisibility(View.GONE);

        dialog.setOnDismissListener(d -> {
            if (sectionsDialog == dialog) {
                sectionsDialog = null;
            }
        });

        dialog.show();
        sizeSheet(dialog);
        fetchSectionsIfNeeded(dialog, list, progress, status);
    }

    private void dismissSections() {
        if (sectionsDialog != null && sectionsDialog.isShowing()) {
            sectionsDialog.dismiss();
        }
    }

    private void showSearch() {
        if (searchDialog != null && searchDialog.isShowing()) {
            return;
        }

        final Dialog dialog = createSheet(R.layout.sheet_search);
        searchDialog = dialog;

        EditText input = dialog.findViewById(R.id.searchInput);
        ImageButton clear = dialog.findViewById(R.id.searchClear);
        Button submit = dialog.findViewById(R.id.searchSubmit);
        TextView error = dialog.findViewById(R.id.searchError);
        LinearLayout chips = dialog.findViewById(R.id.searchChips);
        View suggestions = dialog.findViewById(R.id.searchSuggestions);
        View header = dialog.findViewById(R.id.searchHeader);
        View inputRow = dialog.findViewById(R.id.searchInputRow);
        View content = dialog.findViewById(R.id.searchContent);

        // En apaisado la altura útil es reducida: se oculta el encabezado para
        // que el campo y el botón Buscar queden siempre por encima del teclado.
        if (isLandscape()) {
            header.setVisibility(View.GONE);
            LinearLayout.LayoutParams rowParams =
                (LinearLayout.LayoutParams) inputRow.getLayoutParams();
            rowParams.topMargin = 0;
            inputRow.setLayoutParams(rowParams);
            content.setPadding(
                content.getPaddingLeft(),
                dp(8),
                content.getPaddingRight(),
                dp(14)
            );
        }

        renderSearchChips(chips, input, loadCachedSections());

        // Al escribir se ocultan las sugerencias para que la hoja y el botón
        // Buscar queden siempre visibles por encima del teclado.
        input.setOnFocusChangeListener((v, hasFocus) ->
            suggestions.setVisibility(hasFocus ? View.GONE : View.VISIBLE)
        );

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Sin acción.
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                clear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                if (s.length() > 0) {
                    error.setVisibility(View.GONE);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Sin acción.
            }
        });

        clear.setOnClickListener(v -> {
            input.setText("");
            input.requestFocus();
        });

        submit.setOnClickListener(v -> submitSearch(input, error, dialog));
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                submitSearch(input, error, dialog);
                return true;
            }
            return false;
        });

        dialog.setOnDismissListener(d -> {
            hideKeyboard(input);
            if (searchDialog == dialog) {
                searchDialog = null;
            }
        });

        dialog.show();
        sizeSheet(dialog);
    }

    private void submitSearch(EditText input, TextView error, Dialog dialog) {
        String query = input.getText() != null ? input.getText().toString().trim() : "";
        if (query.isEmpty()) {
            error.setVisibility(View.VISIBLE);
            input.requestFocus();
            return;
        }

        error.setVisibility(View.GONE);
        hideKeyboard(input);
        dialog.dismiss();
        loadUrl(HOME_URL + "search?q=" + Uri.encode(query), false);
    }

    private void openLabel(String label) {
        loadUrl(HOME_URL + "search/label/" + Uri.encode(label), false);
    }

    private Dialog createSheet(int layoutRes) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(layoutRes);
        dialog.setCanceledOnTouchOutside(true);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.48f);
            window.setGravity(Gravity.BOTTOM);
            window.setWindowAnimations(R.style.EOC_SheetAnimation);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            WindowCompat.setDecorFitsSystemWindows(window, false);
            boolean dark = AppPrefs.isDarkMode(this);
            if (Build.VERSION.SDK_INT < 35) {
                window.setNavigationBarColor(
                    ContextCompat.getColor(this, R.color.surface)
                );
            }
            WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                window,
                window.getDecorView()
            );
            if (controller != null) {
                controller.setAppearanceLightNavigationBars(!dark);
            }

            // La hoja se dibuja hasta el borde inferior, pero su contenido
            // respeta la barra de navegación del sistema.
            View content = dialog.findViewById(android.R.id.content);
            if (content instanceof ViewGroup && ((ViewGroup) content).getChildCount() > 0) {
                final View sheet = ((ViewGroup) content).getChildAt(0);
                final int baseLeft = sheet.getPaddingLeft();
                final int baseTop = sheet.getPaddingTop();
                final int baseRight = sheet.getPaddingRight();
                final int baseBottom = sheet.getPaddingBottom();
                ViewCompat.setOnApplyWindowInsetsListener(sheet, (view, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    view.setPadding(
                        baseLeft,
                        baseTop,
                        baseRight,
                        baseBottom + bars.bottom
                    );
                    return insets;
                });
                ViewCompat.requestApplyInsets(sheet);
            }
        }
        return dialog;
    }

    private void sizeSheet(Dialog dialog) {
        Window window = dialog.getWindow();
        if (window != null) {
            int screenWidthDp = getResources().getConfiguration().screenWidthDp;
            window.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            window.setLayout(
                dp(Math.min(screenWidthDp, 600)),
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
    }

    private void renderSections(LinearLayout list, List<Section> sections) {
        list.removeAllViews();

        addSheetRow(
            list,
            R.drawable.ic_home,
            getString(R.string.sections_home),
            getString(R.string.sections_home_sub),
            v -> {
                dismissSections();
                loadHome(false);
            }
        );

        for (Section section : sections) {
            String subtitle = section.count > 0
                ? getResources().getQuantityString(
                    R.plurals.section_article_count,
                    section.count,
                    section.count
                )
                : getString(R.string.nav_sections);
            addSheetRow(
                list,
                R.drawable.ic_label,
                section.name,
                subtitle,
                v -> {
                    dismissSections();
                    openLabel(section.name);
                }
            );
        }
    }

    private void addSheetRow(
        LinearLayout list,
        int iconRes,
        String titleText,
        String subtitleText,
        View.OnClickListener onClick
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_sheet_row);
        row.setClickable(true);
        row.setFocusable(true);
        row.setPadding(dp(12), dp(9), dp(8), dp(9));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rowParams.bottomMargin = dp(2);
        row.setLayoutParams(rowParams);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setImageTintList(
            ColorStateList.valueOf(ContextCompat.getColor(this, R.color.brand_blue_dark))
        );
        row.addView(icon, new LinearLayout.LayoutParams(dp(20), dp(20)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        );
        textParams.leftMargin = dp(14);
        row.addView(texts, textParams);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        title.setTextSize(15f);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setIncludeFontPadding(false);
        texts.addView(title);

        if (!TextUtils.isEmpty(subtitleText)) {
            TextView subtitle = new TextView(this);
            subtitle.setText(subtitleText);
            subtitle.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            subtitle.setTextSize(12f);
            subtitle.setIncludeFontPadding(false);
            LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            subtitleParams.topMargin = dp(2);
            texts.addView(subtitle, subtitleParams);
        }

        ImageView chevron = new ImageView(this);
        chevron.setImageResource(R.drawable.ic_chevron_right);
        chevron.setImageTintList(
            ColorStateList.valueOf(ContextCompat.getColor(this, R.color.text_tertiary))
        );
        row.addView(chevron, new LinearLayout.LayoutParams(dp(20), dp(20)));

        row.setOnClickListener(onClick);
        list.addView(row);
    }

    private void renderSearchChips(LinearLayout chips, EditText input, List<Section> sections) {
        chips.removeAllViews();

        for (Section section : sections) {
            TextView chip = new TextView(this);
            chip.setText(section.name);
            chip.setTextSize(13f);
            chip.setTextColor(ContextCompat.getColor(this, R.color.brand_blue_dark));
            chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            chip.setBackgroundResource(R.drawable.bg_chip);
            chip.setPadding(dp(14), dp(9), dp(14), dp(9));
            chip.setClickable(true);
            chip.setFocusable(true);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.rightMargin = dp(8);
            chip.setLayoutParams(params);

            chip.setOnClickListener(v -> {
                input.setText(section.name);
                input.setSelection(input.getText().length());
                input.requestFocus();
            });

            chips.addView(chip);
        }
    }

    private void fetchSectionsIfNeeded(
        Dialog dialog,
        LinearLayout list,
        ProgressBar progress,
        TextView status
    ) {
        SharedPreferences prefs = getSharedPreferences(SECTIONS_PREFS, Context.MODE_PRIVATE);
        long last = prefs.getLong(KEY_SECTIONS_AT, 0L);
        if (System.currentTimeMillis() - last < SECTIONS_TTL_MS) {
            return;
        }
        if (!checkOnline() || sectionsFetchInFlight) {
            return;
        }

        sectionsFetchInFlight = true;
        progress.setVisibility(View.VISIBLE);
        status.setVisibility(View.VISIBLE);

        try {
            ioExecutor.execute(() -> {
                List<Section> fetched = null;
                try {
                    fetched = requestSections();
                } catch (Exception ignored) {
                    // Se conservan las secciones guardadas.
                }

                final List<Section> result = fetched;
                runOnUiThread(() -> {
                    sectionsFetchInFlight = false;
                    progress.setVisibility(View.GONE);
                    status.setVisibility(View.GONE);
                    if (result == null || result.isEmpty()) {
                        return;
                    }
                    saveSections(result);
                    if (dialog.isShowing()) {
                        renderSections(list, result);
                    }
                });
            });
        } catch (RuntimeException e) {
            sectionsFetchInFlight = false;
            progress.setVisibility(View.GONE);
            status.setVisibility(View.GONE);
        }
    }

    private static List<Section> requestSections() throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(FEED_URL).openConnection();
            connection.setConnectTimeout(9000);
            connection.setReadTimeout(12000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty(
                "User-Agent",
                "ElObservadorCubano/1.0 (Android)"
            );

            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                return null;
            }

            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)
            )) {
                char[] buffer = new char[8192];
                int read;
                while ((read = reader.read(buffer)) > 0) {
                    body.append(buffer, 0, read);
                }
            }

            JSONObject feed = new JSONObject(body.toString()).optJSONObject("feed");
            if (feed == null) {
                return null;
            }
            JSONArray entries = feed.optJSONArray("entry");
            if (entries == null) {
                return null;
            }

            Map<String, Integer> counts = new HashMap<>();
            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.optJSONObject(i);
                if (entry == null) {
                    continue;
                }
                JSONArray categories = entry.optJSONArray("category");
                if (categories == null) {
                    continue;
                }
                for (int j = 0; j < categories.length(); j++) {
                    JSONObject category = categories.optJSONObject(j);
                    if (category == null) {
                        continue;
                    }
                    String term = category.optString("term", "").trim();
                    if (term.isEmpty()) {
                        continue;
                    }
                    Integer previous = counts.get(term);
                    counts.put(term, previous == null ? 1 : previous + 1);
                }
            }

            List<Section> sections = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                sections.add(new Section(entry.getKey(), entry.getValue()));
            }
            Collections.sort(sections, new Comparator<Section>() {
                @Override
                public int compare(Section a, Section b) {
                    int byCount = Integer.compare(b.count, a.count);
                    return byCount != 0
                        ? byCount
                        : String.CASE_INSENSITIVE_ORDER.compare(a.name, b.name);
                }
            });
            if (sections.size() > MAX_SECTIONS) {
                return new ArrayList<>(sections.subList(0, MAX_SECTIONS));
            }
            return sections;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private List<Section> loadCachedSections() {
        SharedPreferences prefs = getSharedPreferences(SECTIONS_PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_SECTIONS, null);
        if (raw != null) {
            try {
                JSONArray array = new JSONArray(raw);
                List<Section> sections = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    String name = item.optString("name", "").trim();
                    if (!name.isEmpty()) {
                        sections.add(new Section(name, item.optInt("count", 0)));
                    }
                }
                if (!sections.isEmpty()) {
                    return sections;
                }
            } catch (Exception ignored) {
                // Se usan las secciones por defecto.
            }
        }

        List<Section> fallback = new ArrayList<>();
        for (String[] item : DEFAULT_SECTIONS) {
            try {
                fallback.add(new Section(item[0], Integer.parseInt(item[1])));
            } catch (NumberFormatException ignored) {
                fallback.add(new Section(item[0], 0));
            }
        }
        return fallback;
    }

    private void saveSections(List<Section> sections) {
        try {
            JSONArray array = new JSONArray();
            for (Section section : sections) {
                JSONObject item = new JSONObject();
                item.put("name", section.name);
                item.put("count", section.count);
                array.put(item);
            }
            getSharedPreferences(SECTIONS_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SECTIONS, array.toString())
                .putLong(KEY_SECTIONS_AT, System.currentTimeMillis())
                .apply();
        } catch (Exception ignored) {
            // La caché es opcional.
        }
    }

    private void hideKeyboard(View view) {
        if (view == null) {
            return;
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(
            Context.INPUT_METHOD_SERVICE
        );
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private static final class Section {
        final String name;
        final int count;

        Section(String name, int count) {
            this.name = name;
            this.count = count;
        }
    }

    // ------------------------------------------------------------------
    // Visor web
    // ------------------------------------------------------------------

    private final class EocWebViewClient extends WebViewClient {

        @Override
        public WebResourceResponse shouldInterceptRequest(
            WebView view,
            WebResourceRequest request
        ) {
            if (dataSaverBlocking
                && !request.isForMainFrame()
                && isImageRequest(request)) {
                return new WebResourceResponse(
                    "image/png",
                    null,
                    new ByteArrayInputStream(TRANSPARENT_PNG)
                );
            }
            return super.shouldInterceptRequest(view, request);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            // Solo se decide la navegación del documento principal. Los iframes
            // (video incrustado, comentarios) siguen su curso normal.
            if (!request.isForMainFrame()) {
                return false;
            }
            return handleNavigation(view, request.getUrl().toString(), request.getMethod());
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return handleNavigation(view, url, "GET");
        }

        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            pageHadError = false;
            pageInfoRequest++;
            pageIsArticle = false;
            readerModeActive = false;
            currentArticleUrl = url == null ? "" : url;
            currentArticleTitle = "";
            progressBar.setProgress(0);
            if (!contentVisible) {
                showWelcome();
            }
            updateArticleActions();
            updateNavState();
        }

        @Override
        public void onPageCommitVisible(WebView view, String url) {
            super.onPageCommitVisible(view, url);
            if (!pageHadError) {
                contentVisible = true;
                hideState();
                stopSplash();
                updateOfflineUi();
                updateDataSaverUi();
                if (UrlPolicy.isInternal(url)) {
                    view.evaluateJavascript(
                        ArticleScripts.applyTheme(AppPrefs.isDarkMode(MainActivity.this)),
                        null
                    );
                }
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            swipeRefresh.setRefreshing(false);
            progressBar.setProgress(0);
            currentTitle = view.getTitle() != null ? view.getTitle() : "";
            updateNavState();

            if (pageHadError) {
                pageIsArticle = false;
                updateArticleActions();
                showError(!checkOnline());
                return;
            }

            contentVisible = true;
            hideState();
            stopSplash();
            updateOfflineUi();
            updateDataSaverUi();

            if (UrlPolicy.isInternal(url)) {
                view.evaluateJavascript(LINK_TARGET_FIX_JS, null);
                view.evaluateJavascript(ArticleScripts.BASE_CSS_JS, null);
                view.evaluateJavascript(
                    ArticleScripts.applyTheme(AppPrefs.isDarkMode(MainActivity.this)),
                    null
                );
                detectPageInfo(view, url);
            } else {
                pageIsArticle = false;
            }
            updateArticleActions();
        }

        @Override
        public void onReceivedError(
            WebView view,
            WebResourceRequest request,
            WebResourceError error
        ) {
            super.onReceivedError(view, request, error);
            if (!request.isForMainFrame()) {
                return;
            }
            // Algunas redirecciones y cancelaciones llegan como ERROR_UNKNOWN.
            if (error.getErrorCode() == ERROR_UNKNOWN) {
                return;
            }
            pageHadError = true;
            pageIsArticle = false;
            readerModeActive = false;
            swipeRefresh.setRefreshing(false);
            updateArticleActions();
            showError(!checkOnline());
        }

        @Override
        public void onReceivedHttpError(
            WebView view,
            WebResourceRequest request,
            WebResourceResponse errorResponse
        ) {
            super.onReceivedHttpError(view, request, errorResponse);
            if (request.isForMainFrame() && errorResponse.getStatusCode() >= 400) {
                pageHadError = true;
                pageIsArticle = false;
                readerModeActive = false;
                swipeRefresh.setRefreshing(false);
                updateArticleActions();
                showError(!checkOnline());
            }
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, android.net.http.SslError error) {
            // Nunca se continúa ante un certificado no verificable.
            handler.cancel();
            pageHadError = true;
            pageIsArticle = false;
            readerModeActive = false;
            swipeRefresh.setRefreshing(false);
            updateArticleActions();
            showSecurityError();
        }

        @Override
        public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
            super.doUpdateVisitedHistory(view, url, isReload);
            updateNavState();
        }

        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            if (!rendererRecreated) {
                rendererRecreated = true;
                Toast.makeText(MainActivity.this, R.string.renderer_restart, Toast.LENGTH_SHORT).show();
                recreate();
                return true;
            }
            return false;
        }
    }

    private final class EocChromeClient extends WebChromeClient {

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            super.onProgressChanged(view, newProgress);
            if (newProgress >= 100) {
                progressBar.setProgress(0);
            } else {
                progressBar.setProgress(newProgress);
            }
        }

        @Override
        public void onReceivedTitle(WebView view, String title) {
            super.onReceivedTitle(view, title);
            if (!TextUtils.isEmpty(title)) {
                currentTitle = title;
            }
        }

        @Override
        public boolean onCreateWindow(
            WebView view,
            boolean isDialog,
            boolean isUserGesture,
            Message resultMsg
        ) {
            if (resultMsg == null || !(resultMsg.obj instanceof WebView.WebViewTransport)) {
                return false;
            }

            final WebView popup = new WebView(MainActivity.this);
            popup.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView popupView, WebResourceRequest request) {
                    String url = request.getUrl().toString();
                    UrlPolicy.Action action = UrlPolicy.classify(url);
                    if (action == UrlPolicy.Action.INTERNAL) {
                        String normalized = UrlPolicy.normalize(url);
                        if (normalized != null) {
                            webView.loadUrl(normalized);
                        }
                    } else {
                        openExternal(url, action);
                    }
                    popupView.post(popupView::destroy);
                    return true;
                }
            });

            popup.postDelayed(popup::destroy, POPUP_TIMEOUT_MS);

            WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
            transport.setWebView(popup);
            resultMsg.sendToTarget();
            return true;
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (fullscreenView != null) {
                callback.onCustomViewHidden();
                return;
            }

            fullscreenView = view;
            customViewCallback = callback;

            FrameLayout container = new FrameLayout(MainActivity.this);
            container.setBackgroundColor(Color.BLACK);
            if (view.getParent() instanceof ViewGroup) {
                ((ViewGroup) view.getParent()).removeView(view);
            }
            container.addView(
                view,
                new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            );

            ViewGroup decor = (ViewGroup) getWindow().getDecorView();
            decor.addView(
                container,
                new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            );
            fullscreenContainer = container;

            appBar.setVisibility(View.GONE);
            bottomBar.setVisibility(View.GONE);
            splashOverlay.setVisibility(View.GONE);

            WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                getWindow(),
                getWindow().getDecorView()
            );
            if (controller != null) {
                controller.hide(WindowInsetsCompat.Type.systemBars());
                controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            }
        }

        @Override
        public void onHideCustomView() {
            hideCustomView();
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            request.deny();
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(
            String origin,
            GeolocationPermissions.Callback callback
        ) {
            callback.invoke(origin, false, false);
        }
    }

    private void hideCustomView() {
        if (fullscreenView == null) {
            return;
        }

        if (fullscreenContainer != null) {
            fullscreenContainer.removeAllViews();
            ViewGroup parent = (ViewGroup) fullscreenContainer.getParent();
            if (parent != null) {
                parent.removeView(fullscreenContainer);
            }
            fullscreenContainer = null;
        }

        fullscreenView = null;
        appBar.setVisibility(View.VISIBLE);
        bottomBar.setVisibility(View.VISIBLE);

        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
            getWindow(),
            getWindow().getDecorView()
        );
        if (controller != null) {
            controller.show(WindowInsetsCompat.Type.systemBars());
        }

        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
            customViewCallback = null;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String url = intent != null ? intent.getStringExtra(EXTRA_ARTICLE_URL) : null;
        if (url != null && UrlPolicy.classify(url) == UrlPolicy.Action.INTERNAL) {
            String normalized = UrlPolicy.normalize(url);
            if (normalized != null) {
                loadUrl(normalized, false);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
        isOnline = checkOnline();
        cellularNetwork = checkCellular();
        updateOfflineUi();
        updateDataSaverUi();
        applyPreferences();

        if (updateManager != null) {
            runUpdateCheck(false);
            root.removeCallbacks(periodicUpdateCheck);
            root.postDelayed(periodicUpdateCheck, UpdatePolicy.CHECK_INTERVAL_MS);
        }
    }

    @Override
    protected void onPause() {
        root.removeCallbacks(periodicUpdateCheck);
        if (webView != null) {
            webView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        root.removeCallbacks(splashFallback);
        root.removeCallbacks(periodicUpdateCheck);

        if (updateManager != null) {
            updateManager.shutdown();
            updateManager = null;
        }

        if (sectionsDialog != null) {
            sectionsDialog.dismiss();
            sectionsDialog = null;
        }
        if (searchDialog != null) {
            searchDialog.dismiss();
            searchDialog = null;
        }
        if (savedDialog != null) {
            savedDialog.dismiss();
            savedDialog = null;
        }
        dismissSavingDialog();
        savedStore = null;
        if (ioExecutor != null) {
            ioExecutor.shutdownNow();
            ioExecutor = null;
        }

        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (IllegalArgumentException ignored) {
                // El callback ya no estaba registrado.
            }
            networkCallback = null;
        }

        if (webView != null) {
            ViewGroup parent = (ViewGroup) webView.getParent();
            if (parent != null) {
                parent.removeView(webView);
            }
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }

        super.onDestroy();
    }
}
