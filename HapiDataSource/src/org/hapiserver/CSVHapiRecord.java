
package org.hapiserver;

import java.util.Map;
import org.json.JSONObject;

/**
 * HapiRecord backed by CSV response.
 * @author jbf
 */
public class CSVHapiRecord implements HapiRecord {
        
    String[] fields;
    Map<Integer,Integer> indexMap;

    public CSVHapiRecord( JSONObject info, String[] fields ) {
        this.fields= fields;
        
    } //TODO: if any parameter has length>0, then recombine them.
    
    @Override
    public String getIsoTime(int i) {
        return fields[i];
    }

    @Override
    public String getString(int i) {
        return fields[i];
    }

    @Override
    public double getDouble(int i) {
        return Double.valueOf(fields[i]);
    }

    @Override
    public double[] getDoubleArray(int i) {
        String[] ss= fields[i].split(",");
        double[] result= new double[ss.length];
        for ( int j=0; j<ss.length; j++ ) {
            result[j]= Double.parseDouble(ss[j]);
        }
        return result;
    }

    @Override
    public int getInteger(int i) {
        return Integer.valueOf(fields[i]);
    }

    @Override
    public int length() {
        return fields.length;
    }
    
    @Override
    public String toString() {
        return String.format( "%s: %d fields", fields[0], fields.length );
    }
}
