package cu.observadorcubano.app;

/**
 * Hojas de estilo compartidas por el modo lectura, el tema oscuro del sitio
 * remoto y los artículos guardados.
 *
 * No dependen de Android para poder revisarse y probarse con JUnit.
 */
final class ReaderStyles {

    private ReaderStyles() {
        // Clase de utilidades.
    }

    /**
     * CSS inyectado en la página de Blogger cuando el modo oscuro está activo.
     * Se apoya en selectores amplios porque el tema del sitio puede cambiar.
     */
    static String darkPageCss() {
        return """
            html{color-scheme:dark!important}
            html,body{background:#0f141a!important;color:#e8edf3!important}
            body *{background-color:transparent!important;background-image:none!important;border-color:#2a3442!important}
            body{background-color:#0f141a!important}
            body div,body p,body span,body li,body td,body th,body label,body small,body strong,body em,body blockquote,body pre,body code{color:#dbe2ea!important}
            body h1,body h2,body h3,body h4,body h5,body h6,.post-title,.post-title a,.entry-title,.entry-title a{color:#f2f5f9!important}
            body a,body a *{color:#8ab4ff!important}
            body blockquote,body pre,body code{background-color:#151b23!important;border-color:#2a3442!important}
            input,textarea,select{background-color:#151b23!important;color:#e8edf3!important;border-color:#2a3442!important}
            hr{border-color:#2a3442!important}
            img,video,iframe{opacity:1!important;max-width:100%!important}
            """;
    }

    /** CSS del panel de modo lectura que se superpone a la página original. */
    static String overlayCss() {
        return """
            :root{--eoc-reader-bg:#ffffff;--eoc-reader-fg:#17202b;--eoc-reader-muted:#5b6675;--eoc-reader-border:#e3e8f0;--eoc-reader-link:#0649a8;--eoc-reader-soft:#f4f6f9}
            :root[data-eoc-theme="dark"]{--eoc-reader-bg:#0f141a;--eoc-reader-fg:#e8edf3;--eoc-reader-muted:#aab4c2;--eoc-reader-border:#2a3442;--eoc-reader-link:#8ab4ff;--eoc-reader-soft:#151b23}
            #eoc-reader-overlay{position:fixed;inset:0;z-index:2147483646;overflow-y:auto;-webkit-overflow-scrolling:touch;background:var(--eoc-reader-bg)!important;color:var(--eoc-reader-fg)!important;font-family:system-ui,-apple-system,Segoe UI,Roboto,Arial,sans-serif}
            #eoc-reader-overlay *{box-sizing:border-box}
            #eoc-reader-overlay .eoc-reader-bar{position:sticky;top:0;z-index:2;display:flex;justify-content:flex-end;padding:10px 16px;background:var(--eoc-reader-bg)!important;border-bottom:1px solid var(--eoc-reader-border)}
            #eoc-reader-overlay .eoc-reader-close{display:inline-flex;align-items:center;min-height:40px;padding:6px 14px;border-radius:999px;background:var(--eoc-reader-soft)!important;color:var(--eoc-reader-link)!important;font-size:13.5px;font-weight:700;text-decoration:none}
            #eoc-reader-overlay .eoc-reader-inner{max-width:720px;margin:0 auto;padding:22px 20px 80px}
            #eoc-reader-overlay .eoc-reader-kicker{margin:0 0 10px;color:var(--eoc-reader-muted)!important;font-size:12px;font-weight:800;letter-spacing:.08em;text-transform:uppercase}
            #eoc-reader-overlay .eoc-reader-title{margin:0 0 10px;color:var(--eoc-reader-fg)!important;font-size:30px;line-height:1.18;letter-spacing:-.4px}
            #eoc-reader-overlay .eoc-reader-byline{margin:0 0 22px;color:var(--eoc-reader-muted)!important;font-size:13.5px;line-height:1.5}
            #eoc-reader-overlay .eoc-reader-content{color:var(--eoc-reader-fg)!important;font-size:18px;line-height:1.75;overflow-wrap:break-word}
            #eoc-reader-overlay .eoc-reader-content *{max-width:100%;color:inherit!important;background-color:transparent!important;background-image:none!important}
            #eoc-reader-overlay .eoc-reader-content a,#eoc-reader-overlay .eoc-reader-content a *{color:var(--eoc-reader-link)!important;text-decoration:underline}
            #eoc-reader-overlay .eoc-reader-content img,#eoc-reader-overlay .eoc-reader-content video,#eoc-reader-overlay .eoc-reader-content iframe{height:auto!important;margin:14px 0;border-radius:10px}
            #eoc-reader-overlay .eoc-reader-content h1,#eoc-reader-overlay .eoc-reader-content h2,#eoc-reader-overlay .eoc-reader-content h3,#eoc-reader-overlay .eoc-reader-content h4{color:var(--eoc-reader-fg)!important;line-height:1.25;margin:1.4em 0 .55em}
            #eoc-reader-overlay .eoc-reader-content p{margin:0 0 1em}
            #eoc-reader-overlay .eoc-reader-content blockquote{margin:1.4em 0;padding:4px 0 4px 18px;border-left:3px solid var(--eoc-reader-link);color:var(--eoc-reader-muted)!important;font-style:italic}
            #eoc-reader-overlay .eoc-reader-content pre,#eoc-reader-overlay .eoc-reader-content code{background:var(--eoc-reader-soft)!important;border-radius:8px}
            #eoc-reader-overlay .eoc-reader-content pre{overflow-x:auto;padding:12px}
            #eoc-reader-overlay .eoc-reader-content code{padding:2px 5px;font-size:.92em}
            #eoc-reader-overlay .eoc-reader-content pre code{padding:0}
            #eoc-reader-overlay .eoc-reader-content table{display:block;overflow-x:auto}
            """;
    }

    /**
     * Documento completo de un artículo guardado. El tema se aplica con el
     * atributo data-theme, de modo que el mismo archivo sirve para claro y
     * oscuro sin volver a descargarlo.
     */
    static String savedCss() {
        return """
            :root{--eoc-bg:#f4f6f9;--eoc-surface:#ffffff;--eoc-text:#17202b;--eoc-muted:#5b6675;--eoc-border:#e3e8f0;--eoc-link:#0649a8;--eoc-soft:#eaf2ff;color-scheme:light}
            html[data-theme="dark"]{--eoc-bg:#0f141a;--eoc-surface:#151b23;--eoc-text:#e8edf3;--eoc-muted:#aab4c2;--eoc-border:#2a3442;--eoc-link:#8ab4ff;--eoc-soft:#11243f;color-scheme:dark}
            *{box-sizing:border-box}
            html,body{margin:0;padding:0;background:var(--eoc-bg);color:var(--eoc-text)}
            body{font-family:system-ui,-apple-system,Segoe UI,Roboto,Arial,sans-serif;font-size:18px;line-height:1.75;-webkit-text-size-adjust:100%;overflow-wrap:break-word}
            .eoc-page{max-width:760px;min-height:100vh;margin:0 auto;padding:26px 20px 64px;background:var(--eoc-surface)}
            .eoc-kicker{margin:0 0 10px;color:var(--eoc-muted);font-size:12px;font-weight:800;letter-spacing:.08em;text-transform:uppercase}
            .eoc-title{margin:0 0 10px;color:var(--eoc-text);font-size:30px;line-height:1.18;letter-spacing:-.4px}
            .eoc-byline{margin:0 0 20px;color:var(--eoc-muted);font-size:13.5px;line-height:1.5}
            .eoc-content{border-top:1px solid var(--eoc-border);padding-top:20px;font-size:18px;line-height:1.75}
            .eoc-content *{max-width:100%}
            .eoc-content img,.eoc-content video,.eoc-content iframe{height:auto!important;margin:14px 0;border-radius:10px}
            .eoc-content a{color:var(--eoc-link);text-decoration:underline}
            .eoc-content h1,.eoc-content h2,.eoc-content h3,.eoc-content h4{color:var(--eoc-text);line-height:1.25;margin:1.4em 0 .55em}
            .eoc-content p{margin:0 0 1em}
            .eoc-content ul,.eoc-content ol{margin:0 0 1em;padding-left:1.35em}
            .eoc-content blockquote{margin:1.4em 0;padding:4px 0 4px 18px;border-left:3px solid var(--eoc-link);color:var(--eoc-muted);font-style:italic}
            .eoc-content pre,.eoc-content code{background:var(--eoc-soft);border-radius:8px}
            .eoc-content pre{overflow-x:auto;padding:12px}
            .eoc-content code{padding:2px 5px;font-size:.92em}
            .eoc-content pre code{padding:0}
            .eoc-image-note{margin:0 0 20px;padding:11px 14px;border:1px solid var(--eoc-border);border-radius:10px;background:var(--eoc-soft);color:var(--eoc-muted);font-size:13px;line-height:1.5}
            .eoc-image-missing{margin:14px 0;padding:12px 14px;border:1px dashed var(--eoc-border);border-radius:10px;background:var(--eoc-soft);color:var(--eoc-muted);font-size:13px;text-align:center}
            .eoc-content table{display:block;overflow-x:auto;border-collapse:collapse}
            .eoc-content th,.eoc-content td{border:1px solid var(--eoc-border);padding:6px 8px}
            .eoc-footer{margin-top:34px;padding-top:18px;border-top:1px solid var(--eoc-border);color:var(--eoc-muted);font-size:12.5px;line-height:1.5}
            @media (min-width:720px){.eoc-page{padding:40px 36px 80px}.eoc-title{font-size:36px}.eoc-content{font-size:19px}}
            """;
    }
}
