/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot;

import org.das2.dataset.DataSet;
import org.das2.dataset.TableDataSet;
import org.das2.dataset.VectorDataSet;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.graph.DasCanvas;
import org.das2.util.DasPNGConstants;
import org.das2.util.DasPNGEncoder;
import org.das2.util.TimeParser;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import javax.swing.JComponent;
import org.das2.DasApplication;
import org.das2.fsm.FileStorageModel;
import org.das2.util.monitor.NullProgressMonitor;
import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.Binding;
import org.jdesktop.beansbinding.Bindings;
import org.python.core.PyJavaInstance;
import org.virbo.aggragator.AggregatingDataSourceFactory;
import org.virbo.autoplot.dom.Application;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.DataSetAdapter;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.DataSetURL;
import org.virbo.datasource.URLSplit;
import org.virbo.datasource.datasource.DataSourceFormat;

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
            dom= model.getDocumentModel();
        }
    }

    protected static void setApplicationModel(ApplicationModel m) {
        model = m;
        dom= m.getDocumentModel();
    }
    
    private static AutoPlotUI view = null;

    private static synchronized void maybeInitView() {
        maybeInitModel();
        if (view == null) {
            view = new AutoPlotUI(model);
            view.setVisible(true);
        }
    }

    protected static void setView(AutoPlotUI v) {
        view = v;
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
     * @param width
     * @param height
     */
    public static void setCanvasSize(int width, int height) {
        maybeInitModel();
        model.canvas.setSize(width, height);
    }

    /**
     * set the internal model's url to surl.  The data set will be loaded,
     * and writeToPng and writeToSvg can be used in headless mode.
     * @param surl
     * @throws java.lang.InterruptedException
     */
    public static void setDataSourceURL(String surl) throws InterruptedException {
        model.setDataSourceURL(surl);
        model.waitUntilIdle(false);
    }

    /**
     * bring up the autoplot with the specified URL.
     * @param surl
     * @throws java.lang.InterruptedException
     */
    public static void plot(String surl) throws InterruptedException {
        maybeInitView();
        model.resetDataSetSourceURL(surl, new NullProgressMonitor());
        model.waitUntilIdle(false);
    }

    public static void plot(QDataSet ds) throws InterruptedException {
        maybeInitModel();
        model.setDataSet(ds);
        model.waitUntilIdle(false);
    }

    public static void plot( QDataSet x, QDataSet y ) throws InterruptedException {
        maybeInitModel();
        DDataSet yds= DDataSet.copy(y);
        if ( x!=null ) yds.putProperty( QDataSet.DEPEND_0, x );
        model.setDataSet(yds);
        model.waitUntilIdle(false);
    }
    
    public static void plot( QDataSet x, QDataSet y, QDataSet z ) throws InterruptedException {
        maybeInitModel();

        if ( z.rank()==1 ) {
            DDataSet yds= DDataSet.copy(y);
            yds.putProperty( QDataSet.DEPEND_0, x );
            yds.putProperty( QDataSet.PLANE_0, z );
            model.setDataSet(yds);
        } else {
            DDataSet zds= DDataSet.copy(z);
            if ( x!=null ) zds.putProperty( QDataSet.DEPEND_0, x );
            if ( y!=null ) zds.putProperty( QDataSet.DEPEND_1, y );
            model.setDataSet(zds);
        }
        
        model.waitUntilIdle(false);
    }

    /**
     * set the autoplot status ber string.  Use the prefixes "busy:", "warning:"
     * and "error:" to set icons.
     * @param message
     */
    public static void setStatus( String message ) {
        dom.getController().setStatus(message);
    }
    
    public static void addTab( String label, JComponent c  ) {
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
     *   spectrogram, series, scatter, histogram, fill_to_zero
     * @param name string name of the plot style.
     */
    public static void setRenderStyle( String name ) {
        dom.getController().getPanel().setRenderType( ApplicationModel.RenderType.valueOf(name) );
    }
    /**
     * write out the current canvas to a png file.
     * @param filename
     * @throws java.lang.InterruptedException
     * @throws java.io.IOException
     */
    public static void writeToPng(String filename) throws InterruptedException, IOException {
        model.waitUntilIdle(false);
        model.getCanvas().writeToPng(filename);
        setStatus("wrote to "+filename);
    }

    public static void peekAt(Object o) throws IOException {
        out.write(o.toString().getBytes());
        return;
    }

    /**
     * write out the current canvas to stdout.  This is introduced to support servers.
     * @param OutputStream out 
     * @throws java.lang.InterruptedException
     * @throws java.io.IOException
     */
    public static void writeToPng(OutputStream out) throws InterruptedException, IOException {
        model.waitUntilIdle(false);

        DasCanvas c = model.getCanvas();

        Image image = c.getImage(c.getWidth(), c.getHeight());

        DasPNGEncoder encoder = new DasPNGEncoder();
        encoder.addText(DasPNGConstants.KEYWORD_CREATION_TIME, new Date().toString());

        encoder.write((BufferedImage) image, out);

    }

    /**
     * write out the current canvas to a pdf file.
     * @param filename
     * @throws java.lang.InterruptedException
     * @throws java.io.IOException
     */
    public static void writeToPdf(String filename) throws InterruptedException, IOException {
        model.waitUntilIdle(false);
        model.getCanvas().writeToPDF(filename);
        setStatus("wrote to "+filename);
    }

    /**
     * return an array of URLs that match the spec for the time range provided.
     * 
     * @param surl an autoplot url with an aggregation specifier.
     * @param timeRange a string that is parsed to a time range, such as "2001"
     * @param format format for the result, such as "%Y-%m-%d"
     * @return a list of URLs without the aggregation specifier.
     * @throws java.io.IOException if the remote folder cannot be listed.
     * @throws java.text.ParseException if the timerange cannot be parsed.
     */
    public static String[] getTimeRangesFor(String surl, String timeRange, String format) throws IOException, ParseException {
        DatumRange dr = DatumRangeUtil.parseTimeRange(timeRange);
        FileStorageModel fsm = AggregatingDataSourceFactory.getFileStorageModel(surl);
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
     * @return ApplicationModel object
     */
    public static ApplicationModel getApplicationModel() {
        maybeInitModel();
        return model;
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
            Binding b= Bindings.createAutoBinding( UpdateStrategy.READ_WRITE, src, BeanProperty.create( srcProp ), dst, BeanProperty.create(dstProp));
            b.bind();
        } else {
            System.err.println("bindings disabled in applet environment");
        }
    }


    /**
     * serializes the dataset to a das2stream, a well-documented, open, streaming
     * data format. (that's a joke.)  
     * Currently, to keep the channel open, the stream is created in a buffer and 
     * then the buffer is sent.  TODO: write a stream-producing code that doesn't
     * close the output stream.
     * @param ds
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
     * 
     * @param ds
     * @param file
     * @throws java.lang.Exception
     */
    public static void formatDataSet(QDataSet ds, String file) throws Exception {
        if (!file.contains(":/")) {
            file = new File(file).toString();
        }
        URI uri = DataSetURL.getURI(file);
        URL url = DataSetURL.getResourceURI(uri).toURL(); //TODO: prevents jdbc:mysql:...

        DataSourceFormat format = DataSetURL.getDataSourceFormat(uri);
        if (!url.getProtocol().equals("file")) {
            throw new IllegalArgumentException("data may only be formatted to local files: " + url);
        }
        if (format == null) {
            throw new IllegalArgumentException("no format for extension: " + file);
        }

        File f;
        if ( url.getProtocol().equals("file" ) ) {
            f= new File( url.getPath() );
        } else {
            f = DataSetURL.getFile(url, new NullProgressMonitor());
        }
        String sparams = url.getQuery();
        HashMap<String, String> params = sparams == null ? new HashMap<String, String>() : URLSplit.parseParams(sparams);

        format.formatData(f, params, ds, new NullProgressMonitor());

    }

    public static Application getDocumentModel() {
        maybeInitModel();
        return dom;
    }
}
