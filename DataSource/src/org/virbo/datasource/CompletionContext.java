/*
 * CompletionContext.java
 *
 * Created on November 10, 2007, 6:01 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.datasource;

/**
 * models a part of a dataset's URL.  
 * @author jbf
 */
public class CompletionContext {
    
    public final static Object CONTEXT_FILESYSTEM="fs";
    public final static Object CONTEXT_FILE="file";  // file and filesystem seem to be the same for now.
    public final static Object CONTEXT_PARAMETER_NAME="paramName";
    public final static Object CONTEXT_PARAMETER_VALUE="paramValue";
    
    public CompletionContext() {
    }
    
    public CompletionContext( Object context, String completable ) {
        this( context, completable, null, null, null );
    }
    
    public CompletionContext( Object context, String completable, DataSourceFactory owner, String implicitName ) {
        this( context, completable, owner, implicitName, null );
    }
    
    public CompletionContext( Object context, String completable, DataSourceFactory owner, String implicitName, String doc ) {
        this.context= context;
        this.completable= completable;
        this.owner= owner;
        this.implicitName= implicitName;
        this.doc= doc;
    }
    
    /**
     * the url in its incomplete state
     */
    public String surl;
    
    /**
     * the position of the carot within the url
     */
    public int surlpos;
    
    /**
     * the context identifier enum for the completion. See CONTEXT_FILESYSTEM, CONTEXT_FILE, CONTEXT_PARAMETER_NAME, CONTEXT_PARAMETER_VALUE, etc.
     */
    public Object context;
    
    /**
     * The string to be completed
     */
    public String completable;
    
    /**
     * the position of the carot within the string
     */
    public int completablepos;  // position of the carot within the completable
    
    /**
     * used to identify the group the parameter implicitly specifies.  For example,
     * in ftp://cdaweb.gsfc.nasa.gov/pub/istp/noaa/noaa14/%Y/noaa14_meped1min_sem_%Y%m%d_v01.cdf?T01_gsmB&timerange=2000-01-01
     * T01_gsmB is part of the implicit group "id" of the cdf data source factory.
     */
    public String implicitName;
            
    /**
     * the factory that owns the parameter.  May be null
     */
    public DataSourceFactory owner;
    
    /**
     * one-line documentation
     */
    public String doc;
    
    /**
     * returns the value for the context
     * cc= new CompletionContext();
     * cc.surl= http://www.autoplot.org/data/myfile.dat?param1=aaa&param2=bbb
     * cc.completable= b
     * cc.surlpos= 59
     * get( COMPLETION_PARAMETER_NAME, cc ) ->  param2
     */
    public static String get( Object context, CompletionContext cc ) {
        if ( context==CONTEXT_FILESYSTEM || context==CONTEXT_FILE ) {
            int i=cc.surl.indexOf('?');
            if (i==-1 ) i=cc.surl.length();
            return cc.surl.substring(0,i);
            
        } else if ( context==CONTEXT_PARAMETER_NAME || context==CONTEXT_PARAMETER_VALUE ) {
            int i0= cc.surl.lastIndexOf('&',cc.surlpos);
            int i1= cc.surl.lastIndexOf('?',cc.surlpos);
            i0= Math.max( i0,i1 );
            if ( i0==-1 ) return null;
            i1= cc.surl.indexOf('&',i0+1);
            if ( i1==-1 ) i1= cc.surl.length();
            int i2= cc.surl.indexOf('=',i0);
            if ( context==CONTEXT_PARAMETER_NAME ) {
                if ( i2==-1 ) {
                    return cc.surl.substring(i0+1,i1);
                } else {
                    return cc.surl.substring(i0+1,i2);
                }
            } else {
                if ( i2==-1 ) {
                    return null;
                } else {
                    return cc.surl.substring(i2+1,i1);
                }
            }
        } else {
            throw new IllegalArgumentException("invalid context");
        }
    }
    
    /**
     * "ftp://cdaweb.gsfc.nasa.gov/pub/istp/noaa/noaa14/%Y/noaa14_meped1min_sem_%Y%m%d_v01.cdf?timerange=2000-01-01"
     * "ftp://cdaweb.gsfc.nasa.gov/pub/istp/noaa/noaa14/%Y/noaa14_meped1min_sem_%Y%m%d_v01.cdf?Epoch&timerange=2000-01-01"
     */
    public static String insert( CompletionContext cc, CompletionContext ccnew ) {
        Object context= ccnew.context;
        if ( context==CONTEXT_FILESYSTEM || context==CONTEXT_FILE ) {
            String surl= cc.surl;
            boolean isURI= false;
            //if ( surl.indexOf('.') < surl.indexOf(':') ) { // check for URI
            //    surl= surl.substring( surl.indexOf('.')+1 );
            //    isURI= true;
            //}
            DataSetURL.URLSplit split= DataSetURL.parse( surl );
            split.file= ccnew.completable;
            return DataSetURL.format( split );
            
        } else if ( context==CONTEXT_PARAMETER_NAME || context==CONTEXT_PARAMETER_VALUE ) {
            
            int i0= cc.surl.lastIndexOf('&',cc.surlpos-1);
            int i1= cc.surl.lastIndexOf('?',cc.surlpos-1);
            i0= Math.max( i0,i1 );
            if ( i0==-1 ) return null;
            i1= cc.surl.indexOf('&',i0+1);
            if ( i1==-1 ) i1= cc.surl.length();
            int i2= cc.surl.indexOf('=',i0);
            if ( i2>i1 ) i2=-1; // equals is from the next name=value pair.
            String delima= "";
            if ( context==CONTEXT_PARAMETER_NAME ) {
                if ( i2==-1 ) {
                    return cc.surl.substring(0,i0+1) + ccnew.completable + delima + cc.surl.substring( i1 );
                } else {
                    return cc.surl.substring(0,i0+1) + ccnew.completable + delima + cc.surl.substring( i2 );
                }
            } else {
                if ( i2==-1 ) {
                    return null;
                } else {
                    return cc.surl.substring(0,i2+1) + ccnew.completable + cc.surl.substring( i1 );
                }
            }
        } else {
            throw new IllegalArgumentException("invalid context");
        }
        
    }
    
    @Override
    public String toString() {
        return this.context + "=" + this.completable;
    }
}
