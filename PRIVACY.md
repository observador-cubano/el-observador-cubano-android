# Política de privacidad

**Aplicación:** El Observador Cubano (`cu.observadorcubano.app`)
**Última actualización:** 9 de noviembre de 2026

El Observador Cubano es una aplicación Android que muestra el contenido público
del periódico digital <https://observadorcubano.blogspot.com/>. Esta política
describe qué datos maneja la aplicación y cuáles se comunican con terceros.

## 1. Responsable

El proyecto está mantenido por **El Observador Cubano** como identidad
institucional. No se publican datos personales del propietario ni de los
colaboradores. Las vías de contacto son las publicadas en el sitio web oficial y
el repositorio de GitHub del proyecto.

## 2. Datos que la aplicación guarda en el dispositivo

La aplicación no crea cuentas ni pide datos personales. Guarda únicamente
preferencias y datos técnicos en el almacenamiento privado de la app:

| Dato | Finalidad | Dónde |
| --- | --- | --- |
| Tamaño del texto y modo de enlaces | Recordar las preferencias de lectura | `SharedPreferences` / almacenamiento local |
| Sección y última visita | Restaurar la navegación | `SharedPreferences` |
| Última comprobación de versión, última Release conocida, aviso ya mostrado y «Más tarde» | Evitar consultas y avisos repetidos | `SharedPreferences` |
| Caché de la WebView | Cargar el sitio con menos datos | Almacenamiento interno de Android |

Estos datos permanecen en el dispositivo, no se envían al proyecto y se borran al
desinstalar la aplicación o al limpiar sus datos desde Android. La app tiene
`android:allowBackup="false"`, por lo que no se incluyen en las copias de
seguridad de Android.

## 3. Comunicaciones de red

La aplicación solo se conecta a:

1. **`observadorcubano.blogspot.com`** para mostrar el contenido del periódico.
   Es un servicio de Google (Blogger). Google puede registrar la dirección IP,
   el agente de usuario y cookies técnicas conforme a sus propias políticas.
2. **`api.github.com`** para comprobar si hay una versión nueva. La consulta es
   anónima: no incluye cuenta, token, identificador del dispositivo ni datos
   personales. GitHub recibe la IP y el agente de usuario de la petición.
3. **`github.com`** solo si el usuario decide descargar un APK o abrir una
   Release en el navegador.

La aplicación **no** usa analítica propia, publicidad, rastreadores, servicios de
mensajería ni un servidor del proyecto. No hay notificaciones push: las
notificaciones de actualización se generan localmente en el dispositivo.

## 4. Cookies

La WebView acepta cookies de primera parte necesarias para el funcionamiento del
sitio de Blogger y **rechaza cookies de terceros**. La aplicación no accede al
contenido de esas cookies ni lo transmite a terceros.

## 5. Permisos de Android

| Permiso | Por qué se solicita |
| --- | --- |
| `INTERNET` y `ACCESS_NETWORK_STATE` | Cargar el sitio y comprobar actualizaciones. |
| `POST_NOTIFICATIONS` (Android 13+) | Avisar de una versión nueva. Solo se pide cuando existe una actualización disponible y el usuario puede rechazarlo. |
| `REQUEST_INSTALL_PACKAGES` | Abrir el instalador de Android para una actualización descargada. La instalación siempre requiere confirmación del usuario. |

La aplicación no solicita acceso a cámara, micrófono, ubicación, contactos,
almacenamiento externo, teléfono ni SMS.

## 6. Actualizaciones

El actualizador consulta las Releases estables del repositorio público de GitHub,
descarga el APK y comprueba su SHA-256, el paquete, la versión y la firma antes de
abrir el instalador del sistema. La aplicación **no instala nada en silencio** ni
evade las restricciones de Android. Véase `UPDATE_SYSTEM.md`.

## 7. Contenido y enlaces externos

El contenido editorial es responsabilidad de El Observador Cubano y se publica en
Blogger. Los enlaces externos se abren en el navegador o en la aplicación
correspondiente y quedan sujetos a las políticas de esos sitios. El proyecto no
controla ni recibe los datos que esos sitios puedan recoger.

## 8. Menores

La aplicación ofrece contenido informativo general y no está dirigida
específicamente a menores. No recopila datos de ningún usuario.

## 9. Cambios en esta política

Cualquier cambio se publicará en este archivo dentro del repositorio y se
reflejará en la fecha de «Última actualización». Los cambios relevantes se
mencionarán en las notas de la versión correspondiente.

## 10. Limitaciones de privacidad

Ninguna aplicación puede garantizar anonimato absoluto frente a los proveedores
de red: el sitio de Blogger y GitHub ven las peticiones que reciben, como en
cualquier visita web. Las versiones antiguas del APK y del historial de Git
pueden seguir siendo accesibles públicamente; véase `SECURITY.md`.
