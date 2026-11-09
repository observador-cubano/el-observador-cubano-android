package cu.observadorcubano.app;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lista de artículos guardados para leer sin conexión.
 *
 * La metadata se lee de forma inmediata; el borrado se hace en un hilo de
 * fondo porque incluye archivos locales.
 */
public class SavedArticlesActivity extends AppCompatActivity {

    private SavedArticlesStore store;
    private ExecutorService ioExecutor;

    private View appBar;
    private View scroll;
    private LinearLayout list;
    private LinearLayout empty;
    private TextView countView;
    private Button emptyAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_saved);

        store = new SavedArticlesStore(this);
        ioExecutor = Executors.newSingleThreadExecutor();

        bindViews();
        configureSystemBars();
        configureInsets();

        findViewById(R.id.savedBack).setOnClickListener(v -> finish());
        emptyAction.setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    @Override
    protected void onDestroy() {
        if (ioExecutor != null) {
            ioExecutor.shutdownNow();
        }
        super.onDestroy();
    }

    private void bindViews() {
        appBar = findViewById(R.id.savedAppBar);
        scroll = findViewById(R.id.savedScroll);
        list = findViewById(R.id.savedList);
        empty = findViewById(R.id.savedEmpty);
        countView = findViewById(R.id.savedCount);
        emptyAction = findViewById(R.id.savedEmptyAction);
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
        View root = findViewById(R.id.savedRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            appBar.setPadding(bars.left, bars.top, bars.right, 0);
            scroll.setPadding(0, 0, 0, bars.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void reload() {
        List<SavedArticle> items = store.list();
        list.removeAllViews();

        int count = items.size();
        countView.setText(
            count > 0
                ? getResources().getQuantityString(R.plurals.saved_count, count, count)
                : getString(R.string.saved_activity_subtitle)
        );

        if (items.isEmpty()) {
            empty.setVisibility(View.VISIBLE);
            list.setVisibility(View.GONE);
            return;
        }

        empty.setVisibility(View.GONE);
        list.setVisibility(View.VISIBLE);
        for (SavedArticle article : items) {
            list.addView(createRow(article));
        }
    }

    private View createRow(SavedArticle article) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_saved_row);
        row.setClickable(true);
        row.setFocusable(true);
        row.setPadding(dp(16), dp(14), dp(6), dp(14));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rowParams.bottomMargin = dp(10);
        row.setLayoutParams(rowParams);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textsParams = new LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        );
        row.addView(texts, textsParams);

        TextView title = new TextView(this);
        title.setText(
            article.title.isEmpty() ? getString(R.string.saved_activity_title) : article.title
        );
        title.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        title.setTextSize(16f);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
        title.setMaxLines(3);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setLineSpacing(dp(2), 1f);
        texts.addView(title);

        TextView meta = new TextView(this);
        String date = SavedArticleRenderer.formatSavedDate(article.savedAt);
        meta.setText(getString(R.string.saved_row_date, date));
        meta.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        meta.setTextSize(12.5f);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        metaParams.topMargin = dp(5);
        texts.addView(meta, metaParams);

        ImageButton delete = new ImageButton(this);
        delete.setImageResource(R.drawable.ic_delete);
        delete.setImageTintList(
            android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.text_tertiary)
            )
        );
        delete.setBackgroundResource(android.R.drawable.list_selector_background);
        delete.setPadding(dp(12), dp(12), dp(12), dp(12));
        delete.setContentDescription(getString(R.string.cd_saved_delete));
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
            dp(44),
            dp(44)
        );
        deleteParams.leftMargin = dp(6);
        row.addView(delete, deleteParams);

        row.setOnClickListener(v -> openArticle(article));
        delete.setOnClickListener(v -> confirmDelete(article));
        return row;
    }

    private void openArticle(SavedArticle article) {
        Intent intent = new Intent(this, SavedArticleActivity.class);
        intent.putExtra(SavedArticleActivity.EXTRA_ID, article.id);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void confirmDelete(SavedArticle article) {
        String name = article.title.isEmpty()
            ? getString(R.string.saved_activity_title)
            : article.title;
        new AlertDialog.Builder(this)
            .setTitle(R.string.saved_delete_title)
            .setMessage(getString(R.string.saved_delete_message, name))
            .setNegativeButton(R.string.saved_delete_cancel, null)
            .setPositiveButton(
                R.string.saved_delete_confirm,
                (dialog, which) -> deleteArticle(article)
            )
            .show();
    }

    private void deleteArticle(SavedArticle article) {
        final SavedArticlesStore currentStore = store;
        if (currentStore == null || ioExecutor == null || ioExecutor.isShutdown()) {
            return;
        }
        ioExecutor.execute(() -> {
            currentStore.remove(article.id);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                Toast.makeText(
                    SavedArticlesActivity.this,
                    R.string.saved_removed,
                    Toast.LENGTH_SHORT
                ).show();
                reload();
            });
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
