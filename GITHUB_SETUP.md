# Configuración de GitHub

Este documento describe la infraestructura oficial del proyecto, cómo se
configuró y qué pasos requieren intervención manual. No contiene secretos ni
datos personales.

## 1. Identidad técnica

| Dato | Valor |
| --- | --- |
| Cuenta institucional | `ElObervadorCubano` (id `354484831`) |
| Nombre visible | El Observador Cubano |
| Correo de registro | Privado; no se usa en commits |
| Correo de commits | `354484831+ElObervadorCubano@users.noreply.github.com` |
| 2FA | **Pendiente de activación manual** |

El correo de registro está marcado como privado en GitHub y la opción de bloquear
pushes que expongan el correo está activada. Los commits nuevos usan la dirección
`noreply`; los commits antiguos conservan la identidad con la que se crearon.

La configuración de Git es **local al repositorio**; la configuración global del
equipo no se modifica:

```powershell
git config user.name "El Observador Cubano"
git config user.email "354484831+ElObervadorCubano@users.noreply.github.com"
```

## 2. Organización

| Dato | Valor |
| --- | --- |
| Identificador | `observador-cubano` |
| Nombre visible | El Observador Cubano |
| Plan | Free |
| Propietario | Cuenta institucional |
| Miembros | Solo la cuenta institucional |
| Permiso base de repositorios | Lectura |
| Creación de repositorios por miembros | Desactivada |
| Creación de equipos por miembros | Desactivada |
| Dependabot y escaneo de secretos | Activados para repositorios nuevos |

URL: <https://github.com/observador-cubano>

La organización es independiente de cualquier cuenta personal. No se añaden
colaboradores sin autorización expresa.

## 3. Repositorio

| Dato | Valor |
| --- | --- |
| Repositorio | `observador-cubano/el-observador-cubano-android` |
| URL | <https://github.com/observador-cubano/el-observador-cubano-android> |
| Visibilidad | Pública |
| Rama principal | `main` |
| Descargas | <https://github.com/observador-cubano/el-observador-cubano-android/releases> |
| Licencia | Sin licencia asignada (pendiente de decisión del propietario) |

El repositorio contiene la plataforma editorial, la aplicación Android, los
flujos de Actions y la documentación. No se versionan `.env`, keystores,
`keystore.properties`, `local.properties` ni credenciales.

## 4. Protección de la rama principal

La rama `main` tiene aplicadas estas reglas:

- Pull request obligatorio antes de fusionar.
- Revisiones obligatorias: **0** (proyecto unipersonal; el propietario puede
  fusionar su propio pull request).
- Comprobaciones de CI obligatorias y rama actualizada antes de fusionar:
  - `Detección de secretos`
  - `Lint, tipos, pruebas y build`
  - `Pruebas E2E`
  - `Android · versionado`
  - `Android · pruebas y APK debug`
- Force-push y borrado de la rama: prohibidos.
- Reglas aplicadas también a administradores.
- Conversaciones del pull request deben resolverse antes de fusionar.

Las etiquetas de versión no están protegidas por esta regla; solo deben crearse
desde un commit ya fusionado en `main`.

## 5. GitHub Actions

- Permiso por defecto de `GITHUB_TOKEN`: **lectura**.
- Aprobación de pull requests por Actions: desactivada.
- `.github/workflows/release.yml` declara `contents: write` solo para publicar la
  Release.
- `.github/workflows/ci.yml` declara `contents: read` y ejecuta la detección de
  secretos.
- Los flujos usan acciones oficiales o verificadas; no se usan tokens personales
  en los workflows.

### Secretos de firma

Se configuran en **Settings → Secrets and variables → Actions** del repositorio:

| Secreto | Uso |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Keystore codificada en Base64. |
| `ANDROID_KEYSTORE_PASSWORD` | Contraseña del almacén. |
| `ANDROID_KEY_ALIAS` | Alias de la clave. |
| `ANDROID_KEY_PASSWORD` | Contraseña de la clave. |

Nunca se imprimen en los registros. El flujo borra la keystore temporal del
runner al terminar, incluso si falla. Véase `ANDROID_SIGNING.md`.

## 6. Canal heredado y transición

La versión 1.1.1 instalada solo acepta actualizaciones de
`R911Y/el-observador-cubano`. Para no dejarla sin actualizaciones, la 1.1.2 se
publica en **ambos** repositorios con la misma APK firmada:

| Repositorio | Función |
| --- | --- |
| `observador-cubano/el-observador-cubano-android` | Canal oficial desde 1.1.2. |
| `R911Y/el-observador-cubano` | Canal heredado; solo mantiene la Release v1.1.2 para permitir la transición. |

Flujo de actualización:

1. Una app 1.1.1 consulta el repositorio heredado y encuentra v1.1.2.
2. Descarga el APK, verifica SHA-256 y firma, e instala con confirmación.
3. La app 1.1.2 pasa a consultar el repositorio oficial de la organización.

El repositorio heredado no debe recibir versiones nuevas. Cuando ya no queden
instalaciones 1.1.1, puede archivarse; sus Releases seguirán descargables.

## 7. Mantenimiento periódico

Revisar al menos una vez al mes:

- Miembros y colaboradores de la organización.
- Permisos administrativos y aplicaciones OAuth autorizadas.
- Claves SSH y tokens personales.
- Secretos y workflows del repositorio.
- Protección de ramas y reglas de etiquetas.
- Releases publicadas y activos adjuntos.
- Alertas de Dependabot y de escaneo de secretos.
- Historial de commits en busca de datos personales o credenciales.

## 8. Pasos manuales pendientes

| Paso | Estado | Dónde |
| --- | --- | --- |
| Activar 2FA de la cuenta institucional | Pendiente | Settings → Password and authentication |
| Guardar códigos de recuperación fuera del proyecto | Pendiente | Al activar 2FA |
| Exigir 2FA a los miembros de la organización | Pendiente (requiere 2FA del propietario) | Organization settings → Security |
| Corregir el nombre visible de la cuenta | Pendiente (`ElObservadorCubano `) | Settings → Public profile |
| Decidir la licencia del repositorio | Pendiente | Repositorio → Add license |

La 2FA no se puede activar por API ni por línea de comandos: requiere la
aplicación autenticadora o una passkey del propietario. Los códigos de
recuperación nunca deben guardarse en el repositorio.

## 9. Verificación

Comandos útiles (sin mostrar secretos):

```powershell
gh auth status
gh api orgs/observador-cubano --jq '{login, plan: .plan.name, members_can_create_repositories}'
gh api repos/observador-cubano/el-observador-cubano-android/branches/main/protection
gh secret list --repo observador-cubano/el-observador-cubano-android
gh release list --repo observador-cubano/el-observador-cubano-android
```

Estado comprobado el 9 de noviembre de 2026:

- Organización, repositorio y protección de rama verificados por API.
- CI y Release `v1.1.2` en verde.
- SHA-256 y certificado del APK comprobados.
- Secretos de firma presentes (solo por nombre).
