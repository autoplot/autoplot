/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot;

import java.io.FileNotFoundException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.dataset.DataSet;
import org.das2.dataset.TableDataSet;
import org.das2.dataset.VectorDataSet;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.graph.DasCanvas;
import org.das2.util.DasPNGConstants;
import org.das2.util.DasPNGEncoder;
import org.das2.datum.TimeParser;
import java.awt.Image;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.swing.JComponent;
import org.das2.DasApplication;
import org.das2.fsm.FileStorageModelNew;
import org.das2.util.awt.PdfGraphicsOutput;
import org.das2.util.monitor.NullProgressMonitor;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.Binding;
import org.jdesktop.beansbinding.Bindings;
import org.python.core.PyJavaInstance;
import org.virbo.aggregator.AggregatingDataSourceFactory;
import org.virbo.autoplot.dom.Application;
import org.virbo.autoplot.dom.DataSourceFilter;
import org.virbo.autoplot.scriptconsole.ExitExceptionHandler;
import org.virbo.dataset.ArrayDataSet;
import org.das2.dataset.DataSetAdapter;
import org.virbo.autoplot.dom.Plot;
import org.virbo.autoplot.dom.PlotElement;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSetURI.CompletionResult;
import org.virbo.datasource.URISplit;
import org.virbo.datasource.DataSourceFormat;
import org.virbo.qstream.SimpleStreamFormatter;
import org.virbo.qstream.StreamException;
import org.virbo.qstream.StreamTool;

/**
 *
 * @author jbf
 */
public class ScriptContext extends PyJavaInstance {

    private static ApplicationModel model = null;
    private static Application dom= null;

    private static synchronized void maybeInitModel() {
        if (model == null) {
            model = new ApplicationModel();
            model.setExceptionHandler( new ExitExceptionHandler() );
            model.addDasPeersToApp();
            dom= model.getDocumentModel();
        }
    }

    protected static void setApplicationModel(ApplicationModel m) {
        model = m;
        dom= m.getDocumentModel();
    }
    
    private static AutoplotUI view = null;

    private static synchronized void maybeInitView() {
        maybeInitModel();
        if (view == null) {
            view = new AutoplotUI(model);
            view.setVisible(true);
        }
    }

    /**
     * Used by AutoplotUI to set the view.
     */
    protected static void setView(AutoplotUI v) {
        view = v;
    }

    /**
     * return the Window for the application, to be used for dialogs.
     * See createGui(), which creates the view.
     * @return
     */
    public static Window getViewWindow() {
        return view;
    }

    private static OutputStream out = System.out;

    /**
     * resets the output stream.  This method is used internally, do not use
     * this routine.
     * @param out
     */
    public static void _setOutputStream(OutputStream out) {
        ScriptContext.out = out;
    }

    /**
     * set the size of the canvas.  This is only used when the GUI is not used, and in
     * headless mode, otherwise the GUI controls the size of the canvas.
     * 
     * @param width the width of the canvas
     * @param height the height of the canvas
     */
    public static void setCanvasSize(int width, int height) throws InterruptedException {
        maybeInitModel();
        model.canvas.setSize(width, height);
        model.getDocumentModel().getCanvases(0).setWidth(width);
        model.getDocumentModel().getCanvases(0).setHeight(height);
        model.waitUntilIdle(false);
    }

    /**
     * set the internal model's url to surl.  The data set will be loaded,
     * and writeToPng and writeToSvg can be used in headless mode.
     * @param surl
     * @throws java.lang.InterruptedException
     * @Deprecated use plot instead;
     */
    public static void setDataSourceURL(String surl) throws InterruptedException {
        model.setDataSourceURL(surl);
        model.waitUntilIdle(false);
    }

    /**
     * bring up the autoplot with the specified URL.  Note the URI is resolved
     * asynchronously, so errors thrown during the load cannot be caught here.
     * See getDataSet to load data synchronously.
     * @param suri a URI or vap file
     * @throws java.lang.InterruptedException
     */
    public static void plot(String suri) throws InterruptedException {
        maybeInitModel();
        if ( view!=null ) {
            view.dataSetSelector.setValue(suri);
        }
        model.resetDataSetSourceURL(suri, new NullProgressMonitor());
        model.waitUntilIdle(false);
    }

    /**
     * plot one URI against another.  No synchronization is done, so beware.
     * Introduced for testing non-time axis TSBs.
     * @param surl1 the independent variable dataset (generally used for X)
     * @param surl2 the dependent variable dataset (generally used for Y, but can be rank 2).
     * @throws java.lang.InterruptedException
     */
    public static void plot(String surl1, String surl2) throws InterruptedException {
        maybeInitModel();
        if ( surl1.endsWith(".vap") || surl1.contains(".vap?")  ) {
            throw new IllegalArgumentException("cannot load vap here in two-argument plot");
        } else {
            DataSourceFilter dsf= model.getDocumentModel().getDataSourceFilters(0);
            List<PlotElement> pes= dom.getController().getPlotElementsFor(dsf);
            PlotElement pe;
            if ( pes.size()>0 ) pe= pes.get(0); else pe=null;
            Plot p= pe==null ? dom.getPlots(0) : dom.getController().getPlotFor(pe);
            dom.getController().doplot( p, pe, surl1, surl2 );
        }
        model.waitUntilIdle(false);
    }

    /**
     * bring up the autoplot with the specified URL.
     * @param chNum the data source number to reset the URI.
     * @param surl a URI to use
     * @throws java.lang.InterruptedException
     */
    public static void plot(int chNum, String surl) throws InterruptedException {
        maybeInitModel();
        model.setDataSet( chNum, null, surl );
        model.waitUntilIdle(false);
    }

    /**
     * bring up the autoplot with the specified URL.
     * @param chNum the data source number to reset the URI.
     * @param label for the plot.
     * @param surl a URI to use
     * @throws java.lang.InterruptedException
     */
    public static void plot(int chNum, String label, String surl) throws InterruptedException {
        maybeInitModel();
        model.setDataSet( chNum, label, surl );
        model.waitUntilIdle(false);
    }

    /**
     * plot the dataset in the first dataSource node.
     * @param ds QDataSet to plot
     * @throws java.lang.InterruptedException
     */
    public static void plot(QDataSet ds) throws InterruptedException {
        plot( 0, (String)null, ds );
    }

    /**
     * plot the dataset in the first dataSource node.
     * @param x QDataSet for the independent parameter
     * @param y QDataSet for the dependent parameter
     * @throws java.lang.InterruptedException
     */
    public static void plot( QDataSet x, QDataSet y ) throws InterruptedException {
        plot( 0, (String)null, x, y );
    }
    
    /**
     * plot the dataset in the first dataSource node.
     * @param x QDataSet for the independent parameter for the X values
     * @param y QDataSet for the independent parameter for the Y values
     * @param z Rank 1 or Rank 2 QDataSet for the dependent parameter
     * @throws java.lang.InterruptedException
     */
    public static void plot( QDataSet x, QDataSet y, QDataSet z ) throws InterruptedException {
        plot( 0, (String)null, x, y, z );
    }

    /**
     * plot the dataset in the specified dataSource node.
     * @chNum the plot to use.  Plots and plot elements are added as necessary to plot the data.
     * @param ds dataset to plot.
     * @throws java.lang.InterruptedException
     */
    public static void plot( int chNum, QDataSet ds) throws InterruptedException {
        plot( chNum, (String)null, ds );
    }

    /**
     * plot the dataset in the specified  dataSource node.
     * @chNum the plot to use.  Plots and plot elements are added as necessary to plot the data.
     * @param x QDataSet for the independent parameter for the X values
     * @param y QDataSet for the independent parameter for the Y values
     * @throws java.lang.InterruptedException
     */
    public static void plot( int chNum, QDataSet x, QDataSet y ) throws InterruptedException {
        plot( chNum, (String)null, x, y );
    }

    /**
     * plot the dataset in the specified  dataSource node.
     * @chNum the plot to use.  Plots and plot elements are added as necessary to plot the data.
     * @param x QDataSet for the independent parameter for the X values
     * @param y QDataSet for the independent parameter for the Y values
     * @param z Rank 1 or Rank 2 QDataSet for the dependent parameter
     * @throws java.lang.InterruptedException
     */
    public static void plot( int chNum, QDataSet x, QDataSet y, QDataSet z ) throws InterruptedException {
        plot( chNum, (String)null, x, y, z );
    }

    /**
     * bring up the autoplot with the dataset
     * @param chNum the data source number to reset the URI.
     * @param label for the plot.
     * @param ds the dataset to use.
     * @throws java.lang.InterruptedException
     */
    public static void plot( int chNum, String label, QDataSet ds) throws InterruptedException {
        maybeInitModel();
        model.setDataSet( chNum, label, ds );
        model.waitUntilIdle(false);
    }

    /**
     * plot the dataset in the specified  dataSource node.
     * @chNum the plot to use.  Plots and plot elements are added as necessary to plot the data.
     * @param x QDataSet for the independent parameter for the X values
     * @param y QDataSet for the independent parameter for the Y values
     * @throws java.lang.InterruptedException
     */
    public static void plot( int chNum, String label, QDataSet x, QDataSet y ) throws InterruptedException {
        maybeInitModel();
        ArrayDataSet yds= ArrayDataSet.copy(y);
        if ( x!=null ) yds.putProperty( QDataSet.DEPEND_0, x );
        model.setDataSet( chNum, label, yds);
        model.waitUntilIdle(false);
    }

    /**
     * plot the dataset in the specified  dataSource node.
     * @chNum the plot to use.  Plots and plot elements are added as necessary to plot the data.
     * @param label the label for the dependent parameter
     * @param x QDataSet for the independent parameter for the X values
     * @param y QDataSet for the independent parameter for the Y values
     * @param z Rank 1 or Rank 2 QDataSet for the dependent parameter
     * @throws java.lang.InterruptedException
     */
    public static void plot( int chNum, String label, QDataSet x, QDataSet y, QDataSet z ) throws InterruptedException {
        maybeInitModel();
        if ( z.rank()==1 ) {
            ArrayDataSet yds= ArrayDataSet.copy(y);
            yds.putProperty( QDataSet.DEPEND_0, x );
            yds.putProperty( QDataSet.PLANE_0, z );
            model.setDataSet(chNum, label, yds);
        } else {
            ArrayDataSet zds= ArrayDataSet.copy(z);
            if ( x!=null ) zds.putProperty( QDataSet.DEPEND_0, x );
            if ( y!=null ) zds.putProperty( QDataSet.DEPEND_1, y );
            model.setDataSet(chNum, label, zds);
        }
        model.waitUntilIdle(false);
    }


    /**
     * commented codes removed that would plot(double[],double[])...  These
     * were commented out for a while, because there are better ways to do this
     * better.
     */

    /**
     * set the autoplot status bar string.  Use the prefixes "busy:", "warning:"
     * and "error:" to set icons.
     * @param message
     */
    public static void setStatus( String message ) {
        dom.getController().setStatus(message);
    }

    /**
     * add a tab to the running application.  A new tab will be added with the
     * label.
     * @param label the label for the component.
     * @param c the component to add.
     */
    public static void addTab( String label, JComponent c  ) {
        maybeInitView();
        int n= view.getTabs().getComponentCount();
        for ( int i=0; i<n; i++ ) {
            final String titleAt = view.getTabs().getTitleAt(i);
            if ( titleAt.equals(label) || titleAt.equals("("+label+")") ) { //DANGER view is model
                view.getTabs().remove( i );
                break;
            }
        }
        view.getTabs().add(label,c);
    }
    
    /**
     * Set the style used to render the data using a string identifier:
     *   spectrogram, series, scatter, histogram, fill_to_zero, digital
     * @param name string name of the plot style.
     */
    public static void setRenderStyle( String name ) {
        dom.getController().getPlotElement().setRenderType( RenderType.valueOf(name) );
    }

    /**
     * write out the current canvas to a png file.
     * TODO: bug 3113441: this has issues with the size.  It's coded to get the size from
     *  the DOM, but if it is fitted and has a container it must get size from
     *  the container.  Use writeToPng( filename, width, height ) instead for now.
     *  See writeToPdf(String filename), which appears to have a fix for this that
     *  would affect how this is resolved.
     * @param filename The name of a local file
     * @throws java.lang.InterruptedException
     * @throws java.io.IOException
     */
    public static void writeToPng(String filename) throws InterruptedException, IOException {
        if ( !( filename.endsWith(".png") || filename.endsWith(".PNG") ) ) {
            filename= filename + ".png";
        }

        model.waitUntilIdle(false);
        int width= model.getDocumentModel().getCanvases(0).getWidth();
        int height= model.getDocumentModel().getCanvases(0).getHeight();
        writeToPng( filename, width, height );
        setStatus("wrote to "+filename);
    }

    private static void maybeMakeParent( String filename ) {
        File file= new File(filename);
        if ( file.getParentFile()!=null ) { // relative filenames are okay.
            if ( !file.getParentFile().exists() ) {
                file.getParentFile().mkdirs();
            }
        }
    }

    /**
     * write out the current canvas to a png file.
     * TODO: bug 3113441: this has issues with the size.  It's coded to get the size from
     *  the DOM, but if it is fitted and has a container it must get size from
     *  the container.  User writeToPng( filename, width, height ) instead for now.
     *  See writeToPdf(String filename), which appears to have a fix for this that
     *  would affect how this is resolved.
     * @param filename The name of a local file
     * @param width the width in pixels of the png
     * @param height the height in pixels of the png
     * @throws java.lang.InterruptedException
     * @throws java.io.IOException
     */
    public static void writeToPng( String filename, int width, int height ) throws InterruptedException, IOException {
        if ( !( filename.endsWith(".png") || filename.endsWith(".PNG") ) ) {
            filename= filename + ".png";
        }

        maybeMakeParent(filename);
        
        final FileOutputStream out1 = new FileOutputStream(filename);

        Image image = model.canvas.getImage( width, height );

        DasPNGEncoder encoder = new DasPNGEncoder();
        encoder.addText(DasPNGConstants.KEYWORD_CREATION_TIME, new Date().toString());
        try {
            encoder.write((BufferedImage) image, out1);
        } catch (IOException ioe) {
        } finally {
            try {
                out1.close();
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }
    }

    public static void writeToPng( BufferedImage image, String filename ) throws IOException {

        maybeMakeParent(filename);

        final FileOutputStream out1 = new FileOutputStream(filename);

        DasPNGEncoder encoder = new DasPNGEncoder();
        encoder.addText(DasPNGConstants.KEYWORD_CREATION_TIME, new Date().toString());
        try {
            encoder.write((BufferedImage) image, out1);
        } catch (IOException ioe) {
        } finally {
            try {
                out1.close();
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }
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
        out.write(o.toString().getBytes());
        return;
    }

    /**
     * write out the current canvas to stdout.  This is introduced to support servers.
     * TODO: this has issues with the size.  See writeToPng(filename).
     * @param OutputStream out
     * @throws java.lang.InterruptedException
     * @throws java.io.IOException
     */
    public static void writeToPng(OutputStream out) throws InterruptedException, IOException {
        model.waitUntilIdle(false);

        DasCanvas c = model.getCanvas();
        int width= model.getDocumentModel().getCanvases(0).getWidth();
        int height= model.getDocumentModel().getCanvases(0).getHeight();
        
        Image image = c.getImage(width,height);

        DasPNGEncoder encoder = new DasPNGEncoder();
        encoder.addText(DasPNGConstants.KEYWORD_CREATION_TIME, new Date().toString());

        encoder.write((BufferedImage) image, out);

    }

    /**
     * write out the current canvas to a pdf file.
     * TODO: this has issues with the size.  See writeToPng(filename).  It looks
     *   like this might be handled here
     * @param filename the local file to write the file.
     * @throws java.lang.InterruptedException
     * @throws java.io.IOException
     */
    public static void writeToPdf(String filename) throws InterruptedException, IOException {
        if ( !( filename.endsWith(".pdf") || filename.endsWith(".PDF") ) ) {
            filename= filename + ".pdf";
        }
        model.waitUntilIdle(false);
        int width= model.getDocumentModel().getCanvases(0).getWidth();
        int height= model.getDocumentModel().getCanvases(0).getHeight();
        model.getCanvas().setSize( width, height );
        model.getCanvas().validate();
        model.waitUntilIdle(false);

        maybeMakeParent(filename);

        model.getCanvas().writeToPDF(filename);
        setStatus("wrote to "+filename);
    }

    /**
     * write out the current canvas to a pdf to the output stream.  This is to
     * support servers.
     * TODO: this has issues with the size.  See writeToPng(filename).  It looks
     *   like this might be handled here
     * @param out the OutputStream
     * @throws java.lang.InterruptedException
     * @throws java.io.IOException
     */
    public static void writeToPdf( OutputStream out ) throws InterruptedException, IOException, IllegalAccessException {
        model.waitUntilIdle(false);
        int width= model.getDocumentModel().getCanvases(0).getWidth();
        int height= model.getDocumentModel().getCanvases(0).getHeight();
        model.getCanvas().setSize( width, height );
        model.getCanvas().validate();
        model.waitUntilIdle(false);
        model.getCanvas().writeToGraphicsOutput( out, new PdfGraphicsOutput() );
        
    }

    /**
     * creates a BufferedImage from the provided DOM.  This blocks until the
     * image is ready.
     * TODO: this has issues with the size.  See writeToPng(filename).  It looks
     *   like this might be handled here
     * @param applicationIn
     * @return
     */
    public static BufferedImage writeToBufferedImage( Application applicationIn ) {
        ApplicationModel appmodel= new ApplicationModel();
        appmodel.addDasPeersToApp();
        appmodel.getDocumentModel().syncTo(applicationIn);

        int height= applicationIn.getCanvases(0).getHeight();
        int width= applicationIn.getCanvases(0).getWidth();

        BufferedImage image= (BufferedImage) appmodel.getCanvas().getImage(width, height);
        
        return image;
    }

    /**
     * convenient method for getting an image from the current canvas.
     * @return
     */
    public static BufferedImage writeToBufferedImage( ) throws InterruptedException {
        model.waitUntilIdle(false);
        
        int height= model.getDocumentModel().getCanvases(0).getHeight();
        int width= model.getDocumentModel().getCanvases(0).getWidth();

        BufferedImage image= (BufferedImage)  model.getDocumentModel().getCanvases(0).getController().getDasCanvas().getImage(width, height);
        return image;
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
        DatumRange dr = DatumRangeUtil.parseTimeRange(timeRange);
        FileStorageModelNew fsm = AggregatingDataSourceFactory.getFileStorageModel(surl);
        TimeParser tf = TimeParser.create(format);

        String[] ss = fsm.getNamesFor(dr);
        String[] result = new String[ss.length];

        for (int i = 0; i < ss.length; i++) {
            DatumRange dr2 = fsm.getRangeFor(ss[i]);
            result[i] = tf.format(dr2.min(), dr2.max());
        }

        return result;
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
        TimeParser tp= TimeParser.create(spec);
        DatumRange range= DatumRangeUtil.parseTimeRange(srange);

        String sstart= tp.format( range.min(), null );

        tp.parse(sstart);
        DatumRange curr= tp.getTimeRange();
        List<String> result= new ArrayList<String>();
        while ( range.intersects(curr) ) {
            String scurr= tp.format( curr.min(), curr.max() );
            result.add( scurr );
            curr= curr.next();
        }
        return result.toArray( new String[result.size()] );

    }

    /**
     * set the title of the plot.
     * @param title
     */
    public static void setTitle(String title) {
        model.getDocumentModel().getController().getPlot().setTitle(title);
    }

    /**
     * create a model with a GUI presentation layer.  If the GUI is already 
     * created, then this does nothing.
     */
    public static void createGui() {
        maybeInitView();
    }

    /**
     * returns the internal application model (the object that does all the 
     * business).  This provides access to the internal model for power users.
     * Note the applicationModel provides limited access, and the DOM now
     * provides full access to the application.
     * @return ApplicationModel object
     */
    public static ApplicationModel getApplicationModel() {
        maybeInitModel();
        return model;
    }

    /**
     * provide way to see if the model is already initialized (e.g. for clone application)
     * @return true is the model is already initialized.
     */
    public static boolean isModelInitialized() {
        return model!=null;
    }

    /**
     * binds two bean properties together.  Bindings are bidirectional, but
     * the initial copy is from src to dst.  In MVC terms, src should be the model
     * and dst should be a view.  The properties must fire property
     * change events for the binding mechanism to work.
     * 
     * Example:
     * model= getApplicationModel()
     * bind( model.getPlotDefaults(), "title", model.getPlotDefaults().getXAxis(), "label" )
     * 
     * @param src java bean such as model.getPlotDefaults()
     * @param srcProp a property name such as "title"
     * @param dst java bean such as model.getPlotDefaults().getXAxis()
     * @param dstProp a property name such as "label"
     */
    public static void bind(Object src, String srcProp, Object dst, String dstProp) {
        if ( DasApplication.hasAllPermission() ) {
            BeanProperty srcbp= BeanProperty.create(srcProp);
            Object value= srcbp.getValue(src);
            if ( value==null ) {
                System.err.println("warning: src property "+srcProp+ " of "+src+" is null");
            }
            BeanProperty dstbp= BeanProperty.create(dstProp);
            dstbp.setValue(dst, value );
            dstbp.getValue(dst);
            Binding b= Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, src, srcbp, dst, dstbp );
            b.bind();
        } else {
            System.err.println("bindings disabled in applet environment");
        }
    }



    /**
     * serializes the dataset to a QStream, a self-documenting, streaming format
     * useful for moving datasets.
     *
     * <p><blockquote><pre>
     * ds= getDataSet('http://autoplot.org/data/somedata.cdf?BGSEc')
     * from java.lang import System
     * dumpToQStream( ds, System.out, True )
     * </pre></blockquote></p>
     *
     * @param ds The dataset to stream.  Note all schemes should be streamable, but
     *   some bugs exist that may prevent this.
     * @param output stream, such as "System.out"
     * @param ascii use ascii transfer types, otherwise binary are used.
     */
    public static void dumpToQStream( QDataSet ds, OutputStream out, boolean ascii ) throws IOException {
        try {
            SimpleStreamFormatter f= new SimpleStreamFormatter();
            f.format( ds, out, ascii );
        } catch (StreamException ex) {
            Logger.getLogger(ScriptContext.class.getName()).log(Level.SEVERE, null, ex);
        }
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
        try {
            ByteArrayOutputStream bufout = new ByteArrayOutputStream(10000);
            DataSet lds = DataSetAdapter.createLegacyDataSet(ds);
            if (ascii) {
                if (lds instanceof TableDataSet) {
                    org.das2.dataset.TableUtil.dumpToAsciiStream((TableDataSet) lds, bufout);
                } else {
                    org.das2.dataset.VectorUtil.dumpToAsciiStream((VectorDataSet) lds, bufout);
                }
            } else {
                if (lds instanceof TableDataSet) {
                    org.das2.dataset.TableUtil.dumpToBinaryStream((TableDataSet) lds, bufout);
                } else {
                    org.das2.dataset.VectorUtil.dumpToBinaryStream((VectorDataSet) lds, bufout);
                }

            }
            out.write(bufout.toByteArray());
        } catch (IOException ex) {
        }
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
     */
    public static void dumpToDas2Stream(QDataSet ds, String file, boolean ascii) throws IOException {
        if (file.startsWith("file:/")) {
            file = new URL(file).getPath();
        }
        OutputStream bufout = new FileOutputStream(file);
        DataSet lds = DataSetAdapter.createLegacyDataSet(ds);
        if (ascii) {
            if (lds instanceof TableDataSet) {
                org.das2.dataset.TableUtil.dumpToAsciiStream((TableDataSet) lds, bufout);
            } else {
                org.das2.dataset.VectorUtil.dumpToAsciiStream((VectorDataSet) lds, bufout);
            }
        } else {
            if (lds instanceof TableDataSet) {
                org.das2.dataset.TableUtil.dumpToBinaryStream((TableDataSet) lds, bufout);
            } else {
                org.das2.dataset.VectorUtil.dumpToBinaryStream((VectorDataSet) lds, bufout);
            }
        }
    }

    /**
     * Export the data into a format implied by the filename extension.  
     * See the export data dialog for additional parameters available for formatting.
     *
     * For example:
     * <p><blockquote><pre>
     * ds= getDataSet('http://autoplot.org/data/somedata.cdf?BGSEc')
     * formatDataSet( ds, 'vap+dat:file:/home/jbf/temp/foo.dat?tformat=minutes&format=6.2f')
     * </pre></blockquote></p>
     * 
     * @param ds
     * @param file local file name that is the target
     * @throws java.lang.Exception
     */
    public static void formatDataSet(QDataSet ds, String file) throws Exception {
        if (!file.contains(":/")) {
            file = new File(file).getCanonicalFile().toString();
        }
        URI uri = DataSetURI.getURIValid(file);

        DataSourceFormat format = DataSetURI.getDataSourceFormat(uri);
        
        if (format == null) {
            throw new IllegalArgumentException("no format for extension: " + file);
        }

        format.formatData( DataSetURI.fromUri(uri), ds, new NullProgressMonitor());

    }

    /**
     * get the document model (DOM).  This may initialize the model, in which
     * case defaults like the cache directory are set.
     * @return
     */
    public static Application getDocumentModel() {
        maybeInitModel();
        return dom;
    }


    /**
     * wait until the application is idle.
     *@see http://autoplot.org/data/tools/reloadAll.jy
     */
    public static void waitUntilIdle() throws InterruptedException {
        model.waitUntilIdle(false);
    }


    /**
     * save the current state as a vap file
     * @param filename
     */
    public static void save( String filename ) throws IOException {
        maybeInitModel();
        if ( ! filename.endsWith(".vap") )
            throw new IllegalArgumentException("filename must end in vap");
        if ( !( filename.startsWith("file:") ) ) {
            filename= "file://" + new File(filename).getAbsolutePath().replaceAll("\\\\", "/" );
        }
        URISplit split= URISplit.parse(filename);
        String uri= DataSetURI.fromUri(split.resourceUri);
        if ( !uri.startsWith("file:/") )
            throw new IllegalArgumentException("save only supported to files, got "+split.resourceUri);
        String f= uri.startsWith("file:///") ?  split.file.substring(7) : split.file.substring(5);
        model.doSave( new File( f ) );
    }

    /**
     * return a list of completions.  I was talking to Tom N. who was looking for this
     * to get a list of CDF variables, and realized this would be useful in the IDL context
     * as well as python scripts.  This will perform the completion for where the carot is
     * at the end of the string.
     * @param file, for example http://autoplot.org/data/somedata.cdf?
     * @return list of completions, containing the entire URI.
     */
    public static String[] getCompletions( String file ) throws Exception {
        List<CompletionResult> cc= DataSetURI.getCompletions( file, file.length(), new NullProgressMonitor() );

        String[] result= new String[cc.size()];
        for ( int i=0; i<cc.size(); i++ ) {
            result[i]= cc.get(i).completion;
        }

        return result;
    }


    /**
     * load the .vap file.  This is implemented by calling plot on the URI.
     * @param filename
     * @throws java.io.IOException
     */
    public static void load( String filename ) throws IOException {
        try {
            plot(filename);
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * reset the application to its initial state.
     */
    public static void reset() {
        maybeInitModel();
        dom.getController().reset();
    }

    /**
     * called when the application closes so if we reopen it will be in a
     * good state.
     */
    protected static void close() {
        model= null;
        view= null;
        out= null;
    }
}
