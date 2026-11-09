package cu.observadorcubano.app;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
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

/**
 * Historial local de artículos abiertos.
 *
 * Permite volver a un artículo y retomar la posición de lectura. La lista se
 * guarda solo en este dispositivo.
 */
public class ReadingHistoryActivity extends AppCompatActivity {

    private ReadingHistoryStore store;

    private View appBar;
    private View scroll;
    private LinearLayout list;
    private LinearLayout empty;
    private TextView countView;
    private Button emptyAction;
    private ImageButton clearButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        store = new ReadingHistoryStore(this);

        bindViews();
        configureSystemBars();
        configureInsets();

        findViewById(R.id.historyBack).setOnClickListener(v -> finish());
        emptyAction.setOnClickListener(v -> backToNewspaper());
        clearButton.setOnClickListener(v -> confirmClear());
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void bindViews() {
        appBar = findViewById(R.id.historyAppBar);
        scroll = findViewById(R.id.historyScroll);
        list = findViewById(R.id.historyList);
        empty = findViewById(R.id.historyEmpty);
        countView = findViewById(R.id.historyCount);
        emptyAction = findViewById(R.id.historyEmptyAction);
        clearButton = findViewById(R.id.historyClear);
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
        View root = findViewById(R.id.historyRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            appBar.setPadding(bars.left, bars.top, bars.right, 0);
            scroll.setPadding(0, 0, 0, bars.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void reload() {
        List<ReadingHistoryEntry> items = store.list();
        list.removeAllViews();

        int count = items.size();
        countView.setText(
            count > 0
                ? getResources().getQuantityString(R.plurals.history_count, count, count)
                : getString(R.string.history_activity_subtitle)
        );
        clearButton.setVisibility(count > 0 ? View.VISIBLE : View.GONE);

        if (items.isEmpty()) {
            empty.setVisibility(View.VISIBLE);
            list.setVisibility(View.GONE);
            return;
        }

        empty.setVisibility(View.GONE);
        list.setVisibility(View.VISIBLE);
        for (ReadingHistoryEntry entry : items) {
            list.addView(createRow(entry));
        }
    }

    private View createRow(ReadingHistoryEntry entry) {
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
            entry.title.isEmpty() ? getString(R.string.history_untitled) : entry.title
        );
        title.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        title.setTextSize(16f);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
        title.setMaxLines(3);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setLineSpacing(dp(2), 1f);
        texts.addView(title);

        TextView meta = new TextView(this);
        meta.setText(buildMeta(entry));
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
        delete.setContentDescription(getString(R.string.cd_history_delete));
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
            dp(44),
            dp(44)
        );
        deleteParams.leftMargin = dp(6);
        row.addView(delete, deleteParams);

        row.setOnClickListener(v -> openEntry(entry));
        delete.setOnClickListener(v -> confirmRemove(entry));
        return row;
    }

    private String buildMeta(ReadingHistoryEntry entry) {
        String date = SavedArticleRenderer.formatSavedDate(entry.readAt);
        int percent = ReadingProgress.percent(entry.progress);
        if (percent < 5) {
            return date;
        }
        return getString(R.string.history_row_meta, date, percent);
    }

    private void backToNewspaper() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void openEntry(ReadingHistoryEntry entry) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_ARTICLE_URL, entry.url);
        intent.putExtra(MainActivity.EXTRA_HISTORY_PROGRESS, entry.progress);
        intent.putExtra(MainActivity.EXTRA_HISTORY_READER, entry.reader);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void confirmRemove(ReadingHistoryEntry entry) {
        String name = entry.title.isEmpty()
            ? getString(R.string.history_untitled)
            : entry.title;
        new AlertDialog.Builder(this)
            .setTitle(R.string.history_activity_title)
            .setMessage(getString(R.string.history_remove_message, name))
            .setNegativeButton(R.string.saved_delete_cancel, null)
            .setPositiveButton(
                R.string.history_remove_confirm,
                (dialog, which) -> {
                    store.remove(entry.url);
                    Toast.makeText(
                        ReadingHistoryActivity.this,
                        R.string.history_removed,
                        Toast.LENGTH_SHORT
                    ).show();
                    reload();
                }
            )
            .show();
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.history_clear_title)
            .setMessage(R.string.history_clear_message)
            .setNegativeButton(R.string.saved_delete_cancel, null)
            .setPositiveButton(R.string.history_clear_confirm, (dialog, which) -> {
                store.clear();
                Toast.makeText(
                    ReadingHistoryActivity.this,
                    R.string.history_cleared,
                    Toast.LENGTH_SHORT
                ).show();
                reload();
            })
            .show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
