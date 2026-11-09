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
 * Pantalla híbrida (Capacitor + HTML/CSS/TypeScript) con la información de la
 * aplicación y las preferencias. El periódico se muestra en la WebView nativa
 * de MainActivity, sin acceso a las APIs de Capacitor.
 */
public class InfoActivity extends BridgeActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        registerPlugin(ShellPlugin.class);
        super.onCreate(savedInstanceState);

        // El botón Atrás del sistema cierra esta pantalla y vuelve al periódico.
        // Se registra después de super.onCreate para tener prioridad sobre el
        // manejador de historial de Capacitor.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        boolean dark = AppPrefs.isDarkMode(this);
        if (android.os.Build.VERSION.SDK_INT < 35) {
            getWindow().setNavigationBarColor(
                androidx.core.content.ContextCompat.getColor(this, R.color.surface)
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

        // La página se dibuja de borde a borde; el contenido respeta la barra
        // de navegación para que ningún texto quede oculto tras ella.
        View content = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(content, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, 0, bars.right, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(content);
    }
}
