# Historial de cambios

Todas las versiones distribuidas de la aplicación Android usan
`MAJOR.MINOR.PATCH` y se publican como GitHub Releases con etiqueta `vX.Y.Z`.
El historial visible dentro de la app se lee directamente de esas Releases.

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
