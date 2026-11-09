package cu.observadorcubano.app;

import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.getcapacitor.BridgeActivity;

/**
 * Pantalla «Desde nuestro canal» (Capacitor + HTML/CSS/TypeScript).
 *
 * Carga una página local distinta de la pantalla de información. El periódico
 * sigue viviendo en la WebView nativa de MainActivity, sin puentes expuestos.
 */
public class ChannelActivity extends BridgeActivity {

    private static final String CHANNEL_PAGE = "https://localhost/channel.html";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        registerPlugin(ShellPlugin.class);
        super.onCreate(savedInstanceState);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
            getWindow(),
            getDecorView()
        );
        if (controller != null) {
            controller.setAppearanceLightStatusBars(false);
            controller.setAppearanceLightNavigationBars(true);
        }

        View content = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(content, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, 0, bars.right, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(content);

        // BridgeActivity carga index.html por defecto; se sustituye por la
        // página del canal una vez creado el puente local.
        if (getBridge() != null && getBridge().getWebView() != null) {
            getBridge().getWebView().post(() -> {
                if (getBridge() != null && getBridge().getWebView() != null) {
                    getBridge().getWebView().loadUrl(CHANNEL_PAGE);
                }
            });
        }
    }

    private View getDecorView() {
        return getWindow().getDecorView();
    }
}
