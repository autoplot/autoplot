/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot;

import edu.uiowa.physics.pw.das.dataset.DataSet;
import edu.uiowa.physics.pw.das.dataset.TableDataSet;
import edu.uiowa.physics.pw.das.dataset.VectorDataSet;
import edu.uiowa.physics.pw.das.datum.DatumRange;
import edu.uiowa.physics.pw.das.datum.DatumRangeUtil;
import edu.uiowa.physics.pw.das.util.TimeParser;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.text.ParseException;
import javax.beans.binding.BindingContext;
import org.das2.fsm.FileStorageModel;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.python.core.PyJavaInstance;
import org.virbo.aggragator.AggregatingDataSourceFactory;
import org.virbo.dataset.DataSetAdapter;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.DataSetURL;
import org.virbo.datasource.DataSource;
import org.virbo.datasource.DataSourceFactory;

/**
 *
 * @author jbf
 */
public class ScriptContext extends PyJavaInstance {

    private static ApplicationModel model = null;

    private static synchronized void maybeInitModel() {
        if (model == null) {
            model = new ApplicationModel();
        }
    }
    
    protected static void setApplicationModel( ApplicationModel m ) {
        model= m;
    }
    
    private static AutoPlotUI view = null;

    private static synchronized void maybeInitView() {
        maybeInitModel();
        if (view == null) {
            view = new AutoPlotUI(model);
            view.setVisible(true);
        }
    }

    protected static void setView( AutoPlotUI v ) {
        view= v;
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
        model.resetDataSetSourceURL(surl, new NullProgressMonitor() );
        model.waitUntilIdle(false);
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
    }

    /**
     * return an array of URLs that match the spec for the time range provided.
     * 
     * @param surl an autoplot url with an aggregation specifier.
     * @param timeRange a string that is parsed to a time range, such as "2001"
     * @return a list of URLs without the aggregation specifier.
     * @throws java.io.IOException if the remote folder cannot be listed.
     * @throws java.text.ParseException if the timerange cannot be parsed.
     */
    public static String[] getTimeRangesFor( String surl, String timeRange, String format ) throws IOException, ParseException {
        DatumRange dr = DatumRangeUtil.parseTimeRange( timeRange );
        FileStorageModel fsm = AggregatingDataSourceFactory.getFileStorageModel(surl);
        TimeParser tf= TimeParser.create(format);
                
        String[] ss= fsm.getNamesFor(dr);
        String[] result= new String[ss.length];
        
        for ( int i=0; i<ss.length; i++ ) {
            DatumRange dr2=  fsm.getRangeFor( ss[i] );
            result[i]= tf.format( dr2.min(), dr2.max() );
        }
        
        return result;
    }
    
    /**
     * set the title of the plot.
     * @param title
     */
    public static void setTitle( String title ) {
        model.getPlot().setTitle( title );
    }
    
    /**
     * create a model with a GUI presentation layer.
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
    
    public static void bind( Object src, String srcProp, Object dst, String dstProp ) {
        BindingContext bc= new BindingContext();
        bc.addBinding( src, "${"+srcProp+"}", dst, dstProp );
        bc.bind();
    }
    
    public static QDataSet getDataSet( String surl, ProgressMonitor mon) throws Exception {
        URL url= new URL( surl );
        DataSourceFactory factory = DataSetURL.getDataSourceFactory(url, new NullProgressMonitor());
        DataSource result = factory.getDataSource(url);
        if ( mon==null ) mon= new NullProgressMonitor() ;
        return result.getDataSet( mon );
    }
    
    public static QDataSet getDataSet( String surl ) throws Exception {
        return getDataSet( surl, null );
    }
    
    public static void dumpToDas2Stream( QDataSet ds ) {
        OutputStream out= System.out;
        DataSet lds= DataSetAdapter.createLegacyDataSet(ds);
        if ( lds instanceof TableDataSet ) {
            edu.uiowa.physics.pw.das.dataset.TableUtil.dumpToAsciiStream( (TableDataSet)lds, out );
        } else {
            edu.uiowa.physics.pw.das.dataset.VectorUtil.dumpToAsciiStream( (VectorDataSet)lds, out );
        }
    }
    
}
