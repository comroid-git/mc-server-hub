let stompClient = null;
let subscriptionDisconnect;
let subscriptionHandshake;
let subscriptionStatus;
let subscriptionOutput;
let sessionId;

function connect() {
    writeLine('Connecting...')
    let socket = new SockJS('/console');
    stompClient = Stomp.over(socket);
    stompClient.connect({}, function () {
        subscriptionDisconnect = stompClient.subscribe('/user/' + userName + '/console/disconnect', function () {
            disconnect();
        });
        subscriptionHandshake = stompClient.subscribe('/user/' + userName + '/console/handshake', function (msg) {
            handleHandshake(JSON.parse(msg.body));
        });
        stompClient.send('/console/connect', {}, JSON.stringify(serverId));
    });
}

function disconnect() {
    if (stompClient != null) {
        stompClient.send('/console/disconnect', {}, sessionId);
        subscriptionDisconnect.unsubscribe();
        subscriptionOutput.unsubscribe();
        stompClient.disconnect();
    }
    writeLine("Disconnected")
}

function handleHandshake(status) {
    if (status === undefined) {
        let msg = 'Handshake was not successful';
        handleOutput(msg)
        throw new Error(msg)
    }
    console.log("Session started with status " + JSON.stringify(status));
    sessionId = status.userId;
    subscriptionHandshake.unsubscribe();
    subscriptionStatus = stompClient.subscribe('/user/' + userName + '/console/status', function (msg) {
        handleStatus(msg.body);
    });
    subscriptionOutput = stompClient.subscribe('/user/' + userName + '/console/output', function (msg) {
        handleOutput(msg.body);
    });
    subscriptionOutput = stompClient.subscribe('/user/' + userName + '/console/error', function (msg) {
        handleError(msg.body);
    });
    writeLine("Connected")
}

function handleStatus(data) {
    let status = data.status;
    let btnStart = document.getElementById('ui-server-start');
    let btnStop = document.getElementById('ui-server-stop');

    btnStart.enabled = status !== "Offline";
    btnStop.enabled = status === "Offline";
}

function handleOutput(msg) {
    let output = document.getElementById('output');
    output.innerHTML += msg;
    output.scrollTop = output.scrollHeight;
}

function handleError(msg) {
    let output = document.getElementById('output');
    let span = document.createElement('span')
    span.className = 'stderr';
    span.innerText = msg;
    output.innerHTML += span;
    output.scrollTop = output.scrollHeight;
}

function writeLine(msg) {
    handleOutput(msg + '<br/>');
}

function sendMessage() {
    sendInput(document.getElementById('input').value);
    document.getElementById('input').value = '';
}

function sendInput(input) {
    stompClient.send('/console/input', {}, JSON.stringify(" "+input)); //hackeroo with the space pad
}

function restartServer() {
    sendInput('stop');
}

async function runBackup(id) {
    stompClient.send('/console/backup');
}

function init() {
    connect();

// Enables pressing the Enter Key in the Send Message Prompt
    document.getElementById('input')
        .addEventListener('keyup', function (event) {
            event.preventDefault();
            if (event.keyCode === 13) {
                document.getElementById("send").click();
            }
        });
}
