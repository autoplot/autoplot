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

    <p>
    This demonstrates how scripting might be used with Autoplot to provide
    precise specification of an image.
    
    Caveat: this allows arbitrary code to be executed on the server, so this 
    server should not be left on and should not be advertised.  We try to guard against attacks with
    taint-checking, but this is not thorough.  Scripts are logged in /tmp/autoplotservlet.
    </p>

    <p>Note there are issues with the design right now, and this lacks abusive testing!</p>
    
    <form action="ScriptServlet" method="POST">
        Enter Script:<br>
        <textarea rows="14" cols="120" name="script" >
setCanvasSize( 400, 400 )

dom= getDocumentModel()
plot( 0, 'http://www.sarahandjeremy.net/jeremy/1wire/data/2008/0B000800408DD710.20080117.d2s' )
dom.plots[0].title= 'Garage 20080117'

plot( 1, 'http://www.sarahandjeremy.net/jeremy/1wire/data/2008/8500080044259C10.20080117.d2s' )
dom.plots[1].title= 'Other Garage 20080117'

response.setContentType("image/png");
out = response.getOutputStream()
writeToPng( out )
out.close()
        </textarea>
        <br>
        <input type="submit" value="Plot" />
    </form>

    
    </body>
</html>
