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
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSourceUtil;
import org.virbo.spase.VOTableReader;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
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
    
    String[] _spacecraft=null;
    
    /**
     * returns the spacecraft.  
     * @return 
     */
    public synchronized String[] getSpacecraft() {
        if ( _spacecraft==null ) {
            try {
                _spacecraft= getStringArrayFromXML( PDSPPIDB.class.getResource("/resources/spacecraft.xml"), "/Doc/SPACECRAFT_NAME[text()]");
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
        return Arrays.copyOf(_spacecraft,_spacecraft.length);
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
     * return the result of the URL as a string array.
     * @param src the source.
     * @param reqPrefix the prefix that each entry should start with.
     * @return the string array. 
     */
    private String[] getStringArray( URL src, String reqPrefix ) throws IOException {
        List<String> result= new ArrayList();
        BufferedReader reader= null;
        try {
            reader= new BufferedReader( new InputStreamReader( src.openStream() ) );
            String line= reader.readLine();
            while ( line!=null ) {
                if ( !line.startsWith(reqPrefix) ) {
                    line= reqPrefix + line;
                }
                result.add(line);
                line= reader.readLine();
            }
        } finally {
            if ( reader!=null ) reader.close();
        }
        return result.toArray( new String[result.size()] );
    }
    
    /**
     * return an array of strings from the given path.
     * @param url resource location.
     * @param path XPath path, like /Doc/SPACECRAFT_NAME for
     *<code> <Doc><SPACECRAFT_NAME>Voyager 1</SPACECRAFT_NAME><Doc></code>
     * @return the string array, like [ "Voyager 1" ]
     */
    private String[] getStringArrayFromXML( URL url, String path ) throws IOException {
               
        List<String> result= new ArrayList(); 
        InputStream fin=null;
            
        Document document;
        
        try {
            logger.log(Level.FINE, "opening {0}", url);
            fin= url.openStream();

            InputSource source = new InputSource( fin );
            
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            document = builder.parse(source); 
            
            XPathFactory xpf= DataSourceUtil.getXPathFactory();
            XPath xp = xpf.newXPath();
            
            NodeList nodes = (NodeList) xp.evaluate( path, document, XPathConstants.NODESET );
            if ( nodes==null ) {
                return new String[0];
            } else {
                for ( int i=0; i<nodes.getLength(); i++ ) {
                    Element node= (Element)nodes.item(i);
                    result.add( node.getChildNodes().item(0).getNodeValue() );
                }
            }
            
        } catch ( XPathExpressionException ex ) {
            throw new RuntimeException(ex);   
            
        } catch ( SAXException ex ) {
            throw new RuntimeException(ex);   
            
        } catch ( ParserConfigurationException ex ) {
            throw new RuntimeException(ex);
            
        } finally {
            if ( fin!=null ) fin.close();
        }
            String[] listing=result.toArray(new String[result.size()]);
           
            return listing;
            
    }
    /**
     * Get the IDs matching the constraint.
     * @param constraint constaints, such as sc=Galileo
     * @param reqPrefix each item of result must start with this.  (PPI/ was omitted.)
     * @return 
     */
    public String[] getIds( String constraint, String reqPrefix ) {
        Pattern p= Pattern.compile("sc=[a-zA-Z 0-9]*");
        if ( !p.matcher(constraint).matches() ) {
            throw new IllegalArgumentException("constraint doesn't match");
        }
        try {
            //http://ppi.pds.nasa.gov/ditdos/inventory?sc=Galileo&facet=SPACECRAFT_NAME&title=Cassini&o=txt
            URL url= new URL( String.format( "http://ppi.pds.nasa.gov/ditdos/inventory?%s&o=txt", constraint.replaceAll(" ","+") ) );
            logger.log( Level.FINE, "getIds {0}", url);
            final String[] dss= getStringArray( url, reqPrefix ); //TODO: I still don't know why I need to add this.
            return dss;
        } catch ( IOException ex ) {
            throw new RuntimeException(ex);
        }
    }
    
    /**
     * return a list of the plottable parameter datasets within the ID.
     * TODO: this loads the entire dataset, this will be fixed.
     * @param id
     * @param mon
     * @return Map label->title of the params.
     */
    public Map<String,String> getParams( String id, ProgressMonitor mon ) {
        VOTableReader read;
        String url= "http://ppi.pds.nasa.gov/ditdos/write?f=vo&id=pds://"+id;
        
        try {
            read= new VOTableReader();
            mon.setProgressMessage("downloading data");
            logger.log(Level.FINE, "getParams {0}", url);
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
            logger.log(Level.SEVERE, "IOException from "+url, ex);
            return Collections.singletonMap( "IOException from "+url, ex.getMessage() );
        } catch (SAXException ex) {
            logger.log(Level.SEVERE, "SAXException from "+url, ex);
            return Collections.singletonMap( "SAXException from "+url, ex.getMessage() );
        } catch (ParserConfigurationException ex) {
            logger.log(Level.SEVERE, "ParserConfigurationException", ex);
            return Collections.singletonMap( "ParserConfigurationException", ex.getMessage() );
        }
    }
 
    public static void main( String[] args ) {
        System.err.println( getInstance().getSpacecraft() );
    }
}
