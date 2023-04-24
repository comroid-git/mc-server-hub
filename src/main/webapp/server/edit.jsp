<%--suppress HtmlFormInputWithoutLabel --%>
<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags"%>
<%--@elvariable id="user" type="org.comroid.mcsd.web.entity.User"--%>
<%--@elvariable id="creating" type="java.lang.Boolean"--%>
<%--@elvariable id="editing" type="org.comroid.mcsd.web.entity.Server"--%>
<%--@elvariable id="shMap" type="java.util.Map<java.lang.String, java.util.UUID>"--%>
<%--@elvariable id="perms" type="java.util.Map<java.lang.String, java.lang.Integer>"--%>
<h3>${creating?"Creating":"Editing"} Server ${editing.name}</h3>
<script type="application/javascript">
    let perms = {
        <c:forEach var="entry" items="${editing.userPermissions}">
            '${entry.key}': ${entry.value},
        </c:forEach>
    }
</script>
<form id="editForm" method="post" action="/server/${editing.id}">
    <h5>Name
        <input type="text" name="name" value="${editing.name}" required />
    </h5>

    <h5>Minecraft Version
        <input type="text" name="mcVersion" value="${editing.mcVersion}" required />
    </h5>

    <h5>Port
        <input type="number" name="port" value="${editing.port}" min="1" max="65535" required />
    </h5>

    <h5>Directory
        <input type="text" name="directory" value="${editing.directory}" required />
    </h5>

    <h5>RAM
        <input type="number" name="ramGB" value="${editing.ramGB}" min="1" max="512" required /> GB
    </h5>

    <h5>Mode
        <select name="mode" required>
            <option value="1" ${editing.vanilla ? "selected" : ""}>Vanilla</option>
            <option value="2" ${editing.paper ? "selected" : ""}>Paper</option>
            <option value="3" ${editing.forge ? "selected" : ""}>Forge</option>
            <option value="4" ${editing.fabric ? "selected" : ""}>Fabric</option>
        </select>
    </h5>

    <h5>SSH Connection
        <select name="shConnection" required>
            <c:forEach var="entry" items="${shMap}">
                <option value="${entry.value}">${entry.key}</option>
            </c:forEach>
            <option value="createNew" onselect="window.location.href = '<c:url value="/connection/create"/>">Create new...</option>
        </select>
    </h5>

    <h5>User Hub Permissions</h5>
    <select id="select-user" onselect="selectUser()" size="10" style="width: 20%" multiple>
        <option value="${user.id}" selected>${user.name}</option>
    </select>
    <select id="select-perms" onselect="selectPerms()" size="10" style="width: 10%" multiple>
        <c:forEach var="perm" items="${perms}">
            <c:if test="${perm.value > 0}">
                <option value="${perm.value}" selected>${perm.key}</option>
            </c:if>
        </c:forEach>
    </select>
    <h6><button onclick="addUser()">Add User</button>
        <input id="add-user" type="text" alt="User ID" />
    </h6>
    <br/>
    <input type="hidden" name="id" value="${editing.id}" required readonly />
    <input type="button" value="Submit" onclick="submitForm()" style="width: 10%; height: 2%" />
</form>
