var stompClient = null;
var subscriptionDisconnect;
var subscriptionHandshake;
var subscriptionStatus;
var subscriptionOutput;
var userName;
var serverId;
var sessionId;

function connect() {
    writeLine('Connecting...')
    var socket = new SockJS('/console');
    stompClient = Stomp.over(socket);
    stompClient.connect({}, function(frame) {
        subscriptionDisconnect = stompClient.subscribe('/user/'+userName+'/console/disconnect', function(){
            disconnect();
        });
        subscriptionHandshake = stompClient.subscribe('/user/'+userName+'/console/handshake', function(msg){
            handleHandshake(JSON.parse(msg.body));
        });
        stompClient.send('/console/connect', {}, JSON.stringify(serverId));
    });
}

function disconnect() {
    if(stompClient != null) {
        stompClient.send('/console/disconnect', {}, sessionId);
        subscriptionDisconnect.unsubscribe();
        subscriptionOutput.unsubscribe();
        stompClient.disconnect();
    }
    writeLine("Disconnected")
}

function handleHandshake(session) {
    if (session === undefined) {
        let msg = 'Handshake was not successful';
        handleOutput(msg)
        throw new Error(msg)
    }
    sessionId = session;
    subscriptionHandshake.unsubscribe();
    subscriptionStatus = stompClient.subscribe('/user/'+userName+'/console/status', function(msg){
        handleStatus(msg.body);
    });
    subscriptionOutput = stompClient.subscribe('/user/'+userName+'/console/output', function(msg){
        handleOutput(msg.body);
    });
    writeLine("Connected")
}

function handleStatus(data) {
    let status = data.status;
    let btnStart = document.getElementById('ui-server-start');
    let btnStop = document.getElementById('ui-server-stop');

    btnStart.enabled = status !== 4;
    btnStop.enabled = status >= 1;
}

function handleOutput(msg) {
    let output = document.getElementById('output');
    output.innerHTML += msg;
    output.scrollTop = output.scrollHeight;
}

function writeLine(msg) {
    handleOutput(msg + '\r\n');
}

function sendMessage() {
    var text = document.getElementById('input').value;
    stompClient.send('/console/input', {}, JSON.stringify(text));
    document.getElementById('input').value = '';
}

function init() {
    userName = document.getElementById('userName').value;
    serverId = document.getElementById('serverId').value;

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
