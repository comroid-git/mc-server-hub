<%--suppress HtmlFormInputWithoutLabel --%>
<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%--@elvariable id="user" type="org.comroid.mcsd.core.entity.User"--%>
<%--@elvariable id="connection" type="org.comroid.mcsd.core.entity.ShConnection"--%>
<h3>${connection}</h3>
<button onclick="window.location.href = <c:url value="/server/edit/${connection.id}"/>">Edit</button>
<table>
    <tr>
        <td>ID</td>
        <td>${connection.id}</td>
    </tr>
    <tr>
        <td>Host</td>
        <td>${connection.host}</td>
    </tr>
    <tr>
        <td>Port</td>
        <td>${connection.port}</td>
    </tr>
    <tr>
        <td>Username</td>
        <td>${connection.username}</td>
    </tr>
</table>
