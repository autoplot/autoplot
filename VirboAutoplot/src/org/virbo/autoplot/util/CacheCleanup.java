/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.text.ParseException;
import java.util.ArrayList;
import org.das2.datum.DatumRange;
import org.das2.fsm.FileStorageModel;
import org.das2.util.filesystem.FileSystem;
import org.virbo.datasource.AutoplotSettings;
import org.virbo.datasource.URISplit;
import javax.swing.JPanel;
import javax.swing.JCheckBox;



/**
 *
 * @author mmclouth
 */
public class CacheCleanup {
    
    /**
     * this should not be instantiated
     */
    private CacheCleanup() {

    }
    
    /**
     * find aggregations within the user's history.  This currently looks for $Y, but aggregations
     * can also be $y, etc.
     * 
     * @return list of aggregations.
     * @throws IOException 
     */
    public static String[] findAggs() throws IOException {
        File filePath= new File( AutoplotSettings.settings().resolveProperty( AutoplotSettings.PROP_AUTOPLOTDATA ) + "/bookmarks/history.txt" );
        FileReader fr = new FileReader(filePath);
        BufferedReader bf = new BufferedReader(fr);
        
        String aLine;
        ArrayList<String> result= new ArrayList(100);
        boolean hasAgg;
        int numOfLines = 0;
        
        while ((aLine = bf.readLine()) != null)  { // this is a nice bit of code that avoids two calls to readLine!
            numOfLines++;
        }
        
        fr = new FileReader(filePath);
        bf = new BufferedReader(fr);
        int i;
        String[] data = new String[numOfLines];
        String[] parts = new String[2];
        
        for (i=0 ; i<numOfLines ; i++) {
            data[i] = bf.readLine();
            hasAgg = data[i].contains("$Y");
            if (hasAgg == true) {
            
                int iq= data[i].indexOf("?"); 
                int iy= data[i].indexOf("$Y"); 
                if ( iq==-1 || iq>iy ) {
                    parts = data[i].split("\\s+"); 
                    if (parts.length > 1)  {
                        result.add(parts[1]);
                    }
                }
            }
        }
        bf.close();

        return result.toArray( new String[result.size()] );
    }
    
    /**
     * Return an array of files where newer versions prevent the older from being used.  This will
     * not look for version constraints (e.g. $(v,lt=2)), so use with some care.
     * @return an array of files where newer versions prevent the older from being used.
     * @throws IOException 
     */
    public static String[] findOldVersions() throws IOException {
        String[] aggs = findAggs();
        ArrayList<String> oldversions= new ArrayList(1000);
        String[] result;
        FileSystem.settings().setOffline(true); // turn off web access        
        for (String agg : aggs) {
            URI fileagguri = URISplit.parse(agg).resourceUri;
            String fileagg= fileagguri.toString();
            System.err.println(fileagg);
            if ( fileagg.contains("rbspa_def_MagEphem_OP77Q" ) ) { 
                System.err.println("stop here");
            }
            int i = FileStorageModel.splitIndex( fileagg );
            String constantPart= fileagg.substring(0,i);
            String templatePart= fileagg.substring(i);
            //System.out.println(constantPart);
            //System.out.println(templatePart);
            FileSystem fs= FileSystem.create( constantPart );
            DatumRange dr= null;    //for testing, just do one month.  None means everything but it's much slower.
            //DatumRange dr = DatumRangeUtil.parseTimeRangeValid("2010-mar");
            FileStorageModel fsm = FileStorageModel.create( fs, templatePart );
            String localRoot= fsm.getFileSystem().getLocalRoot().toString();
            File[] ff= fsm.getFilesFor(dr);
            for (File ff1 : ff) {
                DatumRange tr = fsm.getRangeFor(ff1.toString().substring(localRoot.length() + 1));
                File[] fbest= fsm.getBestFilesFor(tr);
                if (!ff1.toString().equals(fbest[0].toString())) {
                    oldversions.add(ff1.toString());
                }
            }
        }
        if (oldversions.size() > 0) {
            result = oldversions.toArray( new String[oldversions.size()] );
        } else {
            result = new String[] {"No Old Versions"};
        }
        FileSystem.settings().setOffline(false); // turn off web access
        
    return result;
    }
    
    public static void deleteOldVersions() throws ParseException, IOException  {
        String[] oldversions = findOldVersions();
        
    }
}
    