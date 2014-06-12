<%-- 
    Document   : CdawebVapServlet
    Created on : Jun 12, 2014, 6:28:53 AM
    Author     : jbf
--%>

<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>Demo CdawebVapServlet</title>
    </head>
    <body>
        <h1>VapServlet for CDAWeb</h1>
        <p>This is an experimental servlet that takes a set of URIs and forms a vap from it.  This requires Autoplot 2014a_6, which 
            allows more parts of the vap to be missing.</p>
        
        <form action="CdawebVapServlet">
            timeRange:
            <input size="24" type="text" name="timeRange" value="1983-02-17"><br>
            data0:
            <input size="60" type="text" name="data0" value="vap+cdaweb:ds=DE_VS_EICS&id=Geographic_position&slice1=2"><br>
            data1:
            <input size="60" type="text" name="data1" value="vap+cdaweb:ds=DE2_DUCT16MS_RPA&id=mlt"><br>
            data2: 
            <input size="60" type="text" name="data2" value="vap+cdaweb:ds=DE_VS_EICS&id=h_flux"><br>
            <input type="submit" value="Send .vap file">
        </form>
        
        <p>Notes:</p>
        <ul>
            <li>the autoLabel and autoRange properties of the top plot are ignored as the v1.08 vap is read in.  This will be fixed.
            <li>the spectrogram colorbar is always showing, and I need to make it so I can hide that.  The problem is only Autoplot knows if it is needed.
            <li>I haven't tested this recently for spectrograms.
        </ul>
                
            <small>version 20140612.2</small>
    </body>
</html>
