# Repositorio oficial, versionado y publicación

Este documento describe cómo se versiona, prueba, firma y publica El Observador
Cubano. El repositorio oficial es GitHub; la aplicación Android se distribuye
como APK firmada desde **GitHub Releases**.

> **Repositorio:** https://github.com/observador-cubano/el-observador-cubano-android
> **Descargas:** https://github.com/observador-cubano/el-observador-cubano-android/releases

## 1. Estructura del repositorio

| Ruta | Contenido |
| --- | --- |
| `src/`, `prisma/`, `docs/` | Plataforma editorial (Next.js y Prisma). |
| `mobile/` | Aplicación Android: Capacitor, interfaz local y código nativo. |
| `mobile/android/` | Proyecto Gradle. El APK se compila aquí. |
| `mobile/android/version.properties` | **Fuente única** de `versionName` y `versionCode`. |
| `.github/workflows/ci.yml` | Comprobaciones en cada push y pull request. |
| `.github/workflows/release.yml` | Compilación, firma y publicación al crear una etiqueta. |
| `docs/ACTUALIZACIONES.md` | Diseño y pruebas del actualizador in-app. |

No se versionan: `node_modules/`, `dist/`, `mobile/backup/`, salidas de Gradle,
`keystore.properties`, `*.jks`, `*.keystore`, `local.properties`, `.env` ni
carpetas de herramientas de agentes.

## 2. Ramas y commits

- Rama principal: `main`.
- Ramas de trabajo: `feat/descripcion-corta`, `fix/descripcion-corta`,
  `docs/…`, `ci/…`.
- Cada cambio entra por pull request con CI en verde. No se publica una APK
  desde una compilación fallida.
- Mensajes de commit descriptivos, en imperativo y con prefijo:
  - `feat(mobile): pantalla de Novedades con historial de Releases`
  - `fix(update): rechazar APK sin SHA-256`
  - `ci(release): verificar firma antes de publicar`
  - `docs: documentar recuperación de firma`

## 3. Versionado

Formato `MAJOR.MINOR.PATCH` (SemVer):

- **MAJOR**: cambios incompatibles o rediseño profundo.
- **MINOR**: funciones nuevas compatibles (por ejemplo, actualizador in-app).
- **PATCH**: correcciones compatibles.

Reglas obligatorias:

1. `versionName` y `versionCode` se editan en
   `mobile/android/version.properties`.
2. `versionCode` **siempre aumenta** en cada APK distribuible.
3. La etiqueta de GitHub es `v<versionName>` (ejemplo: `v1.1.0`).
4. `mobile/package.json` debe tener el mismo `versionName`; CI lo comprueba.

Cambiar de versión:

```powershell
cd mobile
npm run version:bump patch    # o minor, major, 1.2.0
cd ..
git add mobile/android/version.properties mobile/package.json
git commit -m "chore(release): prepare v1.1.0"
```

## 4. Flujos de GitHub Actions

### CI (`.github/workflows/ci.yml`)

Se ejecuta en `push` y `pull_request` sobre `main`:

1. Comprueba que `versionName` coincide con `package.json`.
2. Instala dependencias, ejecuta TypeScript y Vite.
3. Sincroniza Capacitor.
4. Ejecuta las pruebas unitarias (`testDebugUnitTest`).
5. Compila `assembleDebug` y publica el APK y los informes como artefactos.

Si cualquier paso falla, el flujo queda en rojo y no se debe fusionar.

### Release (`.github/workflows/release.yml`)

Se ejecuta al subir una etiqueta `vX.Y.Z`:

1. Verifica que la etiqueta coincide con `versionName`.
2. Exige los secretos de firma.
3. Compila la web, sincroniza Capacitor y ejecuta las pruebas.
4. Firma `assembleRelease` con la keystore de producción.
5. Verifica la firma con `apksigner` y el paquete/versión con `aapt`.
6. Genera `ElObservadorCubano-vX.Y.Z.apk` y su `.sha256`.
7. Crea la GitHub Release con notas generadas desde los commits y adjunta
   ambos archivos.
8. Borra la keystore temporal del runner.

## 5. Secretos de firma

Se configuran una sola vez en **Settings → Secrets and variables → Actions**:

| Secreto | Contenido |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | La keystore `.jks` codificada en Base64. |
| `ANDROID_KEYSTORE_PASSWORD` | Contraseña del almacén. |
| `ANDROID_KEY_ALIAS` | Alias de la clave de firma. |
| `ANDROID_KEY_PASSWORD` | Contraseña de la clave. |

La keystore de este proyecto vive fuera del repositorio, en una carpeta privada
del equipo de firma (por ejemplo `%USERPROFILE%\.observadorcubano\`). **No se
copia al repositorio ni se sube como archivo normal.** Para cargarla como
secreto sin imprimir su contenido:

```powershell
$bytes = [IO.File]::ReadAllBytes("$env:USERPROFILE\.observadorcubano\NOMBRE.jks")
$b64 = [Convert]::ToBase64String($bytes)
$b64 | gh secret set ANDROID_KEYSTORE_BASE64 --repo observador-cubano/el-observador-cubano-android
Remove-Variable bytes, b64
```

Los demás secretos se escriben con `gh secret set NOMBRE --repo observador-cubano/el-observador-cubano-android`
(la terminal los pide de forma interactiva). Nunca se envían por chat ni se
incluyen en la APK.

**Conserva siempre la misma keystore.** Android solo instala una actualización
si el APK está firmado con el mismo certificado. Si se pierde, los usuarios
deberán desinstalar y reinstalar, perdiendo el historial de actualizaciones
automático. No hay forma legítima de eludir esta comprobación.

## 6. Publicar una versión

```powershell
# 1. Verificar que todo compila y las pruebas pasan
cd mobile
npm ci
npm run build
npx cap sync android
cd android
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug

# 2. Preparar la versión
cd ..
npm run version:bump minor

# 3. Revisar, confirmar y subir
cd ..
git add -A
git commit -m "chore(release): prepare v1.2.0"
git push origin main

# 4. Crear la etiqueta y disparar el flujo de release
git tag v1.2.0
git push origin v1.2.0
```

El flujo tarda varios minutos. Al terminar, en **Releases** debe existir
`v1.2.0` con el APK, el `.sha256` y las notas reales.

## 7. Verificar una Release

```powershell
gh release view v1.2.0 --repo observador-cubano/el-observador-cubano-android
gh release download v1.2.0 --repo observador-cubano/el-observador-cubano-android
Get-FileHash .\ElObservadorCubano-v1.2.0.apk -Algorithm SHA256
Get-Content .\ElObservadorCubano-v1.2.0.apk.sha256
```

La huella debe coincidir. La app repite esta comprobación antes de instalar y
además valida paquete, versión y firma.

## 8. Recuperación ante fallos

| Situación | Actuación |
| --- | --- |
| CI rojo | Corregir en una rama y abrir PR. No crear etiquetas. |
| Release falla antes de publicar | Corregir el flujo, borrar la etiqueta (`git tag -d`, `git push --delete`) y volver a crearla con el mismo commit corregido. El `versionCode` no se reutiliza en una versión ya distribuida. |
| APK publicado con un fallo | Publicar una versión PATCH nueva (`versionCode` mayor). No borrar la Release si ya hay usuarios con ella; marcar la incidencia en las notas. |
| Se publicó el APK equivocado | Editar la Release, eliminar el activo erróneo y adjuntar el correcto manteniendo el mismo `versionName` solo si nadie lo instaló. Si ya se distribuyó, publicar PATCH. |
| Firma incorrecta | La app rechaza la instalación con «La firma del APK no coincide». Revisar secretos y volver a compilar. Nunca cambiar de keystore. |
| SHA-256 no coincide | El activo está corrupto o fue sustituido. Volver a subir el APK y regenerar el `.sha256`. La app descarta la descarga. |
| GitHub limita las consultas | La app reintenta en 15 minutos y conserva la última Release conocida. No hay que hacer nada. |
| Sin conexión | La app sigue funcionando; el actualizador muestra «No se pudo comprobar si hay una versión nueva. Revisa la conexión» al abrir Novedades. |
| Play Protect bloquea la instalación | La app se distribuye fuera de Play y puede instalar sus propias actualizaciones, por lo que Play Protect puede marcarla. Elegir «Más detalles → Instalar de todas formas»; el SHA-256 y la firma ya se verificaron. |

## 9. Protecciones recomendadas del repositorio

- Proteger `main`: PR obligatorio, CI en verde y sin force-push.
- Activar **Dependabot alerts** y actualizaciones de seguridad.
- Mantener los secretos solo en GitHub Actions, nunca en archivos.
- Revisar cada Release antes de anunciarla.
