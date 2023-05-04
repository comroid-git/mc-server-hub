<%--suppress HtmlFormInputWithoutLabel --%>
<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags"%>
<%--@elvariable id="user" type="org.comroid.mcsd.web.entity.User"--%>
<%--@elvariable id="server" type="org.comroid.mcsd.web.entity.Server"--%>
<h3>Server Console</h3>
<button id="ui-server-start" onclick="window.location.reload()">Start Server</button>
<button id="ui-server-stop" onclick="stopServer('${server.id}')">Stop Server</button>
<button id="ui-server-restart" onclick="restartServer()">Restart Server</button>
<button id="ui-server-backup" onclick="runBackup('${server.id}')" disabled>Run Backup Script</button>
<div id="output"></div>
<br/>
<input type="text" id="input" style="width: 80%;" />
<script type="application/javascript">
    let userName = '${user.name}';
    let serverId = '${server.id}';
</script>
<button id="send" onclick="sendMessage()" style="width: 9.65%;">Send</button>
