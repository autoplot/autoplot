/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.pdsppi;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 *
 * @author jbf
 */
public class PDSPPIDB {
    
    private static final PDSPPIDB instance= new PDSPPIDB();
    
    private boolean loaded = false;
    
    List<String> ids= new ArrayList<String>(1100);
    
    public static PDSPPIDB getInstance() {
        
        if ( instance.loaded ) {
            // no need to load
        } else {
            synchronized ( PDSPPIDB.class ) {
                if ( instance.loaded ) {
                    // another thread loaded it
                } else {
                    instance.load();
                    instance.loaded= true;
                }
            } 
        }
        return instance;
    }
    
    private void load() {
        InputStream in= null;
        try {
            URL url= PDSPPIDB.class.getResource("pdsid.txt");
            in = url.openStream();
            BufferedReader bin= new BufferedReader(new InputStreamReader(in));
            String line= bin.readLine();
            while ( line!=null ) {
                ids.add(line);
            }
        } catch (IOException ex) {
            Logger.getLogger(PDSPPIDB.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                in.close();
            } catch (IOException ex) {
                Logger.getLogger(PDSPPIDB.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    public List<String> getIds() {
        return Collections.unmodifiableList(ids);
    }
    
    /**
     * return the IDs matching the result.
     * @param p regular expression pattern
     * @return the matching list.
     */
    public List<String> getIds( Pattern p ) {
        ArrayList result= new ArrayList( ids.size() );
        for ( String s: ids ) {
            if ( p.matcher(s).matches() ) {
                result.add( s );
            }
        }
        return result;
    }
    
}
