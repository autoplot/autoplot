/*
 * UserDocumentationItem.java
 *
 * Created on August 3, 2006, 10:27 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.das2.jythoncompletion;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Action;
import org.das2.jythoncompletion.support.CompletionDocumentation;

/**
 *
 * @author jbf
 */
public class DefaultDocumentationItem implements CompletionDocumentation {

    private static final Logger logger= Logger.getLogger("jython.editor");

    String link;
    String text;

    /** Creates a new instance of UserDocumentationItem */
    public DefaultDocumentationItem(String link) {
        this( link, null );
    }

    public DefaultDocumentationItem(String link,String text) {
        this.link = link;
        this.text = text;
    }

    public String getText() {
        if ( text!=null ) {
            return text;
        }
        URL url = getURL();
        if (url == null) {
            return "<html>unable to resolve link: <br>"+link+"</html>";
        } else {
            return null;
        }
        /* keep the following code in case we can improve performance by clipping
         * out the documentation.
         BufferedReader in = null;
        try {
            StringBuffer buf = new StringBuffer();
            in = new BufferedReader(new InputStreamReader(url.openStream()));
            String s = in.readLine();
            while (s != null) {
                buf.append(s + "\n");
                s = in.readLine();
            }
            return buf.toString();
        } catch (IOException ex) {
            ex.printStackTrace();
            return "I/O Error while reading link";
        } finally {
            try {
                in.close();
            } catch (IOException ex) {
                Logger.getLogger(DefaultDocumentationItem.class.getName()).log(Level.SEVERE, null, ex);
            }
        }*/
    }

    public URL getURL() {
        URL result= resolveURL(this.link);
        logger.log(Level.FINE, "getURL={0}", result);
        return result;
    }

    private static URL resolveURL(String link) {
        if (link == null) {
            return null;
        }
        if (link.contains("://")) {
            try {
                return new URL(link);
            } catch (MalformedURLException ex) {
                logger.severe(ex.toString());
                return null;
            }
        } else {
            URL url;
            int i= link.indexOf("#");
            if ( i==-1 ){
                url= DefaultDocumentationItem.class.getResource(link);
            } else {
                url= DefaultDocumentationItem.class.getResource(link.substring(0,i));
                try {
                    url = new URL(url, link.substring(i) );
                } catch (MalformedURLException ex) {
                    logger.severe(ex.toString());
                }
            }
            return url;
        }
    }

    public CompletionDocumentation resolveLink(String string) {
        try {
            //TODO: make sure this works when the server is down.
            URL url = new URL(new URL(link), string);
            return new DefaultDocumentationItem(url.toString());
        } catch (MalformedURLException ex) {
            return null;
        }
    }

    public Action getGotoSourceAction() {
        return null;
    }
}
