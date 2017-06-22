/*
 * NetcdfMetadataModel.java
 *
 * Created on November 14, 2007, 8:48 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.autoplot.netCDF;

import org.das2.datum.Units;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.das2.qds.QDataSet;
import org.autoplot.datasource.MetadataModel;

/**
 * This was once used to implement scale_factor and add_offset, but is no longer used.
 * @author jbf
 */
public class NetcdfMetadataModel extends MetadataModel {

    /** Creates a new instance of NetcdfMetadataModel */
    public NetcdfMetadataModel() {
    }

    @Override
    public Map<String, Object> properties( Map<String,Object> meta) {
        Map<String,Object> result= new HashMap<String, Object>();
        if (meta.containsKey("valid_range")) {
            //result.put( QDataSet.VALID_RANGE, meta.get("valid_range" ) );
            Logger.getLogger("apdss.netcdf").fine("here's where I didn't think there was going to be VALID_RANGE");
        }
        return result;
    }

    public static Map<String, Object> interpretProps(Map map) {
        HashMap<String, Object> result = new HashMap<String, Object>();
        if (map.containsKey("units")) {
            result.put(QDataSet.UNITS, Units.t1970); //TODO: study this.  Surely this is too loose.
        }
        if (map.containsKey("add_offset")) {
            result.put( "add_offset", (Double) map.get("add_offset") );
        }
        if (map.containsKey("scale_factor")) {
            result.put( "scale_factor", (Double) map.get("scale_factor") );
        }
        //if (map.containsKey("valid_range")) {
        //    result.put( QDataSet.VALID_RANGE, map.get("valid_range" ) );
        //}
        return result;
    }

    @Override
    public String getLabel() {
        return "NetCDF";
    }

}