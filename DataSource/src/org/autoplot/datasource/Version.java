/*
 * Version.java
 *
 * Created on May 30, 2007, 6:59 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.autoplot.datasource;

/**
 *
 * @author jbf
 */
public class Version {

    /**
     * Version of the DataSource library is {@value}.
     */
    public static final String version= "20070530.1" ;
    
    /* configuration properties */
    
    /**
     * System.getProperty(PROP_ENABLE_REFERENCE_CACHE)=
     * "true" means the reference cache should be used. 
     */
    public static final String PROP_ENABLE_REFERENCE_CACHE= "enableReferenceCache";
    
    
    /**
     * System.getProperty(PROP_ENABLE_LOG_EXCEPTIONS)=
     * "true" means runtime exceptions should be logged.
     */
    public static final String PROP_ENABLE_LOG_EXCEPTIONS="enableLogExceptions";
    
    /**
     * System.getProperty(PROP_ENABLE_CLEAN_CACHE)=
     * "true" means old files in the cache should be removed
     */
    public static final String PROP_ENABLE_CLEAN_CACHE="enableCleanCache";
    
    
    /** Creates a new instance of Version */
    private Version() {
    }
    
}
