package cu.observadorcubano.app;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Búsqueda en el archivo del blog.
 *
 * Consulta el feed público y muestra los resultados en la app, sin cargar la
 * página del buscador de Blogger. Al tocar un resultado se abre el artículo en
 * la WebView principal, con el historial y el lector ya disponibles.
 */
public class SearchActivity extends AppCompatActivity {

    static final String EXTRA_QUERY = "query";

    private static final long DEBOUNCE_MS = 450L;
    private static final int MIN_CHARS = 2;
    private static final String STATE_QUERY = "state_query";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable debounce = () -> startSearch(currentInput());

    private ExecutorService executor;
    private int requestSeq = 0;
    private String currentQuery = "";

    private View appBar;
    private ScrollView scroll;
    private EditText input;
    private ImageButton clear;
    private ProgressBar progress;
    private Button retry;
    private TextView statusTitle;
    private TextView statusMessage;
    private TextView countView;
    private LinearLayout results;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        executor = Executors.newSingleThreadExecutor();
        bindViews();
        configureSystemBars();
        configureInsets();

        findViewById(R.id.searchBack).setOnClickListener(v -> finish());
        retry.setOnClickListener(v -> startSearch(currentQuery));
        clear.setOnClickListener(v -> {
            input.setText("");
            input.requestFocus();
        });

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Sin acción.
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Sin acción.
            }

            @Override
            public void afterTextChanged(Editable s) {
                onQueryChanged(s == null ? "" : s.toString());
            }
        });

        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard();
                startSearch(currentInput());
                return true;
            }
            return false;
        });

        String initial = savedInstanceState != null
            ? savedInstanceState.getString(STATE_QUERY, null)
            : getIntent().getStringExtra(EXTRA_QUERY);
        if (initial != null && !initial.trim().isEmpty()) {
            input.setText(initial);
            input.setSelection(input.getText().length());
            input.clearFocus();
            hideKeyboard();
            handler.removeCallbacks(debounce);
            startSearch(initial);
        } else {
            showIntro();
            input.requestFocus();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_QUERY, currentQuery);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(debounce);
        if (executor != null) {
            executor.shutdownNow();
        }
        super.onDestroy();
    }

    private void bindViews() {
        appBar = findViewById(R.id.searchAppBar);
        scroll = findViewById(R.id.searchScroll);
        input = findViewById(R.id.searchField);
        clear = findViewById(R.id.searchFieldClear);
        progress = findViewById(R.id.searchProgress);
        retry = findViewById(R.id.searchRetry);
        statusTitle = findViewById(R.id.searchStatusTitle);
        statusMessage = findViewById(R.id.searchStatusMessage);
        countView = findViewById(R.id.searchCount);
        results = findViewById(R.id.searchResults);
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
        View root = findViewById(R.id.searchRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            appBar.setPadding(bars.left, bars.top, bars.right, 0);
            scroll.setPadding(0, 0, 0, Math.max(bars.bottom, ime.bottom));
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void onQueryChanged(String text) {
        clear.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        handler.removeCallbacks(debounce);

        String query = text.trim();
        if (query.isEmpty()) {
            cancelPending();
            showIntro();
            return;
        }
        if (query.length() < MIN_CHARS) {
            cancelPending();
            showTooShort();
            return;
        }
        handler.postDelayed(debounce, DEBOUNCE_MS);
    }

    private String currentInput() {
        CharSequence value = input.getText();
        return value == null ? "" : value.toString();
    }

    /** Invalida cualquier respuesta en camino y detiene el estado de carga. */
    private void cancelPending() {
        requestSeq++;
        progress.setVisibility(View.GONE);
    }

    private void startSearch(String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.length() < MIN_CHARS) {
            showTooShort();
            return;
        }

        handler.removeCallbacks(debounce);
        currentQuery = query;
        final int seq = ++requestSeq;

        if (!isOnline()) {
            showOffline();
            return;
        }

        showLoading();
        executor.execute(() -> {
            List<SearchFeed.Result> found = null;
            boolean failed = false;
            try {
                found = SearchFeed.search(query);
            } catch (IOException | RuntimeException e) {
                failed = true;
            }

            final List<SearchFeed.Result> value = found;
            final boolean error = failed;
            runOnUiThread(() -> {
                if (seq != requestSeq || isFinishing() || isDestroyed()) {
                    return;
                }
                if (error) {
                    showError();
                } else if (value == null || value.isEmpty()) {
                    showNoResults(query);
                } else {
                    showResults(value);
                }
            });
        });
    }

    private void showIntro() {
        currentQuery = "";
        resetState();
        statusTitle.setText(R.string.search_intro_title);
        statusMessage.setText(R.string.search_intro_message);
    }

    private void showTooShort() {
        resetState();
        statusTitle.setText(R.string.search_intro_title);
        statusMessage.setText(R.string.search_too_short);
    }

    private void showLoading() {
        resetState();
        statusTitle.setText(R.string.search_loading);
        statusMessage.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
    }

    private void showOffline() {
        resetState();
        statusTitle.setText(R.string.search_error_title);
        statusMessage.setText(R.string.search_offline_message);
        retry.setVisibility(View.VISIBLE);
    }

    private void showError() {
        resetState();
        statusTitle.setText(R.string.search_error_title);
        statusMessage.setText(R.string.search_error_message);
        retry.setVisibility(View.VISIBLE);
    }

    private void showNoResults(String query) {
        resetState();
        statusTitle.setText(R.string.search_no_results_title);
        statusMessage.setText(getString(R.string.search_no_results_message, query));
    }

    private void showResults(List<SearchFeed.Result> items) {
        resetState();
        statusTitle.setVisibility(View.GONE);
        statusMessage.setVisibility(View.GONE);
        countView.setText(
            getResources().getQuantityString(
                R.plurals.search_result_count,
                items.size(),
                items.size()
            )
        );
        countView.setVisibility(View.VISIBLE);
        results.setVisibility(View.VISIBLE);
        for (SearchFeed.Result item : items) {
            results.addView(createRow(item));
        }
    }

    private void resetState() {
        results.removeAllViews();
        results.setVisibility(View.GONE);
        countView.setVisibility(View.GONE);
        progress.setVisibility(View.GONE);
        retry.setVisibility(View.GONE);
        statusTitle.setVisibility(View.VISIBLE);
        statusMessage.setVisibility(View.VISIBLE);
        scroll.scrollTo(0, 0);
    }

    private View createRow(SearchFeed.Result item) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundResource(R.drawable.bg_saved_row);
        row.setClickable(true);
        row.setFocusable(true);
        row.setPadding(dp(16), dp(14), dp(16), dp(15));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rowParams.bottomMargin = dp(10);
        row.setLayoutParams(rowParams);

        TextView title = new TextView(this);
        title.setText(
            item.title.isEmpty() ? getString(R.string.search_untitled) : item.title
        );
        title.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        title.setTextSize(16f);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
        title.setMaxLines(3);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setLineSpacing(dp(2), 1f);
        row.addView(title);

        String date = SearchFeed.formatPublished(item.publishedAt);
        if (!date.isEmpty()) {
            TextView meta = new TextView(this);
            meta.setText(date);
            meta.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            meta.setTextSize(12.5f);
            LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            metaParams.topMargin = dp(5);
            row.addView(meta, metaParams);
        }

        if (!item.snippet.isEmpty()) {
            TextView snippet = new TextView(this);
            snippet.setText(item.snippet);
            snippet.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            snippet.setTextSize(13.5f);
            snippet.setMaxLines(3);
            snippet.setEllipsize(TextUtils.TruncateAt.END);
            snippet.setLineSpacing(dp(2), 1f);
            LinearLayout.LayoutParams snippetParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            snippetParams.topMargin = dp(7);
            row.addView(snippet, snippetParams);
        }

        row.setOnClickListener(v -> openArticle(item));
        return row;
    }

    private void openArticle(SearchFeed.Result item) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_ARTICLE_URL, item.url);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private boolean isOnline() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(
            Context.CONNECTIVITY_SERVICE
        );
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

    private void hideKeyboard() {
        InputMethodManager manager = (InputMethodManager) getSystemService(
            Context.INPUT_METHOD_SERVICE
        );
        if (manager != null) {
            manager.hideSoftInputFromWindow(input.getWindowToken(), 0);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
