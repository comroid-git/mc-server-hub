<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags"%>
<div class="menu-icon" onclick="if (typeof openSidebar === 'function') openSidebar()" />
<a href="<c:url value="/"/>">Minecraft Server Hub</a>
<a href="<c:url value="/logout"/>" class="ui-logout">Logout</a>
