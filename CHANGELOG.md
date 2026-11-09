# Historial de cambios

Todas las versiones distribuidas de la aplicación Android usan
`MAJOR.MINOR.PATCH` y se publican como GitHub Releases con etiqueta `vX.Y.Z`.
El historial visible dentro de la app se lee directamente de esas Releases.

## [1.2.0] - 2026-11-09

### Añadido

- Sección **«Desde nuestro canal»** en la barra inferior, con pantalla propia
  Capacitor y la misma identidad visual del periódico.
- Feed JSON versionado en `data/channel/` para las publicaciones del canal
  oficial de WhatsApp, sin scraping ni tokens en el APK.
- Tarjetas de texto, imagen, vídeo, enlace y comunicado, con filtros,
  paginación bajo demanda y enlaces externos por Intent HTTPS.
- Caché local del índice y las páginas, con aviso de copia guardada cuando no
  hay conexión.
- Marcas «Nuevo» y «Actualizado» calculadas en el dispositivo, sin simular push.
- Unificación con artículos de Blogger por URL, título y similitud de contenido,
  con revisión manual para coincidencias medias.
- CLI `channel-admin` para listar, añadir, editar, eliminar, construir, revisar,
  validar y publicar el feed mediante pull request.
- 10 pruebas del feed y validación del JSON en CI.

### Cambiado

- `versionName` pasa de `1.1.2` a `1.2.0`; `versionCode` pasa de `4` a `5`.
- El feed puede actualizarse sin publicar una nueva versión de la aplicación.

### Limitaciones

- WhatsApp no ofrece una API pública de lectura de canales; las publicaciones se
  registran manualmente con el CLI.
- El feed está vacío hasta que el equipo registre contenido real.
- No hay notificaciones push: solo marcas locales de novedad.

## [1.1.0] - 2026-11-07

### Añadido

- Sistema de actualizaciones in-app que consulta las Releases estables de
  GitHub, con caché de 6 horas y reintento controlado.
- Notificación local de nueva versión (una por versión) y permiso de
  notificaciones en Android 13+.
- Pantalla **Novedades** con logotipo oficial, versión instalada, versión
  publicada, fecha, notas reales, progreso de descarga e historial de Releases.
- Descarga del APK con verificación de SHA-256, paquete, `versionCode`,
  `versionName` y firma antes de abrir el instalador de Android.
- Aviso discreto en la cabecera y opción «Más tarde».
- Flujos de GitHub Actions para CI y para publicar Releases firmadas.
- Documentación de repositorio, ramas, versionado, secretos, publicación y
  recuperación ante fallos.
- Fuente única de versión en `mobile/android/version.properties` y script
  `npm run version:bump`.
- Pruebas unitarias de comparación de versiones, caché, SHA-256, lectura de
  Releases y verificación de APK.

### Cambiado

- `versionName` pasa de `1.0.0` a `1.1.0`; `versionCode` pasa de `1` a `2`.
- La sección de notificaciones de la pantalla Info describe el canal real de
  actualizaciones.

### Seguridad

- No se incluyen tokens de GitHub en la APK.
- Las descargas solo se aceptan desde el repositorio oficial y hosts de
  descarga de GitHub.
- Las notas de la Release se muestran como texto plano.
- La instalación nunca es silenciosa y no se permiten downgrades.

## [1.0.0] - 2026-11-07

### Añadido

- Primera entrega del rediseño Android: cabecera azul, navegación
  Inicio/Secciones/Buscar/Info, splash real, iconos oficiales, búsqueda con
  chips, secciones reales, pull-to-refresh, estados de error y conexión,
  pantalla Info y adaptación a orientación vertical/apaisada.
- APK debug verificada en Samsung Galaxy A12 (Android 12).
