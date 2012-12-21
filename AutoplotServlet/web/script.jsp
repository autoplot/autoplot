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

    <h1>Autoplot Script Servlet Demo</h1>

    <p>
    This demonstrates how scripting might be used with Autoplot to provide
    precise specification of an image or access to the libraries it uses.</p>

    <p>
    WARNING: This allows arbitrary code to be executed on the server, so this
    should not be left on and should not be advertised.  We try to guard against attacks with
    taint-checking (for example, imports are not allowed nor formatDataSet,
    <a target="_blank" href="https://autoplot.svn.sourceforge.net/svnroot/autoplot/autoplot/trunk/AutoplotServlet/src/java/org/virbo/autoplot/ScriptServlet.java">etc</a>)
    but this is not thorough.
    Scripts are logged in /tmp/autoplotservlet or the location indicated in the environment variable AUTOPLOT_SERVLET_HOME.
    The file AUTOPLOT_SERVLET_HOME/allowhosts can be used
    to restrict access to the service, and by default only localhost is allowed.  It is a list of
    allowed clients IP, allowing *'s (globs or wildcards) to match multiple IPs. 
    </p>

    <p>Note there are issues with the design right now, and this lacks abusive testing!</p>

    Documentation:
    <a href="http://apps-pw.physics.uiowa.edu/hudson/job/autoplot-javadoc/ws/doc/org/virbo/autoplot/ScriptContext.html">Script Context</a>
    <a href="https://autoplot.svn.sourceforge.net/svnroot/autoplot/autoplot/trunk/JythonSupport/src/org/virbo/jythonsupport/imports.py">Imported Codes</a>
    <br><br>
    
    <form action="ScriptServlet" method="POST">
        Enter Script:<br>
        <textarea rows="14" cols="120" name="script" >
response.setContentType("text/plain");
out = response.getOutputStream();

for i in listDirectory('http://www.autoplot.org/data/*.cdf'):
  out.println(i);
out.close();
        </textarea>
        <br>
        <input type="submit" value="Execute" />
    </form>

<!--    Here's another example script:
setCanvasSize( 600, 400 )
setDataSourceURL( 'http://www.sarahandjeremy.net/jeremy/1wire/data/2008/0B000800408DD710.20080118.d2s' )
setTitle( 'Garage 20080118' )
response.setContentType('image/png')
out = response.getOutputStream()
writeToPng( out )
-->

<!--    This demos security.  (Security lacks a thorough study!  Please do not leave this server unattended!)
response.setContentType("text/plain");
out = response.getOutputStream();

f= java.io.File( '/etc/passwd' )
out.println( f.length() )
out.close();
-->
    </body>
</html>
