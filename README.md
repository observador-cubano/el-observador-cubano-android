# El Observador Cubano

**Periodismo desde las comunidades**

Repositorio de la sede digital de *El Observador Cubano*: un periódico multilingüe (español/inglés)
con verificación editorial, privacidad por defecto y protección de fuentes.

> **Estado:** Fase 0 aprobada. Fase 1 (fundación) implementada y verificada en local. No hay
> despliegue, base de datos ni contenido migrado todavía.
> Ver [`docs/13-fase-1-fundacion.md`](docs/13-fase-1-fundacion.md).

---

## Aplicación Android

La app oficial (`cu.observadorcubano.app`) vive en [`mobile/`](mobile/) y se
compila sin Android Studio con Gradle. La versión actual es **1.1.1**
(`versionCode 3`).

- **Repositorio oficial:** https://github.com/observador-cubano/el-observador-cubano-android
- **Descargas firmadas:** https://github.com/observador-cubano/el-observador-cubano-android/releases
- Interfaz nativa + WebView del periódico, búsqueda, secciones, Info y
  adaptación a orientación vertical/apaisada.
- Actualizaciones in-app desde GitHub Releases: SHA-256, paquete, versión y
  firma verificados antes de abrir el instalador de Android.
- CI y publicación firmada con GitHub Actions; instrucciones completas en
  [`docs/GITHUB.md`](docs/GITHUB.md) y
  [`docs/ACTUALIZACIONES.md`](docs/ACTUALIZACIONES.md).

```bash
cd mobile
npm ci
npm run build
npx cap sync android
cd android
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

La APK queda en `mobile/android/app/build/outputs/apk/debug/app-debug.apk`.
Para publicar una versión: `npm run version:bump minor`, commit, etiqueta
`vX.Y.Z` y push de la etiqueta. El flujo de release firma y adjunta el APK y su
SHA-256.

---

## Qué funciona hoy

- Next.js 16 con App Router, React 19, TypeScript estricto y Tailwind 4.
- Rutas localizadas reales: `/es/nacional`, `/en/national`, `/es/ultimas`, `/en/latest`.
- Portada, 11 secciones, últimas, búsqueda, acerca, radio, edición impresa y privacidad, con estados
  vacíos explícitos.
- Sistema de diseño con tokens, tipografías Newsreader/Inter, tema claro/oscuro, menú móvil accesible
  y selector de idioma que conserva la página.
- Cabeceras de seguridad y CSP en modo informe.
- Prisma 7 con 43 modelos y migración inicial (aún no aplicada).
- Better Auth configurado con MFA y RBAC de 7 roles y 17 permisos.
- 16 pruebas unitarias, 3 pruebas E2E y build de 41 páginas estáticas.

**Lo que no funciona todavía:** inicio de sesión real, MFA, creación de usuarios, migraciones contra
PostgreSQL, subidas, boletín, comentarios y despliegue. Nada de eso se presenta como terminado.

---

## Requisitos

- Node.js **24** o superior y npm 11.
- PostgreSQL 16+ para datos, migraciones y autenticación (opcional para compilar).
- Git.

No se necesita Docker para compilar, pero sí una base de datos para probar la capa de datos.

---

## Puesta en marcha

```bash
npm ci
cp .env.example .env
# Edita .env: DATABASE_URL, BETTER_AUTH_SECRET, BETTER_AUTH_URL y NEXT_PUBLIC_SITE_URL

npm run db:generate
npm run db:migrate   # requiere PostgreSQL
npm run dev
```

Sin base de datos:

```bash
npm run build        # funciona
npm run start        # sirve el sitio
# /api/health responde 503 y las rutas de auth no pueden operar
```

Generar el secreto de Better Auth:

```bash
node -e "console.log(require('crypto').randomBytes(32).toString('base64url'))"
```

La CLI de Prisma lee `.env`; Next.js lee `.env` y `.env.local`. Nunca se commitea `.env`.

---

## Scripts

| Comando | Qué hace |
|---|---|
| `npm run dev` | Servidor de desarrollo |
| `npm run build` | `prisma generate` + build de producción |
| `npm run start` | Sirve el build |
| `npm run lint` | ESLint |
| `npm run typecheck` | Genera tipos de rutas y comprueba TypeScript |
| `npm run test` | Pruebas unitarias con Vitest |
| `npm run test:watch` | Vitest en modo vigilancia |
| `npm run test:e2e` | Playwright |
| `npm run check` | Lint + tipos + pruebas unitarias |
| `npm run db:generate` | Genera el cliente Prisma |
| `npm run db:validate` | Valida el esquema |
| `npm run db:migrate` | Crea y aplica migraciones en local |
| `npm run db:deploy` | Aplica migraciones pendientes |
| `npm run db:status` | Estado de migraciones |
| `npm run db:studio` | Explorador de datos |

En este equipo el CDN de Playwright está bloqueado. Para usar Edge instalado:

```powershell
$env:PLAYWRIGHT_CHANNEL = "msedge"
npm run test:e2e
```

En CI se instala Chromium automáticamente.

---

## Estructura

```text
src/
├── app/[locale]/        # páginas públicas localizadas
├── app/api/             # auth y health
├── components/          # ui, layout y editorial
├── i18n/                # routing, request y mensajes es/en
├── lib/                 # auth, db, rbac, rutas, utilidades
├── styles/              # tokens y estilos globales
└── proxy.ts             # middleware de next-intl (Next 16)

prisma/                  # esquema y migraciones
tests/                   # unitarias y E2E
docs/                    # investigación, planes e informes
```

La separación por módulos de dominio (`modules/articles`, `modules/users`, etc.) llega en las fases
2–3. La estructura actual es deliberadamente pequeña y verificable.

---

## Documentación

| Documento | Contenido |
|---|---|
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | Stack real, estructura, i18n, datos, auth y decisiones |
| [`SECURITY.md`](SECURITY.md) | Controles implementados, CSP y pendientes antes de producción |
| [`DEPLOYMENT.md`](DEPLOYMENT.md) | Entornos, variables, migraciones y despliegue en Vercel |
| [`docs/00-informe-fase-0.md`](docs/00-informe-fase-0.md) | Resumen ejecutivo de la investigación |
| [`docs/01-analisis-blogspot.md`](docs/01-analisis-blogspot.md) | Análisis del sitio actual |
| [`docs/02-analisis-whatsapp.md`](docs/02-analisis-whatsapp.md) | Canal de WhatsApp |
| [`docs/03-inventario-contenidos.md`](docs/03-inventario-contenidos.md) | 34 entradas y 14 páginas |
| [`docs/04-arquitectura-tecnica.md`](docs/04-arquitectura-tecnica.md) | Arquitectura propuesta |
| [`docs/05-modelo-datos.md`](docs/05-modelo-datos.md) | Modelo de datos |
| [`docs/06-mapa-paginas.md`](docs/06-mapa-paginas.md) | Rutas y slugs |
| [`docs/07-propuesta-visual.md`](docs/07-propuesta-visual.md) | Identidad y sistema de diseño |
| [`docs/08-plan-seguridad.md`](docs/08-plan-seguridad.md) | Amenazas, controles y respuesta |
| [`docs/09-plan-migracion.md`](docs/09-plan-migracion.md) | Migración de Blogger |
| [`docs/10-plan-desarrollo.md`](docs/10-plan-desarrollo.md) | Fases y criterios de salida |
| [`docs/11-costes-servicios.md`](docs/11-costes-servicios.md) | Estimación de servicios |
| [`docs/12-riesgos-y-preguntas.md`](docs/12-riesgos-y-preguntas.md) | Riesgos y datos pendientes |
| [`docs/13-fase-1-fundacion.md`](docs/13-fase-1-fundacion.md) | Informe de la Fase 1 |

Datos tabulares en [`docs/data/inventario-contenidos.csv`](docs/data/inventario-contenidos.csv) y
[`docs/data/inventario-contenidos.md`](docs/data/inventario-contenidos.md).

---

## Seguridad y privacidad

- Sin cookies de analítica, publicidad ni seguimiento.
- Sin formularios que recojan datos personales en esta fase.
- Cabeceras de seguridad activas; CSP en modo informe y pendiente de endurecer.
- Alta pública de cuentas deshabilitada.
- La plataforma **no promete anonimato absoluto** de fuentes. Ver [`SECURITY.md`](SECURITY.md).

---

## Limitaciones actuales

1. No hay repositorio remoto: falta el token de GitHub o `gh auth login`.
2. No hay PostgreSQL local ni remoto; la migración no se ha aplicado.
3. No hay pantallas de login, MFA ni administración de usuarios.
4. No hay despliegue ni dominio de prueba.
5. Faltan XML de Blogger, materiales de radio/PDF, autores y equipo editorial.
6. `npm audit` reporta 8 vulnerabilidades moderadas en la cadena de ESLint; no se aplica
   `npm audit fix --force`.
7. Chromium de Playwright no se puede descargar en este equipo (403 por ubicación); se usa Edge.

---

## Siguiente paso

1. Crear el repositorio privado y ejecutar CI.
2. Crear la base de datos y aplicar la migración.
3. Completar login, MFA y alta interna de usuarios para cerrar el criterio de la Fase 1.
4. Desplegar el entorno de prueba en `*.vercel.app`.
5. Iniciar la Fase 2 con contenido real y taxonomía aprobada.

No se compra dominio, no se contrata ningún servicio y no se publica contenido migrado sin
autorización expresa.

---

## Licencia y materiales

El código se alojará en un repositorio privado. Las fotografías, marcas y materiales de terceros no se
reutilizan sin autorización. El logotipo tipográfico es provisional y original. Los textos legales son
borradores y requieren revisión profesional.
