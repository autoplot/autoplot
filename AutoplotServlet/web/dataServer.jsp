<%-- 
    Document   : dataServer
    Created on : May 29, 2018, 12:11:12 PM
    Author     : jbf
--%>

<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>JSP Page</title>
    </head>
    <body>
        <h1>DataServer Request Examples</h1>
        <a href="DataServlet?url=http%3A%2F%2Fjfaden.net%2F~jbf%2F1wire%2Fdata%2F2007%2F0B000800408DD710.%24Y%24m%24d.d2s%3Ftimerange%3D20071210">No Agg</a>
        <a href="DataServlet?url=http%3A%2F%2Fjfaden.net%2F~jbf%2F1wire%2Fdata%2F2007%2F0B000800408DD710.%24Y%24m%24d.d2s&timeRange=2007">Agg 2017</a>
        <h1>All the parameters</h1>
        <form action="DataServlet">
        URI: <input name="uri" value="vap+cdaweb:ds=OMNI2_H0_MRG1HR&id=DST1800" size="100" type="text"><br>
        Time Range: <input name="timeRange" value="2003-mar"><br>
        format: <select name="format">
            <option>hapi-csv</option>
            <option>hapi-info</option>
            <option>hapi-binary</option>
            <option>qds</option>
            <option>d2s</option>
        </select><br>
        <input value="Send Data" type="submit">
        </form>
    </body>
</html>
