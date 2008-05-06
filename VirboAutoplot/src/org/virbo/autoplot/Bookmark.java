/*
 * Bookmark.java
 *
 * Created on November 9, 2007, 10:32 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.virbo.autoplot;

import java.io.BufferedReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.InputSource;

/**
 *
 * @author jbf
 */
public class Bookmark {

    public static List<Bookmark> parseBookmarks(Document doc) {
        Element root = doc.getDocumentElement();

        ArrayList<Bookmark> result = new ArrayList<Bookmark>();
        NodeList list = root.getElementsByTagName("bookmark");
        for (int i = 0; i < list.getLength(); i++) {
            Element n = (Element) list.item(i);
            NodeList nl;
            nl = n.getElementsByTagName("title");
            String url = null;
            String s = null;
            String title = null;
            try {
                s = ((Text) (nl.item(0).getFirstChild())).getData();
                title = URLDecoder.decode(s, "UTF-8");
                nl = n.getElementsByTagName("url");
                s = ((Text) (nl.item(0).getFirstChild())).getData();
                url = URLDecoder.decode(s, "UTF-8");
            } catch (NullPointerException ex) {
                System.err.println("## bookmark number=" + i);
                ex.printStackTrace();
                continue;
            } catch (UnsupportedEncodingException ex) {
                System.err.println("## bookmark number=" + i);
                System.err.println("offending string: " + s);
                ex.printStackTrace();
                continue;
            } catch (DOMException ex) {
                System.err.println("## bookmark number=" + i);
                System.err.println("offending string: " + s);
                ex.printStackTrace();
                continue;
            } catch (IllegalArgumentException ex) {
                System.err.println("## bookmark number=" + i);
                System.err.println("offending string: " + s);
                ex.printStackTrace();
                continue;
            }
            Bookmark book = new Bookmark(url);
            book.setTitle(title);
            result.add(book);
        }

        return result;
    }
    ;

    public static String formatBooks(List<Bookmark> bookmarks) {
        try {
            StringBuffer buf = new StringBuffer();

            buf.append("<bookmark-list>\n");
            for (Bookmark b : bookmarks) {
                buf.append("  <bookmark>\n");
                buf.append("     <title>" + URLEncoder.encode(b.getTitle(), "UTF-8") + "</title>\n");
                buf.append("     <url>" + URLEncoder.encode(b.getUrl(), "UTF-8") + "</url>\n");
                buf.append("  </bookmark>\n");
            }
            buf.append("</bookmark-list>\n");

            return buf.toString();
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws Exception {
        String data = "<!-- note title is not supported yet --><bookmark-list>    <bookmark>        <title>demo autoplot</title>        <url>http://autoplot.org/autoplot.vap</url>    </bookmark>    <bookmark>        <title>Storm Event</title>        <url>http://cdaweb.gsfc.nasa.gov/cgi-bin/opendap/nph-dods/istp_public/data/genesis/3dl2_gim/2003/genesis_3dl2_gim_20030501_v01.cdf.dds?Proton_Density</url>    </bookmark></bookmark-list>";

        Reader in = new BufferedReader( new StringReader( data ) );
        
        DocumentBuilder builder;
        builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        InputSource source = new InputSource(in);
        Document document = builder.parse(source);

        System.err.println(parseBookmarks(document));
    }

    /** Creates a new instance of Bookmark */
    public Bookmark(String surl) {
        this.url = surl;
        this.title = surl;
    }

    public String toString() {
        return this.title;
    }
    /**
     * Holds value of property title.
     */
    private String title;
    /**
     * Utility field used by bound properties.
     */
    private java.beans.PropertyChangeSupport propertyChangeSupport = new java.beans.PropertyChangeSupport(this);

    /**
     * Adds a PropertyChangeListener to the listener list.
     * @param l The listener to add.
     */
    public void addPropertyChangeListener(java.beans.PropertyChangeListener l) {
        propertyChangeSupport.addPropertyChangeListener(l);
    }

    /**
     * Removes a PropertyChangeListener from the listener list.
     * @param l The listener to remove.
     */
    public void removePropertyChangeListener(java.beans.PropertyChangeListener l) {
        propertyChangeSupport.removePropertyChangeListener(l);
    }

    /**
     * Getter for property title.
     * @return Value of property title.
     */
    public String getTitle() {
        return this.title;
    }

    /**
     * Setter for property title.
     * @param title New value of property title.
     */
    public void setTitle(String title) {
        String oldTitle = this.title;
        this.title = title;
        propertyChangeSupport.firePropertyChange("title", oldTitle, title);
    }
    /**
     * Holds value of property url.
     */
    private String url;

    /**
     * Getter for property url.
     * @return Value of property url.
     */
    public String getUrl() {
        return this.url;
    }

    /**
     * Setter for property url.
     * @param url New value of property url.
     */
    public void setUrl(String url) {
        String oldUrl = this.url;
        this.url = url;
        propertyChangeSupport.firePropertyChange("url", oldUrl, url);
    }

    public boolean equals(Object obj) {
        if (obj instanceof Bookmark) {
            return ((Bookmark) obj).url.equals(this.url);
        } else {
            return false;
        }
    }

    public int hashCode() {
        return url.hashCode();
    }
}
