/*
 * SPDFMetaDataScraper.java
 *
 * Created on February 2, 2007, 7:21 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.dods;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;

/**
 * Scrape the metadata from the <dods URL>.html form of the data.  
 * Get a new instance, call parse( <dods URL>.html ), then 
 * call getAttr(String varName) which returns a Map of the properties.
 *
 * Note the scraping is only necessary because Jeremy forgot about the
 * .das and .dds extensions.  .dds returns the stream syntax.  .das returns 
 * the metadata.
 * 
 * @author jbf
 */
public class MetaDataScraper {
    
    HashMap varAttrs;
    HashMap varAttrsData;
    HashMap recDims;
    
    class MyCallBack extends HTMLEditorKit.ParserCallback {        
        String varName;
        String recDim;
        Object nameKey=HTML.getAttributeKey("name"); 
        
        public void handleText(char[] data, int pos) {
            if ( varName!=null ) {
                varAttrsData.put( varName, data );
            } else {
                String s= new String( data );
                int i= s.indexOf("[RecDim =");
                if ( i==-1 ) i= s.indexOf("[Dim0 ="); // sometimes doesn't start with RecDim (ENERGY_ELE)
                if (i!= -1 ) {
                    recDim= s.substring(i); // the RecDim is the last bit in the the Text segment.
                }
            }
        }

        public void handleStartTag(HTML.Tag t, MutableAttributeSet a, int pos) {
            super.handleStartTag(t, a, pos);
            if ( t==HTML.Tag.TEXTAREA ) {
                String nameAttr=  (String)a.getAttribute(nameKey) ;
                if ( nameAttr!=null  && nameAttr.endsWith("_attr") ) {
                    varName= nameAttr.substring(0,nameAttr.length()-5);
                    if ( recDim!=null ) {
                        recDims.put( varName, recDim );
                        recDim= null;
                    }
                } else {
                    varName=null;
                }
            } else {
                varName=null;
            }
        }
        
        public void handleEndTag(HTML.Tag t, int pos) {
            super.handleEndTag(t, pos);
            varName= null;
        }


    }
    
    /**
     * retrieve the URL, which should be a dods server form.  The
     * content is scraped, looking for textareas with the name
     * <i>varname</i>_attr.  The textarea content is assumed to
     * be a newline delimited set of name value pairs, name: value.
     * Value is of type Double or String.
     *
     * After parseURL is performed, getAttr is used to get Attributes.
     * @param url
     * @throws IllegalArgumentException when the url does not end in .html
     */
    public void parseURL( URL url ) throws IOException {
        if ( !url.toString().endsWith(".html" ) ) throw new IllegalArgumentException("must end in .html");
        varAttrs= new HashMap();
        varAttrsData= new HashMap();
        recDims= new HashMap();
        InputStream in= url.openStream();
        new ParserDelegator().parse( new InputStreamReader(in), new MyCallBack(), true );
        in.close();
    }
    
    private Map parseData( char[] data ) {
        int ipos= 0;
        HashMap result= new HashMap();
        String s= new String(data);
        String[] ss= s.split("\n");
        for ( int i=0; i<ss.length; i++ ) {
            int ic= ss[i].indexOf(":");
            String name= ss[i].substring(0,ic);
            String value= ss[i].substring(ic+1).trim();
            if ( value.startsWith("\"") ) {
                value= value.substring(1,value.length()-1);
                result.put( name, value );
            } else {
                result.put( name, new Double( Double.parseDouble(value) ) );
            }
        }
        return result;
    }
    
    /**
     * provides the attributes for this variable in a map.  The keys are the String 
     * attribute name (e.g. UNITS) and the values are either type String or Double.
     */
    public Map getAttr( String varName ) {
        if ( varAttrs==null ) throw new IllegalArgumentException("need to parse URL first");
        Map result= (Map) varAttrs.get(varName);
        if ( result==null ) {
            char[] data= (char[]) varAttrsData.get(varName);
            if ( data==null ) throw new IllegalArgumentException("variable not found: "+varName );
            result= parseData( data );
            varAttrs.put( varName, result );
        }
        return result;
    }
    
    public int[] getRecDims( String varName ) {
        String rds= (String) recDims.get(varName);
        if ( rds==null ) throw new IllegalArgumentException("variable not found: "+varName );
        String[] ss= rds.split("]");
        int[] result= new int[ss.length];
        for ( int i=0; i<ss.length; i++ ) {
            int idd= ss[i].indexOf("..");
            result[i]= Integer.parseInt( ss[i].substring(idd+2) );
        }
        return result;
    }
    
    
    /** 
     * Creates a new instance of SPDFMetaDataScraper.  Use parseData then getAttr after creating the instance. 
     */
    public MetaDataScraper() {
        
    }
    
}
