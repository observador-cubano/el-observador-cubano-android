package cu.observadorcubano.app;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.io.ByteArrayInputStream;
import java.util.Locale;
import java.util.Map;

/**
 * Lector nativo de artículos en línea.
 *
 * Recibe el HTML ya extraído de la WebView principal y lo muestra en un
 * documento propio sin JavaScript. Solo permite imágenes HTTPS y guarda la
 * posición en el historial local para poder continuar la lectura.
 */
public class ReaderActivity extends AppCompatActivity {

    public static final String EXTRA_URL = "reader_url";
    public static final String EXTRA_TITLE = "reader_title";
    public static final String EXTRA_BYLINE = "reader_byline";
    public static final String EXTRA_HTML = "reader_html";
    public static final String EXTRA_PROGRESS = "reader_progress";

    private static final long[] RESTORE_DELAYS_MS = {0L, 400L};
    private static final String BASE_URL = "https://observadorcubano.blogspot.com/";

    private ReadingHistoryStore historyStore;

    private View appBar;
    private WebView webView;
    private View errorView;
    private TextView errorMessage;
    private Button errorAction;
    private ProgressBar progress;

    private String articleUrl = "";
    private String articleTitle = "";
    private String articleByline = "";
    private String contentHtml = "";

    private double pendingRestoreProgress = 0d;
    private double lastSavedProgress = -1d;
    private boolean renderedDark = false;
    private boolean renderedAllowImages = true;
    private int renderedZoom = 0;
    private int restoreGeneration = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reader);

        historyStore = new ReadingHistoryStore(this);

        bindViews();
        configureSystemBars();
        configureInsets();
        configureWebView();

        findViewById(R.id.readerBack).setOnClickListener(v -> finish());
        findViewById(R.id.readerShare).setOnClickListener(v -> shareArticle());
        errorAction.setOnClickListener(v -> finish());

        Intent intent = getIntent();
        String url = intent != null ? intent.getStringExtra(EXTRA_URL) : null;
        String html = intent != null ? intent.getStringExtra(EXTRA_HTML) : null;
        if (!UrlPolicy.isInternal(url) || TextUtils.isEmpty(html)) {
            Toast.makeText(this, R.string.reader_read_error, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        articleUrl = url;
        contentHtml = html;
        articleTitle = clean(intent.getStringExtra(EXTRA_TITLE));
        articleByline = clean(intent.getStringExtra(EXTRA_BYLINE));
        pendingRestoreProgress = ReadingProgress.clamp(
            intent.getDoubleExtra(EXTRA_PROGRESS, 0d)
        );

        renderDocument();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView == null || isFinishing()) {
            return;
        }
        webView.onResume();
        reloadForPreferenceChange();
    }

    @Override
    protected void onPause() {
        persistProgress();
        if (webView != null) {
            webView.onPause();
        }
        super.onPause();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        configureSystemBars();
        configureInsets();
        reloadForPreferenceChange();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private void bindViews() {
        appBar = findViewById(R.id.readerAppBar);
        webView = findViewById(R.id.readerWebView);
        errorView = findViewById(R.id.readerError);
        errorMessage = findViewById(R.id.readerErrorMessage);
        errorAction = findViewById(R.id.readerErrorAction);
        progress = findViewById(R.id.readerProgress);
    }

    private void configureSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        boolean dark = AppPrefs.isDarkMode(this);
        if (Build.VERSION.SDK_INT < 35) {
            getWindow().setNavigationBarColor(
                ContextCompat.getColor(this, R.color.surface)
            );
        }
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
            getWindow(),
            getWindow().getDecorView()
        );
        if (controller != null) {
            controller.setAppearanceLightStatusBars(false);
            controller.setAppearanceLightNavigationBars(!dark);
        }
    }

    private void configureInsets() {
        View root = findViewById(R.id.readerRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            appBar.setPadding(bars.left, bars.top, bars.right, 0);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        if (webView == null) {
            return;
        }
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(false);
        settings.setDomStorageEnabled(false);
        settings.setDatabaseEnabled(false);
        settings.setSaveFormData(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setTextZoom(AppPrefs.getTextZoom(this));
        webView.setBackgroundColor(ContextCompat.getColor(this, R.color.surface));
        webView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        webView.setWebViewClient(new ReaderWebClient());
    }

    private void renderDocument() {
        if (webView == null || isFinishing()) {
            return;
        }

        boolean dark = AppPrefs.isDarkMode(this);
        boolean images = allowImages();
        int zoom = AppPrefs.getTextZoom(this);
        renderedDark = dark;
        renderedAllowImages = images;
        renderedZoom = zoom;

        webView.getSettings().setTextZoom(zoom);
        webView.getSettings().setLoadsImagesAutomatically(images);
        webView.setBackgroundColor(ContextCompat.getColor(this, R.color.surface));

        String html;
        try {
            html = SavedArticleRenderer.renderOnline(
                articleTitle,
                articleByline,
                articleUrl,
                contentHtml,
                dark,
                images
            );
        } catch (RuntimeException e) {
            showError();
            return;
        }

        progress.setVisibility(View.VISIBLE);
        webView.setVisibility(View.GONE);
        errorView.setVisibility(View.GONE);
        webView.loadDataWithBaseURL(BASE_URL, html, "text/html", "UTF-8", null);
    }

    private void reloadForPreferenceChange() {
        if (webView == null || isFinishing()) {
            return;
        }
        boolean dark = AppPrefs.isDarkMode(this);
        boolean images = allowImages();
        int zoom = AppPrefs.getTextZoom(this);
        if (dark == renderedDark && images == renderedAllowImages && zoom == renderedZoom) {
            return;
        }
        lastSavedProgress = currentProgress();
        pendingRestoreProgress = lastSavedProgress;
        renderDocument();
    }

    private void scheduleRestore() {
        if (webView == null) {
            return;
        }
        final int generation = ++restoreGeneration;
        final double target = ReadingProgress.clamp(pendingRestoreProgress);
        for (long delay : RESTORE_DELAYS_MS) {
            webView.postDelayed(() -> {
                if (generation != restoreGeneration || webView == null) {
                    return;
                }
                applyScroll(target);
            }, delay);
        }
    }

    private void applyScroll(double target) {
        int max = scrollRange();
        if (max <= 0) {
            return;
        }
        webView.scrollTo(0, (int) Math.round(max * ReadingProgress.clamp(target)));
    }

    private int scrollRange() {
        if (webView == null) {
            return 0;
        }
        return Math.max(
            0,
            Math.round(webView.getContentHeight() * webView.getScale()) - webView.getHeight()
        );
    }

    private double currentProgress() {
        int max = scrollRange();
        if (max <= 0 || webView == null) {
            return 0d;
        }
        return ReadingProgress.clamp(webView.getScrollY() / (double) max);
    }

    private void persistProgress() {
        if (historyStore == null || webView == null || TextUtils.isEmpty(articleUrl)) {
            return;
        }
        double progress = currentProgress();
        lastSavedProgress = progress;
        historyStore.saveProgress(articleUrl, articleTitle, progress, true);
    }

    private boolean allowImages() {
        return !(AppPrefs.isDataSaverEnabled(this) && isCellular());
    }

    private boolean isCellular() {
        ConnectivityManager manager =
            (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
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

    private void showError() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        progress.setVisibility(View.GONE);
        webView.setVisibility(View.GONE);
        errorView.setVisibility(View.VISIBLE);
        errorMessage.setText(R.string.reader_read_error);
    }

    private void shareArticle() {
        if (TextUtils.isEmpty(articleUrl)) {
            return;
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        String title = TextUtils.isEmpty(articleTitle)
            ? getString(R.string.app_name)
            : articleTitle;
        send.putExtra(Intent.EXTRA_SUBJECT, title);
        send.putExtra(Intent.EXTRA_TEXT, title + "\n" + articleUrl);
        try {
            startActivity(Intent.createChooser(send, getString(R.string.nav_share)));
        } catch (ActivityNotFoundException ignored) {
            // Sin aplicaciones que puedan compartir.
        }
    }

    private void openExternal(String url) {
        try {
            CustomTabsIntent launcher = new CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build();
            launcher.launchUrl(this, Uri.parse(url));
        } catch (ActivityNotFoundException ignored) {
            Toast.makeText(this, R.string.no_app, Toast.LENGTH_SHORT).show();
        }
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private boolean isImageRequest(WebResourceRequest request) {
        Map<String, String> headers = request.getRequestHeaders();
        if (headers != null) {
            String accept = headers.get("Accept");
            if (accept != null && accept.toLowerCase(Locale.ROOT).contains("image")) {
                return true;
            }
            String destination = headers.get("Sec-Fetch-Dest");
            if ("image".equalsIgnoreCase(destination)) {
                return true;
            }
        }
        String path = request.getUrl().getPath();
        return path != null && path.toLowerCase(Locale.ROOT).matches(
            ".*\\.(png|jpe?g|gif|webp|bmp|avif|svg)$"
        );
    }

    private static WebResourceResponse blockedResponse() {
        return new WebResourceResponse(
            "text/plain",
            "utf-8",
            new ByteArrayInputStream(new byte[0])
        );
    }

    private final class ReaderWebClient extends WebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return handleLink(request.getUrl() != null ? request.getUrl().toString() : null);
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return handleLink(url);
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(
            WebView view,
            WebResourceRequest request
        ) {
            Uri uri = request.getUrl();
            if (uri == null || uri.getScheme() == null) {
                return blockedResponse();
            }
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            if ("about".equals(scheme) || "data".equals(scheme)) {
                return null;
            }
            // El documento es local: solo se permite descargar imágenes HTTPS.
            if ("https".equals(scheme) && isImageRequest(request)) {
                return null;
            }
            return blockedResponse();
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            if (isFinishing() || isDestroyed()) {
                return;
            }
            progress.setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);
            if (historyStore != null && !TextUtils.isEmpty(articleUrl)) {
                historyStore.saveProgress(
                    articleUrl,
                    articleTitle,
                    ReadingProgress.clamp(pendingRestoreProgress),
                    true
                );
            }
            scheduleRestore();
        }

        private boolean handleLink(String raw) {
            if (raw == null) {
                return true;
            }
            Uri uri = Uri.parse(raw);
            String scheme = uri.getScheme();
            if (scheme == null) {
                return true;
            }
            scheme = scheme.toLowerCase(Locale.ROOT);
            if ("about".equals(scheme)) {
                return false;
            }
            if (!"https".equals(scheme)) {
                Toast.makeText(ReaderActivity.this, R.string.unsafe_link, Toast.LENGTH_SHORT).show();
                return true;
            }
            String safe = UrlPolicy.normalize(raw);
            if (safe == null || UrlPolicy.classify(safe) == UrlPolicy.Action.BLOCK) {
                Toast.makeText(ReaderActivity.this, R.string.unsafe_link, Toast.LENGTH_SHORT).show();
                return true;
            }
            if (UrlPolicy.isInternal(safe)) {
                Intent intent = new Intent(ReaderActivity.this, MainActivity.class);
                intent.putExtra(MainActivity.EXTRA_ARTICLE_URL, safe);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
                return true;
            }
            openExternal(safe);
            return true;
        }
    }
}
