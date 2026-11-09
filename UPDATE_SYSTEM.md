# Sistema de actualizaciones

La aplicación comprueba las **Releases estables** publicadas en el repositorio
público de GitHub y ofrece la instalación del APK verificado. No usa tokens, no
instala en silencio y no permite versiones anteriores.

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

## 2. Repositorio de actualizaciones

El propietario y el nombre del repositorio se fijan **en tiempo de compilación**
(`BuildConfig.GITHUB_OWNER` y `BuildConfig.GITHUB_REPO`):

```properties
# mobile/android/gradle.properties
eocGithubOwner=PROPIETARIO
eocGithubRepo=REPOSITORIO
```

Los flujos de GitHub Actions los sobrescriben con `-PeocGithubOwner` y
`-PeocGithubRepo` a partir de `GITHUB_REPOSITORY`, de modo que una compilación
publicada desde el repositorio oficial siempre apunta a sí misma.

> **Importante para traslados:** una app ya instalada sigue consultando el
> repositorio con el que fue compilada. Si el repositorio se transfiere o
> renombra, las versiones antiguas no verán las Releases nuevas (la app valida
> que las URL de descarga pertenezcan al mismo propietario/repositorio). Antes de
> trasladar el canal hay que publicar una **versión de transición** que apunte al
> destino nuevo y ofrecerla también en el origen.

## 3. Flujo

```
Inicio / volver a la app / cada 6 h en primer plano
        │
        ▼
GET https://api.github.com/repos/PROPIETARIO/REPOSITORIO/releases/latest
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
- Cada 6 horas mientras la app está abierta.
- Manualmente desde **Info → Actualizaciones** o desde el aviso.

No se ejecuta en segundo plano ni con la app cerrada. La caché evita más de una
consulta cada 6 horas; tras un error se reintenta a los 15 minutos.

### Notificaciones y «Más tarde»

- Solo se avisa de versiones **estables** más recientes que la instalada.
- Como máximo una notificación por versión.
- «Más tarde» guarda esa versión como descartada: no vuelve a mostrarse el aviso
  ni la notificación para ella. Una versión posterior sí se avisa.
- En Android 13+ el permiso `POST_NOTIFICATIONS` se solicita únicamente cuando
  existe una actualización disponible.

## 4. Verificación antes de instalar

1. **SHA-256** publicado en el activo `.apk.sha256` de la Release.
2. **Paquete** `cu.observadorcubano.app` idéntico al instalado.
3. **`versionCode`** estrictamente mayor (sin downgrades).
4. **`versionName`** del APK igual a la versión de la Release y mayor que la
   instalada.
5. **Firma**: el certificado del APK debe coincidir con el de la app instalada.
6. **Tamaño** entre 1 byte y 200 MB; el archivo debe existir y no estar vacío.

Si algo falla, el APK se elimina y no se abre el instalador.

## 5. Privacidad y seguridad

- Consultas anónimas a `api.github.com`; nunca se incluye un token de GitHub.
- Solo se aceptan activos alojados en `github.com` y en el repositorio
  configurado.
- Las notas se muestran como texto plano: no se interpreta HTML ni JavaScript.
- La instalación siempre requiere confirmación en el instalador del sistema.
- La app no solicita permisos de almacenamiento ni de instalación silenciosa.
- No hay notificaciones push ni servidor propio.

## 6. Interfaz

- **Aviso en la cabecera**: «Nueva versión X disponible» con «Ver» y cerrar.
- **Pantalla Novedades**: logotipo oficial, versión instalada, última versión,
  fecha, notas reales, botón «Actualizar a la versión X», «Más tarde», «Ver en
  GitHub», progreso de descarga, errores e historial de las 10 últimas Releases
  estables.
- Si la Release no tiene descripción, se indica expresamente; no se inventan
  novedades.
- Si no hay conexión o la API falla, se muestra la última información guardada
  y un aviso; la app sigue funcionando.

## 7. Diagnóstico

| Mensaje | Causa y solución |
| --- | --- |
| «El repositorio de actualizaciones no está configurado» | Compilación con `eocGithubOwner=PENDIENTE`. Configurar `gradle.properties` o pasar `-PeocGithubOwner=... -PeocGithubRepo=...`. |
| «Todavía no hay versiones publicadas» | El repositorio no tiene Releases estables con etiqueta `vX.Y.Z`. |
| «GitHub limitó temporalmente las consultas» | Límite de la API anónima; esperar o reducir la frecuencia. |
| «La firma del APK no coincide» | Release firmada con otra keystore o compilación debug. Revisar `ANDROID_SIGNING.md`. |
| «La Release no publica el SHA-256» | Falta el activo `.apk.sha256`; el flujo de release lo genera automáticamente. |
| «No se pudo comprobar si hay una versión nueva» | Sin conexión o API inaccesible; la app conserva el contenido y reintenta en 15 min. |
| Play Protect bloquea la instalación | Elegir «Más detalles → Instalar de todas formas»; el APK ya se verificó. |

## 8. Pruebas

La lista completa de escenarios, los resultados en dispositivo y las pruebas
unitarias están en `docs/ACTUALIZACIONES.md`.

Para probar una actualización real:

1. Instalar una versión anterior.
2. Publicar una Release con `versionCode` mayor.
3. Abrir **Info → Buscar actualizaciones**.
4. Comprobar versión, notas, descarga, progreso y verificación.
5. Confirmar el instalador del sistema y que los datos se conservan.

## 9. Límites conocidos

- Solo se admiten Releases **estables**; los preestrenos se ignoran.
- La app no comprueba si el dispositivo tiene espacio suficiente antes de
  descargar; el error se muestra al fallar la descarga.
- Si el usuario deniega «Instalar apps desconocidas», la app explica cómo
  habilitarlo, pero no puede hacerlo por él.
- Play Protect puede pedir confirmación adicional en cada instalación.
