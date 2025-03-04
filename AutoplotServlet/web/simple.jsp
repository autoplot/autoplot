<%@page contentType="text/html"%>
<%@page pageEncoding="UTF-8"%>
<%--
The taglib directive below imports the JSTL library. If you uncomment it,
you must also add the JSTL library to the project. The Add Library... action
on Libraries node in Projects view can be used to add the JSTL 1.1 library.
--%>
<%--
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%> 
--%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
   "http://www.w3.org/TR/html4/loose.dtd">

<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>Autoplot Simple Servlet</title>
    </head>
    <style>
.tooltip {
  position: relative;
  display: inline-block;
  border-bottom: 1px dotted black;
}

.tooltip .tooltiptext {
  visibility: hidden;
  width: 120px;
  background-color: black;
  color: #fff;
  text-align: center;
  border-radius: 6px;
  padding: 5px 0;

  /* Position the tooltip */
  position: absolute;
  z-index: 1;
}
.tooltip:hover .tooltiptext {
  visibility: visible;
}
</style>
    <body>

    <h1>Autoplot Simple Servlet</h1>
    
    <p>This demonstrates how Autoplot can be used within a web application
    container to provide static images.</p>

    <h2>Enter Autoplot URI:</h2>

    <form action="SimpleServlet">
        <input name="url" value="http://jfaden.net/~jbf/1wire/data/2007/0B000800408DD710.$Y$m$d.d2s?timerange=20071210" size="80" type="text">
        <input value="Plot" type="submit">
    </form>
<br>
    <form action="SimpleServlet">
       <input name="url" value="vap+cdaweb:ds=OMNI2_H0_MRG1HR&id=DST1800&timerange=Mar+2003" size="80" type="text">
        <input value="Plot" type="submit">
    </form>


<h2>Or refer to .vap files:</h2>
<form action="SimpleServlet">
        <input name="vap" value="http://autoplot.org/data/autoplot-applet.vap" size="80" type="text">
        <input value="Plot" type="submit">
    </form>
    <br>
<form action="SimpleServlet">
    <input name="vap" value="http://autoplot.org/data/autoplot-applet.vap?timerange=2003-05-08" size="80" type="text">
        <input value="Plot" type="submit">
    </form>

    <h1>This shows all sorts of controls:</h1>
    <form action="SimpleServlet">
        <input name="url" value="vap+cdaweb:ds=OMNI2_H0_MRG1HR&id=DST1800" size="100" type="text"><br>
        Apply a process to the dataset after loading: <select name="process">
            <option selected></option>
            <option>histogram</option>
            <option>magnitude(fft)</option>
        </select>
        <br>
        font: <input name="font" value="sans-8" ><br>
        format: <select name="format">
            <option>image/png</option>
            <option>application/pdf</option>
            <option>image/svg+xml</option>
        </select><br>
        width: <input name="width" value="700" ><br>
        height: <input name="height" value="400" > <br>
        <br>
        <input type="checkbox" name="autolayout" value="true" ><div class="tooltip">autolayout<span class="tooltiptext">automatically position the plot to make room for labels and colorbars.</span></div></input><br>
        <div class="tooltip">layout horiz position<span class="tooltiptext">horizontal position of the plot.</span></div>: <input name="column" value="5em,100%-10em"> (<a href="https://github.com/autoplot/documentation/blob/master/md/help.md#modifying-layout">help</a>)<br>
        <div class="tooltip">layout vert position<span class="tooltiptext">vertical position of the plot.</span></div>: <input name="row" value="3em,100%-3em">  (<a href="https://github.com/autoplot/documentation/blob/master/md/help.md#modifying-layout">help</a>)<br>
        <br>
        time range: <input name="timeRange" value="2003-mar"><br>
        <br>
        <div class="tooltip">plot style<span class="tooltiptext">style of the plot.</span></div>: <select name="renderType">
            <option selected></option>
            <option>spectrogram</option>
            <option>series</option>
            <option>scatter</option>
            <option>stairSteps</option>
            <option>fill_to_zero</option>
        </select><br>
        <div class="tooltip">color<span class="tooltiptext">symbol and lineplot color.</span></div>: <input name="color" value="#e0e0ff"><br>
        <div class="tooltip">symbol size<span class="tooltiptext">size (in pixels) of plot symbols.</span></div>: <input name="symbolSize" value=""><br>
        <div class="tooltip">fill color<span class="tooltiptext">fill color is the color used when "fill_to_zero" is used.</span></div>: <input name="fillColor" value="#aaaaff"><br>
        <div class="tooltip">foreground<span class="tooltiptext">color used for axes and labels.</span></div>: <input name="foregroundColor" value="#ffffff"><br>
        <div class="tooltip">background<span class="tooltiptext">canvas color, or "none" for transparent.</span></div>: <input name="backgroundColor" value="#000000"><br>
        <input type="checkbox" name="drawGrid" value="true" ><div class="tooltip">drawGrid<span class="tooltiptext">T or true will draw a grid.</span></div></input><br>
        <input value="Plot" type="submit">
    </form>
    </body>
    
    
</html>
