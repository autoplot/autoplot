/*
 * Bookmark.java
 *
 * Created on November 9, 2007, 10:32 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.virbo.autoplot.bookmarks;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.das2.util.Base64;
import org.das2.util.monitor.NullProgressMonitor;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSourceUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *
 * @author jbf
 */
public abstract class Bookmark {

    public static final String MSG_NO_REMOTE= "(remote not available)";
    public static final String MSG_REMOTE= "(remote)";

    public static List<Bookmark> parseBookmarks(String data) throws SAXException, IOException {
        try {

            Reader in = new BufferedReader(new StringReader(data));

            DocumentBuilder builder;
            builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            InputSource source = new InputSource(in);
            Document document = builder.parse(source);

            return parseBookmarks(document.getDocumentElement());
        } catch (ParserConfigurationException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static Bookmark parseBookmark(String data) throws SAXException, IOException {
        try {

            Reader in = new BufferedReader(new StringReader(data));

            DocumentBuilder builder;
            builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            InputSource source = new InputSource(in);
            Document document = builder.parse(source);

            return parseBookmark(document.getDocumentElement());
        } catch (ParserConfigurationException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static Bookmark parseBookmark(Node element) throws UnsupportedEncodingException, IOException {

        String url = null;
        String s = null;
        String title = null;
        ImageIcon icon = null;
        String description= null;

        NodeList nl;
        nl = ((Element) element).getElementsByTagName("title");
        if (nl.getLength()>0 ) {
            if ( !nl.item(0).hasChildNodes() ) throw new IllegalArgumentException("bookmark has empty title");
            s = ((Text) (nl.item(0).getFirstChild())).getData();
            title = URLDecoder.decode(s, "UTF-8");
        } else {
            throw new IllegalArgumentException("bookmark has no title");
        }

        nl = ((Element) element).getElementsByTagName("icon");
        if (nl.getLength() > 0) {
            s = ((Text) (nl.item(0).getFirstChild())).getData();
            icon = new ImageIcon(decodeImage(s));
        }

        nl = ((Element) element).getElementsByTagName("description");
        if (nl.getLength() > 0) {
            s = ((Text) (nl.item(0).getFirstChild())).getData();
            description = URLDecoder.decode(s, "UTF-8");
        }
        
        if (element.getNodeName().equals("bookmark")) {

            nl = ((Element) element).getElementsByTagName("url");
            s = ((Text) (nl.item(0).getFirstChild())).getData();
            url = URLDecoder.decode(s, "UTF-8");
            Bookmark book = new Bookmark.Item(url);
            book.setTitle(title);
            if ( icon!=null ) book.setIcon(icon);
            if ( description!=null ) book.setDescription(description);
            return book;

        } else if (element.getNodeName().equals("bookmark-folder")) {

            List<Bookmark> contents = null;

            Node remoteUrlNode= ((Element)element).getAttributes().getNamedItem("remoteUrl");
            String remoteUrl= null;
            if ( remoteUrlNode!=null ) { // 2984078
                remoteUrl= URLDecoder.decode( remoteUrlNode.getNodeValue(), "US-ASCII" );
                InputStream in=null;
                try {
                    System.err.println("opening "+remoteUrl+"...");
                    URL rurl= new URL(remoteUrl);
                    URLConnection connect= rurl.openConnection();
                    connect.setConnectTimeout(1000);
                    connect.setReadTimeout(1000);

                    // copy remote file to local string, so we can check content type.  Autoplot.org always returns 200 okay, even if file doesn't exist.
                    in = DataSetURI.getInputStream( rurl.toURI(), new NullProgressMonitor() );
                    ByteArrayOutputStream boas=new ByteArrayOutputStream();
                    WritableByteChannel dest = Channels.newChannel(boas);
                    ReadableByteChannel src = Channels.newChannel(in);
                    DataSourceUtil.transfer(src, dest);
                    in.close();
                    in= null; // don't close it again.

                    String sin= new String( boas.toByteArray() );

                    if ( !sin.startsWith("<book") && !sin.startsWith("<?xml") ) {
                        System.err.println("not a bookmark xml file: "+rurl );
                        throw new IllegalArgumentException("not a bookmark xml file: "+rurl );
                    }
                    
                    DocumentBuilder builder;
                    builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                    InputSource source = new InputSource( new StringReader(sin) );
                    Document document = builder.parse(source);

                    XPathFactory factory= XPathFactory.newInstance();

                    XPath xpath= (XPath) factory.newXPath();
                    Object o= xpath.evaluate( "/bookmark-list/bookmark-folder/bookmark-list", document, XPathConstants.NODESET );
                    nl= (NodeList)o;
                    //nl = ((Element) document.getDocumentElement()).getElementsByTagName("bookmark-list");
                    Element flist = (Element) nl.item(0);
                    contents = parseBookmarks(flist);

                } catch (URISyntaxException ex ) {
                    ex.printStackTrace();
                } catch (XPathExpressionException ex) {
                    Logger.getLogger(Bookmark.class.getName()).log(Level.SEVERE, null, ex);
                } catch (SAXException ex) {
                    Logger.getLogger(Bookmark.class.getName()).log(Level.SEVERE, null, ex);
                    ex.printStackTrace();
                } catch (ParserConfigurationException ex) {
                    Logger.getLogger(Bookmark.class.getName()).log(Level.SEVERE, null, ex);
                    ex.printStackTrace();
                } catch ( IllegalArgumentException ex ) {
                    ex.printStackTrace();
                } catch ( FileNotFoundException ex ) {
                    ex.printStackTrace();
                } catch ( IOException ex ) {
                    ex.printStackTrace();
                } finally {
                    if ( in!=null ) {
                        try {
                            in.close();
                        } catch ( IOException ex ) {
                            ex.printStackTrace();
                        }
                    }
                }
                if ( contents==null || contents.size()==0 ) {
                    System.err.println("unable to parse bookmarks at "+remoteUrl);
                    System.err.println("Maybe using local copy");
                    title= title + " "+MSG_NO_REMOTE;
                } else {
                    title= title + " "+MSG_REMOTE;
                }
            } else {

            }
            
            if ( contents==null || contents.size()==0 ) {
                nl = ((Element) element).getElementsByTagName("bookmark-list");
                Element flist = (Element) nl.item(0);
                contents = parseBookmarks(flist);
            }

            Bookmark.Folder book = new Bookmark.Folder(title);
            if ( icon!=null ) book.setIcon(icon);
            if ( remoteUrl!=null ) book.setRemoteUrl(remoteUrl);
            if ( description!=null ) book.setDescription(description);

            book.getBookmarks().addAll(contents);
            for ( int i=0; i<contents.size(); i++ ) {
                contents.get(i).setParent(book);
            }
            return book;
            
        } else {
            return null;

        }

    }

    public static List<Bookmark> parseBookmarks(Element root) {
        ArrayList<Bookmark> result = new ArrayList<Bookmark>();
        NodeList list = root.getChildNodes();
        Bookmark lastBook=null;
        for (int i = 0; i < list.getLength(); i++) {
            Node n = list.item(i);
            if ( ! ( n instanceof Element ) ) continue;
            try {
                Bookmark book = parseBookmark(n);
                result.add(book);
                lastBook= book;
            } catch (Exception ex) {
                System.err.println("## bookmark number=" + i);
                ex.printStackTrace();
                System.err.println("last bookmark parsed:"+lastBook);
                continue;
            }

        }

        return result;
    }

    /**
     * format the bookmarks into xml for persistent storage.
     * @param bookmarks List of Bookmark.List or Bookmark
     * @return
     */
    public static String formatBooks(List<Bookmark> bookmarks) {
        StringBuilder buf = new StringBuilder();

        buf.append("<bookmark-list>\n");
        for (Bookmark o : bookmarks) {
            buf.append( formatBookmark(o) );
        }
        buf.append("</bookmark-list>\n");
        return buf.toString();

    }

    private static String encodeImage(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write( image, "png", out );
        out.size();
        return Base64.encodeBytes(out.toByteArray());
    }

    private static BufferedImage decodeImage(String data) throws IOException {
        byte[] bd = Base64.decode(data);
        return ImageIO.read(new ByteArrayInputStream(bd));
    }

    /**
     * format the bookmarks into xml for persistent storage.
     * @param bookmarks List of Bookmark.List or Bookmark
     * @return
     */
    public static String formatBookmark(Bookmark bookmark) {

        try {
            StringBuilder buf = new StringBuilder();

            if (bookmark instanceof Bookmark.Item) {
                if ( bookmark.title.contains("PO_H0_TIM") ) {
                    System.err.println("here307");
                }
                Bookmark.Item b = (Bookmark.Item) bookmark;
                buf.append("  <bookmark>\n");
                buf.append("     <title>").append(URLEncoder.encode(b.getTitle(), "UTF-8")).append("</title>\n");
                if (b.icon != null) buf.append("     <icon>").append(encodeImage((BufferedImage) b.icon.getImage())).append("</icon>\n");
                if (b.description != null) buf.append("     <description>").append( URLEncoder.encode(b.getDescription(), "UTF-8")).append("</description>\n");
                buf.append("     <url>").append(URLEncoder.encode(b.getUrl(), "UTF-8")).append("</url>\n");
                buf.append("  </bookmark>\n");
            } else if (bookmark instanceof Bookmark.Folder) {
                Bookmark.Folder f = (Bookmark.Folder) bookmark;
                String title= f.getTitle();
                if ( f.getRemoteUrl()!=null ) {
                    if ( title.endsWith(" " + MSG_REMOTE) ) title= title.substring(0,title.length()-(1+MSG_REMOTE.length()));
                    if ( title.endsWith(" " + MSG_NO_REMOTE ) ) title= title.substring(0,title.length()-(1+MSG_NO_REMOTE.length()));
                    buf.append("  <bookmark-folder remoteUrl=\"").append(URLEncoder.encode(f.getRemoteUrl(), "UTF-8")).append("\">\n");
                } else {
                    buf.append("  <bookmark-folder>\n");
                }
                buf.append("    <title>").append(URLEncoder.encode(title, "UTF-8")).append("</title>\n");
                if (f.icon != null) buf.append("     <icon>").append(encodeImage((BufferedImage) f.icon.getImage())).append("</icon>\n");
                if (f.description != null) buf.append("     <description>").append( URLEncoder.encode(f.getDescription(), "UTF-8")).append("</description>\n");
                buf.append(formatBooks(f.getBookmarks()));
                buf.append("  </bookmark-folder>\n");
            }
            return buf.toString();
        } catch (UnsupportedEncodingException ex) {
            throw new RuntimeException(ex); //this shouldn't happen
        } catch (IOException ex) {
            throw new RuntimeException(ex); //this shouldn't happen
        }

    }

    public static void main(String[] args) throws Exception {
        String data = "<!-- note title is not supported yet --><bookmark-list>    <bookmark>        <title>demo autoplot</title>        <url>http://autoplot.org/autoplot.vap</url>    </bookmark>    <bookmark>        <title>Storm Event</title>        <url>http://cdaweb.gsfc.nasa.gov/cgi-bin/opendap/nph-dods/istp_public/data/genesis/3dl2_gim/2003/genesis_3dl2_gim_20030501_v01.cdf.dds?Proton_Density</url>    </bookmark></bookmark-list>";

        Reader in = new BufferedReader(new StringReader(data));

        DocumentBuilder builder;
        builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        InputSource source = new InputSource(in);
        Document document = builder.parse(source);

        System.err.println(parseBookmarks(document.getDocumentElement()));
    }

    private Bookmark(String title) {
        this.title = title;
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
    protected java.beans.PropertyChangeSupport propertyChangeSupport = new java.beans.PropertyChangeSupport(this);

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

    public String getTitle() {
        return this.title;
    }

    public void setTitle(String title) {
        String oldTitle = this.title;
        this.title = title;
        propertyChangeSupport.firePropertyChange("title", oldTitle, title);
    }


    protected String description;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        String oldValue= this.description;
        this.description = description;
        propertyChangeSupport.firePropertyChange( "description", oldValue, description );
    }


    protected ImageIcon icon = null;
    public static final String PROP_ICON = "icon";

    public ImageIcon getIcon() {
        return icon;
    }

    public void setIcon(ImageIcon icon) {
        Icon oldIcon = this.icon;
        this.icon = icon;
        propertyChangeSupport.firePropertyChange(PROP_ICON, oldIcon, icon);
    }

    public static final String PROP_PARENT= "parent";
    private Bookmark.Folder parent= null;

    public Bookmark.Folder getParent() {
        return parent;
    }

    public void setParent( Bookmark.Folder parent ) {
        Bookmark.Folder old= this.parent;
        this.parent= parent;
        propertyChangeSupport.firePropertyChange( PROP_PARENT, old, parent );
    }

    public abstract Bookmark copy();

    public static class Folder extends Bookmark {

        List<Bookmark> bookmarks;

        /**
         * a remote bookmark is one that is a copy of a folder at the remote
         * location.  If it's a remote folder, then we use it to maintain the
         * bookmarks.  We'll keep a local copy, but this may be updated.
         */
        String remoteUrl= null;

        public void setRemoteUrl( String url ) {
            this.remoteUrl= url;
        }

        public String getRemoteUrl( ) {
            return this.remoteUrl;
        }

        public Folder(String title) {
            super(title);
            bookmarks = new ArrayList<Bookmark>();
        }

        public Folder( String title, String remoteUrl ) {
            super(title);
            this.remoteUrl= remoteUrl;
            bookmarks = new ArrayList<Bookmark>();
        }

        /**
         * return the bookmarks, the mutable internal store.  use copy() to
         * get a deep copy.
         *
         * @return
         */
        public List<Bookmark> getBookmarks() {
            return bookmarks;
        }

        @Override
        public int hashCode() {
            return bookmarks.hashCode() + ( remoteUrl!=null ? remoteUrl.hashCode() : 0 );
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Bookmark.Folder) {
                Bookmark.Folder that= (Bookmark.Folder) obj;
                return that.bookmarks.equals(this.bookmarks)
                        && ( that.getTitle().equals(this.getTitle()) )
                        && ( that.getParent()==null || that.getParent().getTitle().equals(this.getParent().getTitle()) );
            } else {
                return false;
            }
        }

        public Bookmark copy() {
            Bookmark.Folder result = new Bookmark.Folder(getTitle());
            result.description= this.description;
            result.remoteUrl= remoteUrl;
            result.bookmarks = new ArrayList<Bookmark>(this.bookmarks);
            return result;
        }
    }

    public static class Item extends Bookmark {

        /** Creates a new instance of Bookmark */
        public Item(String surl) {
            super(surl);
            this.url = surl;
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

        @Override
        public int hashCode() {
            return url.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Bookmark.Item) {
                Bookmark.Item that= (Bookmark.Item)obj;
                return that.url.equals(this.url)
                        && (that.getParent()==null || this.getParent()==null || that.getParent().getTitle().equals(this.getParent().getTitle()) );
            } else {
                return false;
            }
        }

        public Bookmark copy() {
            Bookmark.Item result = new Bookmark.Item(getUrl());
            result.setTitle(getTitle());
            result.description= this.description;
            return result;
        }
    }
}
