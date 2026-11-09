# Firma y keystore de Android

La aplicación se distribuye fuera de Google Play como APK firmada. Android solo
instala una actualización si el APK nuevo está firmado con **el mismo
certificado** que el instalado; por eso la keystore de producción es un activo
crítico y permanente.

## 1. Identidad de firma

| Dato | Valor |
| --- | --- |
| Paquete | `cu.observadorcubano.app` |
| Certificado (SHA-256) | `c63ba1a6df9b2c481f8c70553c4abfd590e1c8e70b0193ba4cb4b34e0ab5ac7c` |
| Algoritmo | RSA 2048 con SHA-256 (esquema de firma v1+v2 según `apksigner`) |
| Formato | Keystore PKCS12/JKS en un archivo `.jks` |

Ese valor es público: identifica la clave, no la revela. Se puede comprobar con:

```powershell
apksigner verify --print-certs ElObservadorCubano-vX.Y.Z.apk
```

Debe aparecer el mismo `SHA-256 digest` que la tabla anterior y que la versión
instalada. La aplicación rechaza cualquier APK cuya firma no coincida.

## 2. Dónde vive la keystore

- **Nunca** dentro del repositorio.
- Fuera del árbol de trabajo, en una carpeta privada del equipo de firma
  (`%USERPROFILE%\.observadorcubano\` en el equipo actual).
- Copia de seguridad cifrada en al menos dos ubicaciones seguras, separadas del
  equipo. Sin la keystore no se pueden publicar actualizaciones instalables.
- Contraseñas y alias en un gestor de contraseñas, nunca en documentos del
  proyecto, issues, chats o capturas.

El archivo está excluido por `.gitignore` junto con `keystore.properties`,
`*.jks`, `*.keystore` y `local.properties`.

## 3. Secretos de GitHub Actions

Se configuran una sola vez en **Settings → Secrets and variables → Actions** del
repositorio:

| Secreto | Contenido |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Keystore `.jks` codificada en Base64. |
| `ANDROID_KEYSTORE_PASSWORD` | Contraseña del almacén. |
| `ANDROID_KEY_ALIAS` | Alias de la clave de firma. |
| `ANDROID_KEY_PASSWORD` | Contraseña de la clave. |

Para cargarlos sin imprimir su contenido:

```powershell
$bytes = [IO.File]::ReadAllBytes("$env:USERPROFILE\.observadorcubano\NOMBRE.jks")
$b64 = [Convert]::ToBase64String($bytes)
$b64 | gh secret set ANDROID_KEYSTORE_BASE64 --repo PROPIETARIO/REPOSITORIO
Remove-Variable bytes, b64
```

Los demás se escriben con `gh secret set NOMBRE --repo PROPIETARIO/REPOSITORIO`,
que los pide de forma interactiva. El flujo de release los inyecta como
variables de entorno, crea `keystore.properties` y `keystore.jks` en el runner y
los borra incluso si el flujo falla.

## 4. Compilación local firmada

`mobile/android/app/build.gradle` lee `mobile/android/keystore.properties` si
existe:

```properties
storeFile=C:\ruta\privada\observador.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Sin ese archivo, `assembleRelease` genera un APK **sin firmar** o firmado con la
clave de depuración; no debe distribuirse. Compilación:

```powershell
cd mobile
npm ci
npm run build
npx cap sync android
cd android
.\gradlew.bat :app:assembleRelease
```

Verificación antes de instalar:

```powershell
apksigner verify --print-certs app\build\outputs\apk\release\app-release.apk
aapt dump badging app\build\outputs\apk\release\app-release.apk | Select-String "package:"
Get-FileHash app\build\outputs\apk\release\app-release.apk -Algorithm SHA256
```

## 5. Firma en la integración continua

`.github/workflows/release.yml`:

1. Exige `ANDROID_KEYSTORE_BASE64` y falla si falta.
2. Decodifica la keystore solo en el runner.
3. Compila `assembleRelease`.
4. Verifica la firma con `apksigner` y el paquete/versión con `aapt`.
5. Compara el certificado con el de la Release anterior del mismo repositorio.
   Si no coincide, **detiene la publicación**.
6. Genera el APK, su `.sha256` y la GitHub Release.
7. Borra `keystore.jks` y `keystore.properties` del runner.

## 6. Reglas que no se pueden romper

- No se regenera la keystore en cada compilación.
- No se cambia el `applicationId` (`cu.observadorcubano.app`) mientras existan
  usuarios con la app instalada.
- No se publica una versión con un `versionCode` ya utilizado.
- No se sube la keystore ni sus contraseñas al repositorio, a un issue, a una
  Release o a un chat.
- No se usa una keystore distinta «solo para esta versión».

## 7. Pérdida o compromiso de la keystore

| Situación | Consecuencia | Actuación |
| --- | --- | --- |
| Se pierde la keystore | Los usuarios no pueden actualizar desde la app; Android rechaza el APK nuevo por firma distinta. | Restaurar la copia de seguridad. Sin copia, solo queda publicar un paquete nuevo con otro `applicationId` y pedir una reinstalación manual. |
| Se filtra la keystore | Un tercero podría firmar APKs que Android aceptaría como actualización. | Revocar la distribución afectada, publicar un paquete nuevo con otra identidad y avisar a los usuarios. No hay forma de «revocar» un certificado Android instalado. |
| Se cambia de equipo de firma | Riesgo de perder el acceso. | Mantener la keystore y las contraseñas en un gestor compartido por la organización, con 2FA. |

La keystore de este proyecto **no se ha regenerado** desde la primera versión
publicada. La continuidad de firma se comprueba en cada Release.
