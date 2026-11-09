# Seguridad — El Observador Cubano

**Estado:** Fase 1 de la plataforma web (sin producción, dominio ni datos reales). La aplicación Android 1.1.x sí se distribuye de forma firmada.
**Última actualización:** 9 de noviembre de 2026
**Marco de referencia:** OWASP ASVS nivel 2, OWASP Top 10 y
[`docs/08-plan-seguridad.md`](docs/08-plan-seguridad.md).

Este documento describe **lo que está implementado y verificado**, no lo que se desea. Todo control
pendiente aparece en la lista de la sección 7.

---

## 1. Alcance y advertencia

- El repositorio contiene la fundación técnica del medio: páginas públicas vacías, sistema de diseño,
  esquema de datos, configuración de autenticación y RBAC.
- **No hay base de datos desplegada**, ni cuentas, ni contenido migrado, ni formularios que recojan
  datos personales.
- La plataforma **no garantiza anonimato absoluto** de fuentes. Ninguna web puede hacerlo. Las medidas
  organizativas y de comunicación segura son responsabilidad del medio.
- No se afirma cumplimiento automático de RGPD ni de ninguna otra norma. Los textos legales son
  borradores y requieren revisión profesional.

---

## 2. Comunicar una vulnerabilidad

El canal privado es el **GitHub Security Advisory** del repositorio oficial:
<https://github.com/observador-cubano/el-observador-cubano-android/security/advisories/new>.

- No publiques detalles explotables, datos de fuentes ni información personal en un issue público.
- No envíes contraseñas, códigos 2FA, códigos de recuperación, tokens ni claves privadas.
- Indica la versión afectada, los pasos para reproducirlo y el impacto que crees que tiene.

No hay programa de recompensas ni un acuerdo de nivel de servicio. Se acusará recibo y se informará
de la resolución en la medida de lo posible.

---

## 3. Controles implementados

### 3.1 Cabeceras HTTP

Aplicadas a todas las respuestas desde [`next.config.ts`](next.config.ts):

| Cabecera | Valor | Estado |
|---|---|---|
| `X-Content-Type-Options` | `nosniff` | Activa |
| `X-Frame-Options` | `DENY` | Activa |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | Activa |
| `Permissions-Policy` | `camera=(), microphone=(), geolocation=(), payment=(), usb=()` | Activa |
| `Cross-Origin-Opener-Policy` | `same-origin` | Activa |
| `Cross-Origin-Resource-Policy` | `same-site` | Activa |
| `Content-Security-Policy-Report-Only` | Ver sección 4 | Solo informe |
| `Strict-Transport-Security` | — | Pendiente de dominio y HTTPS |

`poweredByHeader` está desactivado. `reactStrictMode` está activo.

### 3.2 Autenticación

Configuración en [`src/lib/auth/index.ts`](src/lib/auth/index.ts):

- Better Auth con adaptador Prisma y sesiones en base de datos.
- **Alta pública cerrada** (`disableSignUp: true`). Las cuentas son internas.
- Contraseña mínima de 12 caracteres; máxima de 128.
- Sesión de 30 días con actualización cada 24 horas.
- Plugin `twoFactor`: TOTP y códigos de recuperación.
- Cookies gestionadas por Better Auth (`HttpOnly`, `SameSite`; `Secure` en producción).
- Campos `role` y `status` marcados como `input: false`: el cliente no puede asignárselos.
- La instancia se construye de forma perezosa para que el build no necesite secreto ni base de datos.

**Sin verificar:** el flujo real de alta, inicio de sesión, enrolamiento MFA, recuperación y cierre de
sesión no se ha ejecutado porque no hay PostgreSQL. No se presenta como probado.

### 3.3 Autorización

[`src/lib/rbac.ts`](src/lib/rbac.ts) implementa 7 roles y 17 permisos. La matriz coincide con
`docs/08-plan-seguridad.md`:

| Rol | Capacidad principal | MFA |
|---|---|:--:|
| `OWNER` | Todo, incluida configuración y usuarios | Sí |
| `ADMIN` | Todo, incluida configuración y usuarios | Sí |
| `EDITOR_IN_CHIEF` | Ciclo editorial completo, boletín, páginas | Sí |
| `EDITOR` | Crear, editar, enviar a revisión y revisar; **no publicar** | Sí |
| `JOURNALIST` | Borradores propios y envío a revisión | No |
| `TRANSLATOR` | Traducción y revisión de traducciones | No |
| `MODERATOR` | Moderación de comentarios | Sí |

[`src/lib/auth/session.ts`](src/lib/auth/session.ts) comprueba en servidor, en este orden: sesión,
estado activo de la cuenta, rol válido, MFA cuando el rol lo exige y permiso concreto. El menú y los
botones nunca conceden permisos.

### 3.4 Secretos y configuración

- `.env` está ignorado por Git; solo se versiona `.env.example` sin valores reales.
- El secreto de Better Auth se genera con 32 bytes aleatorios en base64url.
- CI usa valores ficticios, nunca secretos reales.
- No hay claves de Vercel, Neon, R2 ni Resend en el repositorio.

### 3.5 Dependencias

- Versiones fijadas con `package-lock.json`.
- `npm audit` reports **8 vulnerabilidades moderadas**, todas en la cadena de herramientas de ESLint
  (`minimatch` dentro de `@typescript-eslint/*`). No hay vulnerabilidades altas ni críticas.
- La corrección automática propone `eslint-config-next@15.5.29`, un cambio incompatible. **No se usa
  `npm audit fix --force`.**
- Se revisará al actualizar ESLint/TypeScript-ESLint.

### 3.6 Privacidad y datos

- No hay cookies de analítica, publicidad ni seguimiento.
- El tema se guarda en `localStorage` como preferencia funcional; no identifica a nadie.
- No hay formularios de contacto, boletín, comentarios ni donaciones activos.
- No se recogen direcciones IP completas en la aplicación.
- El modelo de datos prevé hash con sal para IP y correos de comentaristas, no texto claro.

---

## 4. Content Security Policy

### 4.1 Política actual (solo informe)

```text
default-src 'self';
script-src 'self' 'unsafe-inline';
style-src 'self' 'unsafe-inline';
img-src 'self' data: blob:;
font-src 'self';
connect-src 'self';
media-src 'self';
object-src 'none';
base-uri 'self';
form-action 'self';
frame-ancestors 'none';
upgrade-insecure-requests
```

Se envía como `Content-Security-Policy-Report-Only`. En este modo, los navegadores ignoran
`upgrade-insecure-requests` (se observó el aviso en consola durante las pruebas); no es un fallo de la
aplicación, sino una limitación del modo informe.

### 4.2 Motivo de los permisos temporales

- Next.js inyecta scripts en línea para la hidratación y para el arranque del tema.
- Tailwind y el runtime pueden emitir atributos `style` en línea.
- Endurecer la política sin romper la hidratación requiere nonces por petición o hashes, y eso afecta
  al renderizado estático/ISR.

### 4.3 Objetivo antes de producción

1. Sustituir `'unsafe-inline'` por **nonces** en rutas dinámicas y **hashes** en páginas estáticas.
2. Eliminar `unsafe-eval` (ya no está permitido hoy, pero debe comprobarse en cada dependencia).
3. Pasar a `Content-Security-Policy` en modo bloqueo.
4. Añadir `Strict-Transport-Security` cuando el dominio y todos los subdominios estén en HTTPS.
5. Añadir `X-Robots-Tag: noindex` a los *previews* de Vercel.
6. Verificar con `securityheaders.com` y pruebas propias.

La política actual **no se considera suficiente para producción**.

---

## 5. Origen de desarrollo

Next.js 16 bloquea los recursos de desarrollo (`/_next/hmr`, etc.) cuando la página se sirve desde un
origen distinto al anunciado por el servidor. En este proyecto se accede por `localhost` y Playwright
usa `127.0.0.1`; por eso `next.config.ts` autoriza ambos orígenes **solo en desarrollo**:

```ts
allowedDevOrigins: ["127.0.0.1", "localhost"]
```

Sin esta opción, la página 404 se quedaba en su armazón de error sin hidratar. No afecta a producción.

---

## 6. Registro, auditoría y respuesta a incidentes

- El modelo `AuditLog` existe en Prisma, pero todavía no hay operaciones que escriban en él.
- No se registran contraseñas, tokens, códigos MFA, contenido completo de borradores, correos en claro
  ni IP completa.
- El procedimiento de respuesta (detección, contención, erradicación, recuperación, notificación,
  comunicación y postmortem) está en `docs/08-plan-seguridad.md`, sección 12.
- Copias y restauración: pendientes de proveedor y de probar una restauración real.

---

## 7. Pendientes obligatorios antes de producción

### Bloqueantes (P0)

- [ ] PostgreSQL accesible y migración `20261106000000_init` aplicada y verificada.
- [ ] Pruebas reales de alta interna, inicio de sesión, MFA, recuperación y cierre de sesión.
- [ ] Pantalla de administración de usuarios con permiso `user.manage` comprobado en servidor.
- [ ] CSP en modo bloqueo con nonces/hashes.
- [ ] Revisión de que ningún endpoint permita crear cuentas sin autorización.
- [ ] Rate limiting en login, recuperación y formularios.
- [ ] Copia de seguridad y restauración probadas.

### Antes del lanzamiento (P1)

- [ ] HSTS tras verificar el dominio.
- [ ] `noindex` en previews.
- [ ] Validación Zod en todos los formularios y *server actions*.
- [ ] Sanitización del HTML enriquecido y pruebas XSS.
- [ ] Auditoría de acciones sensibles.
- [ ] Verificación de tipos reales en subidas y eliminación de EXIF.
- [ ] Revisión ASVS 2 y prueba de penetración externa.
- [ ] Auditoría de accesibilidad con axe y lector de pantalla.
- [ ] Revisión legal de privacidad, cookies y donaciones.

---

## 8. Evidencia de verificación (6 de noviembre de 2026)

| Comprobación | Resultado |
|---|---|
| `npm run lint` | Pasa |
| `npm run typecheck` | Pasa |
| `npm run test` | 16 pruebas en 2 archivos |
| `npm run build` | Pasa; 41 páginas estáticas |
| `npx prisma validate` | Esquema válido |
| Cabeceras en `/es` | Las seis cabeceras activas + CSP en informe |
| `/api/health` sin base de datos | 503 controlado, sin filtrar detalles |
| Registro público | Deshabilitado en configuración (no probado contra base de datos) |
| E2E | 3 pruebas locales con Edge; Chromium no descargable en este equipo por bloqueo del CDN |

Los resultados son de este entorno. No se extrapolan a producción.

---

## 9. Aplicación Android y cadena de distribución

### 9.1 Alcance y versiones mantenidas

| Versión | Soporte |
|---|---|
| 1.1.x | Recibe correcciones de seguridad y actualizaciones. |
| 1.0.x y anteriores | Solo actualizables a la última versión estable. |

La app (`cu.observadorcubano.app`) se distribuye **solo** mediante GitHub Releases del repositorio
oficial. Cualquier APK obtenido por otra vía no está respaldado por el proyecto.

Fuera del alcance de seguridad de la app: el sitio de Blogger y los servicios de Google, GitHub,
Android y el firmware del dispositivo, y los ataques que requieran root, depuración USB o acceso
físico desbloqueado.

### 9.2 Medidas aplicadas

- **Firma de producción** con keystore privada fuera del repositorio; los secretos viven solo en
  GitHub Actions. Detalles en `ANDROID_SIGNING.md`.
- **Verificación en cadena** antes de instalar: SHA-256 publicado, paquete, `versionCode`,
  `versionName` y certificado.
- **Sin instalación silenciosa**: siempre se requiere confirmación del instalador de Android.
- **HTTPS obligatorio**: `usesCleartextTraffic="false"` y política de red que bloquea el tráfico en
  claro.
- **Sin secretos en el código**: `GITHUB_TOKEN` con permisos mínimos; la keystore se decodifica de
  forma temporal en el runner y se borra al terminar.
- **Detección de secretos y rutas personales** en la integración continua.
- **Protección de la rama principal**, revisión mediante pull request y CI obligatoria.
- **Correo noreply** en los commits nuevos; el correo personal no se publica.

### 9.3 Limitaciones conocidas

- **No hay anonimato absoluto.** GitHub y Google ven las peticiones de red y el historial de Git es
  público. Los commits antiguos conservan la identidad con la que se crearon.
- **Play Protect** puede advertir al instalar un APK fuera de Google Play. Es esperable en apps que se
  actualizan a sí mismas; no implica que el APK esté comprometido. Verifica siempre el SHA-256.
- **Un dispositivo comprometido** (root, depuración USB, accesibilidad maliciosa) puede eludir los
  controles de la aplicación.
- **Dependencias de terceros**: una vulnerabilidad en Android System WebView, Capacitor o las
  bibliotecas de Android afecta a la app hasta publicar una versión corregida.

### 9.4 Respuesta a incidentes de distribución

1. Confirmar el alcance y la versión afectada.
2. Si hay un secreto expuesto, revocarlo y rotarlo de inmediato.
3. Publicar una versión PATCH con la corrección; nunca reutilizar un `versionCode` ya distribuido.
4. Documentar el incidente y las medidas adoptadas en las notas de la Release.
