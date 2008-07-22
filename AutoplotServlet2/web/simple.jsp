<%@page contentType="text/html"%>
<%@page pageEncoding="UTF-8"%>
<%--
The taglib directive below imports the JSTL library. If you uncomment it,
you must also add the JSTL library to the project. The Add Library... action
on Libraries node in Projects view can be used to add the JSTL 1.1 library.
--%>
<%--
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%> 
--%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
   "http://www.w3.org/TR/html4/loose.dtd">

<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>JSP Page</title>
    </head>
    <body>

    <h1>Autoplot Servlet</h1>
    
    This demonstrates how Autoplot can be used within a web application
    container to provide static images.
    
    <form action="apservlet">
        Enter Autoplot URL:
        <input type="text" name="url" value="http://www.sarahandjeremy.net/~jbf/1wire/data/2007/0B000800408DD710.%Y%m%d.d2s?timerange=20071210" size="80" />
        <input type="submit" value="Plot" />
    </form>

    
    </body>
</html>
