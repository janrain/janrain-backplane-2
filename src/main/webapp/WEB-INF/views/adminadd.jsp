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

<jsp:useBean id="user" class="com.janrain.backplane.config.AdminData" scope="request"/>
<jsp:setProperty name="user" property="*"/>

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

${message}

</body>
</html>