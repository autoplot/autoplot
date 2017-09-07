/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import org.das2.datum.InconvertibleUnitsException;
import org.python.core.PyJavaInstance;
import org.autoplot.ApplicationModel;
import org.autoplot.AutoplotUI;
import org.autoplot.dom.Application;
import org.autoplot.scriptconsole.ExitExceptionHandler;
import org.das2.qds.ArrayDataSet;
import org.das2.qds.QDataSet;

/**
 * This new class is meant to replace ScriptContext, and fixing a number of
 * old problems.  First, the ScriptContext methods are all static methods, which
 * meant that any Java instance had just one script context.  This has some really
 * unfortunate results that should have been expected, like Tomcat instances have
 * coupling between sessions, and a user must decide which DOM scripts will be
 * run with.  Second, ???
 * 
 * In python this can be used like so:
 *  ...
 * @author jbf
 */
public class PythonScriptContext extends PyJavaInstance {

    private static final Logger logger= org.das2.util.LoggerManager.getLogger("autoplot");    
    
    PythonScriptContext instance;

    private ApplicationModel model = null;
    
    private Application dom= null;
    
    private AutoplotUI view = null;

    public PythonScriptContext( ApplicationModel model ) {
        this.model= model;
        this.dom= model.getDocumentModel();
    }
    
    public PythonScriptContext( AutoplotUI app ) {
        this.view= app;
        this.dom= app.getDocumentModel();
        this.model= this.dom.getController().getApplicationModel();
    }
    
    /**
     * set up the uncaught exception handler for headless applications, like CreatePngWalk.
     */
    private void setUpHeadlessExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                logger.log(Level.SEVERE, "runtime exception: " + e, e);
                if (e instanceof InconvertibleUnitsException) {
                    // do nothing!!!  this is associated with the state change.  TODO: this should probably not be here, and it should be caught elsewhere.
                    return;
                }
                model.getExceptionHandler().handleUncaught(e);
            }
        });
    }
    /**
     * initialize the model and view.
     */
    private void maybeInitModel() {
        if (model == null) {
            model = new ApplicationModel();
            model.setExceptionHandler( new ExitExceptionHandler() );
            setUpHeadlessExceptionHandler();
            model.addDasPeersToAppAndWait();
            dom= model.getDocumentModel();
        }
        if ( view!=null ) {
            if ( SwingUtilities.isEventDispatchThread() ) {
                if ( !view.isVisible() ) view.setVisible(true);
            } else {
                SwingUtilities.invokeLater( new Runnable() {
                    public void run() {
                        if ( !view.isVisible() ) view.setVisible(true);
                    }
                } );
            }
        }
    }
    
    /**
     * plot the dataset in the specified dataSource node, using the render type
     * specified.  The renderType parameter is a string identifier, and currently the following are
     * used: digital spectrogram nnSpectrogram hugeScatter series scatter colorScatter stairSteps
     * fillToZero digital image  pitchAngleDistribution eventsBar vectorPlot orbitPlot contour
     *<blockquote><pre><small>{@code
     *plot( 0, 'label', findgen(20), ripples(20), ripples(20), 'digital' )
     *from org.virbo.autoplot import RenderType
     *plot( 0, 'label', findgen(20), ripples(20), ripples(20), RenderType.digital.toString() )
     *}</small></pre></blockquote>
     *
     * @param chNum the plot to use.  Plots and plot elements are added as necessary to plot the data.
     * @param label the label for the dependent parameter
     * @param x QDataSet for the independent parameter for the X values
     * @param y QDataSet for the independent parameter for the Y values
     * @param z Rank 1 or Rank 2 QDataSet for the dependent parameter
     * @param renderType hint at the render type to use, such as "nnSpectrogram" or "digital", 
     */
    public void plot( int chNum, String label, QDataSet x, QDataSet y, QDataSet z, String renderType ) {
        maybeInitModel();
        if ( z.rank()==1 ) {
            ArrayDataSet yds= ArrayDataSet.copy(y);
            yds.putProperty( QDataSet.RENDER_TYPE, renderType );
            yds.putProperty( QDataSet.DEPEND_0, x );
            yds.putProperty( QDataSet.PLANE_0, z );
            model.setDataSet(chNum, label, yds);
        } else {
            ArrayDataSet zds= ArrayDataSet.copy(z);
            zds.putProperty( QDataSet.RENDER_TYPE, renderType );
            if ( x!=null ) zds.putProperty( QDataSet.DEPEND_0, x );
            if ( y!=null ) zds.putProperty( QDataSet.DEPEND_1, y );
            model.setDataSet(chNum, label, zds);
        }
        if ( !SwingUtilities.isEventDispatchThread() ) model.waitUntilIdle();
    }
    
}
