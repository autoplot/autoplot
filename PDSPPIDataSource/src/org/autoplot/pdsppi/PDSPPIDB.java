/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.pdsppi;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.xml.parsers.ParserConfigurationException;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.DataSetURI;
import org.virbo.spase.VOTableReader;
import org.xml.sax.SAXException;

/**
 *
 * @author jbf
 */
public class PDSPPIDB {
    
    private static final Logger logger= LoggerManager.getLogger("apdss.pdsppi");
    
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
                    //instance.load();  this is going to change.
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
            logger.log(Level.SEVERE, null, ex);
        } finally {
            try {
                in.close();
            } catch (IOException ex) {
                logger.log(Level.SEVERE, null, ex);
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
    
    /**
     * return a list of the plottable datasets within the ID.
     * TODO: this loads the entire dataset, this will be fixed.
     * @param id
     * @param mon
     * @return 
     */
    public Map<String,String> getDss( String id, ProgressMonitor mon ) {
        try {
            VOTableReader read;
            
            String url= "http://ppi.pds.nasa.gov/ditdos/write?f=vo&id=pds://"+id;
            read= new VOTableReader();
            mon.setProgressMessage("downloading data");
            logger.fine("read "+url );
            File f= DataSetURI.downloadResourceAsTempFile( new URL(url), 3600, mon );
            mon.setProgressMessage("reading data");
            QDataSet ds= read.readHeader( f.toString(), mon );
            
            Map<String,String> result= new LinkedHashMap();
            
            for ( int i=0; i<ds.length(); i++ ) {
                String n= (String) ds.property( QDataSet.NAME, i );
                String l= (String) ds.property( QDataSet.LABEL, i );
                String t= (String) ds.property( QDataSet.TITLE, i );
                result.put( l,t );
            }
            return result;
            
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
            return Collections.singletonMap( "IOException", ex.getMessage() );
        } catch (SAXException ex) {
            logger.log(Level.SEVERE, null, ex);
            return Collections.singletonMap( "SAXException", ex.getMessage() );
        } catch (ParserConfigurationException ex) {
            logger.log(Level.SEVERE, null, ex);
            return Collections.singletonMap( "ParserConfigurationException", ex.getMessage() );
        }
    }
    
}
