
package org.autoplot.util;

import java.awt.Window;
import java.awt.image.BufferedImage;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import org.autoplot.ApplicationModel;
import org.autoplot.AutoplotUtil;
import org.autoplot.ScriptContext;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.jythonsupport.ui.DataMashUp;
import org.das2.qds.QDataSet;
import org.das2.util.monitor.NullProgressMonitor;

/**
 *
 * @author jbf
 */
public class PlotDataMashupResolver implements DataMashUp.Resolver {
    private static Logger logger= org.das2.util.LoggerManager.getLogger("autoplot.gui");
    
    final DataMashUp dm;
    
    public PlotDataMashupResolver( DataMashUp dm ) {
        this.dm= dm;
    }
    
    @Override
        public QDataSet getDataSet(String uri) {
            try {
                return DataSetURI.getDataSource(uri).getDataSet( new NullProgressMonitor() );
            } catch (Exception ex) {
                logger.log(Level.INFO,null,ex);
                return null;
            }
        }

        @Override
        public BufferedImage getImage(QDataSet qds) {
            return AutoplotUtil.createImage( qds, 120, 60 );
        }

        @Override
        public void interactivePlot( QDataSet qds ) {
            Window w= SwingUtilities.getWindowAncestor(dm);
            ApplicationModel model= ScriptContext.newDialogWindow( w, qds.toString() );
            model.setDataSet( qds );
        }
}
