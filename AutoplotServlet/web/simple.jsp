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
    <body>

    <h1>Autoplot Simple Servlet</h1>
    
    <p>This demonstrates how Autoplot can be used within a web application
    container to provide static images.</p>

    <h2>Enter Autoplot URI:</h2>

    <form action="SimpleServlet">
        <input name="url" value="http://www.sarahandjeremy.net/~jbf/1wire/data/2007/0B000800408DD710.%Y%m%d.d2s?timerange=20071210" size="80" type="text">
        <input value="Plot" type="submit">
    </form>
<br>
    <form action="SimpleServlet">
       <input name="url" value="vap+tsds:http://timeseries.org/get.cgi?StartDate=20030301&EndDate=20030401&ext=bin&out=tsml&ppd=1440&param1=OMNI_OMNIHR-26-v0" size="80" type="text">
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
        <input name="url" value="vap+tsds:http://timeseries.org/get.cgi?StartDate=20030101&EndDate=20080831&ext=bin&out=tsml&ppd=1440&param1=OMNI_OMNIHR-26-v0" size="100" type="text"><br>
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
        <input type="checkbox" name="autolayout" value="true" >autolayout</input><br>
        layout horiz position: <input name="column" value="5em,100%-10em"> (<a href="http://autoplot.org/help#layout">help</a>)<br>
        layout vert position: <input name="row" value="3em,100%-3em">  (<a href="http://autoplot.org/help#layout">help</a>)<br>
        <br>
        timerange: <input name="timeRange" value="2003-mar"><br>
        <br>
        plot style: <select name="renderType">
            <option selected></option>
            <option>spectrogram</option>
            <option>series</option>
            <option>scatter</option>
            <option>stairSteps</option>
            <option>fill_to_zero</option>
        </select><br>
        color: <input name="color" value="#0000ff"><br>
        fillColor: <input name="fillColor" value="#aaaaff"><br>
        foreground: <input name="foregroundColor" value="#ffffff"><br>
        background: <input name="backgroundColor" value="#000000"><br>
        <input value="Plot" type="submit">
    </form>
    </body>
    
    
</html>
