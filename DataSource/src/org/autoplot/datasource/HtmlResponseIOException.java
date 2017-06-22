/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.datasource;

import java.io.IOException;
import java.net.URL;

/**
 * Allow special exception to be thrown when Html source code is returned when
 * something else was expected.  This might happen when:
 *  1. a file reference on autoplot.org is not found, and mediawiki returns an human-readable error page instead of a 404 page.
 *  2. a hotel or coffee house login screen is returned until the user logs in.
 * We can look for this exception type, and present the page instead of a confusing error.
 * @author jbf
 */
public class HtmlResponseIOException extends IOException {
    private URL url;

    /**
     * @param s a human readable message
     * @param url a URL, or null.
     */
    public HtmlResponseIOException( String s, URL url ) {
        super( s );
        this.url= url;
    }

    public URL getURL() {
        return this.url;
    }

}
