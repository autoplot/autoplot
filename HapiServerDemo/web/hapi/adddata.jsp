<%-- 
    Document   : adddata
    Created on : Mar 22, 2017, 9:01:23 AM
    Author     : jbf
--%>

<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>HAPI Upload page</title>
    </head>
    <body>
        <h1>This HAPI server allows data to be uploaded as well into the server.</h1>
        
        Here are some requests you can try:
        <ul>
            <li>wget -O Spectrum2.csv 'http://localhost:8080/HapiServerDemo/hapi/data?id=Spectrum&time.min=2016-01-01T00:00:00.000Z&time.max=2016-01-02T00:00:00.000Z'
            <li>curl -T Spectrum2.csv 'http://localhost:8080/HapiServerDemo/hapi/data?id=Spectrum_new'
            <li>wget -O Spectrum2.json 'http://localhost:8080/HapiServerDemo/hapi/info?id=Spectrum'
            <li>curl -T Spectrum2.json 'http://localhost:8080/HapiServerDemo/hapi/info?id=Spectrum_new'
        </ul>
    </body>
</html>
