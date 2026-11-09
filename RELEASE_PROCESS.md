# Proceso de publicación

Este documento describe cómo se prepara, verifica y publica una versión de El
Observador Cubano. Una Release solo se crea cuando el código está en la rama
principal, la CI está en verde y el APK se ha probado.

## 1. Versionado

Formato `MAJOR.MINOR.PATCH`:

- **MAJOR**: cambios incompatibles o rediseño profundo.
- **MINOR**: funciones nuevas compatibles.
- **PATCH**: correcciones compatibles.

Reglas:

1. La fuente única es `mobile/android/version.properties`
   (`versionName` y `versionCode`).
2. `versionCode` **siempre aumenta** en cada APK distribuible. No se reutiliza
   aunque la Release anterior se haya borrado.
3. La etiqueta es `v<versionName>`.
4. `mobile/package.json` y `mobile/package-lock.json` deben tener la misma
   versión; la CI lo comprueba.
5. No se publican preestrenos como canal estable. Si se usan, deben marcarse
   como `prerelease` y la app los ignora.

Cambiar de versión:

```powershell
cd mobile
npm run version:bump patch    # o minor, major, 1.2.0
cd ..
git add mobile/android/version.properties mobile/package.json mobile/package-lock.json
git commit -m "chore(release): prepare vX.Y.Z"
```

## 2. Lista de comprobación previa

- [ ] Los cambios están fusionados en la rama principal.
- [ ] La CI está en verde (lint, tipos, pruebas, build, E2E y Android).
- [ ] Las pruebas unitarias de Android pasan (43 en la versión 1.1.1).
- [ ] El `versionCode` es mayor que el de la última versión distribuida.
- [ ] La etiqueta `vX.Y.Z` no existe.
- [ ] Los secretos de firma están configurados en el repositorio.
- [ ] La keystore es la misma que la de la versión anterior.
- [ ] Las notas describen cambios reales, sin promesas no verificadas.
- [ ] Se ha probado la instalación sobre una versión anterior.

## 3. Publicar

```powershell
# 1. Comprobar el estado
git status
git pull --ff-only

# 2. Preparar la versión (si aún no se hizo)
cd mobile
npm run version:bump patch
cd ..

# 3. Confirmar y subir
git add -A
git commit -m "chore(release): prepare vX.Y.Z"
git push origin main

# 4. Crear la etiqueta y disparar el flujo
git tag vX.Y.Z
git push origin vX.Y.Z
```

El flujo `.github/workflows/release.yml` se ejecuta al subir la etiqueta:

1. Comprueba que la etiqueta coincide con `versionName`.
2. Exige los secretos de firma.
3. Compila la web, sincroniza Capacitor y ejecuta las pruebas.
4. Firma `assembleRelease`.
5. Verifica firma, paquete y versión.
6. Compara el certificado con la Release anterior y detiene la publicación si
   no coincide.
7. Genera `ElObservadorCubano-vX.Y.Z.apk` y su `.sha256`.
8. Crea la GitHub Release con notas generadas desde los commits.
9. Borra la keystore temporal del runner.

Tarda varios minutos. No se debe crear otra etiqueta mientras el flujo está en
curso.

## 4. Verificar la Release

```powershell
gh release view vX.Y.Z --repo PROPIETARIO/REPOSITORIO
gh release download vX.Y.Z --repo PROPIETARIO/REPOSITORIO
Get-FileHash .\ElObservadorCubano-vX.Y.Z.apk -Algorithm SHA256
Get-Content .\ElObservadorCubano-vX.Y.Z.apk.sha256
apksigner verify --print-certs .\ElObservadorCubano-vX.Y.Z.apk
```

Comprobar:

- El `.sha256` coincide con `Get-FileHash`.
- El certificado es el de `ANDROID_SIGNING.md`.
- El paquete es `cu.observadorcubano.app` y la versión coincide con la etiqueta.
- La Release no está marcada como preestreno.
- Los activos son exactamente el APK y su `.sha256`.

Después, en un dispositivo con la versión anterior:

1. Abrir **Info → Buscar actualizaciones**.
2. Comprobar versión, fecha y notas.
3. Actualizar y confirmar que el instalador del sistema pide autorización.
4. Verificar que la versión instalada cambia y los datos se conservan.

## 5. Notas de la versión

Cada Release debe incluir:

- Número de versión y fecha.
- Resumen de cambios.
- Correcciones.
- Mejoras.
- Cambios visuales.
- Compatibilidad (Android mínimo soportado: 7.0 / API 24).
- Recordatorio del SHA-256 y de la firma.
- Limitaciones conocidas, si las hay.

No se inventan novedades: la app muestra el cuerpo real de la Release.

## 6. Incidencias y recuperación

| Situación | Actuación |
| --- | --- |
| CI en rojo | Corregir en una rama y abrir pull request. No crear etiquetas. |
| El flujo falla antes de publicar | Corregir, borrar la etiqueta (`git tag -d vX.Y.Z` y `git push --delete origin vX.Y.Z`) y volver a crearla. El `versionCode` no se reutiliza si la versión ya se distribuyó. |
| APK publicado con un fallo | Publicar una versión PATCH con `versionCode` mayor. No borrar la Release si ya hay usuarios. |
| APK equivocado publicado | Si nadie lo instaló, reemplazar el activo manteniendo la versión. Si ya se distribuyó, publicar PATCH. |
| Firma incorrecta | La app rechaza la instalación. Revisar los secretos y volver a compilar. **Nunca** cambiar de keystore. |
| SHA-256 no coincide | El activo está corrupto o fue sustituido. Volver a subir el APK y regenerar el `.sha256`. |
| Hay que trasladar el repositorio | Publicar antes una versión de transición que apunte al destino nuevo y ofrecerla también en el origen. Véase `UPDATE_SYSTEM.md`. |

## 7. Reglas que no se pueden romper

- No se publica desde una compilación fallida.
- No se reutiliza un `versionCode`.
- No se cambia de keystore ni de `applicationId`.
- No se instala nada en silencio ni se pide al usuario que desactive la
  verificación de Android.
- No se incluyen secretos, rutas personales ni datos privados en las notas.
- No se declara una versión «probada» sin haberla instalado realmente.
