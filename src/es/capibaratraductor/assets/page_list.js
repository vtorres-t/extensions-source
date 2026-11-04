(function () {
    function isPages(list) {
        return Array.isArray(list) && list.length > 0 && list.every(function (page) {
            return page && typeof page.imageUrl === 'string' && /^https?:/.test(page.imageUrl);
        });
    }

    var urls = [];

    // 1. Intento clásico por árbol de componentes de React
    document.querySelectorAll('*').forEach(function (element) {
        Object.keys(element).forEach(function (key) {
            if (key.startsWith('__reactContainer$') || key.startsWith('__reactFiber$') || key.indexOf('__reactContainer$') === 0) {
                var fiber = element[key];
                var current = fiber.stateNode && fiber.stateNode.current || fiber;
                for (; current; current = current.sibling) {
                    for (var hook = current.memoizedState; hook && typeof hook === 'object'; hook = hook.next) {
                        if (hook.memoizedState && isPages(hook.memoizedState)) {
                            urls = hook.memoizedState.map(function (p) { return p.imageUrl; });
                            if (urls.length > 0) return;
                        }
                    }
                    if (urls.length > 0) return;
                }
            }
        });
        if (urls.length > 0) return;
    });

    if (urls.length > 0) return urls;

    // 2. Extractor de respaldo por fuerza bruta en el DOM (en caso de que React no haya inicializado el árbol)
    var allImages = document.querySelectorAll('img');
    var extracted = [];
    allImages.forEach(function(img) {
        var src = img.src || img.getAttribute('data-src') || img.getAttribute('lazy-src');
        if (src && (src.includes('capibaratraductor.com') || src.includes('r2.')) && src.match(/\.(webp|jpg|jpeg|png)/i)) {
            if (!extracted.includes(src)) extracted.push(src);
        }
    });

    return extracted.length > 0 ? extracted : null;
})();
