/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.idlsupport;

import java.util.Map;
import org.das2.util.AboutUtil;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.DataSetSelectorSupport;

/**
 *
 * @author jbf
 */
public class Util {
    public static boolean isMap( Object o ) {
        return o instanceof Map;
    }

    public static boolean isQDataSet( Object o ) {
        return o instanceof QDataSet;
    }
    
    public static String getPlugins(  ) {
        return DataSetSelectorSupport.getPluginsText();
    }

    public static String getVersions(  ) {
        return AboutUtil.getAboutHtml();
    }
    
    
}
