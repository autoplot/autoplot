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
        <title>Autoplot Simple Servlet</title>
    </head>
    <body>

    <h1>Autoplot Simple Servlet</h1>
    
    This demonstrates how Autoplot can be used within a web application
    container to provide static images.
    
    <form action="SimpleServlet">
        Enter Autoplot URL:
        <input type="text" name="url" value="http://www.sarahandjeremy.net/~jbf/1wire/data/2007/0B000800408DD710.%Y%m%d.d2s?timerange=20071210" size="80" />
        <input type="submit" value="Plot" />
    </form>

<br><br>
    <form action="SimpleServlet">
        <input name="url" value="http://www.sarahandjeremy.net/~jbf/1wire/data/2007/0B000800408DD710.%Y%m%d.d2s?timerange=20071210" size="80" type="text">
        <input value="Plot" type="submit">
        <input type="checkbox" name="autolayout" value="true" >autolayout</input>
    </form>
<br><br>
    <form action="SimpleServlet">
       <input name="url" value="tsds.http://timeseries.org/cgi-bin/get.cgi?StartDate=20010101&EndDate=20010101&ext=bin&ppd=24&param1=SourceAcronym_Subset-1-v0" size="80" type="text">
        <input value="Plot" type="submit">
    </form>
<br><br>
    <form action="SimpleServlet">
    <input name="url" value="http://timeseries.org/cgi-bin/get.cgi?StartDate=20010101&EndDate=20010101&ext=bin&ppd=24&param1=SourceAcronym_Subset-1-v0" size="80" type="text">      
        <input value="Plot" type="submit">

    </form>
    </body>
</html>
