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
        <title>Autoplot Servlet</title>
    </head>
    <body>
        <h2>Autoplot Servlet</h2>
        <p>This is an example showing how Autoplot can be used to produce graphics
        for clients via HTTP.  Request parameters are sent to the "SimpleServlet"
        indicating the data set URI or vap file to plot, and parameters such as
        plot size.  Autoplot then produces a static image or PDF on the server
        side, and sends the result.
        </p>
        <p>Typically this would be used to serve data from files on the server side
            to clients.  Note that URIs need not be local files, but remote files
            will be cached locally on the server, potentially causing problems.
            Please understand the limitations of the server before deploying it for
            production use, and contact us (autoplot@groups.google.com) if you have
            questions.
        </p>
        <p>
            <a href="simple.jsp"> simple.jsp</a>: web form for sending requests to the SimpleServlet.
        </p>
    </body>
</html>
