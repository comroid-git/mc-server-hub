function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

async function requestServerStatus(server) {
    return fetch('/server/status/' + server)
        .then(r => r.json());
}

async function start() {
    // noinspection InfiniteLoopJS
    while (true) {
        // update server entries
        let serverEntries = document.getElementsByClassName('serverEntry');
        for (let i = 0; i < serverEntries.length; i++) {
            let entry = serverEntries[i];
            let id = entry.getElementsByClassName('serverEntryId')[0].innerText;
            let status = entry.getElementsByClassName('serverEntryStatus')[0]
                .getElementsByTagName('div')[0];
            let response = await requestServerStatus(id);
            status.className = 'serverStatus' + response.status;
        }

        await sleep(10_000);
    }
}