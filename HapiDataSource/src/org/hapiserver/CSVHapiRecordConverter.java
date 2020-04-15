/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.hapiserver;

import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * @author jbf
 */
public class CSVHapiRecordConverter {
    
    Map<Integer,Integer> indexMap;
    JSONObject info;
    JSONArray params;
    int[] sizes;
    
    public CSVHapiRecordConverter( JSONObject info ) throws JSONException {
        this.info= info;
        this.params= info.getJSONArray("parameters");
        this.sizes= new int[params.length()];
        for ( int i=0; i<params.length(); i++ ) {
            JSONObject jo= params.getJSONObject(i);
            if ( jo.has("size") ) {
                JSONArray size= jo.getJSONArray("size");
                sizes[i]= size.getInt(0);
                for ( int j=1; j<size.length(); j++ ) {
                    sizes[i]*= size.getInt(j);
                }
            } else {
                sizes[i]= 1;
            }
        }
    }

    public HapiRecord convert( String record ) {
        String[] fields= record.trim().split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)",-2);
        String[] ff= new String[params.length()];
        int i=0;
        for ( int j=0; i<params.length(); j++ ) {
            if ( sizes[j]==1 ) {
                ff[j]= fields[i];
                i=i+1;
            } else {
                StringBuilder build= new StringBuilder(fields[i]);
                for ( int k=1; k<sizes[j]; k++ ) {
                    build.append(",").append(fields[i+k]);
                }
                ff[j]= build.toString();
                i+=sizes[j];
            }
        }
        return new CSVHapiRecord(info,ff);
    }
}
