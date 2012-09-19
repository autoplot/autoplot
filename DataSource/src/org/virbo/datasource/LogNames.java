/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.datasource;

/**
 * Useful Constants for retrieving loggers, to be used like so: Logger.getLogger(LogNames.APDSS).  This
 * is intended to encourage use of standard names, and minimize the number of log channels.
 * See http://www.autoplot.org/developer.logging .
 * @author jbf
 */
public class LogNames {

    public static final String APDSS= "apdss";

    public static final String APDSS_UPDATING= "apdss.updating";

    public static final String APDSS_CDAWEB= "apdss.cdaweb";

    /**
     * native CDF logger
     */
    public static final String APDSS_CDFN= "apdss.cdfn";

    /**
     * pure-Java CDF logger
     */
    public static final String APDSS_CDFJAVA= "apdss.cdfjava";

    /**
     * HTML tables reader.
     */
    public static final String APDSS_HTML= "apdss.html";

    public static final String APDSS_ASCII= "apdss.ascii";

    public static final String APDSS_JYDS= "apdss.jyds";

    /**
     * Comma separated values reader.
     */
    public static final String APDSS_CSV= "apdss.csv";

    /*
     * data set selector
     */
    public static final String APDSS_DSS= "apdss.dss";

    public static final String APDSS_URI= "apdss.uri";


}
