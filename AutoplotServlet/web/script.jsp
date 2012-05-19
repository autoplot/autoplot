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

    </p>
    Caveat: this allows arbitrary code to be executed on the server, so this 
    server should not be left on and should not be advertised.  We try to guard against attacks with
    taint-checking (for example imports are not allowed) but this is not thorough.  Scripts are logged in /tmp/autoplotservlet.
    The file /tmp/allowhosts can be used to restrict access to the service.  It is a list of
    allowed clients IP, allowing *'s (globs or wildcards) to match multiple IPs.  Note too that the
    /tmp/autoplotservlet location can be changed with the environment variable AUTOPLOT_SERVLET_HOME.
    </p>

    <p>Note there are issues with the design right now, and this lacks abusive testing!</p>

    <a href="http://apps-pw.physics.uiowa.edu/hudson/job/autoplot-javadoc/ws/doc/org/virbo/autoplot/ScriptContext.html">Script Context</a>
    <a href="https://autoplot.svn.sourceforge.net/svnroot/autoplot/autoplot/trunk/JythonSupport/src/org/virbo/jythonsupport/imports.py">Imported Codes</a>
    <br>
    
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

    
    </body>
</html>
