# Sistema de actualizaciones in-app

La aplicación comprueba las **Releases estables** publicadas en el repositorio
público de GitHub (`observador-cubano/el-observador-cubano-android`) y ofrece la instalación del APK
verificado. No usa tokens, no instala en silencio y no permite versiones anteriores.

## 1. Componentes

| Clase | Responsabilidad |
| --- | --- |
| `SemVer` | Interpreta y compara `MAJOR.MINOR.PATCH`; descarta preestrenos. |
| `ReleaseInfo` | Convierte la respuesta de GitHub en datos seguros y notas de texto plano. |
| `UpdatePolicy` | Caché de 6 h, reintento de 15 min tras error y aviso único por versión. |
| `UpdateClient` | Consulta anónima a la API, historial y descarga con límites de tamaño. |
| `UpdateStore` | Persiste última comprobación, última Release, aviso enviado y «Más tarde». |
| `UpdateManager` | Orquesta comprobación, descarga, SHA-256 y verificación final. |
| `ApkVerifier` | Compara paquete, `versionCode`, `versionName` y firma con la app instalada. |
| `ApkInstaller` | Abre el instalador de Android o el ajuste de apps desconocidas. |
| `UpdateNotifier` | Canal `eoc_updates`, permiso Android 13+ y notificación única. |
| `UpdateActivity` | Pantalla Novedades: versión, notas reales, botones, progreso e historial. |

## 2. Flujo

```
Inicio / volver a la app / cada 6 h en primer plano
        │
        ▼
GET /repos/observador-cubano/el-observador-cubano-android/releases/latest   (anónimo, TLS)
        │
        ├── sin red o error → se conserva la última Release conocida
        │                     y se reintenta en 15 min
        ▼
Versión estable > versión instalada  y  APK + SHA-256 publicados
        │
        ├── aviso discreto en la cabecera
        ├── notificación local (una por versión; Android 13+ pide permiso)
        ▼
Pantalla Novedades → «Actualizar ahora»
        │
        ▼
Descarga APK → SHA-256 → paquete → versionCode/versionName → firma
        │
        ├── cualquier fallo → se borra el archivo y se muestra el motivo
        ▼
Instalador de Android (confirmación del usuario)
```

### Frecuencia

- Al iniciar la app (1,8 s después de abrir, sin bloquear la interfaz).
- Al volver a primer plano.
- Cada 6 horas mientras la app está abierta (temporizador en primer plano).
- Manualmente desde **Info → Actualizaciones** o desde el aviso.

No se ejecuta en segundo plano ni con la app cerrada. La caché evita más de
una consulta cada 6 horas; tras un error se reintenta a los 15 minutos.

### Notificaciones y «Más tarde»

- Solo se avisa de versiones **estables** más recientes que la instalada.
- Como máximo una notificación por versión.
- «Más tarde» guarda esa versión como descartada: no vuelve a mostrarse el
  aviso ni la notificación para ella. Una versión posterior sí se avisa.
- En Android 13+ el permiso `POST_NOTIFICATIONS` se solicita únicamente cuando
  existe una actualización disponible.

### Verificación antes de instalar

1. **SHA-256** publicado en el activo `.apk.sha256` de la Release.
2. **Paquete** `cu.observadorcubano.app` idéntico al instalado.
3. **`versionCode`** estrictamente mayor (sin downgrades).
4. **`versionName`** del APK igual a la versión de la Release y mayor que la
   instalada.
5. **Firma**: el certificado del APK debe coincidir con el de la app instalada.
6. **Tamaño** entre 1 byte y 200 MB; el archivo debe existir y no estar vacío.

Si algo falla, el APK se elimina y no se abre el instalador.

### Privacidad y seguridad

- Consultas anónimas a `api.github.com`; nunca se incluye un token de GitHub.
- Solo se aceptan activos alojados en `github.com` (y sus redirecciones
  oficiales de descargas).
- Las notas se muestran como texto plano: no se interpreta HTML ni JavaScript.
- La instalación siempre requiere confirmación en el instalador del sistema.
- La app no solicita permisos de almacenamiento ni de instalación silenciosa.

## 3. Interfaz

- **Aviso en la cabecera**: «Nueva versión X disponible» con «Ver» y cerrar.
- **Pantalla Novedades**: logotipo oficial, versión instalada, última versión,
  fecha, notas reales, botón «Actualizar a la versión X», «Más tarde», «Ver en
  GitHub», progreso de descarga, errores e historial de las 10 últimas
  Releases estables.
- Si la Release no tiene descripción, se indica expresamente; no se inventan
  novedades.
- Si no hay conexión o la API falla, se muestra la última información guardada
  y un aviso; la app sigue funcionando.

## 4. Historias de prueba obligatorias

La columna **Estado** distingue lo comprobado con pruebas automáticas, lo
comprobado en dispositivo y lo que requiere una Release real publicada.

| # | Escenario | Cómo se comprueba | Estado |
| --- | --- | --- | --- |
| 1 | Sin actualización: instalada = última | `UpdatePolicy`/`SemVer` y pantalla «Tienes la última versión» | **Aprobado**: 5 pruebas automáticas + pantalla real en 1.1.0 y 1.1.1 |
| 2 | Actualización disponible: aviso y notificación una sola vez | `UpdatePolicy.shouldNotify` + aviso en cabecera | **Aprobado**: aviso y notificación `eoc_updates` observados en dispositivo; aviso único por versión cubierto por pruebas |
| 3 | Preestreno ignorado | `ReleaseInfo` descarta `prerelease` y `SemVer` rechaza sufijos | **Aprobado** (automatizado) |
| 4 | Caché de 6 h y reintento de 15 min | `UpdatePolicyTest` | **Aprobado** (automatizado) |
| 5 | Sin conexión: error controlado sin bloquear | `UpdateManager` conserva caché; pantalla muestra aviso | **Aprobado en dispositivo** (modo avión) tras corregir en 1.1.1 el mensaje técnico en inglés |
| 6 | Notas reales de la Release, sin inventar | `ReleaseInfo.cleanNotes` + pantalla | **Aprobado**: notas reales de v1.1.0 y v1.1.1 mostradas en dispositivo |
| 7 | Descarga con progreso y SHA-256 correcto | `UpdateManager` + descarga real | **Aprobado**: progreso visible y «Descarga verificada» en descargas reales de v1.1.0 y v1.1.1 |
| 8 | SHA-256 incorrecto: se rechaza | `UpdateVerifierTest` (CHECKSUM_MISMATCH) | **Aprobado** (automatizado) |
| 9 | Paquete equivocado o downgrade: se rechaza | `UpdateVerifierTest` | **Aprobado** (automatizado) |
| 10 | Firma distinta: se rechaza | `UpdateVerifierTest` + `ApkVerifier` | **Aprobado** (lógica automatizada). No se publicó una Release con firma distinta para no distribuir un APK inválido |
| 11 | Instalador de Android con autorización y app actualizada | Instalación real v1.0.0 → v1.1.0 y v1.0.0 → v1.1.1 | **Aprobado en dispositivo**: instalador del sistema, confirmación y `versionName`/`versionCode` actualizados |
| 12 | Historial real de versiones y «Más tarde» | `UpdateClient.fetchHistory` + `UpdateStore` | **Aprobado en dispositivo**: historial con 1.1.1 y 1.1.0; «Más tarde» oculta el aviso y sigue oculto tras reiniciar |

Pruebas unitarias: **43 en verde** (7 clases, incluidas las nuevas
`UpdateManagerTest`). Las pruebas manuales se ejecutaron en el dispositivo
`R58RA2CXZGR` (Samsung SM-A125U, Android 12) contra las Releases reales
`v1.1.0` y `v1.1.1`.

### Google Play Protect

Google Play Protect puede marcar la app como «dañina» al instalar una versión
descargada fuera de Play, porque la aplicación puede instalar sus propias
actualizaciones. En la prueba real apareció «Se bloqueó la app dañina» y fue
necesario pulsar **Más detalles → Instalar de todas formas**. Esto no significa
que falle la verificación de la app: el SHA-256, el paquete y la firma ya se
comprobaron antes de abrir el instalador.

Desde 1.1.1 la pantalla Novedades muestra ese aviso antes de actualizar. Play
Protect también puede bloquear instalaciones por ADB; para usuarios finales la
vía prevista es el actualizador interno y, si aparece el aviso, «Instalar de
todas formas».

## 5. Pruebas manuales en dispositivo

```powershell
# Compilar e instalar la versión en pruebas
cd mobile
npm run build
npx cap sync android
cd android
.\gradlew.bat :app:assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

1. Abrir la app y confirmar que la portada carga.
2. Ir a **Info → Buscar actualizaciones**.
3. Con una Release más reciente publicada, comprobar versión, fecha y notas.
4. Pulsar «Actualizar ahora»; aceptar «Instalar apps desconocidas» si Android lo pide.
5. Verificar el progreso, la verificación y el instalador del sistema.
6. Confirmar que la versión instalada cambia y que los datos se conservan.
7. Pulsar «Más tarde» en otra versión y comprobar que no reaparece el aviso.

## 6. Diagnóstico

- «El repositorio de actualizaciones no está configurado»: la compilación se
  hizo con `eocGithubOwner=PENDIENTE`. Configura `mobile/android/gradle.properties`
  o pasa `-PeocGithubOwner=... -PeocGithubRepo=...`.
- «Todavía no hay versiones publicadas»: el repositorio aún no tiene Releases
  estables con etiqueta `vX.Y.Z`.
- «GitHub limitó temporalmente las consultas»: límite de la API anónima;
  esperar o reducir la frecuencia.
- «La firma del APK no coincide»: la Release se firmó con otra keystore o es
  una compilación debug. Revisa los secretos de firma.
- «La Release no publica el SHA-256»: falta el activo `.apk.sha256`; el flujo
  de release lo genera automáticamente.
