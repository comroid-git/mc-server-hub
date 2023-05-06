<%--suppress HtmlFormInputWithoutLabel --%>
<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%--@elvariable id="user" type="org.comroid.mcsd.web.entity.User"--%>
<%--@elvariable id="creating" type="org.java.Boolean"--%>
<%--@elvariable id="editing" type="org.comroid.mcsd.web.entity.ShConnection"--%>
<h3>${creating?"Creating":"Editing"} SSH Connection ${editing}</h3>
<form method="post" action="/connection/${editing.id}">
    <h5>Host</h5>
    <input type="text" name="host" value="${editing.host}" required/>
    <input type="number" name="port" value="${editing.port}" min="1" max="65535" required/>
    <h5>Username</h5><input type="text" name="username" value="${editing.username}" required/>
    <h5>Password</h5><input type="password" name="password" value="${editing.password}" required/>
    <c:if test="${not creating}">
        <!-- todo -->
    </c:if>
    <input type="hidden" name="id" value="${editing.id}" required readonly/>
    <input type="submit" value="Submit"/>
</form>
