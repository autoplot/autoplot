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
        <p>The resolves all the templated URIs.  Note this is using Das2's FileStorageModel, so the web server will be
            queried to resolve only available files and version numbers.</p>
    <form action="SecureScriptServlet" method="GET">
        Enter Aggregation URI (<a href="http://autoplot.org/help#Wildcard_codes">help</a>)<br>
        <textarea rows="1" cols="120" name="resourceURI" >http://autoplot.org/data/versioning/data_$Y_$m_$d_v$v.qds</textarea><br>
        Enter ISO8601 Time Range: <br>
        <textarea rows="1" cols="120" name="timerange" >2010-03-01/2010-03-10</textarea><br>
        <input type="checkbox" checked name="fast"/>fast
        <br>
        <input type="hidden" name="scriptFile" value="unaggregate.jy" />
        <input type="submit" value="Execute" />
    </form>
    </body>
</html>
