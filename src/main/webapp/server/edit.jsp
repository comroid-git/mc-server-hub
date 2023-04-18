<%--suppress HtmlFormInputWithoutLabel --%>
<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags"%>
<%--@elvariable id="user" type="org.comroid.mcsd.web.entity.User"--%>
<%--@elvariable id="creating" type="org.java.Boolean"--%>
<%--@elvariable id="editing" type="org.comroid.mcsd.web.entity.Server"--%>
<%--@elvariable id="shMap" type="java.util.Map<java.util.String, java.util.UUID>"--%>
<h3>${creating?"Creating":"Editing"} Server ${editing.name}</h3>
<form method="post" action="/server/${editing.id}">
    <h5>Name</h5>
    <input type="text" name="name" value="${editing.name}" required />
    <h5>Minecraft Version</h5>
    <input type="text" name="mcVersion" value="${editing.mcVersion}" required />
    <h5>Port</h5>
    <input type="number" name="port" value="${editing.port}" min="1" max="65535" required />
    <h5>Mode</h5>
    <select name="mode" required>
        <option value="1" ${editing.vanilla ? "selected" : ""}>Vanilla</option>
        <option value="2" ${editing.paper ? "selected" : ""}>Paper</option>
        <option value="3" ${editing.forge ? "selected" : ""}>Forge</option>
        <option value="4" ${editing.fabric ? "selected" : ""}>Fabric</option>
    </select>
    <h5>SSH Connection</h5>
    <select name="shConnection" required>
        <c:forEach var="entry" items="${shMap}">
            <option value="${entry.value}">${entry.key}</option>
        </c:forEach>
        <option value="createNew" onselect="window.location.href = '<c:url value="/connection/create"/>">Create new...</option>
    </select>
    <c:if test="${not creating}">
        <!-- todo -->
    </c:if>
    <input type="hidden" name="id" value="${editing.id}" required readonly />
    <input type="submit" value="Submit" />
</form>
