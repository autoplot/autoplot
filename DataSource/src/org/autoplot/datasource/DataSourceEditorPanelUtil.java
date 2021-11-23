
package org.autoplot.datasource;

import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JPanel;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.NullProgressMonitor;
import org.autoplot.aggregator.AggregatingDataSourceEditorPanel;
import org.autoplot.aggregator.AggregatingDataSourceFactory;
import static org.autoplot.datasource.DataSetSelector.logger;
import org.das2.util.monitor.AlertNullProgressMonitor;

/**
 * Utilities for URLs.
 * @author jbf
 */
public class DataSourceEditorPanelUtil {
    
    private static final Logger logger= LoggerManager.getLogger("apdss.uri");
    
    /**
     * get an editor for the URI.  This must be inserted into the GUI, using getPanel.
     * @param parent
     * @param uri
     * @return the editor panel.
     * @throws IllegalArgumentException when the editor panel throws an exception.
     */
    public static DataSourceEditorPanel getDataSourceEditorPanel( JPanel parent, String uri ) {
        logger.entering("org.autoplot.datasource.DataSourceEditorPanelUtil", "getDataSourceEditorPanel");
        if ( parent==null ) {
            throw new IllegalArgumentException("parent is null");
        }
        DataSourceEditorPanel edit;
        edit = DataSourceEditorPanelUtil.getDataSourceEditorPanel( uri );
        if ( edit==null ) {
            throw new IllegalArgumentException("can''t get editor for " + uri); // shouldn't happen now.
        } else {
            try {
                if ( !edit.reject( uri ) ) {
                    edit.prepare( uri, null, new NullProgressMonitor() );
                    edit.setURI( uri );
                    JPanel editPanel= edit.getPanel();
                    editPanel.setAlignmentX( Component.LEFT_ALIGNMENT );
                    parent.add( editPanel );
                }
            } catch ( Exception ex ) {
                throw new IllegalArgumentException(ex);
            }
        }
        logger.exiting("org.autoplot.datasource.DataSourceEditorPanelUtil", "getDataSourceEditorPanel");
        return edit;
    }
    
    /**
     * @param uri
     * @return an EditorPanel or null if one is not found.
     */
    public static DataSourceEditorPanel getDataSourceEditorPanel(URI uri) {
        return getDataSourceEditorPanel( uri.toString() );
    } 
    
    /**
     * @param suri the autoplot vap+xxx: URI.
     * @return an EditorPanel or null if one is not found.
     */
    public static DataSourceEditorPanel getDataSourceEditorPanel( String suri) {
        String surl = suri;
        String ext = DataSetURI.getExt(surl);

        if ( ext.equals(DataSetURI.RECOGNIZE_FILE_EXTENSION_JSON) || ext.equals( DataSetURI.RECOGNIZE_FILE_EXTENSION_XML ) ) {
            try {
                File f= DataSetURI.getFile(suri,new AlertNullProgressMonitor("download on event thread"));
                String ext2= DataSourceRecognizer.guessDataSourceType(f);
                if ( ext2!=null ) {
                    ext= ext2;
                }                    
            } catch (IOException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
        
        if (  DataSetURI.isAggregating(surl) ) {
            String eext = DataSetURI.getExplicitExt(surl);
            if (eext != null) {
                AggregatingDataSourceEditorPanel result = new AggregatingDataSourceEditorPanel();
                DataSourceEditorPanel edit = getEditorByExt(eext);
                if (edit != null) {
                    result.setDelegateEditorPanel(edit);
                    try {
                        String delegateUri = AggregatingDataSourceFactory.getDelegateDataSourceFactoryUri(surl, new NullProgressMonitor() );
                        if ( edit.reject(delegateUri) ) { // contracts say that reject should be called before getPanel.
                            logger.log( Level.WARNING, null, "delegate editor rejects URI, ignoring: " +suri );   
                        }
                    } catch (Exception ex) {
                        logger.log( Level.WARNING, null, ex );
                    }
                    result.setName( edit.getPanel().getName() );
                }
                return result;
            } else {
                return new AggregatingDataSourceEditorPanel();
            }

        }

        DataSourceEditorPanel edit = getEditorByExt(ext);
        return edit;
    }

    /**
     * return the editor by the extension, like "cdf"
     * @param extension
     * @return 
     */
    public static DataSourceEditorPanel getEditorByExt(String extension) {
        if ( extension==null ) return null;
        extension= DataSourceRegistry.getExtension(extension);
        Object o = DataSourceRegistry.getInstance().dataSourceEditorByExt.get(extension);
        if (o == null) {
            return null;
        }

        DataSourceEditorPanel result;
        if (o instanceof String) {
            try {
                Class clas = Class.forName((String) o);
                Constructor constructor = clas.getDeclaredConstructor(new Class[]{});
                result = ( DataSourceEditorPanel) constructor.newInstance(new Object[]{});
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        } else {
            result = (DataSourceEditorPanel) o;
        }
        return result;
    }


}
