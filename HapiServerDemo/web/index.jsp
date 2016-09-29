<%-- 
    Document   : index
    Created on : Sep 17, 2016, 6:42:16 AM
    Author     : jbf
--%>

<%@page import="org.autoplot.hapiserver.Util"%>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>HAPI Server JSP Demo</title>
    </head>
    <body>
        <h1>This is a HAPI Server.</h1>
        
        <h3>Some example requests:</h3>
        <a href="hapi/catalog">Catalog</a><br>
        <a href="hapi/info?id=0B000800408DD710">Info</a><br>
        <a href="hapi/data?id=0B000800408DD710&time.min=2016-01-01&time.max=2016-01-05">Data</a><br>
        <a href="hapi/data?id=0B000800408DD710&time.min=2016-01-01&time.max=2016-01-05&include=header">Data w/header</a><br>
        <a href="hapi/data?id=Iowa+City+Conditions&time.min=2016-01-01&time.max=2016-01-05&include=header&parameters=Time,Humidity">Data w/multiple parameters</a><br>
        <a href="hapi/data?id=Iowa+City+Conditions&time.min=2016-01-01&time.max=2016-01-05&include=header&parameters=Time,Humidity">Data w/subset</a><br>
        
        <%
            long l= org.virbo.dataset.RecordIterator.TIME_STAMP; // load RecordIterator class first, or we'll get a negative time.
        %>
        <br><small>deployed <%= Util.getDurationForHumans( System.currentTimeMillis() - org.virbo.dataset.RecordIterator.TIME_STAMP ) %> ago</small>
        
        <small>
        <ul>
            <li>2016-09-21: bugfix: parameters supported when include is not set.
            <li>2016-09-26: more parameters in current conditions.
            <li>2016-09-29: add landing page and time ranges.
        </ul>
        </small>
    </body>
</html>
