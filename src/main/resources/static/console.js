var stompClient = null;
var subscriptionHandshake;
var subscriptionOutput;
var userId;
var serverId;
var sessionId;

function connect() {
    appendOutput('Connecting...')
    var socket = new SockJS('/console');
    stompClient = Stomp.over(socket);
    stompClient.connect({}, function(frame) {
        subscriptionHandshake = stompClient.subscribe('/user/console/handshake', function(success){
            handleHandshake(JSON.parse(success.body));
        });
        stompClient.send('/console/connect', {}, JSON.stringify(serverId));
    });
}

function handleHandshake(success) {
    if (!success) {
        let msg = 'Handshake was not successful';
        appendOutput(msg)
        throw new Error(msg)
    }
    subscriptionHandshake.unsubscribe();
    subscriptionOutput = stompClient.subscribe('/user/console/output', function(msg){
        appendOutput(msg.body);
    });
    appendOutput("Connected!")
}

function disconnect() {
    if(stompClient != null) {
        stompClient.send('/console/disconnect', {}, sessionId);
        subscriptionOutput.unsubscribe();
        stompClient.disconnect();
    }
    console.log(`Disconnected`);
}

function sendMessage() {
    var text = document.getElementById('input').value;
    stompClient.send('/console/input', {}, JSON.stringify(text));
    document.getElementById('input').value = '';
}

function appendOutput(msg) {
    document.getElementById('output').innerHTML += msg + '\r\n';
}

function init() {
    userId = document.getElementById('userId').value;
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
