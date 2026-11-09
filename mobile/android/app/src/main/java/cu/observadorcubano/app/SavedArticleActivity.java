package cu.observadorcubano.app;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.webkit.WebResourceRequest;
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

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lector local de un artículo guardado.
 *
 * El documento se construye con {@link SavedArticleRenderer}, no ejecuta
 * JavaScript y tiene bloqueadas todas las cargas de red. Las imágenes se
 * sirven desde la carpeta privada del artículo.
 */
public class SavedArticleActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "saved_article_id";

    private SavedArticlesStore store;
    private ExecutorService ioExecutor;

    private View appBar;
    private WebView webView;
    private View errorView;
    private TextView errorMessage;
    private Button errorAction;
    private ProgressBar progress;
    private SavedArticle article;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_saved_article);

        store = new SavedArticlesStore(this);
        ioExecutor = Executors.newSingleThreadExecutor();

        bindViews();
        configureSystemBars();
        configureInsets();
        configureWebView();

        findViewById(R.id.savedArticleBack).setOnClickListener(v -> finish());
        findViewById(R.id.savedArticleShare).setOnClickListener(v -> shareArticle());
        errorAction.setOnClickListener(v -> finish());

        String id = getIntent() != null ? getIntent().getStringExtra(EXTRA_ID) : null;
        article = store.findById(id);
        if (article == null) {
            Toast.makeText(this, R.string.saved_missing, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        loadArticle(article);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        if (ioExecutor != null) {
            ioExecutor.shutdownNow();
        }
        super.onDestroy();
    }

    private void bindViews() {
        appBar = findViewById(R.id.savedArticleAppBar);
        webView = findViewById(R.id.savedArticleWebView);
        errorView = findViewById(R.id.savedArticleError);
        errorMessage = findViewById(R.id.savedArticleErrorMessage);
        errorAction = findViewById(R.id.savedArticleErrorAction);
        progress = findViewById(R.id.savedArticleProgress);
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
        View root = findViewById(R.id.savedArticleRoot);
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
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setBlockNetworkLoads(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setTextZoom(AppPrefs.getTextZoom(this));
        webView.setBackgroundColor(ContextCompat.getColor(this, R.color.surface));
        webView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        webView.setWebViewClient(new SavedArticleWebClient());
    }

    private void loadArticle(SavedArticle item) {
        progress.setVisibility(View.VISIBLE);
        webView.setVisibility(View.GONE);
        errorView.setVisibility(View.GONE);

        final SavedArticlesStore currentStore = store;
        if (currentStore == null || ioExecutor == null || ioExecutor.isShutdown()) {
            showError();
            return;
        }
        ioExecutor.execute(() -> {
            String document = null;
            try {
                String content = currentStore.readContent(item.id);
                document = SavedArticleRenderer.render(
                    item.title,
                    item.byline,
                    item.url,
                    SavedArticleRenderer.formatSavedDate(item.savedAt),
                    content,
                    AppPrefs.isDarkMode(this)
                );
            } catch (Exception ignored) {
                // Se informa abajo con un mensaje claro.
            }
            final String html = document;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (html == null) {
                    showError();
                    return;
                }
                File folder = currentStore.folderFor(item.id);
                String baseUrl = folder != null
                    ? Uri.fromFile(folder).toString() + "/"
                    : "file:///";
                webView.loadDataWithBaseURL(
                    baseUrl,
                    html,
                    "text/html",
                    "UTF-8",
                    null
                );
                progress.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
            });
        });
    }

    private void showError() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        progress.setVisibility(View.GONE);
        webView.setVisibility(View.GONE);
        errorView.setVisibility(View.VISIBLE);
        errorMessage.setText(R.string.saved_read_error);
    }

    private void shareArticle() {
        if (article == null || TextUtils.isEmpty(article.url)) {
            return;
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        String title = article.title.isEmpty() ? getString(R.string.app_name) : article.title;
        send.putExtra(Intent.EXTRA_SUBJECT, title);
        send.putExtra(Intent.EXTRA_TEXT, title + "\n" + article.url);
        try {
            startActivity(Intent.createChooser(send, getString(R.string.nav_share)));
        } catch (ActivityNotFoundException ignored) {
            // Sin aplicaciones que puedan compartir.
        }
    }

    private void openOnline(String url) {
        String normalized = UrlPolicy.normalize(url);
        if (normalized == null) {
            return;
        }
        if (UrlPolicy.isInternal(normalized)) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra(MainActivity.EXTRA_ARTICLE_URL, normalized);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
            return;
        }
        if (UrlPolicy.classify(normalized) == UrlPolicy.Action.EXTERNAL_WEB) {
            try {
                CustomTabsIntent launcher = new CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .build();
                launcher.launchUrl(this, Uri.parse(normalized));
            } catch (ActivityNotFoundException ignored) {
                // Sin navegador disponible.
            }
        }
    }

    private final class SavedArticleWebClient extends WebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return handleLink(request.getUrl() != null ? request.getUrl().toString() : null);
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return handleLink(url);
        }

        private boolean handleLink(String url) {
            if (url == null) {
                return true;
            }
            if (url.startsWith("about:")) {
                return false;
            }
            // El documento es local: nunca se navega a otros archivos del
            // dispositivo desde un enlace del artículo.
            if (url.startsWith("file:")) {
                return true;
            }
            openOnline(url);
            return true;
        }
    }
}
