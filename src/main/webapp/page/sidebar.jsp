<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags"%>
<%--@elvariable id="user" type="org.comroid.mcsd.web.entity.User"--%>
<%--@elvariable id="servers" type="java.util.List<org.comroid.mcsd.web.entity.Server>"--%>
<%--@elvariable id="connections" type="java.util.List<org.comroid.mcsd.web.entity.ShConnection>"--%>
<div class="lt t0">
    <a href="<c:url value="/"/>">> Servers</a>
    <c:forEach var="srv" items="${servers}">
        <div class="lt t1">
            <a href="<c:url value="/server/${srv.id}" />">> ${srv.name}</a>
            <br/>
            <div class="lt t2">
                <a href="<c:url value="/server/console/${srv.id}"/>">> Console</a>
            </div>
        </div>
    </c:forEach>
</div>
<c:if test="${user.canManageShConnections()}">
    <div class="lt t0">
        <a href="<c:url value="/"/>">> SSH Connections</a>
        <c:forEach var="con" items="${connections}">
            <div class="lt t1">
                <a href="<c:url value="/connection/${con.id}" />">> ${con.toString()}</a>
            </div>
        </c:forEach>
    </div>
</c:if>
<div class="lt t0">
    <a href="https://hub.comroid.org/hub/users/${user.id}" target="_blank">Account</a>
</div>
<style>
    .lt {
        margin-left: 5px;
    }
</style>
<script type="application/javascript">
    // todo
    function expand() {}
    function collapse() {}
</script>
