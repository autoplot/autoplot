/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot;

import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.text.ParseException;
import java.util.List;
import java.util.Map;
import javax.swing.JComponent;
import org.autoplot.dom.Application;
import org.autoplot.dom.DomNode;
import org.autoplot.dom.Plot;
import org.das2.components.DataPointRecorder;
import org.das2.event.MouseModule;
import org.das2.graph.DasColorBar;
import org.das2.qds.QDataSet;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.jdesktop.beansbinding.Converter;
import org.python.core.PyFunction;

/**
 *
 * @author jbf
 */
public class ScriptContext {

    private static ScriptContext2023 sc= ScriptContext2023.getInstance();
    
    /**
     * set the current application for commands.  For example, to
     * have two windows plotting data, you would:
     * <pre>
     * plot([1,2,3])
     * popup=newWindow('popup')
     * setWindow(popup)
     * plot([3,2,1])
     * </pre>
     * @param app the application
     * @see #setWindow(org.autoplot.ApplicationModel) 
     * @see #newApplication(java.lang.String) 
     */
    public static synchronized void setApplication( AutoplotUI app ) {
        sc.setApplication(app);
    }
    
    /**
     * reset the script focus to the default application.
     */
    public  static synchronized void setDefaultApplication() {
        sc.setDefaultApplication();
    }
    
    /**
     * Set the application model.  This is the simplest Autoplot implementation
     * existing, where there are no buttons, etc.
     * @param appm 
     * @see #newWindow(java.lang.String) 
     * @see #getWindow() which returns the current window.
     * @see #setApplication(org.autoplot.AutoplotUI) which creates another application.
     */
    public  static synchronized void setWindow( ApplicationModel appm ) {
        sc.setWindow(appm);
    }
    
    /**
     * return the focus application.
     * @return the focus application.
     */
    public static synchronized AutoplotUI getApplication() {
        return sc.getApplication();
    }
    
    /**
     * return the internal handle for the window and dom within.
     * @return the internal handle for the application.
     */
    public static synchronized ApplicationModel getWindow() {
        return sc.getWindow();
    }
        
    /**
     * get or create the application identified by the name.  For example:
     *<blockquote><pre>
     *ds= ripples(20,20)
     *plot( 0, ds )
     *sliceWindow= newApplication('slice')
     *setApplication(sliceWindow)
     *plot( slice0(ds,0) ) 
     * </pre></blockquote>
     * Windows with this name will be reused.
     * @param id an identifier
     * @return the AutoplotUI.
     */
    public static synchronized AutoplotUI newApplication( String id ) {
        return sc.newApplication(id);
    }
    
    /**
     * create a new window with the given location and size.
     * @param id identifier for the window
     * @param width the canvas width
     * @param height the canvas height
     * @param x the window upper-left location, if possible
     * @param y the window upper-left location, if possible
     * @return a handle (do not use this object, code will break) for the window.
     * @see #setWindow(org.autoplot.ApplicationModel) 
     */
    public static synchronized ApplicationModel newWindow( final String id, int width, int height, int x, int y ) {
        return sc.newWindow(id, width, height, x, y);
    }
    
    /**
     * set the window location
     * @param x the window upper-left location, if possible
     * @param y the window upper-left location, if possible
     */
    public static synchronized void setWindowLocation( int x, int y ) {
        sc.setWindowLocation(x, y);
    }
    
    /**
     * return a new Autoplot.
     * @param parent
     * @param title
     * @return 
     */
    public static synchronized ApplicationModel newDialogWindow( Window parent, final String title ) {
        return sc.newDialogWindow(parent, title);
    }

    /**
     * create a new window.
     * @param id identifier for the window
     * @return a handle (do not use this object, code will break) for the window.
     * @see #setWindow(org.autoplot.ApplicationModel) 
     */
    public static synchronized ApplicationModel newWindow( final String id ) {
        return sc.newWindow( id );
    }
            
    /**
     * set the focus for scripts.
     * @param m the application model.
     * @see setWindow, which should be used instead.
     */
    public synchronized static void setApplicationModel(ApplicationModel m) {
        sc.setApplicationModel(m);
    }
    
    /**
     * return the Window for the application, to be used for dialogs.
     * See createGui(), which creates the view.
     * @return
     */
    public synchronized static Window getViewWindow() {
        return sc.getViewWindow();
    }

    /**
     * resets the output stream.  This method is used internally, do not use
     * this routine.
     * @param out
     */
    public static synchronized void _setOutputStream(OutputStream out) {
        sc._setOutputStream(out);
    }
    
    /**
     * set the size of the canvas.  This is only used when the GUI is not used, and in
     * headless mode, otherwise the GUI controls the size of the canvas.
     * 
     * @param width the width of the canvas
     * @param height the height of the canvas
     */
    public static synchronized  void setCanvasSize( final int width, final int height) {
        sc.setCanvasSize(width, height);
    }

    /**
     * set the internal model's url to surl.  The data set will be loaded,
     * and writeToPng and writeToSvg can be used in headless mode.
     * @param surl
     * @Deprecated use plot instead;
     */
    public static synchronized void setDataSourceURL(String surl) {
        sc.setDataSourceURL(surl);
    }

    /**
     * bring up the autoplot with the specified URL.  Note the URI is resolved
     * asynchronously, so errors thrown during the load cannot be caught here.
     * See getDataSet to load data synchronously.
     * @param suri a URI or vap file
     */
    public static synchronized void plot(String suri) {
        sc.plot(suri);
    }

    /**
     * plot one URI against another.  No synchronization is done, so beware.
     * Introduced for testing non-time axis TSBs.
     * @param surl1 the independent variable dataset (generally used for X)
     * @param surl2 the dependent variable dataset (generally used for Y, but can be rank 2).
     */
    public  static synchronized void plot(String surl1, String surl2) {
        sc.plot(surl1,surl2);
    }

    /**
     * bring up the autoplot with the specified URL.
     * @param chNum the data source number to reset the URI.
     * @param surl a URI to use
     */
    public  static synchronized void plot(int chNum, String surl) {
        sc.plot(chNum,surl);
    }

    /**
     * bring up the plot with the specified URI.
     * @param chNum the data source number to reset the URI.
     * @param label for the plot.
     * @param surl a URI to use
     */
    public  static synchronized void plot(int chNum, String label, String surl) {
        sc.plot(chNum,label,surl);
    }

    /**
     * plot the dataset in the first dataSource node.
     * @param ds QDataSet to plot
     */
    public  static synchronized void plot(QDataSet ds) {
        sc.plot(ds);
    }

    /**
     * plot the dataset in the first dataSource node.
     * @param x QDataSet for the independent parameter
     * @param y QDataSet for the dependent parameter
     */
    public  static synchronized void plot( QDataSet x, QDataSet y )  {
        sc.plot( x, y );
    }
    
    /**
     * plot the dataset in the first dataSource node.
     * @param x QDataSet for the independent parameter for the X values
     * @param y QDataSet for the independent parameter for the Y values
     * @param z Rank 1 or Rank 2 QDataSet for the dependent parameter
     */
    public  static synchronized void plot( QDataSet x, QDataSet y, QDataSet z ) {
        sc.plot( 0, (String)null, x, y, z );
    }

    /**
     * plot the dataset in the specified dataSource node.
     * @param chNum the plot to use.  Plots and plot elements are added as necessary to plot the data.
     * @param ds dataset to plot.
     */
    public  static synchronized void plot( int chNum, QDataSet ds)  {
        sc.plot( chNum, ds );
    }

    /**
     * plot the dataset in the specified  dataSource node.
     * @param chNum the plot to use.  Plots and plot elements are added as necessary to plot the data.
     * @param x QDataSet for the independent parameter for the X values
     * @param y QDataSet for the independent parameter for the Y values
     */
    public static  synchronized void plot( int chNum, QDataSet x, QDataSet y )  {
        sc.plot( chNum, x, y );
    }

    /**
     * plot the dataset in the specified  dataSource node.
     * @param chNum the plot to use.  Plots and plot elements are added as necessary to plot the data.
     * @param x QDataSet for the independent parameter for the X values
     * @param y QDataSet for the independent parameter for the Y values
     * @param z Rank 1 or Rank 2 QDataSet for the dependent parameter
     */
    public  static synchronized void plot( int chNum, QDataSet x, QDataSet y, QDataSet z ) {
        sc.plot( chNum, x, y, z );
    }

    /**
     * returns a color table with the given name.  If the name is already registered,
     * then the registered colortable is returned.
     * using QDataSets as inputs to make it easier to use in scripts.
     * 
     * @param name the name for the colortable.   
     * @param index control points for the colors, or None
     * @param rgb dataset of ds[N,3] where N is the number of colors
     * @return object representing the color table.
     * @see rgbColorDataset
     */
    public synchronized static DasColorBar.Type makeColorTable( String name, QDataSet index, QDataSet rgb ) {
        return ScriptContext2023.makeColorTable(name, index, rgb);
    }    
    

    /**
     * bring up the autoplot with the dataset
     * @param chNum the plot to use.  Plots and plot elements are added as necessary to plot the data.
     * @param label the label for the plot dependent parameter
     * @param ds the dataset to use.
     */
    public static synchronized void plot( int chNum, String label, QDataSet ds) {
        sc.plot( chNum, label, ds );
    }

    /**
     * plot the dataset in the specified  dataSource node.
     * @param chNum the plot to use.  Plots and plot elements are added as necessary to plot the data.
     * @param label the label for the plot dependent parameter
     * @param x QDataSet for the independent parameter for the X values
     * @param y QDataSet for the independent parameter for the Y values
     */
    public static synchronized void plot( int chNum, String label, QDataSet x, QDataSet y ) {
        sc.plot( chNum, label, x, y );
    }

    /**
     * plot the dataset in the specified  dataSource node.  Note this should not
     * be called from the event thread.
     * @param chNum the plot to use.  Plots and plot elements are added as necessary to plot the data.
     * @param label the label for the plot dependent parameter
     * @param x QDataSet for the independent parameter for the X values, or null.
     * @param y QDataSet for the independent parameter for the Y values, or null.
     * @param renderType string explicitly controlling the renderType and hints.
     */    
    public static synchronized void plot( int chNum, String label, QDataSet x, QDataSet y, String renderType ) {
        sc.plot( chNum, label, x, y, renderType );
    }

    /**
     * plot the dataset in the specified  dataSource node.  Note this should not
     * be called from the event thread.
     * @param chNum the plot to use.  Plots and plot elements are added as necessary to plot the data.
     * @param label the label for the plot dependent parameter
     * @param x QDataSet for the independent parameter for the X values, or null.
     * @param y QDataSet for the independent parameter for the Y values, or null.
     * @param renderType string explicitly controlling the renderType and hints.
     * @param reset reset by autoranging, autolabelling, etc.
     */    
    public static synchronized void plot( int chNum, String label, QDataSet x, QDataSet y, String renderType, boolean reset ) {        
        sc.plot(chNum, label, x, y, renderType, reset);
    }
    
    /**
     * plot the dataset in the specified  dataSource node.
     * @param chNum the plot to use.  Plots and plot elements are added as necessary to plot the data.
     * @param label the label for the plot dependent parameter
     * @param x QDataSet for the independent parameter for the X values
     * @param y QDataSet for the independent parameter for the Y values
     * @param z Rank 1 or Rank 2 QDataSet for the dependent parameter
     */
    public static synchronized void plot( int chNum, String label, QDataSet x, QDataSet y, QDataSet z ) {
        sc.plot( chNum, label, x, y, z ) ;
    }

    /**
     * plot the dataset in the specified dataSource node, using the render type
     * specified.  The renderType parameter is a string identifier, and currently the following are
     * used: digital spectrogram nnSpectrogram hugeScatter series scatter colorScatter stairSteps
     * fillToZero digital image  pitchAngleDistribution eventsBar vectorPlot orbitPlot contour
     *<blockquote><pre><small>{@code
     *plot( 0, 'label', findgen(20), ripples(20), ripples(20), 'digital' )
     *from org.autoplot import RenderType
     *plot( 0, 'label', findgen(20), ripples(20), ripples(20), RenderType.digital.toString() )
     *}</small></pre></blockquote>
     *
     * @param chNum the plot to use.  Plots and plot elements are added as necessary to plot the data.
     * @param label the label for the dependent parameter
     * @param x QDataSet for the independent parameter for the X values
     * @param y QDataSet for the independent parameter for the Y values
     * @param z Rank 1 or Rank 2 QDataSet for the dependent parameter, or null.
     * @param renderType hint at the render type to use, such as "nnSpectrogram" or "digital", 
     */
    public synchronized  void plot( int chNum, String label, QDataSet x, QDataSet y, QDataSet z, String renderType ) {
        sc.plot(chNum, label, x, y, z, renderType );
    }

    /**
     * plot the dataset in the specified dataSource node, using the render type
     * specified.  The renderType parameter is a string identifier, and currently the following are
     * used: digital spectrogram nnSpectrogram hugeScatter series scatter colorScatter stairSteps
     * fillToZero digital image  pitchAngleDistribution eventsBar vectorPlot orbitPlot contour
     *<blockquote><pre><small>{@code
     *plot( 0, 'label', findgen(20), ripples(20), ripples(20), 'digital' )
     *from org.autoplot import RenderType
     *plot( 0, 'label', findgen(20), ripples(20), ripples(20), RenderType.digital.toString() )
     *}</small></pre></blockquote>
     *
     * @param chNum the plot to use.  Plots and plot elements are added as necessary to plot the data.
     * @param label the label for the dependent parameter
     * @param x QDataSet for the independent parameter for the X values
     * @param y QDataSet for the independent parameter for the Y values
     * @param z Rank 1 or Rank 2 QDataSet for the dependent parameter, or null.
     * @param renderType hint at the render type to use, such as "nnSpectrogram" or "digital", 
     * @param reset reset by autoranging, autolabelling, etc.
     */
    public static synchronized void plot( int chNum, String label, QDataSet x, QDataSet y, QDataSet z, String renderType, boolean reset ) {
        sc.plot(chNum, label, x, y, z, renderType, reset);
    }
    
    /**
     * "overplot" by adding another PlotElement to the plot and setting the data to this PlotElement.
     * @param chNum the focus 
     * @return the channel number for the new plot element.
     * @see #setLayout(int, int) 
     */
    public static synchronized int addPlotElement( int chNum ) {
        return sc.addPlotElement(chNum);
    }
    
    /**
     * add code that will respond to mouse events.  This will receive an 
     * event following the mouse release when a box is dragged out.
     *<blockquote><pre><small>{@code
def boxLookup( evt ):
    showMessageDialog( "<html>start: (%s,%s)<br>finish: (%s,%s)" %
        ( evt.getStartX(), evt.getStartY(), 
        evt.getFinishX(), evt.getFinishY() ) )
  
addMouseModule( dom.plots[0], 'Box Lookup', boxLookup )   
     *}</small></pre></blockquote>
     * @param plot the plot which will receive the events.
     * @param label a label for the mouse module.
     * @param listener the PyFunction to call with new events.
     * @return the mouse module.  
     * @see org.das2.event.MouseModule#setDragRenderer(org.das2.event.DragRenderer) setDragRenderer to see how to set how feedback can be provided.
     * @see org.das2.event.BoxSelectionEvent BoxSelectionEvent for the methods of the event.
     * 
     */
    public synchronized static MouseModule addMouseModule( Plot plot, String label, final PyFunction listener ) {
        return ScriptContext2023.addMouseModule( plot, label, listener);
    }
    
    /**
     * add code that will paint custom graphics on the canvas or on a plot.
     * The command will be invoked after all other painting is done, making
     * the decoration to be on top.  Note plots can only have one decoration,
     * and the Canvas can have any number.  Calling reset() will remove all
     * decorations.
     *<blockquote><pre><small>{@code
def paint(g):
    for i in xrange(0,1000,100):
        g.drawOval(500-i/2,500-i/2,i,i)

addTopDecoration( dom.canvases[0], paint )
     *}</small></pre></blockquote>
     * @param node the plot or canvas over which to plot
     * @param painter the PyFunction to call when painting
     * @see https://github.com/autoplot/dev/blob/master/demos/2020/20200229/demoAddBottomDecoration.jy
     * 
     */
    public static void addTopDecoration( DomNode node, final PyFunction painter ) {
        ScriptContext2023.addTopDecoration(node, painter);
    }
    
    /**
     * add code that will paint custom graphics on the canvas or on a plot.
     * The command will be invoked after all other painting is done, making
     * the decoration to be on top.  Note plots can only have one decoration,
     * and the Canvas can have any number.  Calling reset() will remove all
     * decorations.
     *<blockquote><pre><small>{@code
def paint(g):
    g.color= Color.BLUE
    for i in xrange(0,1000,100):
        g.drawOval(500-i/2,500-i/2,i,i)

addBottomDecoration( dom.canvases[0], paint )
     *}</small></pre></blockquote>
     * @param node the plot or canvas over which to plot
     * @param painter the PyFunction to call when painting
     * @see https://github.com/autoplot/dev/blob/master/demos/2020/20200229/demoAddBottomDecoration.jy
     * 
     */
    public static void addBottomDecoration( DomNode node, final PyFunction painter ) {
        ScriptContext2023.addBottomDecoration(node, painter);
    }
    
    /**
     * return a component which can be used to accumulate data.  This is typically
     * inserted into its own tab with the addTab command.  This is set to
     * sort data in X as it comes in, and this can be disabled with setSorted(False)
     *<blockquote><pre><small>{@code
     *dpr= createDataPointRecorder()
     *addTab( 'digitizer', dpr )
     *}</small></pre></blockquote>
     * 
     * @return a new DataPointRecorder.
     * @see DataPointRecorder
     * @see https://github.com/autoplot/dev/blob/master/demos/digitizers/createDataPointRecorder.jy
     * 
     */
    public static DataPointRecorder createDataPointRecorder(  ) {
        return ScriptContext2023.createDataPointRecorder();
    }
    
    /**
     * return a new dom in a minimal Autoplot application.  Use result.getCanvas()
     * to get the DasCanvas which can be added to other components.
     *<blockquote><pre><small>{@code
     *report= createApplicationModel()
     *addTab( 'report', report.canvas )
     *report.setDataSet(linspace(0,1,101))
     *}</small></pre></blockquote>
     * @param id
     * @return 
     */
    public static ApplicationModel createApplicationModel( final String id ) {
        return ScriptContext2023.createApplicationModel(id);
    }
        
    /**
     * set the Autoplot status bar string.  Use the prefixes "busy:", "warning:"
     * and "error:" to set icons.
     * @param message
     * @see #showMessageDialog(java.lang.String) 
     */
    public static void setStatus( String message ) {
        sc.setStatus(message);
    }
    
    /**
     * show a popup that you know the user will see.  Note HTML code will work.
     * @param message 
     */
    public static void alert( String message ) {
        sc.alert(message);
    }
    
    /**
     * show a popup to the scientist, which they must acknowledge before this
     * returns.
     * @param message, possibly containing HTML.
     * @see #setStatus(java.lang.String) 
     */
    public static void showMessageDialog( String message ) {
        sc.showMessageDialog(message);
    }

    
    /**
     * add a tab to the running application.  A new tab will be added with the
     * label, with the component added within a scroll pane.
     * @param label the label for the component.
     * @param c the component to add.
     * @see AutoplotUI#setLeftPanel(javax.swing.JComponent) setLeftPanel which adds to the GUI
     */
    public static void addTab( final String label, final JComponent c  ) {
        sc.addTab(label, c);
    }
    
    /**
     * Set the style used to render the data using a string identifier:
     *   spectrogram, series, scatter, histogram, fill_to_zero, digital
     * @param name string name of the plot style.
     */
    public static void setRenderStyle( String name ) {
        sc.setRenderStyle(name);
    }

    /**
     * This is intended to be used with a debugger.  The developer should put
     * a breakpoint at the out.write statement, and then call peekAt from
     * the script.
     *
     * @param o any object we want to look at.
     * @throws java.io.IOException
     */
    public static void peekAt(Object o) throws IOException {
        sc.peekAt(o);
    }
    
    /**
     * write out the current canvas to a png file.
     * TODO: bug 557: this has issues with the size.  It's coded to get the size from
     *  the DOM, but if it is fitted and has a container it must get size from
     *  the container.  Use writeToPng( filename, width, height ) instead for now.
     *  See writeToPdf(String filename), which appears to have a fix for this that
     *  would affect how this is resolved.
     * Note for relative references, this will use the Java process present working directory (PWD) instead
     * of the PWD variable found in scripts
     * @param filename The name of a local file
     * @throws java.io.IOException
     */
    public static void writeToPng(String filename) throws IOException {
        sc.writeToPng(filename);
    }
    
    /**
     * The name of the script which results in the image, optionally with its arguments.
     * @see org.das2.util.DasPNGConstants
     */
    public static final String PNG_KEY_SCRIPT="AutoplotScriptURI";
    
    /**
     * The Autoplot .vap file which results in the image, optionally with "?" and modifiers.
     * @see org.das2.util.DasPNGConstants
     */
    public static final String PNG_KEY_VAP="AutoplotVap";
    
    /**
     * The Autoplot URI which results in the image.
     * @see org.das2.util.DasPNGConstants
     */
    public static final String PNG_KEY_URI="AutoplotURI";
        
    /**
     * write out the current canvas to a png file.
     * TODO: bug 557: this has issues with the size.  It's coded to get the size from
     *  the DOM, but if it is fitted and has a container it must get size from
     *  the container.  User writeToPng( filename, width, height ) instead for now.
     *  See writeToPdf(String filename), which appears to have a fix for this that
     *  would affect how this is resolved.
     * Note for relative references, this will use the Java process present working directory (PWD) instead
     * of the PWD variable found in scripts
     * @param filename The name of a local file
     * @param width the width in pixels of the png
     * @param height the height in pixels of the png
     * @throws java.io.IOException
     */
    public static void writeToPng( String filename, int width, int height ) throws IOException {
        sc.writeToPng( filename, width, height );
    }

    /**
     * write out the current canvas to a png file, using the given size and also insert
     * additional metadata.
     * Note for relative references, this will use the Java process present working directory (PWD) instead
     * of the PWD variable found in scripts
     * @param filename The name of a local file
     * @param width the width in pixels of the png
     * @param height the height in pixels of the png
     * @param metadata if non-null, then write name/values pairs into the PNG Metadata. "Creation Time", "Software" and "plotInfo" are always added.
     * @see 
     * @throws java.io.IOException
     */
    public static void writeToPng( String filename, int width, int height, Map<String,String> metadata ) throws IOException {
        sc.writeToPng( filename, width, height, metadata );
    }
    
    /**
     * See also writeToPng( OutputStream out )
     * @param image the image to write out.
     * @param filename the name of the output file.
     * @param metadata if non-null, then write name/values pairs into the PNG Metadata. "Creation Time" is always added.  http://englishjavadrinker.blogspot.com/2010/09/png-keywords_12.html
     * @throws IOException
     */
    public static void writeToPng( BufferedImage image, String filename, Map<String,String> metadata ) throws IOException {
        sc.writeToPng( image, filename, metadata );
    }

    /**
     * write out the current canvas to stdout.  This is introduced to support servers.
     * TODO: this has issues with the size.  See writeToPng(filename).
     * @param out the OutputStream accepting the data, which is not closed.
     * @throws java.io.IOException
     */
    public static void writeToPng(OutputStream out) throws IOException {
        sc.writeToPng( out );
    }
    
    /**
     * write out the current canvas to a svg file.
     * Note for relative references, this will use the Java process present working directory (PWD) instead
     * of the PWD variable found in scripts
     * @param filename the local file to write the file.
     * @throws java.io.IOException
     */    
    public static void writeToSvg( String filename ) throws IOException {
        sc.writeToSvg(filename);
    }

    /**
     * write out the current canvas to stdout.  This is introduced to support servers.
     * @param out the OutputStream accepting the data, which is not closed.
     * @throws java.io.IOException
     */
    public static void writeToSvg(OutputStream out) throws IOException {
        sc.writeToSvg(out);
    }
    
    /**
     * write out the current canvas to a pdf file.
     * TODO: this has issues with the size.  See writeToPng(filename).  It looks
     *   like this might be handled here
     * Note for relative references, this will use the Java process present working directory (PWD) instead
     * of the PWD variable found in scripts
     * @param filename the local file to write the file.
     * @throws java.io.IOException
     */
    public static void writeToPdf(String filename) throws IOException {
        sc.writeToPdf(filename);
    }

    /**
     * write out the current canvas to a pdf to the output stream.  This is to
     * support servers.
     * TODO: this has issues with the size.  See writeToPng(filename).  It looks
     *   like this might be handled here
     * @param out the OutputStream
     * @throws java.io.IOException
     */
    public static void writeToPdf( OutputStream out ) throws IOException {
        sc.writeToPdf(out);
    }

    /**
     * creates a BufferedImage from the provided DOM.  This blocks until the
     * image is ready.
     * @param applicationIn
     * @return the image
     */
    public static BufferedImage writeToBufferedImage( Application applicationIn ) {
        return ScriptContext2023.writeToBufferedImage( applicationIn );
    }

    /**
     * convenient method for getting an image from the current canvas.
     * @return
     */
    public BufferedImage writeToBufferedImage( ) {
        return sc.writeToBufferedImage();
    }
    
    /**
     * return an array of URLs that match the spec for the time range provided.
     * For example,
     * <p><blockquote><pre>
     *  uri= 'http://cdaweb.gsfc.nasa.gov/istp_public/data/polar/hyd_h0/$Y/po_h0_hyd_$Y$m$d_v01.cdf?ELECTRON_DIFFERENTIAL_ENERGY_FLUX'
     *  xx= getTimeRangesFor( uri, '2000-jan', '$Y-$d-$m' )
     *  for x in xx:
     *    print x
     * </pre></blockquote><p>
     *
     * @param surl an Autoplot uri with an aggregation specifier.
     * @param timeRange a string that is parsed to a time range, such as "2001"
     * @param format format for the result, such as "%Y-%m-%d"
     * @return a list of URLs without the aggregation specifier.
     * @throws java.io.IOException if the remote folder cannot be listed.
     * @throws java.text.ParseException if the timerange cannot be parsed.
     */
    public static String[] getTimeRangesFor(String surl, String timeRange, String format) throws IOException, ParseException {
        return org.autoplot.jythonsupport.Util.getTimeRangesFor( surl, timeRange, format );
    }

    /**
     * Given a spec to format timeranges and a range to contain each timerange,
     * produce a list of all timeranges covering the range formatted with the
     * spec.  For example, <code>generateTimeRanges( "%Y-%m-%d", "Jun 2009" )</code> would result in
     * 2009-06-01, 2009-06-02, ..., 2009-06-30.
     * @param spec such as "%Y-%m".  Note specs like "%Y%m" will not be parsable.
     * @param srange range limiting the list, such as "2009"
     * @return a string array of formatted time ranges, such as [ "2009-01", "2009-02", ..., "2009-12" ]
     * @throws java.text.ParseException of the outer range cannot be parsed.
     */
    public static String[] generateTimeRanges( String spec, String srange ) throws ParseException {
        return org.autoplot.jythonsupport.Util.generateTimeRanges( spec, srange );
    }
    
    /**
     * return a list of completions.  This is useful in the IDL context
     * as well as Jython scripts.  This will perform the completion for where the carot is
     * at the end of the string.  Only completions where maybePlot indicates the URI is now 
     * valid are returned, so for example http://autoplot.org/data/somedata.cdf?noDep is not
     * returned and http://autoplot.org/data/somedata.cdf?Magnitude is.
     * Note this is included to continue support as this is described in http://autoplot.org/developer.idlMatlab.
     * @param file, for example http://autoplot.org/data/somedata.cdf?
     * @return list of completions, containing the entire URI.
     * @throws java.lang.Exception any exception thrown by the data source.
     */
    public static String[] getCompletions( String file ) throws Exception {
        return org.autoplot.jythonsupport.Util.getAllCompletions(file);
    }
    
    /**
     * sleep for so many milliseconds.  This is introduced to avoid the import,
     * which makes running scripts securely non-trivial.
     * @param millis number of milliseconds to pause execution
     */
    public static void sleep( int millis ) {
        org.autoplot.jythonsupport.Util.sleep( millis );
    }
    
    /**
     * Export the data into a format implied by the filename extension.  
     * See the export data dialog for additional parameters available for formatting.
     *
     * For example:
     *<blockquote><pre><small>{@code
     * ds= getDataSet('http://autoplot.org/data/somedata.cdf?BGSEc')
     * formatDataSet( ds, 'vap+dat:file:/home/jbf/temp/foo.dat?tformat=minutes&format=6.2f')
     *}</small></pre></blockquote>
     * 
     * @param ds
     * @param file local file name that is the target
     * @throws java.lang.Exception
     */
    public static void formatDataSet(QDataSet ds, String file) throws Exception {
        
        formatDataSet( ds, file, new NullProgressMonitor());

    }
    
    /**
     * Export the data into a format implied by the filename extension.  
     * See the export data dialog for additional parameters available for formatting.
     *
     * For example:
     *<blockquote><pre><small>{@code
     * ds= getDataSet('http://autoplot.org/data/somedata.cdf?BGSEc')
     * formatDataSet( ds, 'vap+dat:file:/home/jbf/temp/foo.dat?tformat=minutes&format=6.2f')
     * formatDataSet( ds, 'foo.dat' ) # also okay
     *}</small></pre></blockquote>
     * 
     * @param ds
     * @param file local file name that is the target.
     * @param monitor
     * @throws java.lang.Exception
     */
    public static void formatDataSet(QDataSet ds, String file, ProgressMonitor monitor ) throws Exception {
        sc.formatDataSet( ds, file, monitor );
    }
    
    
    /**
     * set the title of the plot.
     * @param title
     */
    public static void setTitle(String title) {
        sc.setTitle(title);
    }
    
    /**
     * create a model with a GUI presentation layer.  If the GUI is already 
     * created, then this does nothing.
     */
    public static void createGui() {
        sc.createGui();
    }

    /**
     * returns the internal application model (the object that does all the 
     * business).  This provides access to the internal model for power users.
     * Note the applicationModel provides limited access, and the DOM now
     * provides full access to the application.
     * @return ApplicationModel object
     */
    public ApplicationModel getApplicationModel() {
        return sc.getApplicationModel();
    }

    /**
     * provide way to see if the model is already initialized (e.g. for clone application)
     * @return true is the model is already initialized.
     */
    public boolean isModelInitialized() {
        return sc.isModelInitialized();
    }

    /**
     * binds two bean properties together.  Bindings are bidirectional, but
     * the initial copy is from src to dst.  In MVC terms, src should be the model
     * and dst should be a view.  The properties must fire property
     * change events for the binding mechanism to work.
     * 
     * As of v2014a_10, if the src is a DomNode and a child of the application, then use
     * dom.getController().bind so that the vap will contain the binding.
     * 
     * Example:
     *<blockquote><pre><small>{@code
     * model= getApplicationModel()
     * bind( model.getPlotDefaults(), "title", model.getPlotDefaults().getXAxis(), "label" )
     *}</small></pre></blockquote>
     * 
     * @see org.autoplot.dom.ApplicationController#bind(org.autoplot.dom.DomNode, java.lang.String, java.lang.Object, java.lang.String) which will save the binding to a vap.
     * @param src java bean such as model.getPlotDefaults()
     * @param srcProp a property name such as "title"
     * @param dst java bean such as model.getPlotDefaults().getXAxis()
     * @param dstProp a property name such as "label"
     */
    public static void bind( Object src, String srcProp, Object dst, String dstProp ) {
        bind( src, srcProp, dst, dstProp, null );
    }
    
    /**
     * binds two bean properties together.  Bindings are bidirectional, but
     * the initial copy is from src to dst.  In MVC terms, src should be the model
     * and dst should be a view.  The properties must fire property
     * change events for the binding mechanism to work.
     * 
     * As of v2014a_10, if the src is a DomNode and a child of the application, then use
     * dom.getController().bind so that the vap will contain the binding.
     * 
     * Example:
     *<blockquote><pre><small>{@code
     * model= getApplicationModel()
     * bind( model.getPlotDefaults(), "title", model.getPlotDefaults().getXAxis(), "label" )
     *}</small></pre></blockquote>
     * 
     * @see org.autoplot.dom.ApplicationController#bind(org.autoplot.dom.DomNode, java.lang.String, java.lang.Object, java.lang.String, org.jdesktop.beansbinding.Converter) which will save the binding to a vap.
     * @param src java bean such as model.getPlotDefaults()
     * @param srcProp a property name such as "title"
     * @param dst java bean such as model.getPlotDefaults().getXAxis()
     * @param dstProp a property name such as "label"
     * @param c converter for the binding, or null.
     */    
    public static void bind( Object src, String srcProp, Object dst, String dstProp, Converter c ) {
        sc.bind(src, srcProp, dst, dstProp, c);
    }
    
    /**
     * unbind the property
     * @param src 
     */
    public static void unbind( DomNode src ) {
        sc.unbind(src);
    }

    /**
     * unbind the property
     * @param src 
     * @param srcProp 
     * @param dst 
     * @param dstProp 
     */
    public static void unbind( DomNode src, String srcProp, DomNode dst, String dstProp ) {
        sc.unbind(src, srcProp, dst, dstProp);
    }
    

    /**
     * binds two bean properties together.  Bindings are bidirectional, but
     * the initial copy is from src to dst.  In MVC terms, src should be the model
     * and dst should be a view.  The properties must fire property
     * change events for the binding mechanism to work.
     * 
     * As of v2014a_10, if the src is a DomNode and a child of the application, then use
     * dom.getController().bind so that the vap will contain the binding.
     * 
     * Example:
     *<blockquote><pre><small>{@code
     * model= getApplicationModel()
     * bind( model.getPlotDefaults(), "title", model.getPlotDefaults().getXAxis(), "label" )
     *}</small></pre></blockquote>
     * 
     * @see org.autoplot.dom.ApplicationController#bind(org.autoplot.dom.DomNode, java.lang.String, java.lang.Object, java.lang.String, org.jdesktop.beansbinding.Converter) which will save the binding to a vap.
     * @param src java bean such as model.getPlotDefaults()
     * @param srcProp a property name such as "title"
     * @param dst java bean such as model.getPlotDefaults().getXAxis()
     * @param dstProp a property name such as "label"
     * @param c converter for the binding, or null.
     */    
    public static void bindGuiSafe( final Object src, final String srcProp, 
        final Object dst, final String dstProp, final Converter c ) {
        sc.bindGuiSafe(src, srcProp, dst, dstProp, c);
    }

    
    /**
     * serializes the dataset to a QStream, a self-documenting, streaming format
     * useful for moving datasets.
     *
     * <p><blockquote><pre>
     *ds= getDataSet('http://autoplot.org/data/somedata.cdf?BGSEc')
     *from java.lang import System
     *dumpToQStream( ds, System.out, True )
     * </pre></blockquote></p>
     *
     * @param ds The dataset to stream.  Note all schemes should be streamable, but
     *   some bugs exist that may prevent this.
     * @param out stream, such as "System.out"
     * @param ascii use ascii transfer types, otherwise binary are used.
     * @throws java.io.IOException 
     */
    public static void dumpToQStream( QDataSet ds, OutputStream out, boolean ascii ) throws IOException {
        ScriptContext2023.dumpToQStream(ds, out, ascii);
    }


    /**
     * serializes the dataset to a das2stream, a well-documented, open, streaming
     * data format. (that's a joke.)  Das2Streams are the legacy stream format used
     * by the Plasma Wave Groups's server, and can serialize a limited set of
     * QDataSets.  QStreams were introduced to allow streaming of any QDataSet, see
     * dumpToQStream.
     * Currently, to keep the channel open, the stream is created in a buffer and 
     * then the buffer is sent.
     * TODO: write a stream-producing code that doesn't close the output stream.  (TODO: does it still do this?)
     * @param ds
     * @param ascii use ascii transfer types, otherwise binary are used.
     */
    public static void dumpToDas2Stream(QDataSet ds, boolean ascii) {
        ScriptContext2023.dumpToDas2Stream(ds, ascii);
    }

    /**
     * serializes the dataset to a das2stream, a well-documented, open, streaming
     * data format. (that's a joke.)  
     * Currently, to keep the channel open, the stream is created in a buffer and 
     * then the buffer is sent.  TODO: write a stream-producing code that doesn't
     * close the output stream.
     * @param ds
     * @param file the file target for the stream.
     * @param ascii use ascii transfer types.
     * @throws java.io.IOException
     */
    public static void dumpToDas2Stream(QDataSet ds, String file, boolean ascii) throws IOException {
        ScriptContext2023.dumpToDas2Stream(ds, file, ascii);
    }

    /**
     * make the directory.  This must be a local file right now, but may start with "file://"
     * @param dir the directory
     */
    public static void mkdir( String dir ) {
        ScriptContext2023.mkdir(dir);
    }

    /**
     * get the document model (DOM).  This may initialize the model, in which
     * case defaults like the cache directory are set.
     * @return
     */
    public Application getDocumentModel() {
        return sc.getDocumentModel();
    }


    /**
     * wait until the application is idle.  This does a model.waitUntilIdle,
     * but also checks for the DataSetSelector for pending operations.
     */
    public static void waitUntilIdle() {
        sc.waitUntilIdle();
    }

    /**
     * wait until the application is idle.  The id is a convenience to 
     * developers when debugging, for example used to trigger breakpoints.
     * @param id string for debugging.
     *@see http://autoplot.org/data/tools/reloadAll.jy
     */
    public static void waitUntilIdle( String id ) {
        sc.waitUntilIdle(id);
    }    

    /**
     * save the current state as a vap file
     * @param filename
     * @throws java.io.IOException
     */
    public static void save( String filename ) throws IOException {
        sc.save(filename);
    }

    /**
     * load the .vap file.  This is implemented by calling plot on the URI.
     * @param filename local or remote filename like http://autoplot.org/data/autoplot.vap
     * @throws java.io.IOException
     */
    public static void load( String filename ) throws IOException {
        sc.load(filename);
    }
    
    /**
     * load a vap from a file and return the dom.
     * @param filename .vap file
     * @return Application
     * @throws java.io.IOException
     * @see #saveVap(org.autoplot.dom.Application, java.lang.String) 
     */
    public static Application loadVap( String filename ) throws IOException {
        return ScriptContext2023.loadVap(filename);
    }
    
    /**
     * save the application dom to a file.
     * @param dom the application state
     * @param filename the file.
     * @throws IOException 
     * @see #loadVap(java.lang.String) 
     */
    public static void saveVap( Application dom, String filename ) throws IOException {
        ScriptContext2023.saveVap(dom, filename);
    }
    
    /**
     * make the layout more efficient by removing empty spaces and overlapping 
     * plots.
     */
    public static void fixLayout() {
        sc.fixLayout();
    }
    
    /**
     * make a stack plot.
     * @param nrows number of rows (plots)
     */
    public static void setLayout( int nrows ) {
        sc.setLayout(nrows);
    }
    
    /**
     * reset the layout to have the given number of rows and columns.  This is
     * similar to the subplot command in Matlab and !p.multi in IDL.  Once 
     * additional plots are added, use the plot command with the index argument.
     * 
     * @param nrows number of rows
     * @param ncolumns number of columns
     */
    public static void setLayout( int nrows, int ncolumns ) {
        sc.setLayout(nrows, ncolumns);
    }
    
    /**
     * adds a block of plots to the canvas below the focus plot. 
     * A plotElement is added for each plot as well. 
     * @param nrows number of rows
     * @param ncolumns number of columns
     * @param dir below, above, right, or left, or null (None in Jython) to replace the current plot.
     * @return the new plots.
     */
    public List<Plot> addPlots( int nrows, int ncolumns, String dir ) {
        return sc.addPlots(nrows, ncolumns, dir);
    }
    
    /**
     * make a single plot with so many plot elements.
     * @param nplotElement the number of plotElements on the one plot.
     * @see #setLayout(int, int) setLayout(int, int) which will create a grid of plots.
     * @see #addPlotElement(int) addPlotElement(int) which will an another plotElement (an overplot) to the ith position.
     */
    public static void setLayoutOverplot( int nplotElement ) {
        sc.setLayoutOverplot(nplotElement);
    }
            
    /**
     * reset the application to its initial state.
     */
    public static void reset() {
        sc.reset();
    }

    
}
