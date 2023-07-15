async function loadWidget() {
    await updateWidget();
}

async function updateWidget() {
    let response = await fetch('/server/status/' + serverId);
    if (response.status !== 200)
        throw 'Fetch status returned code ' + response.status;
    let status = await response.json()
    for (let key in status) {
        let div = document.getElementById(key);
        if (key === 'status' || key === 'rcon' || key === 'ssh')
            {
                let symbol = document.createElement('div');
                symbol.className = 'serverStatus' + status[key];
                div.content = symbol;
            }
        else div.innerText = status[key];
    }
    setTimeout(updateWidget, 5_000)
}
