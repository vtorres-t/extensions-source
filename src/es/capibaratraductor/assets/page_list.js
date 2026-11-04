(async function () {
    function isPages(list) {
        return Array.isArray(list) && list.length > 0 && list.every(function (page) {
            return page && typeof page.imageUrl === 'string' && /^https?:/.test(page.imageUrl) && typeof page.number === 'number' && typeof page.chapterId === 'number';
        });
    }

    function getPages() {
        var pages = null;
        function walk(fiber) {
            for (; fiber; fiber = fiber.sibling) {
                for (var hook = fiber.memoizedState; hook && typeof hook === 'object' && 'next' in hook && 'queue' in hook; hook = hook.next) {
                    if (isPages(hook.memoizedState) && (!pages || hook.memoizedState.length > pages.length)) pages = hook.memoizedState;
                }
                if (fiber.child) walk(fiber.child);
            }
        }
        document.querySelectorAll('*').forEach(function (element) {
            Object.keys(element).forEach(function (key) {
                if (key.startsWith('__reactContainer$') || key.startsWith('__reactFiber$') || key.indexOf('__reactContainer$') === 0) {
                    var fiber = element[key];
                    walk(fiber.stateNode && fiber.stateNode.current || fiber);
                }
            });
        });
        return pages;
    }

    // Bucle de reintento: Espera hasta 5 segundos (50 intentos x 100ms) a que React monte las imágenes
    for (var i = 0; i < 50; i++) {
        var foundPages = getPages();
        if (foundPages && foundPages.length > 0) {
            var urls = foundPages.map(function (page) { return page.imageUrl; });
            if (urls.length > 0) {
                return urls;
            }
        }
        // Pausa de 100 milisegundos antes del próximo intento
        await new Promise(resolve => setTimeout(resolve, 100));
    }

    return null;
})();
