<%-- 
    Document   : demo
    Created on : Dec 18, 2013, 3:04:09 PM
    Author     : jbf
--%>

<%@page import="java.net.URLEncoder"%>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<body>
    <%
         String vap= request.getParameter("vap");
     %>
	<div id="iddivimg">
		<img id="idplot" 
                     src="../../SimpleServlet?vap=<%= URLEncoder.encode(vap,"US-ASCII") %>">
	</div>
    <div id="divprogress">
        <img src="spinner.gif" id="progress" alt="Busy..."></img>
    </div>
        
    <form>
            <button onclick="scanprev();"><< PREV</button>
            <button onclick="scannext();">NEXT >></button>
            <button onclick="zoomout();">Zoom Out</button>    
            <button onclick="zoomin();">Zoom In</button>    
            
	
            <p><button onclick="centerFocus();">Center</button> at x=<input id="xclick" ></input> 
                <button onclick="resetWidth(); return false;">Width</button>=<input id="xwidth" size="4">hr
                    <br>
                        <button onclick="resetTime();">Reset</button> to <input id="timerange" size="50"></input>
            </p>
            
    </form>
            <div id="info"></div>
               
		<!--<pre><div id="iddates"></div></pre> -->
		<!--<pre><div id="idwidthheight"></div></pre>-->
		<!--<pre><div id="idcolumn"></div></pre>-->
		<!--<pre><div id="idrow"></div></pre>-->
		<!--<pre><div id="iddifftime"></div></pre>-->
                
	<script type="text/javascript" src="js/jquery.ui.js"></script>
	<script type="text/javascript" src="js/jquery.imgareaselect.min.js"></script>
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
    <form>
        <input id='vapta' size="80" value="<%=vap%>">
        <button onclick="resetUrl(''); return false;">GO</button>
    </form>
<hr></hr>
<p>Click and drag a range on the plot to zoom in.  Buttons below zoom out
        and scan.  Click to read coordinates, and the center button will center the 
        plot on this click position.</p>
        
        <!-- <p>img src url: 
            <pre><small><div id="idechourl"></div></small></pre></p> -->
        <div id="idstatus">status</div>
        <!-- src="../../SimpleServlet?url=tsds.http%3A%2F%2Ftimeseries.org%2Fget.cgi%3FStartDate%3D20050101%26EndDate%3D20060101%26ext%3Dbin%26out%3Dtsml%26ppd%3D1440%26param1%3DOMNI_OMNIHR-26-v0&font=sans-8&format=image%2Fpng&width=700&height=400&column=5em%2C100%25-10em&row=3em%2C100%25-3em" />-->
        
        <small>v20140110_0725</small>

</body>
</html>