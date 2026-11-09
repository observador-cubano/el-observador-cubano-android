# 12 · Riesgos, bloqueos y preguntas para el propietario

---

## 1. Estado del entorno de trabajo

| Elemento | Estado observado |
|---|---|
| Directorio del proyecto | `<directorio-del-proyecto>` — **vacío** |
| Repositorio Git | **No inicializado**; `git` no está instalado o no está en `PATH` |
| Node.js | v24.21.0 disponible |
| npm | 11.19.0 disponible |
| pnpm | No instalado |
| Sistema | Windows |

Antes de la Fase 1 hay que instalar **Git para Windows**, configurar la identidad y crear el
repositorio privado en GitHub. No se ha creado nada todavía.

---

## 2. Matriz de riesgos

Escala: probabilidad (P) e impacto (I) de 1 a 5.

| # | Riesgo | P | I | Mitigación |
|---|---|---:|---:|---|
| R1 | Pérdida de acceso a la cuenta de Blogger o al contenido | 2 | 5 | Exportar XML ahora; copia cifrada; acceso de respaldo del propietario |
| R2 | Dominio no disponible o registrado por terceros | 2 | 5 | Verificar disponibilidad antes de anunciar; registrar variantes |
| R3 | Restricciones legales o de proveedor para donaciones | 4 | 4 | Módulo desactivado; asesoría legal; alternativa con entidad elegible |
| R4 | Ataque o presión contra la infraestructura | 3 | 5 | Cloudflare, backups, proveedores con protección DDoS, plan de incidentes |
| R5 | Fuga de datos de fuentes | 2 | 5 | Minimización, canales externos, formación, sin IP completa, accesos restringidos |
| R6 | Moderación insostenible | 4 | 3 | Moderación previa, reglas públicas, límites, equipo designado |
| R7 | Baja capacidad editorial | 5 | 3 | CMS simple, plantillas, programación, no inventar contenido |
| R8 | Traducción al inglés incompleta | 4 | 3 | Publicar español primero; estado de traducción visible |
| R9 | Problemas de entregabilidad del correo | 3 | 3 | SPF/DKIM/DMARC, calentamiento, rebotes, proveedor con buena reputación |
| R10 | Costes superiores a lo previsto | 2 | 3 | Alertas de presupuesto, planes gratuitos, revisión mensual |
| R11 | Dependencia de un proveedor | 3 | 3 | Adaptadores, exportaciones, documentación de salida |
| R12 | Incumplimiento legal por textos o datos | 3 | 4 | Borradores legales, revisión profesional, minimización |
| R13 | Sesgo o error editorial facilitado por la herramienta | 2 | 4 | Revisión humana obligatoria; no publicación automática de IA |
| R14 | Pérdida de calidad al migrar | 3 | 3 | Simulación, diffs, muestreo, reversión por lotes |
| R15 | Accesibilidad insuficiente | 3 | 3 | Componentes accesibles, axe en CI, revisión manual |
| R16 | Aumento de tráfico sin capacidad de respuesta | 2 | 3 | CDN, caché, ISR, pruebas de carga |

---

## 3. Riesgos legales y de contexto (requieren asesoría)

1. **Donaciones:** la capacidad de una entidad con sede en Cuba o vinculada a Cuba para
   recibir pagos internacionales está condicionada por sanciones, normativa bancaria y
   políticas de las pasarelas. No se asume viabilidad.
2. **Publicidad:** la contratación de anunciantes y la verificación de su origen requieren
   revisión legal y de cumplimiento.
3. **Protección de datos:** el RGPD puede aplicar si hay lectores o suscriptores en la UE.
   También pueden aplicar normas locales de Cuba, España, Estados Unidos u otros países.
4. **Contenido sensible:** nombres de personas privadas, menores, disidentes y familiares
   requieren evaluación caso por caso.
5. **Derechos de autor:** las 50 imágenes y los PDF deben tener licencia o autorización.
6. **Publicidad institucional y transparencia:** la página de financiación debe reflejar
   ingresos reales, no promesas.

**No se afirma cumplimiento legal automático.** Los textos se entregan como borradores
identificados para revisión profesional.

---

## 4. Preguntas para el propietario

Responder estas preguntas desbloquea la Fase 1 y la migración.

### Identidad y dominio
1. ¿Se aprueba `observadorcubano.com` como dominio principal y `observadorcubano.org` como
   defensivo? ¿Hay otro dominio preferido?
2. ¿El nombre y el lema están registrados o se usan de facto? ¿Existe logotipo oficial y
   quién es su autor?
3. ¿Qué elementos de la identidad actual son intocables y cuáles pueden cambiar?

### Organización y legal
4. ¿Qué persona o entidad contratará los servicios (Vercel, Neon, correo, dominio)? ¿En qué
   país está constituida o reside?
5. ¿Se aceptarán donaciones? ¿A través de qué entidad y con qué situación fiscal?
6. ¿Quién revisará los textos legales y en qué jurisdicción?
7. ¿Existe una política interna de correcciones, conflicto de intereses y publicidad?

### Equipo y permisos
8. ¿Quiénes forman el equipo y qué rol tendrá cada persona (`OWNER`, `ADMIN`, editor jefe,
   editor, periodista, traductor, moderador)?
9. ¿Quién aprueba la publicación y quién puede publicar en caso de urgencia?
10. ¿Los colaboradores externos tendrán cuenta o firmarán solo como autores?

### Contenido y migración
11. ¿Puede exportarse el XML de Blogger y facilitarse una copia?
12. ¿Qué contenido de WhatsApp se quiere republicar y en qué formato se puede aportar?
13. ¿Existen imágenes originales en alta resolución y documentos de autorización?
14. ¿Cuántas ediciones impresas hay y dónde están los PDF?
15. ¿Existe archivo de audio de radio? ¿Programas, fechas, presentadores, portadas?
16. ¿Se quieren migrar los comentarios de Blogger?

### Idioma y audiencia
17. ¿Quién traducirá al inglés y quién aprobará cada traducción?
18. ¿Se aceptarán traducciones asistidas por IA? (Se propone que solo como borrador.)
19. ¿Qué idiomas adicionales se prevén a medio plazo?

### Producto y operación
20. ¿Se quiere moderación previa o posterior en los comentarios?
21. ¿Hay anunciantes comprometidos o la publicidad se lanzará desactivada?
22. ¿Qué métricas son prioritarias para la dirección editorial?
23. ¿Hay una fecha de lanzamiento objetivo o algún evento que condicione el calendario?
24. ¿Quién atenderá soporte, correo de contacto y redes sociales?
25. ¿Se autoriza el uso de herramientas de analítica sin cookies y de un proveedor de correo
   externo?

---

## 5. Decisiones que necesitan aprobación expresa

| Decisión | Consecuencia si no se aprueba |
|---|---|
| Comprar el dominio | No se puede lanzar con dominio propio |
| Crear cuentas en Vercel, Neon, R2, correo y GitHub | No se puede desplegar |
| Instalar Git y crear repositorio privado | No se puede versionar el código |
| Aprobar arquitectura y modelo de datos | No se inicia la Fase 1 |
| Aprobar la propuesta visual | No se implementan plantillas definitivas |
| Definir categorías y autores | No se puede migrar con calidad |
| Decidir sobre donaciones | El módulo queda desactivado |
| Aprobar política de comentarios | No se activan comentarios |

---

## 6. Lo que **no** se hará sin autorización

- Comprar dominios, planes o servicios.
- Cambiar DNS.
- Enviar campañas de correo reales.
- Procesar pagos reales.
- Publicar contenido migrado en producción.
- Modificar o borrar el blog actual.
- Extraer contenido de WhatsApp por medios no autorizados.
- Publicar textos legales como definitivos.
- Afirmar que una prueba se ha ejecutado si no se ha ejecutado.

---

## 7. Siguiente paso

1. Revisar el [informe de Fase 0](00-informe-fase-0.md).
2. Responder a las preguntas de la sección 4.
3. Aprobar o corregir las decisiones D1–D8.
4. Autorizar la preparación del entorno (Git, repositorio, cuentas).
5. Con la aprobación, comenzar la **Fase 1 — Fundación**.
