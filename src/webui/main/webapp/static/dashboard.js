function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

async function requestServerStatus(server) {
    return fetch('/server/status/' + server).then(r => r.json());
}

function refreshUI() {
    // update server entries
    let serverEntries = document.getElementsByClassName('serverEntry');
    for (let i = 0; i < serverEntries.length; i++) {
        let entry = serverEntries[i];
        let id = entry.getElementsByClassName('serverEntryId')[0].innerText;
        let status = entry.getElementsByClassName('serverEntryStatus')[0]
            .getElementsByTagName('div')[0];
        let motd = entry.getElementsByClassName('serverEntryMotd')[0];
        let players = entry.getElementsByClassName('serverEntryPlayers')[0];
        status.className = 'serverStatusUnknown';
        requestServerStatus(id)
            .catch(error => {
                console.warn('could not get server status of ' + id, error);
                return "Offline";
            })
            .then(response => {
                status.className = 'serverStatus' + response.status;
                motd.innerText = response.motd;
                players.innerText = response.playerCount + '/' + response.playerMax;
            }).catch(console.warn);
    }
}

async function start() {
    // noinspection InfiniteLoopJS
    while (true) {
        refreshUI();

        await sleep(60_000);
    }
}