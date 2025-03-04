<%-- 
    Document   : index
    Created on : Jul 22, 2008, 9:52:32 AM
    Author     : jbf
--%>

<%@page import="org.autoplot.servlet.ServletInfo"%>
<%@page import="org.das2.util.AboutUtil"%>
<%@page import="org.autoplot.servlet.SimpleServlet"%>
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
        <p>The <a href="simple.jsp">"SimpleServlet"</a> is an example showing how Autoplot can be used to produce graphics
        for clients via HTTP.  Request parameters are sent to the servlet,
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

        <p>The <a href="script.jsp">"ScriptServlet"</a> allows Jython scripts to be sent and interpreted to produce graphics or to access
            Autoplot internals.  Obviously this introduces security concerns, and access is limited by the "allowhosts" file found
            by default in /tmp/autoplotservlet.  Also, scripts are logged in this location as well.
        </p>
        
        <p>The <a href="ScriptGUIServlet">"ScriptGUIServlet"</a> demonstrates how a Jython script can be automatically converted to a web
            application, automatically creating a form and showing the result.
        </p>

        <p>The <a href="URI_Templates.jsp">"URI_Templates"</a> allows how an Autoplot script can be used to implement a service, here demonstrating
            Autoplot's implementation of the URI templates (<a href="https://github.com/uri-templates-time/uri-templates/wiki/Specification">specification</a>).
        </p>
        
        <p>The <a href="CdawebVapServlet.jsp">"CdawebVapServlet"</a> creates v1.08 vap files.
        </p>

        <p><a href="ServletInfo">"ServletInfo"</a> shows information about the servlet for debugging. 
        </p>

        <p><a href="completions.html">"Completions"</a> shows how to get completions, which could be used to create a more interactive client.
        </p>
        
        <p><a href="thin/zoom/demo.jsp">"Thin"<a> shows how Autoplot is used to implement a thin-client that is usable on phones.  
        </p>
        
        <p>The source for all of these is found at <a href="https://sourceforge.net/p/autoplot/code/HEAD/tree/autoplot/trunk/AutoplotServlet/">SourceForge</a>.</p>
        
        
        <p><b>Note: These are provided as examples and there is no guarantee of security.  Data providers interested in using this software on their servers must do so at their own risk.</b></p>
        <hr>
        
        <p>
            <a href="simple.jsp"> simple.jsp</a>: web form for sending requests to the SimpleServlet. <br>
            <a href="script.jsp"> script.jsp</a>: web form for sending scripts to the ScriptServlet. <br>
            <a href="unaggregate.jsp"> unaggregate.jsp</a>: demo for URI templates, and also how servlets can be made from scripts.<br>
            <a href="thin/zoom/demo.html">thin</a>: a more fully-developed thin-client app.<br>
        </p>
        
        <hr>
        <small><%= ServletInfo.version %></small>
        <small><%= AboutUtil.getJenkinsURL() %></small>
        <small>up: <%= ServletInfo.getDurationForHumans(ServletInfo.getAgeMillis()) %></small>
    </body>
</html>
