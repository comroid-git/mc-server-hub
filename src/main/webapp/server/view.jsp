<%--suppress HtmlFormInputWithoutLabel --%>
<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%--@elvariable id="user" type="org.comroid.mcsd.web.entity.User"--%>
<%--@elvariable id="server" type="org.comroid.mcsd.web.entity.Server"--%>
<h3>${server.name}</h3>
<button onclick="window.location.href = <c:url value="/server/edit/${server.id}"/>">Edit</button>
<table>
    <tr>
        <td>ID</td>
        <td>${server.id}</td>
    </tr>
    <tr>
        <td>Name</td>
        <td>${server.name}</td>
    </tr>
    <tr>
        <td>Minecraft Version</td>
        <td>${server.mcVersion}</td>
    </tr>
    <tr>
        <td>Port</td>
        <td>${server.port}</td>
    </tr>
    <tr>
        <td>Mode</td>
        <td>${server.mode}</td>
    </tr>
    <tr>
        <td>Directory</td>
        <td>${server.directory}</td>
    </tr>
    <tr>
        <td>RAM</td>
        <td>${server.ramGB} GB</td>
    </tr>
    <tr>
        <td>SSH Connection</td>
        <td>${server.shConnection}</td>
    </tr>
</table>
