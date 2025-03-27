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
        <h1>Parsing URIs</h1>
        <p>URI Templates can be used to interpret a URI.  Given a URI, we can 
            interpret the timespan covered and other information.  Note only
            URLs, a type of URI, are supported here, and it is assumed that the
            server supports directory listings.
        </p>
        
        
    <form action="SecureScriptServlet" method="GET">
        Enter URI (<a href="https://github.com/hapi-server/uri-templates/wiki/Specification#Time_Range_Rules">help</a>):
        Examples:
        <a href="#" onclick="document.getElementById('resourceURI1').value='https://emfisis.physics.uiowa.edu/Flight/RBSP-A/L3/$Y/$m/$d/rbsp-a_magnetometer_1sec-gse_emfisis-L3_$Y$m$d_v$(v,sep).cdf';">A</a>
        <a href="#" onclick="document.getElementById('resourceURI1').value='https://cdaweb.gsfc.nasa.gov/sp_phys/data/omni/hourly/$Y/omni2_h0_mrg1hr_$Y$(m;delta=6)01_v$v.cdf';">B</a>
        <a href="#" onclick="document.getElementById('resourceURI1').value='https://autoplot.org/data/versioning/data_$Y_$m_$d_v$v.qds';">C</a>
        <a href="#" onclick="document.getElementById('resourceURI1').value='https://cdaweb.gsfc.nasa.gov/pub/data/rbsp/rbspa/l4/emfisis/density/2017/rbsp-a_density_emfisis-l4_$Y$m$(d,delta=7,phasestart=2017-01-01)_v$(v,sep).cdf';
                             document.getElementById('timerange').value='2017/P1Y'">D</a>
        <br>
        <textarea rows="1" cols="160" id="resourceURI1" name="resourceURI" >http://autoplot.org/data/versioning/data_$Y_$m_$d_v$v.qds</textarea><br>
        <br>Enter ISO8601 Time Range limiting the results printed: <br>
        <textarea rows="1" cols="50" id="timerange" name="timerange">2010-03-01/2010-03-10</textarea><br>
        <input type="checkbox" hidden="true" name="generate" value="off"/>
        <input type="hidden" name="scriptFile" value="URI_Templates.jy" />
        <br>
        <input type="submit" value="Try it out" />
    </form>
        
        <h1>Generating URIs</h1>
        
        <p>We can also use these templates to generate the names of files.  For example, 
            suppose a server doesn't support listings, so all we can do is to generate names.
           This will run much faster, because we don't have to get the file listings
           from the server.
        </p>
        
    <form action="SecureScriptServlet" method="GET">
        Enter URI (<a href="https://github.com/hapi-server/uri-templates/wiki/Specification#Time_Range_Rules">help</a>):
        Examples:
        <a href="#" onclick="document.getElementById('resourceURI2').value='http://emfisis.physics.uiowa.edu/Flight/RBSP-A/L3/$Y/$m/$d/rbsp-a_magnetometer_1sec-gse_emfisis-L3_$Y$m$d_v1.3.2.cdf';">A</a>
        <a href="#" onclick="document.getElementById('resourceURI2').value='https://cdaweb.gsfc.nasa.gov/sp_phys/data/omni/hourly/$Y/omni2_h0_mrg1hr_$Y$(m;delta=6)01_v01.cdf';">B</a>
        <a href="#" onclick="document.getElementById('resourceURI2').value='http://autoplot.org/data/versioning/data_$Y_$m_$d_v1.00.qds';">C</a>
        <a href="#" onclick="document.getElementById('resourceURI2').value='http://data.org/data_bartels_$(periodic;offset=2285;start=2000-346T00:00;period=27d).txt';">D</a>
        <a href="#" onclick="document.getElementById('resourceURI2').value='http://data.org/$Y-$j.$(hrinterval;values=A,B,C,D).txt';">E</a>
        <a href="#" onclick="document.getElementById('resourceURI2').value='http://autoplot.org/data/versioning/data_$Y_$m_$d_$(enum;values=1.00,1.02;id=version).qds';">F</a>
        <a href="#" onclick="document.getElementById('resourceURI2').value='http://data.org/data_$Y_$j_$H$M$S.$(subsec;places=1)';">G</a> <!-- shows rounding error -->
        <br>
        <textarea rows="1" cols="120" id="resourceURI2" name="resourceURI" >http://autoplot.org/data/versioning/data_$Y_$m_$d_v1.00.qds</textarea><br>
        <br>Enter ISO8601 Time Range limiting the results printed: <br>
        <textarea rows="1" cols="50" name="timerange" >2010-03-01/2010-03-10</textarea><br>
        <input type="hidden" name="generate" value="1"/>
        <input type="hidden" name="scriptFile" value="URI_Templates.jy" />
        <br>
        <input type="submit" value="Try it out" />
    </form>        
        
        <h1>Hybrid Generating/Parsing URIs</h1>
        <p>This will use the generation code for all parts of the URI which can be handled
            with generation, and then will switch to parsing for parts that need
            parsing.  For example, the $Y/$m/$d/ components can be resolved, and then 
            listings are used to resolve $H$M$(S,sparse).png.
        </p>
        
        
    <form action="URITemplatesServlet" method="GET">
        Enter URI (<a href="https://github.com/hapi-server/uri-templates/wiki/Specification#Time_Range_Rules">help</a>):
        Examples:
        <a href="#" onclick="document.getElementById('resourceURI3').value='http://emfisis.physics.uiowa.edu/Flight/RBSP-A/L3/$Y/$m/$d/rbsp-a_magnetometer_1sec-gse_emfisis-L3_$Y$m$d_v$(v,sep).cdf';">A</a>
        <a href="#" onclick="document.getElementById('resourceURI3').value='https://cdaweb.gsfc.nasa.gov/sp_phys/data/omni/hourly/$Y/omni2_h0_mrg1hr_$Y$(m;delta=6)01_v$v.cdf';">B</a>
        <a href="#" onclick="document.getElementById('resourceURI3').value='https://autoplot.org/data/agg/hyd/$Y/po_h0_hyd_$Y$m$d_v$v.cdf';document.getElementById('timerange3').value='1999-2000';">C</a>
        <a href="#" onclick="document.getElementById('resourceURI3').value='http://sarahandjeremy.net/~jbf/powerMeter/$Y/$m/$d/$H$M$(S,sparse).jpg';document.getElementById('timerange3').value='2015-05-01';">D</a>
        <a href="#" onclick="document.getElementById('resourceURI3').value='https://spdf.gsfc.nasa.gov/pub/pre_generated_plots/kp_plots/ace/gif/ac_$Y$j00-$(Y;end)$(j;end)00.gif';document.getElementById('timerange3').value='1998-350';">E</a>
        <br>
        <textarea rows="1" cols="120" id="resourceURI3" name="resourceURI" >https://autoplot.org/data/versioning/data_$Y_$m_$d_v$v.qds</textarea><br>
        <br>Enter ISO8601 Time Range limiting the results printed: <br>
        <textarea rows="1" cols="50" id="timerange3" name="timerange" >2010-03-01/2010-03-10</textarea><br>
        <input type="hidden" name="generate" value="0"/>
        <input type="hidden" name="mode" value="hybrid" />
        <br>
        <input type="submit" value="Try it out" />
    </form>        
        <br><small>version 20240803.1</small>
    </body>
</html>
