/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.datasource;

import java.lang.reflect.Constructor;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.virbo.aggregator.AggregatingDataSourceEditorPanel;

/**
 *
 * @author jbf
 */
public class DataSourceEditorPanelUtil {
    /**
     * @param uri
     * @return an EditorPanel or null if one is not found.
     */
    public static DataSourceEditorPanel getDataSourceEditorPanel(URI uri) {
        String surl = DataSetURI.fromUri(uri);
        String ext = DataSetURI.getExt(surl);

        if (  DataSetURI.isAggregating(surl) ) {
            String eext = DataSetURI.getExplicitExt(surl);
            if (eext != null) {
                AggregatingDataSourceEditorPanel result = new AggregatingDataSourceEditorPanel();
                DataSourceEditorPanel edit = getEditorByExt(eext);
                if (edit != null) {
                    result.setDelegateEditorPanel(edit);
                }
                return result;
            } else {
                return new AggregatingDataSourceEditorPanel();
            }

        }

        DataSourceEditorPanel edit = getEditorByExt(ext);
        return edit;
    }

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
