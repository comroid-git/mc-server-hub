<%--suppress HtmlFormInputWithoutLabel --%>
<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags"%>
<%--@elvariable id="user" type="org.comroid.mcsd.web.entity.User"--%>
<%--@elvariable id="server" type="org.comroid.mcsd.web.entity.Server"--%>
<h3>Server Console</h3>
<textarea id="output" readonly></textarea>
<br/>
<input type="text" id="input" style="width: 80%;" />
<input type="text" id="userId" value="${user.id}" style="display: none" readonly />
<input type="text" id="serverId" value="${server.id}" style="display: none" readonly />
<button id="send" onclick="sendMessage()" style="width: 9.65%;">Send</button>
