<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags"%>
<%--@elvariable id="page" type="java.lang.String"--%>
<%--@elvariable id="scripts" type="java.util.List<java.lang.String>"--%>
<%--@elvariable id="load" type="java.lang.String"--%>
<%--@elvariable id="unload" type="java.lang.String"--%>
<!DOCTYPE html>
<html>
<head>
    <title>Minecraft Server Hub</title>
    <c:import url="/page/head.jsp" />
    <c:forEach var="path" items="${scripts}">
        <script type="text/javascript" src="${path}"></script>
    </c:forEach>
</head>
<body
        <c:if test="${not empty load}">onload="${load}"</c:if>
        <c:if test="${not empty unload}">onbeforeunload="${unload}"</c:if>
>
<table>
    <tr>
        <td colspan="2"><div class="ui-menubar"><c:import url="/page/menubar.jsp" /></div></td>
    </tr>
    <tr>
        <td><div class="ui-sidebar"><c:import url="/page/sidebar.jsp" /></div></td>
        <td><div class="ui-content"><c:import url="/${page}.jsp" /></div></td>
    </tr>
</table>
<div style="
  font-family: 'Comic Sans MS', sans-serif;
  position: absolute;
  bottom: 0;
  align-self: center;
  background: red;
  padding: 10px;
  border-radius: 10px 10px 0 0;
  right: 100px;">
    yes i know this page is ugly as fuck
    <br/>
    i'm not a good graphics designer
    <br/>
    <a href="https://github.com/comroid-git/mc-server-hub">go ahead and commit an improvement</a>
</div>
</body>
</html>
