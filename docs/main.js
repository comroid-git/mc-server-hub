const urlParams = new URLSearchParams(window.location.search);
const debug = window.location.href.startsWith('http://localhost');
const pathPrefix = debug ? '/mcsd/docs' : '';
const evals = []

async function fetchText(url) {
    return await (await fetch(url)).text()
}
async function fetchJson(url) {
    return await (await fetch(url)).json()
}

async function load() {
    const contentBox = document.querySelector('div.ui-content');

    function clearContent() {
        contentBox.innerHTML = '';
    }

    // connect to panel or login
    async function determineContent() {
        let page = urlParams.get('page');
        if (page === undefined || page === null || page === '')
            page = 'dash';
        let path = pathPrefix + '/frame/' + page + '.html';
        contentBox.innerHTML = await fetchText(path);
        for (const script of document.querySelectorAll('div.ui-content script[type="application/javascript"]')) {
            if (evals.includes(page))
                continue;
            let code = null;
            if (script.src !== undefined && script.src !== null && script.src !== '') {
                code = await fetchText(script.src);
            } else code = script.innerHTML;
            evals.push(page);
            eval(code);
        }
    }

    // prepare document
    function prepareContent() {
        for (const container of document.querySelectorAll('b.inject')) {
            let expr = container.classList[1];
            // noinspection JSPrimitiveTypeWrapperUsage
            container.innerHTML = new Function('return ' + expr)();
        }
    }

    await determineContent();
    prepareContent();
}

async function unload() {
}
