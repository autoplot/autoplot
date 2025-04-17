<%-- 
    Document   : demo
    Created on : Dec 18, 2013, 3:04:09 PM
    Author     : jbf
--%>

<%@page import="org.autoplot.servlet.ServletInfo"%>
<%@page import="java.io.FilenameFilter"%>
<%@page import="java.io.File"%>
<%@page import="org.autoplot.servlet.ServletUtil"%>
<%@page import="org.autoplot.servlet.SimpleServlet"%>
<%@page import="java.net.URLEncoder"%>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
<html xmlns="https://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
<head>
	<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
	<title>Autoplot Servlet Image Zoom</title>
	<link rel="stylesheet" type="text/css" href="css/imgareaselect-default.css" />
        <link rel="stylesheet" type="text/css" href="demo.css" />
</head>
<body>
    
    <script src="https://ajax.googleapis.com/ajax/libs/jquery/1.7/jquery.min.js"></script>                 
    <script type="text/javascript" src="js/jquery.ui.js"></script>
    <script type="text/javascript" src="js/jquery.imgareaselect.min.js"></script>
    <script type="text/javascript" src="TimeRangeParser.js"></script>
    <script type="text/javascript" src="autoplot-zoom.js"></script>
    <script type="text/javascript" src="prepimage.js"></script>
    <script type="text/javascript" src="binaryajax.js"></script>
    <script type="text/javascript" src="imageinfo.js"></script>
        
    <%
         String vap= request.getParameter("vap");
         String uri= request.getParameter("uri");
         String id= request.getParameter("id");
         String timerange= request.getParameter("timerange");

         String[] dropList= null;
         String loadingMessage= null;
         
         if ( id!=null ) {
             File f= new File( new File( ServletUtil.getServletHome(), "users" ), id );
             if ( f.exists() ) {
                 String s= f.getAbsolutePath();
                 String[] ss= f.list( );
                 int count=0;
                 for ( int i=0; i<ss.length; i++ ) {
                     if ( ss[i].endsWith(".vap") ) {
                         if ( i==0 ) loadingMessage= ss[0];
                         ss[count]= s + "/" + ss[i];
                         count=count+1;
                     }
                 }
                 dropList= new String[count];
                 for ( int i=0; i<count; i++ ) {
                     dropList[i]= ss[i];
                 }
             }
         }
         
         String uriOrVap="";
         String ssArg=null;
         if ( vap==null && uri==null ) {
             if ( dropList!=null && dropList.length>0 ) {
                ssArg= "vap="+URLEncoder.encode(dropList[0],"US-ASCII");
             } else {
                if ( id!=null ) {
                    throw new IllegalArgumentException("id specified contains no vap files in "+ ServletUtil.getServletHome().toString()+"/users" );
                } else {
                    out.println("vap file or uri not specified, or id can be one of:<br>");
                    File f= new File( ServletUtil.getServletHome().toString()+"/users" );
                    String[] ff= f.list();
                    if ( ff==null ) {
                        out.println("Server has not been configured to support individual users."); //  It should contain folders in " +ServletUtil.getServletHome().toString()+"/users");
                    } else {
                        for ( String s: ff ) {
                            out.println("<a href='demo.jsp?id="+s+"'>"+s+"</a><br>");
                        }
                    }
                    out.println("<br><br>");
                }
             }
         } else if ( vap!=null ) {
             ssArg= "vap="+URLEncoder.encode(vap,"US-ASCII");
             uriOrVap= vap;
         } else {
             ssArg= "uri="+URLEncoder.encode(uri,"US-ASCII");
             uriOrVap= uri;
         }
         
         if ( timerange!=null ) {
             ssArg= ssArg + "&timerange="+timerange;
         }
         
     %>
        <div id="iddivimg" style="min-width: 700px; min-height: 400px"> 
        <% if (ssArg!=null ) { %>
		<img id="idplot" 
                     src="../../SimpleServlet?<%= ssArg %>"
                     onload="logloaded();" 
                >
            <% } else { %>
		<img id="idplot" 
                     src="blank.png"
                     onload="logloaded();" 
                >            
            <% } %>
	</div>
    <div id="divprogress">
        <% if ( vap!=null || uri!=null ) { %>
        <img src="spinner.gif" id="progress" alt="Busy..."></img>
        <% } else { %>
        <img src="idle-icon.png" id="progress" alt="No URI or vap file."></img>
        <% } %>
    </div>
            <button onclick="scanprev();" title="Previous interval">&lt;&lt; PREV</button>
            <button onclick="scanhalfprev();" title="Previous half interval">&lt;&lt;PR</button>
            <button onclick="scanhalfnext();" title="Next half interval">NE&gt;&gt;</button>
            <button onclick="scannext();" title="Next interval">NEXT &gt;&gt;</button>
            <button onclick="zoomout();" title="Zoom Out">Zoom Out</button>    
            <button onclick="zoomin();" title="Zoom In">Zoom In</button>    
            <button onclick="refresh();" title="Refresh">Refresh</button>    
            	
            <p><button onclick="centerFocus();" title="Shift center to here">Center</button> at x=<input id="xclick" ></input> 
                <button onclick="resetWidth();" title="Make this the new width">Width</button>=<input id="xwidth" size="4">hr
                    <br>
                        <button onclick="resetTime();" title="Jump to this range">Reset</button> to <input id="timerange" size="50"></input>
            </p>
            <input id='vapta' size="80" value="<%=uriOrVap%>"></input>
            <button onclick="resetUrl(''); return false;"><img src='go.png' alt="GO"></img></button>
</form><br>          
            <span id="aplink"></span>
            <div id="info"></div>
               
		<!--<pre><div id="iddates"></div></pre> -->
		<!--<pre><div id="idwidthheight"></div></pre>-->
		<!--<pre><div id="idcolumn"></div></pre>-->
		<!--<pre><div id="idrow"></div></pre>-->
		<!--<pre><div id="iddifftime"></div></pre>-->
                
        <script src="https://ajax.googleapis.com/ajax/libs/jquery/1.7/jquery.min.js"></script>                 
	<script type="text/javascript" src="js/jquery.ui.js"></script>
	<script type="text/javascript" src="js/jquery.imgareaselect.min.js"></script>
        <script type="text/javascript" src="TimeRangeParser.js"></script>
	<script type="text/javascript" src="autoplot-zoom.js"></script>
        <script type="text/javascript" src="prepimage.js"></script>
        <script type="text/javascript" src="binaryajax.js"></script>
        <script type="text/javascript" src="imageinfo.js"></script>

<script>
    $("#idplot").click(clickshift);
			// TODO: binaryajax.js makes an AJAX request for the image which has
			// already been loaded.  This is not necessary.  Modify binaryajax.js
			// so that it looks to see if image has been loaded before making AJAX request.
			
     		// Global Object that contains info about each image.		
        		var PLOTINFO = {};

     		// When DOM is ready (but images may not be loaded), start working.
     /*		$().ready(function () {
     			// Iterate over all images in DOM.
	        		$('img').each(function () {
	        			prepimage($(this).attr("id"));		        			
	        		});
     			     			
     		}) */
        </script>
        
<br>
    <% if ( vap!=null || uri!=null ) { %>
        <div id="idstatus">loading <%=loadingMessage!=null ? loadingMessage : ssArg %></div>
     <% } %>
        
<hr></hr>
        <%
            if ( id!=null ) {
                File f= new File( new File( ServletUtil.getServletHome(), "users" ), id );
                out.println( ".vap files in " + f + ":</br>");
                if ( dropList!=null ) {
                    for ( String s: dropList ) {
                        String label= s.substring(f.toString().length()+1);
                        out.println( "<a href=\"#\" onclick=\"resetUrl('../../SimpleServlet?vap="+s+"');\">"+label+"</a>" );
                    }
                }
            }
        %>

<p>Click and drag a range on the plot to zoom in.  Buttons below zoom out
        and scan.  Click to read coordinates, and the center button will center the 
        plot on this click position.</p>
Note there is also a version of this at https://github.com/autoplot/web/tree/gh-pages.<br>
        
        <!-- <p>img src url: 
            <pre><small><div id="idechourl"></div></small></pre></p> -->
        <!-- src="../../SimpleServlet?url=tsds.http%3A%2F%2Ftimeseries.org%2Fget.cgi%3FStartDate%3D20050101%26EndDate%3D20060101%26ext%3Dbin%26out%3Dtsml%26ppd%3D1440%26param1%3DOMNI_OMNIHR-26-v0&font=sans-8&format=image%2Fpng&width=700&height=400&column=5em%2C100%25-10em&row=3em%2C100%25-3em" />-->
        
        <small><%= ServletInfo.version  %></small>
</body>
</html>