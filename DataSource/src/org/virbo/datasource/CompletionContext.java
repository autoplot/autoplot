/*
 * CompletionContext.java
 *
 * Created on November 10, 2007, 6:01 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.virbo.datasource;

import java.net.URI;

/**
 * models a part of a dataset's URI.  This class is used to serve as both the
 * input and output of completion engines.  An incomplete completion context is
 * passed in, and the engine returns a set of more complete contexts.  This 
 * process could be repeated to define a tree, where the leaf nodes are valid 
 * datasets urls.
 * @author jbf
 */
public class CompletionContext {
    
    public final static Object CONTEXT_AUTOPLOT_SCHEME="vap";
    /**
     * file resource, which is a URL and doesn't include AP Scheme.
     */
    public final static Object CONTEXT_FILESYSTEM="fs"; 
    /**
     * file resource, which is a URL and doesn't include AP Scheme.
     */
    public final static Object CONTEXT_FILE="file"; 
    public final static Object CONTEXT_PARAMETER_NAME="paramName";
    public final static Object CONTEXT_PARAMETER_VALUE="paramValue";

    public CompletionContext() {
    }
    
    public CompletionContext( Object context, String completable ) {
        this( context, completable, null, null, null ,null );
    }
    
    public CompletionContext( Object context, String completable, DataSourceFactory owner, String implicitName, String label, String doc ) {
        this( context, completable, owner, implicitName, label, doc, false );
    }
    
    public CompletionContext( Object context, String completable, DataSourceFactory owner, String implicitName ) {
        this( context, completable, owner, implicitName, null, null, false );
    }
    
    public CompletionContext( Object context, String completable, String doc ) {
        this( context, completable, null, null, null, doc, false );
    }

    public CompletionContext( Object context, String completable, String label, String doc ) {
        this( context, completable, null, null, label, doc, false );
    }

    public CompletionContext( Object context, String completable, DataSourceFactory owner, String implicitName, String doc ) {
        this( context, completable, owner, implicitName, null, doc, false );
    }

    /**
     * 
     * @param context
     * @param completable
     * @param owner
     * @param implicitName
     * @param label label to use.
     * @param doc additional information that is shown in a tooltip.
     * @param maybePlot url should be valid if this proposal is accepted.
     */
    public CompletionContext( Object context, String completable, 
            DataSourceFactory owner, 
            String implicitName, 
            String label,
            String doc, 
            boolean maybePlot ) {
        this.context= context;
        this.completable= completable;
        this.implicitName= implicitName;
        this.label= label==null ? completable : label;
        this.doc= doc;
        this.maybePlot= maybePlot;
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
     * resource file URI, no params (and no "vap:" schema), starting with http, sftp, etc.
     */
    public URI resourceURI;
    
    /**
     * params parsed
     */
    public String params;
    
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
     * one-line documentation
     */
    public String doc;
    
    /**
     * label identifying the proposal, to be understood within the context of 
     * surl and the insertion point.  
     */
    public String label;
    
    /**
     * hint that this completion should finish a valid URL, so go ahead and try to use it.
     */
    public boolean maybePlot;
    
    /**
     * returns the value for the context
     * <pre>
     * {@code
     * cc= new CompletionContext();
     * cc.surl= vap:http://www.autoplot.org/data/myfile.dat?param1=aaa&param2=bbb
     * cc.completable= b
     * cc.surlpos= 63
     * get( CONTEXT_PARAMETER_NAME, cc ) ->  param2
     * get( CONTEXT_FILE, cc ) ->  http://www.autoplot.org/data/myfile.dat
     * }
     * </pre>
     */
    public static String get( Object context, CompletionContext cc ) {
        if ( context==CONTEXT_FILESYSTEM || context==CONTEXT_FILE ) {
            URISplit split= URISplit.parse( cc.surl );
            return split.file;
            
        } else if ( context==CONTEXT_PARAMETER_NAME || context==CONTEXT_PARAMETER_VALUE ) {
            int i0= cc.surl.lastIndexOf('&',cc.surlpos-1);
            int i1= cc.surl.lastIndexOf('?',cc.surlpos);
            if ( i0==-1 && i1==-1 ) {
                // handle URIs that don't have file component like "vap+cdaweb:ds=a<C>"
                int i3= cc.surl.indexOf(":",4);
                if ( cc.surl.startsWith("vap") && i3>0 ) {
                    i0= i3;
                }
            } else {
                i0= Math.max( i0,i1 );
            }
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
            URISplit split= URISplit.parse( surl );
            split.file= ccnew.completable;
            return URISplit.format( split );
            
        } else if ( context==CONTEXT_PARAMETER_NAME || context==CONTEXT_PARAMETER_VALUE ) {
            
            int i0= cc.surl.lastIndexOf('&',cc.surlpos-1);
            int i1= cc.surl.lastIndexOf('?',cc.surlpos-1);
            if ( i0==-1 && i1==-1 ) {
                // handle URIs that don't have file component like "vap+cdaweb:ds=a<C>"
                int i3= cc.surl.indexOf(":",4);
                if ( cc.surl.startsWith("vap") && i3>0 ) {
                    i0= i3;
                }
            } else {
                i0= Math.max( i0,i1 );
            }
            if ( i0==-1 ) return null;
            i1= cc.surl.indexOf('&',i0+1);
            if ( i1==-1 ) i1= cc.surl.length();
            int i2= cc.surl.indexOf('=',i0);
            if ( i2>i1 ) i2=-1; // equals is from the next name=value pair.
            String delima= "";
            if ( context==CONTEXT_PARAMETER_NAME ) {
                String completable= ccnew.completable;
                String after;
                if ( i2==-1 ) {
                    after= cc.surl.substring( i1 );
                } else {
                    after= cc.surl.substring( i2 );
                }
                if ( completable.endsWith("=") && delima.length()==0 && after.startsWith("=") ) {
                    completable= completable.substring(0,completable.length()-1);
                }
                if ( i2==-1 ) {
                    return cc.surl.substring(0,i0+1) + completable + delima + cc.surl.substring( i1 );
                } else {
                    return cc.surl.substring(0,i0+1) + completable + delima + cc.surl.substring( i2 );
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
