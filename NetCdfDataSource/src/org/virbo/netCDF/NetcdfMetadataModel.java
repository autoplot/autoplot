/*
 * NetcdfMetadataModel.java
 *
 * Created on November 14, 2007, 8:48 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.virbo.netCDF;

import edu.uiowa.physics.pw.das.datum.Units;
import java.util.HashMap;
import java.util.Map;
import javax.swing.tree.TreeModel;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.MetadataModel;

/**
 *
 * @author jbf
 */
public class NetcdfMetadataModel extends MetadataModel {

    /** Creates a new instance of NetcdfMetadataModel */
    public NetcdfMetadataModel() {
    }

    public Map<String, Object> properties(TreeModel meta) {
        int nchild = meta.getChildCount(meta.getRoot());
        HashMap attrs = new HashMap();
        for (int i = 0; i < nchild; i++) {
            String ss = String.valueOf(meta.getChild(meta.getRoot(), i));
            int ii = ss.indexOf("=");
            attrs.put(ss.substring(0, ii), ss.substring(ii + 1).trim());
        }


        return new HashMap<String, Object>();
    }

    public static Map<String, Object> interpretProps(Map map) {
        HashMap<String, Object> result = new HashMap<String, Object>();
        if (map.containsKey("units")) {
            System.err.println(map.get("units"));
            result.put(QDataSet.UNITS, Units.t1970);
        }
        if (map.containsKey("add_offset")) {
            result.put( "add_offset", (Double) map.get("add_offset") );
        }
        if (map.containsKey("scale_factor")) {
            result.put( "scale_factor", (Double) map.get("scale_factor") );
        }
        if (map.containsKey("valid_range")) {
            result.put( QDataSet.VALID_RANGE, map.get("valid_range" ) );
        }
        return result;
    }
}