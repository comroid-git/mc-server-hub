<%--suppress HtmlFormInputWithoutLabel --%>
<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%--@elvariable id="user" type="org.comroid.mcsd.entity.User"--%>
<%--@elvariable id="server" type="org.comroid.mcsd.entity.Server"--%>
<h3>Server ${server.name}</h3>
<script>let serverId = '${server.id}';</script>
<div class="ui-widget-content" id="serverId">Loading...</div>
<div class="ui-widget-content" id="status"><div class="serverStatusUnknown"/></div>
<div class="ui-widget-content" id="rcon"><div class="serverStatusUnknown"/></div>
<div class="ui-widget-content" id="ssh"><div class="serverStatusUnknown"/></div>
<div class="ui-widget-content" id="playerCount">0</div>
<div class="ui-widget-content" id="playerMax">0</div>
<div class="ui-widget-content" id="motd">Loading...</div>
<div class="ui-widget-content" id="players">Loading...</div>
<div class="ui-widget-content" id="gameMode">Loading...</div>
<div class="ui-widget-content" id="worldName">Loading...</div>
<div class="ui-widget-content" id="userId">Loading...</div>
