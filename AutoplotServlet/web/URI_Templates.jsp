<%-- 
    Document   : URI_Templates
    Created on : Sep 28, 2015, 10:35:44 AM
    Author     : jbf
--%>

<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>URI Templates Demo</title>
    </head>
    <body>
        <h1>Parsing URLs</h1>
        <p>URL Templates can be used to interpret a URL.  Given a URL, we can 
            interpret the timespan covered and other information.</p>
        
        
    <form action="SecureScriptServlet" method="GET">
        Enter URL (<a href="http://autoplot.org/help#Wildcard_codes">help</a>):
        Examples:
        <a href="#" onclick="document.getElementById('resourceURI1').value='http://emfisis.physics.uiowa.edu/Flight/RBSP-A/L3/$Y/$m/$d/rbsp-a_magnetometer_1sec-gse_emfisis-L3_$Y$m$d_v$(v,sep).cdf';">A</a>
        <a href="#" onclick="document.getElementById('resourceURI1').value='http://cdaweb.gsfc.nasa.gov/sp_phys/data/omni/hourly/$Y/omni2_h0_mrg1hr_$Y$(m,delta=6)01_v$v.cdf';">B</a>
        <a href="#" onclick="document.getElementById('resourceURI1').value='http://autoplot.org/data/versioning/data_$Y_$m_$d_v$v.qds';">C</a>
        <br>
        <textarea rows="1" cols="120" id="resourceURI1" name="resourceURI" >http://autoplot.org/data/versioning/data_$Y_$m_$d_v$v.qds</textarea><br>
        <br>Enter ISO8601 Time Range limiting the results printed: <br>
        <textarea rows="1" cols="50" name="timerange" >2010-03-01/2010-03-10</textarea><br>
        <input type="checkbox" hidden="true" name="generate"/>
        <input type="hidden" name="scriptFile" value="URI_Templates.jy" />
        <br>
        <input type="submit" value="Try it out" />
    </form>
        
        <h1>Formatting URLs</h1>
        
        <p>We can also use these templates to generate the names of files.  For example, 
            suppose a server doesn't support listings, so all we can do is to generate names.
           This will run much faster, because we don't have to get the file listings
           from the server.
        </p>
        
    <form action="SecureScriptServlet" method="GET">
        Enter URL (<a href="http://autoplot.org/help#Wildcard_codes">help</a>):
        Examples:
        <a href="#" onclick="document.getElementById('resourceURI2').value='http://emfisis.physics.uiowa.edu/Flight/RBSP-A/L3/$Y/$m/$d/rbsp-a_magnetometer_1sec-gse_emfisis-L3_$Y$m$d_v1.3.2.cdf';">A</a>
        <a href="#" onclick="document.getElementById('resourceURI2').value='http://cdaweb.gsfc.nasa.gov/sp_phys/data/omni/hourly/$Y/omni2_h0_mrg1hr_$Y$(m,delta=6)01_v01.cdf';">B</a>
        <a href="#" onclick="document.getElementById('resourceURI2').value='http://autoplot.org/data/versioning/data_$Y_$m_$d_v1.00.qds';">C</a>
        <a href="#" onclick="document.getElementById('resourceURI2').value='data_bartels_$(periodic;offset=2285;start=2000-346T00:00;period=27d).txt';">D</a>
        <a href="#" onclick="document.getElementById('resourceURI2').value='$Y-$j.$(hrinterval,values=A|B|C|D).txt';">E</a>
        <a href="#" onclick="document.getElementById('resourceURI2').value='http://autoplot.org/data/versioning/data_$Y_$m_$d_$(enum,values=1.00|1.02,id=version).qds';">F</a>
        <br>
        <textarea rows="1" cols="120" id="resourceURI2" name="resourceURI" >http://autoplot.org/data/versioning/data_$Y_$m_$d_v1.00.qds</textarea><br>
        <br>Enter ISO8601 Time Range limiting the results printed: <br>
        <textarea rows="1" cols="50" name="timerange" >2010-03-01/2010-03-10</textarea><br>
        <input type="checkbox" hidden="true" name="generate" checked />
        <input type="hidden" name="scriptFile" value="URI_Templates.jy" />
        <br>
        <input type="submit" value="Try it out" />
    </form>        
        
    </body>
</html>
