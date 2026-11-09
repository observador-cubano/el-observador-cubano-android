# «Desde nuestro canal» — feed del canal de WhatsApp

**Estado:** implementado en la aplicación 1.2.0 (`versionCode 5`).
**Canal oficial:** <https://whatsapp.com/channel/0029Vb73VkN7tkj5MlScKL1k>
**Última actualización:** 9 de noviembre de 2026.

Esta sección muestra dentro de la app las publicaciones que el medio comparte en
su canal oficial de WhatsApp. No usa scraping, no simula sesiones de WhatsApp y
no guarda tokens de GitHub en el APK.

---

## 1. Por qué no hay integración automática

WhatsApp Channels no ofrece una API pública de lectura de publicaciones:

- La **WhatsApp Business Platform / Cloud API** está pensada para enviar y
  recibir mensajes de clientes, no para leer canales.
- Los canales no exponen un feed público documentado ni permisos de lectura
  para terceros.
- La página web del canal requiere sesión de WhatsApp y sus términos no permiten
  la extracción automatizada.

Por eso se usa un **feed editorial propio en JSON versionado**, publicado en el
repositorio. La app lo presenta como «Desde nuestro canal», no como una
sincronización automática de WhatsApp. Cuando el equipo publica en WhatsApp,
registra la misma nota con el CLI y regenera el feed.

---

## 2. Arquitectura

```text
data/channel/posts.json        Publicaciones registradas por el equipo
        │
        ├── channel-admin.mjs build ──► index.json
        │                               page-1.json, page-2.json, ...
        │
        └── decisions.json         Revisiones manuales de coincidencias

GitHub (rama main)
        │
        ├── raw.githubusercontent.com/.../data/channel/   (fuente primaria)
        └── cdn.jsdelivr.net/gh/.../data/channel/         (respaldo)

App Android
        └── ChannelActivity ──► channel.html + channel.ts
                ├── caché en Preferences
                ├── paginación y filtros
                └── enlaces abiertos con Intent HTTPS
```

- El **APK no contiene las publicaciones** ni necesita una versión nueva cuando
  cambia el feed. Solo descarga `index.json` y las páginas.
- La app valida `schemaVersion`, el número de páginas y la forma de cada página
  antes de mostrarla.
- La caché local permite abrir la sección sin conexión y muestra el aviso
  «Sin conexión. Se muestra la copia guardada».
- El feed se sirve desde GitHub, por lo que su actualización pasa por revisión y
  CI, igual que el resto del repositorio.

### Estructura de archivos

| Archivo | Contenido |
|---|---|
| `data/channel/posts.json` | Fuente editable con todas las publicaciones. |
| `data/channel/decisions.json` | Decisiones manuales `linked` / `separated` por publicación. |
| `data/channel/index.json` | Índice generado: canal, fecha, tamaño de página, total y nombres de página. |
| `data/channel/page-N.json` | Elementos paginados que consume la app. |

`data/channel/.cache/` guarda la copia de Blogger usada para detectar
duplicados. Está ignorada por Git.

### Pantalla

- `mobile/channel.html`: estructura de la sección.
- `mobile/src/channel.ts`: carga, caché, filtros, paginación, badges y enlaces.
- `mobile/src/channel.css`: estilos integrados con la identidad existente.
- `mobile/android/.../ChannelActivity.java`: actividad Capacitor que sirve
  `https://localhost/channel.html`.
- `mobile/android/.../MainActivity.java`: botón «Canal» de la barra inferior.
- `mobile/android/.../ShellPlugin.java`: `openExternal` con `Intent.ACTION_VIEW`
  para abrir WhatsApp o el navegador del sistema.

La WebView del periódico (Blogger) sigue sin puentes JavaScript-nativos. El
puente `Shell` solo se registra en `InfoActivity` y `ChannelActivity`.

---

## 3. Administración del feed

Todos los comandos se ejecutan desde `mobile/`.

```bash
# Ver publicaciones registradas
npm run channel:list

# Añadir una publicación de forma interactiva
npm run channel:add

# Alta no interactiva (útil para automatizaciones)
node tools/channel-admin.mjs add --text "Texto" --link "https://..." --type link

# Editar o eliminar
node tools/channel-admin.mjs edit 2026-11-09-titulo
node tools/channel-admin.mjs remove 2026-11-09-titulo

# Regenerar el feed y validarlo
npm run channel:build
npm run channel:validate

# Ver y resolver coincidencias dudosas con Blogger
npm run channel:review
node tools/channel-admin.mjs link <postId>
node tools/channel-admin.mjs separate <postId>

# Probar el feed sin red (usa la caché de Blogger)
node tools/channel-admin.mjs build --skip-blogger

# Publicar: crea rama, commit, push y pull request
npm run channel:publish -- --message "canal: nuevas publicaciones"
```

`channel:publish` **no** crea una Release ni sube el `versionCode`. Si el feed no
cambió, no abre un pull request.

### Tipos de publicación

| Tipo | Uso |
|---|---|
| `text` | Nota breve sin adjuntos. |
| `image` | Texto con una imagen HTTPS. |
| `video` | Texto con vídeo HTTPS y miniatura opcional. |
| `link` | Texto que enlaza a una noticia del periódico u otra fuente. |
| `comunicado` | Comunicado oficial del medio. |

Las imágenes y los vídeos deben estar alojados en un servicio HTTPS estable
(Blogger, GitHub o CDN del medio). El feed no copia archivos multimedia.

### Ejemplo de publicación

```json
{
  "id": "2026-11-09-ejemplo-de-nota",
  "type": "link",
  "publishedAt": "2026-11-09T14:30:00.000Z",
  "updatedAt": "2026-11-09T14:30:00.000Z",
  "title": "Título opcional",
  "text": "Texto que se muestra en la sección.",
  "whatsappUrl": "https://whatsapp.com/channel/0029Vb73VkN7tkj5MlScKL1k/abc123",
  "links": ["https://observadorcubano.blogspot.com/2026/11/una-noticia.html"],
  "image": { "url": "https://.../foto.jpg", "alt": "Descripción" },
  "status": "published"
}
```

Reglas de validación:

- `id`: minúsculas, números y guiones, entre 3 y 80 caracteres, sin duplicados.
- `publishedAt`: fecha ISO válida.
- `text`: al menos 4 caracteres.
- `whatsappUrl`, `image.url`, `video.url` y enlaces: solo HTTPS.
- `status`: `published` o `draft` (los borradores no se publican en el feed).

---

## 4. Unificación con las noticias de Blogger

El build consulta el feed público de Blogger y compara cada publicación del
canal con los artículos recientes:

| Señal | Confianza | Resultado |
|---|---|---|
| La publicación enlaza exactamente la URL del artículo | 100 % | Se unifica automáticamente. |
| El título normalizado coincide | 93 % | Se unifica automáticamente. |
| Contenido muy similar | 90 % | Se unifica automáticamente. |
| Contenido parecido | 78 % | Queda **pendiente de revisión**. |
| Sin relación suficiente | — | Se muestra solo como canal. |

La comparación ignora acentos, mayúsculas, puntuación, palabras vacías del
español y parámetros de seguimiento (`utm_*`, `fbclid`, etc.). Los parámetros que
identifican un recurso distinto se conservan.

Cuando varias publicaciones del canal apuntan al mismo artículo, se agrupan en
una sola tarjeta y se conservan todos los enlaces originales de WhatsApp.

Las coincidencias medias no se publican como unificadas: aparecen con una marca
interna `pending` hasta que una persona ejecute `link` o `separate`. Esa decisión
se guarda en `decisions.json` y se aplica en el siguiente build.

---

## 5. Comportamiento en la app

- **Filtros:** Todo, Texto, Fotos, Vídeos, Enlaces y Comunicados.
- **Paginación:** carga la primera página y pide más bajo demanda.
- **Caché:** índice y páginas guardados en `Preferences`; se reutilizan si no hay
  red.
- **Actualización:** al abrir la sección y al volver a ella después de cinco
  minutos. También hay un botón «Actualizar».
- **Badges:** «Nuevo» para publicaciones posteriores a la última visita y
  «Actualizado» cuando cambia el `updatedAt` de una publicación ya vista. Son
  marcas locales, no notificaciones push.
- **Enlaces:** se abren con un Intent HTTPS; si WhatsApp está instalado y
  reconoce el dominio, abre la app; si no, el navegador.
- **Estados:** cargando, vacío, error con reintento y sin conexión con copia
  guardada.

No se generan notificaciones push porque no hay infraestructura de servidor. La
app no simula push ni duplica avisos.

---

## 6. Privacidad y seguridad

- No hay tokens de GitHub, claves ni credenciales en el APK.
- La publicación se hace desde un clon local con permisos de escritura y `gh`.
- Solo se aceptan enlaces HTTPS.
- El feed se valida en CI (`npm run test:channel` y `npm run channel:validate`).
- La app no lee mensajes de WhatsApp, no accede a contactos y no simula una
  sesión.
- Las peticiones al feed son anónimas. GitHub y jsDelivr reciben la IP y el
  agente de usuario, como cualquier descarga web.
- Abrir WhatsApp es una acción del usuario y queda sujeta a la política de
  WhatsApp/Meta.

Ver también [`PRIVACY.md`](PRIVACY.md) y [`SECURITY.md`](SECURITY.md).

---

## 7. Pruebas y verificación

```bash
cd mobile
npm run test:channel       # 10 pruebas de normalización, unificación y paginación
npm run channel:validate   # coherencia entre posts.json, index.json y páginas
npm run build              # TypeScript + Vite (incluye channel.html)
cd android
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Comprobación manual en dispositivo:

1. Abrir la app y pulsar **Canal** en la barra inferior.
2. Verificar la carga del feed (o el estado vacío si aún no hay publicaciones).
3. Probar los filtros y «Cargar más publicaciones» cuando haya más de una página.
4. Activar modo avión y comprobar la copia guardada y el aviso sin conexión.
5. Pulsar «Seguir el canal» y comprobar que abre WhatsApp o el navegador.
6. Volver con el botón Atrás del sistema y comprobar que regresa al periódico.

---

## 8. Limitaciones conocidas

1. **No es una sincronización automática:** cada publicación se registra a mano.
2. **No hay API oficial:** no se debe presentar como integración de Meta.
3. **Sin push:** solo badges locales; no hay servidor de notificaciones.
4. **Feed vacío hasta que se registren publicaciones reales.**
5. Los medios deben estar alojados fuera del feed; no se copian archivos.
6. La unificación es heurística: las coincidencias medias requieren revisión.
7. Si GitHub y jsDelivr no están disponibles, la app usa la última copia local.

---

## 9. Reversión

Para retirar una publicación del feed:

```bash
node tools/channel-admin.mjs remove <postId>
npm run channel:build
npm run channel:validate
npm run channel:publish -- --message "canal: retirar publicación"
```

Para desactivar un elemento sin borrarlo, se puede cambiar su `status` a
`draft` y regenerar. La sección seguirá visible; si el feed queda vacío, muestra
el estado «Aún no hay publicaciones».
