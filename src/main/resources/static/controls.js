function info(msg) {
    if (typeof writeLine === 'function')
        writeLine('\n' + msg);
    else console.log(msg);
}

async function sendServerCommand(id, command, expect = 200) {
    let url = `/server/${command}/${id}`;
    let resp = await fetch(url);
    if (resp.status !== expect) {
        console.error(url + " returned unexpected response: " + resp.status);
    }
    return resp;
}

async function startServer(id) {
    await sendServerCommand(id, "start");
    info("Server " + id + " started");
}

async function stopServer(id) {
    await sendServerCommand(id, "stop");
    if (typeof sendInput === 'function')
        sendInput("stop");
    info("Server " + id + " stopped");
}
