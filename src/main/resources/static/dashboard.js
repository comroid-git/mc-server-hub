function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

async function requestServerStatus(server) {
    return Promise.race([
        fetch('/server/status/' + server).then(r => r.json()),
        new Promise((_, reject) =>
            setTimeout(() => reject(new Error('timeout')), 3_000)
        )
    ]);
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
            requestServerStatus(id).then(response => status.className = 'serverStatus' + response.status);
        }

        await sleep(10_000);
    }
}