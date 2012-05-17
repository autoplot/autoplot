<%-- 
    Document   : script
    Created on : Jul 24, 2008, 9:46:49 AM
    Author     : jbf
--%>

<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
   "http://www.w3.org/TR/html4/loose.dtd">

<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>Autoplot Script Servlet</title>
    </head>
    <body>

    <h1>Autoplot Script Servlet</h1>
    
    This demonstrates how scripting might be used with Autoplot to provide
    precise specification of an image.
    
    Caveat: this allows arbitrary code to be executed on the server, so this 
    server should not be left on and should not be advertised.  We try to guard against attacks with
    taint-checking, but this is not thorough.  Scripts are logged in /tmp/autoplotservlet.
    
    <form action="ScriptServlet" method="POST">
        Enter Script:
        <textarea rows="10" cols="80" name="script" >

setCanvasSize( 400, 400 )

dom= getDocumentModel()
setDataSourceURL( 'http://www.sarahandjeremy.net/jeremy/1wire/data/2008/0B000800408DD710.20080117.d2s' )
setTitle( 'Garage 20080117' )

panel= dom.controller.addPanel()
setDataSourceURL( 'http://www.sarahandjeremy.net/jeremy/1wire/data/2008/0B000800408DD710.20080118.d2s' )
setTitle( 'Garage 20080118' )

response.setContentType("image/png");
out = response.getOutputStream()
writeToPng( out )
out.close()

        </textarea>
        <input type="submit" value="Plot" />
    </form>

    
    </body>
</html>
