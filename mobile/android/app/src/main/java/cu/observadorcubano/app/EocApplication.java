package cu.observadorcubano.app;

import android.app.Application;

/**
 * Aplica el tema claro/oscuro antes de crear las actividades para que la
 * primera pantalla ya use los recursos nocturnos correctos.
 */
public class EocApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        AppPrefs.applyNightMode(this);
    }
}
