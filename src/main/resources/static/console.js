var stompClient = null;
var subscriptionDisconnect;
var subscriptionHandshake;
var subscriptionOutput;
var userName;
var serverId;
var sessionId;

function connect() {
    appendOutput('Connecting...' + '\r\n')
    var socket = new SockJS('/console');
    stompClient = Stomp.over(socket);
    stompClient.connect({}, function(frame) {
        subscriptionDisconnect = stompClient.subscribe('/user/'+userName+'/console/disconnect', function(){
            disconnect();
        });
        subscriptionHandshake = stompClient.subscribe('/user/'+userName+'/console/handshake', function(success){
            handleHandshake(JSON.parse(success.body));
        });
        stompClient.send('/console/connect', {}, JSON.stringify(serverId));
    });
}

function handleHandshake(session) {
    if (session === undefined) {
        let msg = 'Handshake was not successful';
        appendOutput(msg)
        throw new Error(msg)
    }
    sessionId = session;
    subscriptionHandshake.unsubscribe();
    subscriptionOutput = stompClient.subscribe('/user/'+userName+'/console/output', function(msg){
        appendOutput(msg.body);
    });
    appendOutput("Connected" + '\r\n')
}

function disconnect() {
    if(stompClient != null) {
        stompClient.send('/console/disconnect', {}, sessionId);
        subscriptionDisconnect.unsubscribe();
        subscriptionOutput.unsubscribe();
        stompClient.disconnect();
    }
    appendOutput("Disconnected" + '\r\n')
}

function sendMessage() {
    var text = document.getElementById('input').value;
    stompClient.send('/console/input', {}, JSON.stringify(text));
    document.getElementById('input').value = '';
}

function appendOutput(msg) {
    let output = document.getElementById('output');
    output.innerHTML += msg;
    output.scrollTop = output.scrollHeight;
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
