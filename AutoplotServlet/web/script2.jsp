<%-- 
    Document   : index
    Created on : Jul 22, 2008, 9:52:32 AM
    Author     : jbf
--%>

<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
   "http://www.w3.org/TR/html4/loose.dtd">

<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>Autoplot unglob Servlet</title>
    </head>
    <body>
        <h2>Autoplot unglob Servlet</h2>
    <br><br>

    <form action="ScriptServlet2" method="GET">
        Enter Script:<br>
        <textarea rows="1" cols="120" name="resourceURI" >http://autoplot.org/data/versioning/data_$Y_$m_$d_v$v.qds</textarea>
        <textarea rows="1" cols="120" name="timerange" >2010-03-01/2010-03-10</textarea>
        <br>
        <input type="submit" value="Execute" />
    </form>
    </body>
</html>
