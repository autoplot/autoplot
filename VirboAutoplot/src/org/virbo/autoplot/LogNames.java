package org.virbo.autoplot;

/**
 * Useful Constants for retrieving loggers, to be used like so: Logger.getLogger(LogNames.AUTOPLOT).  This
 * is intended to encourage use of standard names, and minimize the number of log channels.
 * See http://www.autoplot.org/developer.logging .
 * @author jbf
 */
public class LogNames {

    public static final String AUTOPLOT= "autoplot";

    public static final String AUTOPLOT_DOM= "autoplot.dom";

    public static final String AUTOPLOT_BOOKMARKS= "autoplot.bookmarks";

    public static final String AUTOPLOT_PNGWALK= "autoplot.pngwalk";

    /**
     * TimeSeriesBrowse handling
     */
    public static final String AUTOPLOT_TSB= "autoplot.tsb";

}
