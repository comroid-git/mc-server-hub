<%--suppress HtmlFormInputWithoutLabel --%>
<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags"%>
<%--@elvariable id="user" type="org.comroid.mcsd.web.entity.User"--%>
<%--@elvariable id="servers" type="java.util.List<org.comroid.mcsd.web.entity.Server>"--%>
<%--@elvariable id="connections" type="java.util.List<org.comroid.mcsd.web.entity.ShConnection>"--%>
<h3>Hello ${user.name}</h3>
<h5>You have access to ${servers.size()} Server${(servers.size() == 1 ? "" : "s")}</h5>
<h4>Servers</h4>
<table>
<tr>
    <th/>
    <th>
        <c:if test="${user.canManageServers()}">
            <button onclick="window.location.href = '<c:url value="/server/create"/>'">Create</button>
        </c:if>
    </th>
    <th>Name</th>
    <th>Minecraft Version</th>
    <th>Mode</th>
    <th>Host</th>
    <th/>
    <th>Port</th>
    <th>Directory</th>
    <th>RAM</th>
    <th/>
</tr>
    <c:forEach var="server" items="${servers}">
        <tr class="serverEntry ui-button">
            <td class="serverEntryId" style="display: none">${server.id}</td>
            <td class="serverEntryStatus">
                <div class="serverStatusUnknown" />
            </td>
            <td class="serverEntryInteract">
                <button onclick="window.location.href = '<c:url value="/server/${server.id}'"/>">View</button>
                <button onclick="window.location.href = '<c:url value="/server/console/${server.id}'"/>">Console</button>
            </td>
            <td class="serverEntryName">${server.name}</td>
            <td class="serverEntryMcVersion">${server.mcVersion}</td>
            <td class="serverEntryMode">${server.mode}</td>
            <td class="serverEntryHost">${connections.stream()
            .filter(con -> con.getId().equals(server.shConnection))
            .findFirst()
            .get()
            .getHost()
            }</td>
            <td class="serverEntrySeparator">:</td>
            <td class="serverEntryPort">${server.port}</td>
            <td class="serverEntryDir">${server.directory}</td>
            <td class="serverEntryRam">${server.ramGB} GB</td>
            <td class="serverEntryManage">
                <button onclick="window.location.href = '<c:url value="/server/edit/${server.id}'"/>">Edit</button>
                <button onclick="window.location.href = '<c:url value="/server/delete/${server.id}'"/>">Delete</button>
            </td>
        </tr>
    </c:forEach>
</table>
<c:if test="${user.canManageShConnections()}">
    <h4>SSH Connections</h4>
    <table>
        <tr>
            <th><button onclick="window.location.href = '<c:url value="/connection/create"/>'">Create</button></th>
            <th>Username</th>
            <th/>
            <th>Host</th>
            <th/>
            <th>Port</th>
            <th/>
        </tr>
        <c:forEach var="con" items="${connections}">
            <tr class="connectionEntry ui-button">
                <td><button onclick="window.location.href = '<c:url value="/connection/${con.id}"/>'">View</button></td>
                <td>${con.username}</td>
                <td>@</td>
                <td>${con.host}</td>
                <td>:</td>
                <td>${con.port}</td>
                <td>
                    <button onclick="window.location.href = '<c:url value="/connection/edit/${con.id}"/>'">Edit</button>
                    <button onclick="window.location.href = '<c:url value="/connection/delete/${con.id}"/>'">Delete</button>
                </td>
            </tr>
        </c:forEach>
    </table>
</c:if>
