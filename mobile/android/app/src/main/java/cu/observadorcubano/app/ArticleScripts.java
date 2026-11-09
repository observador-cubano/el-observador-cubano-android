package cu.observadorcubano.app;

/**
 * Guiones inyectados en la WebView del periódico.
 *
 * Se ejecutan con evaluateJavascript y sólo devuelven datos o modifican la
 * presentación local. No exponen ningún puente JavaScript-nativo al sitio.
 */
final class ArticleScripts {

    private ArticleScripts() {
        // Clase de utilidades.
    }

    /** Ajustes mínimos de ancho para cualquier página interna. */
    static final String BASE_CSS_JS =
        "(function(){try{if(document.getElementById('eoc-reader-style'))return;"
            + "var s=document.createElement('style');s.id='eoc-reader-style';"
            + "s.textContent='html{-webkit-text-size-adjust:100%}"
            + "body{overflow-x:hidden}"
            + "img{max-width:100%;height:auto}"
            + "article,.post-body,.post{overflow-wrap:break-word}';"
            + "document.head.appendChild(s);}catch(e){}})()";

    /**
     * Indica si la página actual parece un artículo legible y devuelve su
     * título. Las portadas, etiquetas y búsquedas quedan excluidas.
     */
    static final String PAGE_INFO_JS = """
        (function(){
          try {
            var path = (location.pathname || '').toLowerCase();
            var blocked = path === '' || path === '/' || path.indexOf('/search') === 0
              || path.indexOf('/feeds') === 0;
            var allBodies = document.querySelectorAll('.post-body,[itemprop="articleBody"],.entry-content');
            var length = 0;
            var totalLength = 0;
            var best = null;
            for (var i = 0; i < allBodies.length; i++) {
              if (allBodies[i].closest('#eoc-reader-overlay')) continue;
              var text = allBodies[i].innerText || allBodies[i].textContent || '';
              totalLength += text.length;
              if (text.length > length) {
                length = text.length;
                best = allBodies[i];
              }
            }
            var title = '';
            if (best) {
              var container = best.closest('article') || best.closest('.post-outer') || best.parentElement;
              if (container) {
                var titleNode = container.querySelector('h1[itemprop="headline"]')
                  || container.querySelector('.post-title')
                  || container.querySelector('.entry-title')
                  || container.querySelector('h1');
                if (titleNode) {
                  title = (titleNode.innerText || titleNode.textContent || '').trim();
                }
              }
            }
            if (!title) title = (document.title || '').trim();
            return {
              article: !blocked && length > 250 && length * 10 >= totalLength * 6,
              title: title,
              path: path
            };
          } catch (e) {
            return { article: false, title: '', path: '' };
          }
        })()
        """;

    /**
     * Extrae el contenido del artículo para guardarlo sin conexión. Devuelve
     * HTML ya limpio, con enlaces absolutos y sin scripts ni controles.
     */
    static final String EXTRACT_JS = """
        (function(){
          try {
            var selectors = ['.post-body','[itemprop="articleBody"]','.entry-content'];
            var best = null;
            var bestLength = 0;
            for (var i = 0; i < selectors.length; i++) {
              var nodes = document.querySelectorAll(selectors[i]);
              for (var j = 0; j < nodes.length; j++) {
                if (nodes[j].closest('#eoc-reader-overlay')) continue;
                var text = (nodes[j].innerText || nodes[j].textContent || '').replace(/\\s+/g, ' ');
                if (text.length > bestLength) {
                  bestLength = text.length;
                  best = nodes[j];
                }
              }
            }
            if (!best || bestLength < 250) {
              var articles = document.querySelectorAll('article');
              for (var t = 0; t < articles.length; t++) {
                if (articles[t].closest('#eoc-reader-overlay')) continue;
                var articleText = (articles[t].innerText || articles[t].textContent || '')
                  .replace(/\\s+/g, ' ');
                if (articleText.length > bestLength) {
                  bestLength = articleText.length;
                  best = articles[t];
                }
              }
            }
            if (!best || bestLength < 250) return null;

            var container = best.closest('article')
              || best.closest('.post-outer')
              || best.parentElement;

            var clone = best.cloneNode(true);
            var removeSelector = 'script,style,iframe,noscript,form,button,input,textarea,select,'
              + '.share-buttons,.post-share-buttons,.related-posts,.comments,.comment-form,'
              + '.blog-pager,.adsbygoogle,ins.adsbygoogle';
            var removable = clone.querySelectorAll(removeSelector);
            for (var k = removable.length - 1; k >= 0; k--) {
              if (removable[k].parentNode) removable[k].parentNode.removeChild(removable[k]);
            }

            function absolute(value) {
              try { return new URL(value, location.href).href; } catch (e) { return value; }
            }

            var images = clone.querySelectorAll('img');
            for (var m = 0; m < images.length; m++) {
              var image = images[m];
              var source = image.getAttribute('data-src')
                || image.getAttribute('data-original')
                || image.getAttribute('data-lazy-src')
                || image.getAttribute('src')
                || '';
              if (source) image.setAttribute('src', absolute(source));
              image.removeAttribute('srcset');
              image.removeAttribute('sizes');
              image.removeAttribute('loading');
              image.removeAttribute('data-src');
              image.removeAttribute('data-original');
              image.removeAttribute('data-lazy-src');
              image.removeAttribute('width');
              image.removeAttribute('height');
              image.removeAttribute('style');
            }

            var links = clone.querySelectorAll('a');
            for (var n = 0; n < links.length; n++) {
              var href = links[n].getAttribute('href') || '';
              if (href) links[n].setAttribute('href', absolute(href));
              links[n].removeAttribute('target');
            }

            var all = clone.querySelectorAll('*');
            for (var p = 0; p < all.length; p++) {
              var attributes = all[p].attributes;
              for (var q = attributes.length - 1; q >= 0; q--) {
                var name = attributes[q].name || '';
                if (name.indexOf('on') === 0 || name.indexOf('data-') === 0) {
                  all[p].removeAttribute(name);
                }
              }
              all[p].removeAttribute('style');
              all[p].removeAttribute('id');
            }

            var title = '';
            if (container) {
              var titleNode = container.querySelector('h1[itemprop="headline"]')
                || container.querySelector('.post-title')
                || container.querySelector('.entry-title')
                || container.querySelector('h1');
              if (titleNode) {
                title = (titleNode.innerText || titleNode.textContent || '').trim();
              }
            }
            if (!title) {
              title = (document.title || '').replace(/\\s*[|\\-–—]\\s*EL OBSERVADOR CUBANO\\s*$/i, '').trim();
            }

            var normalizedTitle = title.replace(/\\s+/g, ' ').trim();
            var cloneHeading = clone.querySelector('h1');
            if (cloneHeading && normalizedTitle
              && (cloneHeading.innerText || cloneHeading.textContent || '')
                .replace(/\\s+/g, ' ').trim() === normalizedTitle) {
              if (cloneHeading.parentNode) cloneHeading.parentNode.removeChild(cloneHeading);
            }

            var byline = '';
            if (container) {
              var bylineNode = container.querySelector('[itemprop="datePublished"]')
                || container.querySelector('.post-timestamp')
                || container.querySelector('time')
                || container.querySelector('.post-header');
              if (bylineNode) {
                byline = (bylineNode.innerText || bylineNode.textContent || '')
                  .replace(/\\s+/g, ' ')
                  .replace(/^[\\s\\-–—·|]+/, '')
                  .replace(/[\\s\\-–—·|]+$/, '')
                  .trim();
              }
            }

            var html = clone.innerHTML || '';
            if (html.replace(/\\s+/g, '').length < 150) return null;
            return { title: title, byline: byline, html: html, url: location.href };
          } catch (e) {
            return null;
          }
        })()
        """;

    private static final String OPEN_JS = """
        (function(dark){
          try {
            var css = `__CSS__`;
            var style = document.getElementById('eoc-reader-overlay-style');
            if (!style) {
              style = document.createElement('style');
              style.id = 'eoc-reader-overlay-style';
              (document.head || document.documentElement).appendChild(style);
            }
            style.textContent = css;
            document.documentElement.setAttribute('data-eoc-theme', dark ? 'dark' : 'light');

            var previous = document.getElementById('eoc-reader-overlay');
            if (previous && previous.parentNode) previous.parentNode.removeChild(previous);

            var selectors = ['.post-body','[itemprop="articleBody"]','.entry-content'];
            var best = null;
            var bestLength = 0;
            for (var i = 0; i < selectors.length; i++) {
              var nodes = document.querySelectorAll(selectors[i]);
              for (var j = 0; j < nodes.length; j++) {
                if (nodes[j].closest('#eoc-reader-overlay')) continue;
                var text = (nodes[j].innerText || nodes[j].textContent || '');
                if (text.length > bestLength) {
                  bestLength = text.length;
                  best = nodes[j];
                }
              }
            }
            if (!best || bestLength < 250) {
              var articles = document.querySelectorAll('article');
              for (var t = 0; t < articles.length; t++) {
                if (articles[t].closest('#eoc-reader-overlay')) continue;
                var articleText = (articles[t].innerText || articles[t].textContent || '');
                if (articleText.length > bestLength) {
                  bestLength = articleText.length;
                  best = articles[t];
                }
              }
            }
            if (!best || bestLength < 250) return false;

            var container = best.closest('article')
              || best.closest('.post-outer')
              || best.parentElement;

            var title = '';
            if (container) {
              var titleNode = container.querySelector('h1[itemprop="headline"]')
                || container.querySelector('.post-title')
                || container.querySelector('.entry-title')
                || container.querySelector('h1');
              if (titleNode) {
                title = (titleNode.innerText || titleNode.textContent || '').trim();
              }
            }
            if (!title) {
              title = (document.title || '').replace(/\\s*[|\\-–—]\\s*EL OBSERVADOR CUBANO\\s*$/i, '').trim();
            }

            var normalizedTitle = title.replace(/\\s+/g, ' ').trim();

            var byline = '';
            if (container) {
              var bylineNode = container.querySelector('[itemprop="datePublished"]')
                || container.querySelector('.post-timestamp')
                || container.querySelector('time')
                || container.querySelector('.post-header');
              if (bylineNode) {
                byline = (bylineNode.innerText || bylineNode.textContent || '')
                  .replace(/\\s+/g, ' ')
                  .replace(/^[\\s\\-–—·|]+/, '')
                  .replace(/[\\s\\-–—·|]+$/, '')
                  .trim();
              }
            }

            var clone = best.cloneNode(true);
            if (normalizedTitle) {
              var cloneHeading = clone.querySelector('h1,h2');
              if (cloneHeading && (cloneHeading.innerText || cloneHeading.textContent || '')
                .replace(/\\s+/g, ' ').trim() === normalizedTitle) {
                if (cloneHeading.parentNode) {
                  cloneHeading.parentNode.removeChild(cloneHeading);
                }
              }
            }
            var removable = clone.querySelectorAll('script,style,iframe,noscript,form,button,input,textarea,select,'
              + '.share-buttons,.post-share-buttons,.related-posts,.comments,.comment-form,.blog-pager,'
              + '.adsbygoogle,ins.adsbygoogle');
            for (var k = removable.length - 1; k >= 0; k--) {
              if (removable[k].parentNode) removable[k].parentNode.removeChild(removable[k]);
            }

            var images = clone.querySelectorAll('img');
            for (var m = 0; m < images.length; m++) {
              var image = images[m];
              var source = image.getAttribute('data-src')
                || image.getAttribute('data-original')
                || image.getAttribute('data-lazy-src')
                || image.getAttribute('src')
                || '';
              if (source) {
                try { image.setAttribute('src', new URL(source, location.href).href); } catch (e) { }
              }
              image.removeAttribute('srcset');
              image.removeAttribute('sizes');
              image.removeAttribute('loading');
              image.removeAttribute('width');
              image.removeAttribute('height');
            }

            var links = clone.querySelectorAll('a');
            for (var n = 0; n < links.length; n++) links[n].removeAttribute('target');

            var all = clone.querySelectorAll('*');
            for (var p = 0; p < all.length; p++) {
              all[p].removeAttribute('id');
              all[p].removeAttribute('style');
              all[p].removeAttribute('onclick');
            }

            var overlay = document.createElement('div');
            overlay.id = 'eoc-reader-overlay';
            overlay.setAttribute('role', 'dialog');
            overlay.setAttribute('aria-label', 'Modo lectura');

            var bar = document.createElement('div');
            bar.className = 'eoc-reader-bar';
            var close = document.createElement('a');
            close.className = 'eoc-reader-close';
            close.setAttribute('href', 'eoc-reader://close');
            close.textContent = 'Cerrar';
            bar.appendChild(close);

            var inner = document.createElement('div');
            inner.className = 'eoc-reader-inner';

            var kicker = document.createElement('p');
            kicker.className = 'eoc-reader-kicker';
            kicker.textContent = 'El Observador Cubano';
            inner.appendChild(kicker);

            if (title) {
              var heading = document.createElement('h1');
              heading.className = 'eoc-reader-title';
              heading.textContent = title;
              inner.appendChild(heading);
            }
            if (byline) {
              var meta = document.createElement('p');
              meta.className = 'eoc-reader-byline';
              meta.textContent = byline;
              inner.appendChild(meta);
            }

            var content = document.createElement('div');
            content.className = 'eoc-reader-content';
            content.appendChild(clone);
            inner.appendChild(content);

            overlay.appendChild(bar);
            overlay.appendChild(inner);
            document.body.appendChild(overlay);
            document.documentElement.style.overflow = 'hidden';
            document.body.style.overflow = 'hidden';
            overlay.scrollTop = 0;
            var start = __SCROLL__;
            if (start > 0) {
              var applyStart = function() {
                var max = overlay.scrollHeight - overlay.clientHeight;
                if (max > 0) overlay.scrollTop = max * start;
              };
              applyStart();
              setTimeout(applyStart, 150);
              setTimeout(applyStart, 600);
            }
            return true;
          } catch (e) {
            return false;
          }
        })(__DARK__)
        """;

    private static final String CLOSE_JS =
        "(function(){try{var o=document.getElementById('eoc-reader-overlay');"
            + "if(o&&o.parentNode)o.parentNode.removeChild(o);"
            + "document.documentElement.style.overflow='';"
            + "document.body.style.overflow='';return true;}catch(e){return false;}})()";

    private static final String THEME_JS = """
        (function(dark){
          try {
            var id = 'eoc-dark-style';
            var style = document.getElementById(id);
            if (!dark) {
              if (style && style.parentNode) style.parentNode.removeChild(style);
              document.documentElement.setAttribute('data-eoc-theme', 'light');
              return true;
            }
            if (!style) {
              style = document.createElement('style');
              style.id = id;
              (document.head || document.documentElement).appendChild(style);
            }
            style.textContent = `__CSS__`;
            document.documentElement.setAttribute('data-eoc-theme', 'dark');
            return true;
          } catch (e) {
            return false;
          }
        })(__DARK__)
        """;

    static final String SCROLL_STATE_JS = """
        (function(){
          try {
            var overlay = document.getElementById('eoc-reader-overlay');
            var el = overlay;
            var reader = !!overlay;
            if (!el) el = document.scrollingElement || document.documentElement;
            if (!el) return {progress: 0, reader: false};
            var max = el.scrollHeight - el.clientHeight;
            var progress = max > 40 ? (el.scrollTop || 0) / max : 0;
            if (!isFinite(progress) || progress < 0) progress = 0;
            if (progress > 1) progress = 1;
            return {progress: progress, reader: reader};
          } catch (e) {
            return {progress: 0, reader: false};
          }
        })()
        """;

    private static final String RESTORE_PAGE_JS = """
        (function(p){
          try {
            var el = document.scrollingElement || document.documentElement;
            if (!el) return false;
            var max = el.scrollHeight - el.clientHeight;
            if (max > 0) window.scrollTo(0, max * p);
            return true;
          } catch (e) {
            return false;
          }
        })(__PROGRESS__)
        """;

    static String restorePageScroll(double progress) {
        return RESTORE_PAGE_JS.replace(
            "__PROGRESS__",
            String.valueOf(ReadingProgress.clamp(progress))
        );
    }

    static String openReader(boolean dark) {
        return openReader(dark, 0d);
    }

    /**
     * Abre el modo lectura. Si progress es mayor que cero, la vista se coloca
     * en esa proporción del artículo para continuar donde se dejó.
     */
    static String openReader(boolean dark, double progress) {
        return OPEN_JS
            .replace("__CSS__", ReaderStyles.overlayCss())
            .replace("__SCROLL__", String.valueOf(ReadingProgress.clamp(progress)))
            .replace("__DARK__", dark ? "true" : "false");
    }

    static String closeReader() {
        return CLOSE_JS;
    }

    static String applyTheme(boolean dark) {
        return THEME_JS
            .replace("__CSS__", ReaderStyles.darkPageCss())
            .replace("__DARK__", dark ? "true" : "false");
    }
}
