<%@ page import="com.janrain.backplane.config.BackplaneSystemProps" %>
<%@ include file="/WEB-INF/views/include.jsp"
%><%@page contentType="text/html;charset=UTF-8"
%><%--
  ~ Copyright 2012 Janrain, Inc.
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~    http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  --%>
<html>
<head>
    <META http-equiv="Content-Type" content="text/html;charset=UTF-8">
    <title><fmt:message key="admin.title"/></title>
    <link rel="stylesheet" href="<c:url value="/styles/blueprint/screen.css" />" type="text/css" media="screen, projection">
    <link rel="stylesheet" href="<c:url value="/styles/blueprint/print.css" />" type="text/css" media="print">
    <!--[if lt IE 8]>
    <link rel="stylesheet" href="<c:url value="/styles/blueprint/ie.css" />" type="text/css" media="screen, projection">
    <![endif]-->


</head>
<body>
<div class="container">
    <h1>
        <fmt:message key="admin.title"/>
    </h1>
    <hr>
</div>

<h2>Admin User Configuration</h2>

<c:choose>
<c:when test="${not adminUserExists}" >
    <form method=post action="adminadd">
        Admin user name: <%= BackplaneSystemProps.ADMIN_USER%><input type=hidden name=username value=<%= BackplaneSystemProps.ADMIN_USER%>><br>
        Admin secret? <input type=text name=password size=30><br>
    <p><input type=submit>
    </form>
</c:when>

<c:otherwise>
    Admin user already exists.  You must delete the entry from the database before submitting a new admin user.
</c:otherwise>
</c:choose>

<br/><br/>
<h2>Configuration Settings</h2>

<form method=post action="adminupdate">
    <table style="border: 0px; width: 20%;">
    <tr><td>Config key:</td><td>${configKey} </td></tr>
    <tr><td>Debug mode:</td><td><input type="radio" name="debug_mode" value="true" <c:if test="${debugMode}">checked</c:if>>true</td></tr>
    <tr><td></td><td><input type="radio" name="debug_mode" value="false" <c:if test="${not debugMode}">checked</c:if>>false</td></tr>
    <tr><td>Default messages max:</td><td><input type="text" name="default_messages_max" value=${defaultMessagesMax}></td></tr>
    </table>

    <p><input type=submit>
</form>

</body>
</html>