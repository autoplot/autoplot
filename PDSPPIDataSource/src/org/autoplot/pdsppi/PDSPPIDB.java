/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.pdsppi;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
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
import org.das2.util.filesystem.FileSystem;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.qds.QDataSet;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSourceUtil;
import org.autoplot.spase.VOTableReader;
import org.das2.datum.HttpUtil;
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
    Logger loggerUrl= org.das2.util.LoggerManager.getLogger( "das2.url" );
    
    private static final PDSPPIDB instance= new PDSPPIDB();
    
    public static final String PDSPPI="https://pds-ppi.igpp.ucla.edu/";
    //public static final String PDSPPI="https://ppi.pds.nasa.gov";
    
    List<String> ids= new ArrayList<String>(1100);
    
    public static PDSPPIDB getInstance() {
        return instance;
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
            loggerUrl.log(Level.FINE, "openConnection {0}", src);
            URLConnection connect= src.openConnection();
            connect.setReadTimeout( FileSystem.settings().getConnectTimeoutMs() );
            connect= HttpUtil.checkRedirect(connect);
            reader= new BufferedReader( new InputStreamReader( connect.getInputStream() ) );
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
            loggerUrl.log(Level.FINE,"GET to get data {0}", url);
            URLConnection connect= url.openConnection();
            connect= HttpUtil.checkRedirect(connect);
            fin= connect.getInputStream();

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
     * apparently the id needs to have underscores where there are slashes...  e.g.
     * PPI/CO-E/J/S/SW-CAPS-5-DDR-ELE-MOMENTS-V1.0 -> PPI/CO-E_J_S_SW-CAPS-5-DDR-ELE-MOMENTS-V1.0
     * @param root like PPI/CO-E/J/S/SW-CAPS-5-DDR-ELE-MOMENTS-V1.0/
     * @return result like PPI/CO-E_J_S_SW-CAPS-5-DDR-ELE-MOMENTS-V1.0
     */
    public static String removeExtraSlashes( String root ) {
        int i= root.indexOf("/"); // 4 for PPI/
        i++;
        return root.substring(0,i) + root.substring(i).replaceAll("/","_");
    }
        
    
    /**
     * return true if the name appears to be a plottable id.
     * @param ds name from their filesystem that ends with .lbl, .tab, etc.
     * @return true if the id appears to be plottable.
     */
    public static boolean isPlottable( String ds ) {
        return ds.endsWith(".lbl") 
            || ds.endsWith(".LBL") 
            || ds.endsWith(".tab" ) 
            || ds.endsWith(".DAT") 
            || ds.endsWith(".dat" ) 
            || ds.endsWith(".TAB") 
            || ds.endsWith(".csv" ) 
            || ds.endsWith(".CSV");
    }
    
    /**
     * Get the IDs matching the constraint.
     * @param constraint constraints, such as sc=Galileo
     * @param reqPrefix each item of result must start with this.  (PPI/ was omitted.)
     * @return the ids
     * @throws IOException when the database is not available.
     */
    public String[] getIds( String constraint, String reqPrefix ) throws IOException {
        Pattern p= Pattern.compile("sc=[a-zA-Z_ 0-9/\\(\\)]*");
        if ( !p.matcher(constraint).matches() ) {
            throw new IllegalArgumentException("constraint doesn't match (sc=[a-zA-Z_ 0-9/]*): "+constraint);
        }
        //https://ppi.pds.nasa.gov/ditdos/inventory?sc=Galileo&facet=SPACECRAFT_NAME&title=Cassini&o=txt
        URL url= new URL( String.format( PDSPPI + "ditdos/inventory?%s&o=txt", constraint.replaceAll(" ","+") ) );
        logger.log( Level.FINE, "getIds {0}", url);
        final String[] dss= getStringArray( url, reqPrefix ); //TODO: I still don't know why I need to add this.
        return dss;
    }
    
    /**
     * check if the file appears to be XML.  This currently looks for <pre>"<?xml "</pre>
     * @param f the file
     * @return null if the file appears to be XML, the first line otherwise.
     * @throws IOException 
     */
    String checkXML( File f ) throws IOException {
        BufferedReader read=null;
        try {
            read= new BufferedReader( new InputStreamReader( new FileInputStream(f) ) );
            String s= read.readLine();
            if ( s!=null && s.length() >= 6 && s.substring(0,6).equals("<?xml ") ) {
                return null;
            } else {
                return s;
            }
        } finally {
            if ( read!=null ) read.close();
        }
    }
    
    /**
     * parameterize the URI so that any number of files can be read in.  For example,
     **<blockquote><pre><small>{@code
     * vap+pdsppi:sc=Cassini&id=PPI/CO-S-MIMI-4-LEMMS-CALIB-V1.0/DATA/LACCAVG0_1MIN/2006/LACCAVG0_1MIN_2006269_01&param=E0
     *}</small></pre></blockquote>
     * would result in 
     *<blockquote><pre><small>{@code
     *vap+pdsppi:sc=Cassini&id=PPI/CO-S-MIMI-4-LEMMS-CALIB-V1.0/DATA/LACCAVG0_1MIN/$Y/LACCAVG0_1MIN_$Y$j_01&param=E0
     *}</small></pre></blockquote> 
     * 
     * @param uri 
     * @return the aggregation URI or null.
     */
    public String checkTimeSeriesBrowse( String uri ) {
        return null;
        //String agg= org.virbo.datasource.DataSourceUtil.makeAggregation(uri);
        //if ( agg==null || agg.equals( uri ) ){
        //    return null;
        //} else {
        //    int i= agg.indexOf("?timerange=");
        //    agg= agg.substring(0,i);
        //    return agg;
        //}
    }
    
    
    /**
     * return a list of the plottable parameter datasets within the ID.
     * TODO: this loads the entire dataset, this will be fixed.
     * @param id
     * @param mon
     * @return Map label->title of the params.
     * @throws IllegalArgumentException the server can throw exceptions
     */
    public Map<String,String> getParams( String id, ProgressMonitor mon ) throws IllegalArgumentException {
        VOTableReader read;
        String url= PDSPPIDB.PDSPPI+"ditdos/write?f=vo&id=pds://"+id;
        
        try {
            read= new VOTableReader();
            mon.setProgressMessage("downloading data");
            logger.log(Level.FINE, "getParams {0}", url);
            File f= DataSetURI.downloadResourceAsTempFile( new URL(url), 3600, mon.getSubtaskMonitor("downloading file") );
            String s= checkXML(f);
            if ( s!=null ) {
                throw new IllegalArgumentException("file does not appear to be xml: "+s );
            }
            
            mon.setProgressMessage("reading data");
            QDataSet ds= read.readHeader( f.toString(), mon.getSubtaskMonitor("reading data") );
            
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
            return Collections.singletonMap( "(IOException from "+url+")", ex.getMessage() );
        } catch (SAXException ex) {
            logger.log(Level.SEVERE, "SAXException from "+url, ex);
            return Collections.singletonMap( "(SAXException from "+url+")", ex.getMessage() );
        } catch (ParserConfigurationException ex) {
            logger.log(Level.SEVERE, "ParserConfigurationException", ex);
            return Collections.singletonMap( "(ParserConfigurationException)", ex.getMessage() );
        }
    }
 
    public static void main( String[] args ) {
        String[] scs= getInstance().getSpacecraft();
        for ( String sc: scs ) {
            System.err.println( sc );
        }
    }
}
