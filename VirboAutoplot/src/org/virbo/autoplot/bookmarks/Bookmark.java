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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
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
import org.das2.DasApplication;
import org.das2.util.Base64;
import org.das2.util.filesystem.FileObject;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.filesystem.FileSystemUtil;
import org.das2.util.filesystem.WebFileSystem;
import org.das2.util.monitor.NullProgressMonitor;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.DataSourceUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *
 * @author jbf
 */
public abstract class Bookmark {

    public static final String TITLE_ERROR_OCCURRED = "Error Occurred";
    public static final String MSG_NO_REMOTE= "[remote*]";
    public static final String TOOLTIP_NO_REMOTE = "Using cached version because <br>remote folder based on contents of remote URL <br>%{URL}<br>which is not available. ";
    public static final String MSG_REMOTE= "";
    public static final String TOOLTIP_REMOTE = "Bookmark folder based on contents of remote URL <br>%{URL}";
    public static final String MSG_NOT_LOADED= "[loading...]";
    public static final String TOOLTIP_NOT_LOADED = "Checking to see if remote bookmark list has changed.<br>%{URL}";

    private static final Logger logger= Logger.getLogger("autoplot.bookmarks");

    public static List<Bookmark> parseBookmarks(String data) throws SAXException, IOException {
        return parseBookmarks(data,0);
    }

    public static List<Bookmark> parseBookmarks(String data,int depth) throws SAXException, IOException {
        try {

            Reader in = new BufferedReader(new StringReader(data));

            DocumentBuilder builder;
            builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            InputSource source = new InputSource(in);
            Document document = builder.parse(source);

            List<Bookmark> books= parseBookmarks(document.getDocumentElement(),depth);
            return books;
        } catch (ParserConfigurationException ex) {
            throw new RuntimeException(ex);
        }
    }


    /**
     * read in the bookmarks file, which should be an xml file with the top node <bookmark-list>.
     * @param url local or remote file.
     * @return
     * @throws SAXException
     * @throws IOException
     */
    public static List<Bookmark> parseBookmarks( URL url ) throws SAXException, IOException {
        try {

            File file= DataSetURI.downloadResourceAsTempFile( url, new NullProgressMonitor() );

            Reader in = new BufferedReader( new FileReader(file) );

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

            String vers= document.getDocumentElement().getAttribute("version");

            return parseBookmark( document.getDocumentElement(),vers,1 );
        } catch (ParserConfigurationException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * check to see if there are remote nodes that still need to be resolved
     * @param contents
     * @return
     */
    private static boolean checkForUnresolved( List<Bookmark> contents ) {
        for ( Bookmark book: contents ) {
            if ( book instanceof Bookmark.Folder ) {
                Bookmark.Folder bf= (Bookmark.Folder) book;
                if ( bf.remoteStatus==Bookmark.Folder.REMOTE_STATUS_NOT_LOADED ) return true;
                if ( checkForUnresolved( bf.getBookmarks() ) ) { //TODO: no check for cycles
                    return true;
                }
            }
        }
        return false;
    }
    /**
     * read in a remote bookmarks file.
     * @param remoteUrl the location of the file.
     * @param remoteLevel limit the depth of remote bookmarks.  For example, remoteLevel=1 indicates this should be read but no remote bookmarks ought to be read.
     * @param startAtRoot if true, then include the root nodes, otherwise return the contents of the folders.
     * @param contents list where the bookmarks should be stored.
     * @return true if there is a remote bookmark within the file.
     */
    protected static RemoteStatus getRemoteBookmarks( String remoteUrl, int remoteLevel, boolean startAtRoot, List<Bookmark> contents ) {
        InputStream in=null;
        boolean remoteRemote= false;

        boolean offline= false;

        RemoteStatus result= new RemoteStatus();
        
        try {
            URL rurl= new URL(remoteUrl);
  
            NodeList nl;

            // Copy remote file to local string, so we can check content type.  Autoplot.org always returns 200 okay, even if file doesn't exist.
            // See if the URI is file-like, not containing query parameters, in which case we allow caching to occur.
            try {
                URI ruri= rurl.toURI();
                URI parentUri= FileSystemUtil.isCacheable( ruri );
                if ( parentUri!=null ) {
                    FileSystem fd= FileSystem.create(parentUri);
                    if ( fd instanceof WebFileSystem ) {
                        offline= ((WebFileSystem)fd).isOffline();
                    }
                    FileObject fo= fd.getFileObject( parentUri.relativize(ruri).toString() );
                    if ( !fo.exists() && fd.getFileObject( fo.getNameExt()+".gz" ).exists() ) {
                        fo= fd.getFileObject( fo.getNameExt()+".gz" );
                        in= new GZIPInputStream( fo.getInputStream() );
                    } else {
                        if ( remoteUrl.endsWith(".gz" ) ) {
                            in= new GZIPInputStream( fo.getInputStream() );
                        } else {
                            in= fo.getInputStream();
                        }
                    }
                } else {
                    in = new FileInputStream( DataSetURI.downloadResourceAsTempFile( rurl, 3600000, new NullProgressMonitor()) );
                }
            } catch ( URISyntaxException ex ) {
                in = new FileInputStream( DataSetURI.downloadResourceAsTempFile( rurl, 3600000, new NullProgressMonitor()) );
            }

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
            Object o;
            if ( startAtRoot ) {
                o= xpath.evaluate( "/bookmark-list", document, XPathConstants.NODESET );
            } else {
                o= xpath.evaluate( "/bookmark-list/bookmark-folder/bookmark-list", document, XPathConstants.NODESET );
            }
            nl= (NodeList)o;

            //TODO: the roots can contain a remoteUrl node, which we must ignore.  Clear it before looking for remoteUrl deeper in the tree.
            NodeList bfs= (NodeList)xpath.evaluate( "/bookmark-list/bookmark-folder", document, XPathConstants.NODESET );
            for ( int i= 0; i<bfs.getLength(); i++ ) {
                Element bf= (Element)bfs.item(i);
                String url= bf.getAttribute("remoteUrl");
                if ( url.contains("%3A%2F%2F") ) {
                    url= URLDecoder.decode(url,"US-ASCII");
                }
                if ( url.equals(remoteUrl) ) {
                    bf.removeAttribute( "remoteUrl" );
                }
            }

            Element flist = (Element) nl.item(0); // the bookmark list.
            if ( flist==null ) {
                // The remote folder itself can contain remote folders,
                //String remoteUrl2= (String)xpath.evaluate( "/bookmark-list/bookmark-folder/@remoteUrl", document, XPathConstants.STRING );
                String remoteUrl2= (String)xpath.evaluate( "//bookmark-list/bookmark-folder/@remoteUrl", document, XPathConstants.STRING );
                if ( remoteUrl2.length()>0 ) {
                    logger.log(Level.FINE, "another remote folder: {0} at {1}", new Object[]{remoteUrl2, remoteLevel});
                    remoteRemote= true; // avoid warning
                }
            } else {
                String remoteUrl2= (String)xpath.evaluate( "//bookmark-list/bookmark-folder/@remoteUrl", document, XPathConstants.STRING ); //TODO: verify that we can have remote in lower position.
                if ( remoteUrl2.length()>0 ) {
                    logger.log(Level.FINE, "another remote folder: {0} at {1}", new Object[]{remoteUrl2, remoteLevel});
                    remoteRemote= true; // avoid warning
                }
                String vers1= (String) xpath.evaluate("/bookmark-list/@version", document, XPathConstants.STRING );
                List<Bookmark> contents1 = parseBookmarks( flist, vers1, remoteLevel-1 );
                remoteRemote= checkForUnresolved(contents1);
                contents.addAll(contents1);
            }

        } catch (Exception ex) {
            Bookmark.Item err= new Bookmark.Item("");
            err.description= ex.toString();
            err.setTitle(TITLE_ERROR_OCCURRED); // note TITLE_ERROR_OCCURRED is used to detect this bookmark.
            try {
                err.setIcon( new ImageIcon( ImageIO.read( Bookmark.class.getResource( "/org/virbo/autoplot/resources/warning-icon.png" ) ) ) );
            } catch (IOException ex2) {
                ex.printStackTrace();
            }
            result.statusMsg= ex.toString();
            contents.add( err );
            offline= true;

        } finally {
            if ( in!=null ) {
                try {
                    in.close();
                } catch ( IOException ex ) {
                    ex.printStackTrace();
                }
            }
        }

        result.remoteURL= remoteUrl;
        result.depth= remoteLevel;
        result.remoteRemote= remoteRemote;
        result.status= offline ? Bookmark.Folder.REMOTE_STATUS_UNSUCCESSFUL : Bookmark.Folder.REMOTE_STATUS_SUCCESSFUL;
        return result;
    }

    /**
     * xml utility for getting the first child node with the given name.
     * @param element
     * @param name
     * @return null or the first child name with the given name.
     */
    private static Node getChildElement( Node element, String name ) {
        NodeList nl= element.getChildNodes();
        for ( int i=0; i<nl.getLength(); i++ ) {
            if ( nl.item(i).getNodeName().equals(name) ) {
                return nl.item(i);
            }
        }
        return null;
    }

    /**
     * parse the bookmarks in this node.
     * @param element
     * @param vers null, empty string &lt;2011, or version number
     * @param remoteLevel if &gt;0, then allow remote to be retrieved (this many levels)
     * @return Bookmark.  If it's a folder, then bookmark.remoteStatus can be used to determine if it needs to be reloaded.
     * @throws UnsupportedEncodingException
     * @throws IOException
     */
    public static Bookmark parseBookmark( Node element, String vers, int remoteLevel ) throws UnsupportedEncodingException, IOException {

        String uri = null; // read this first in case it's useful as the title
        String s = null;
        String title = null;
        ImageIcon icon = null;
        String description= null;
        URL descriptionUrl= null;
        boolean hidden= false;

        Node n;

        if ( element.getNodeName().equals("bookmark") ) {
            if ( vers.equals("") ) {
                n = getChildElement( element,"url" );
                s = ((Text) (n.getFirstChild())).getData();
                try {
                    uri = URLDecoder.decode(s, "UTF-8") ;
                } catch ( IllegalArgumentException ex ) {
                    throw new IllegalArgumentException( ex.getMessage() + "\nBookmarks file is unversioned, so URLs should be encoded");
                }
            } else {
                n = getChildElement( element,"uri" );
                if ( n==null ) {
                    n = getChildElement( element,"url");
                }
                if ( n.getFirstChild()!=null ) {
                    s = ((Text) (n.getFirstChild())).getData();
                    uri = s;
                } else {
                    uri = "???"; // we would have NullPointerException before...
                }
            }
        } else {
            uri= null;
        }

        n= getChildElement( element, "title" );
        if ( n!=null ) {
            if ( !n.hasChildNodes() ) {
                if ( uri==null ) {
                    title= "(untitled)";
                } else {
                    //System.err.println("Using URI for title because title is empty: "+uri );
                    title= uri;
                }
            } else {
                s = ((Text) (n.getFirstChild())).getData();
                try {
                    title = vers.equals("") ? URLDecoder.decode(s, "UTF-8") : s;
                } catch ( IllegalArgumentException ex ) {
                    throw new IllegalArgumentException( ex.getMessage() + "\nBookmarks file is unversioned, so titles should be encoded");
                }
            }
        } else {
            title= "(untitled)";
        }

        n = getChildElement( element,"icon");
        if ( n!=null ) {
            s = ((Text)n.getFirstChild()).getData();
            icon = new ImageIcon(decodeImage(s));
        } else {
            icon= null;
        }

        n = getChildElement( element,"description");
        if ( n!=null ) {
            Node child= n.getFirstChild();
            if ( child==null ) {
                description= "";
            } else {
                s = ((Text)child).getData();
                description = vers.equals("") ? URLDecoder.decode(s, "UTF-8") : s;
            }
        } else {
            description= "";
        }

        n = getChildElement( element, "description-url" );
        if ( n!=null ) {
            Node child= n.getFirstChild();
            if ( child==null ) {
                descriptionUrl= null;
            } else {
                s = ((Text)child).getData();
                try {
                    descriptionUrl = new URL( vers.equals("") ? URLDecoder.decode(s, "UTF-8") : s );
                } catch ( MalformedURLException ex ) {
                    descriptionUrl= null;
                }
            }
        } else {
            descriptionUrl= null;
        }
        
        n = getChildElement( element, "hidden" );
        if ( n!=null ) {
            Node child= n.getFirstChild();
            if ( child==null ) {
                hidden= false;
            } else {
                s = ((Text)child).getData();
                hidden= s.equals("true");
            }
        } else {
            hidden= false;
        }

        if (element.getNodeName().equals("bookmark")) {
            Bookmark book = new Bookmark.Item(uri);
            book.setTitle(title);
            if ( icon!=null ) book.setIcon(icon);
            if ( description!=null ) book.setDescription(description);
            if ( descriptionUrl!=null ) book.setDescriptionUrl( descriptionUrl );
            book.setHidden(hidden);
            return book;

        } else if (element.getNodeName().equals("bookmark-folder")) {

            List<Bookmark> contents = null;

            Node remoteUrlNode= ((Element)element).getAttributes().getNamedItem("remoteUrl");
            String remoteUrl= null;
            int remoteStatus=Bookmark.Folder.REMOTE_STATUS_NOT_LOADED;
            String remoteStatusMsg= "";
            RemoteStatus rs;

            if ( remoteUrlNode!=null ) { // 2984078

                remoteUrl= vers.equals("") ? URLDecoder.decode( remoteUrlNode.getNodeValue(), "UTF-8" ) : remoteUrlNode.getNodeValue();

                if ( remoteLevel>0 ) {
                    boolean waitAction= false;

                    if ( waitAction ) {

                    } else {
                        logger.finer( String.format( "Reading in remote bookmarks folder \"%s\" from %s", title, remoteUrl ) );

                        contents= new ArrayList();

                        rs= getRemoteBookmarks( remoteUrl, remoteLevel, false, contents );

                        if ( ( contents.size()==0 ) & !rs.remoteRemote ) {
                            System.err.println("unable to parse bookmarks at "+remoteUrl);
                            System.err.println("Maybe using local copy");
                            remoteStatus= Bookmark.Folder.REMOTE_STATUS_UNSUCCESSFUL;
                            remoteStatusMsg= contents.get(0).getDescription();
                        } else {
                            remoteStatus= rs.status;
                            remoteStatusMsg= rs.statusMsg;
                        }

                        if ( rs.remoteRemote ) {
                            remoteStatus= Bookmark.Folder.REMOTE_STATUS_NOT_LOADED;
                       }
                    }
                }
                
            } else {
                if ( remoteUrlNode==null ) remoteStatus= Bookmark.Folder.REMOTE_STATUS_SUCCESSFUL;
                
            }
            
            if ( ( remoteUrl==null ||
                    ( remoteStatus==Bookmark.Folder.REMOTE_STATUS_NOT_LOADED || remoteStatus==Bookmark.Folder.REMOTE_STATUS_UNSUCCESSFUL ) ) ) { // remote folders may have local copy be empty, but local folders must not.
                n= getChildElement( element,"bookmark-list");
                if ( n==null ) {
                    if ( remoteStatus== Bookmark.Folder.REMOTE_STATUS_NOT_LOADED || remoteStatus==Bookmark.Folder.REMOTE_STATUS_UNSUCCESSFUL ) { // only if a remote folder is not resolved is this okay
                        contents= Collections.emptyList();
                    } else {
                        throw new IllegalArgumentException("bookmark-folder should contain one bookmark-list");
                    }
                } else {
                    Element flist = (Element)n; // and they may only contain one folder
                    contents = parseBookmarks( flist, vers, remoteLevel );
                }
            }

            Bookmark.Folder book = new Bookmark.Folder(title);
            if ( icon!=null ) book.setIcon(icon);
            if ( remoteUrl!=null ) book.setRemoteUrl(remoteUrl);
            if ( description!=null ) book.setDescription(description);
            if ( descriptionUrl!=null ) book.setDescriptionUrl( descriptionUrl );
            book.setHidden(hidden);
            book.remoteStatus= remoteStatus;
            book.setRemoteStatusMsg( remoteStatusMsg );
            
            book.getBookmarks().addAll(contents);
            for ( int i=0; i<contents.size(); i++ ) {
                contents.get(i).setParent(book);
            }
            return book;
            
        } else {
            return null;

        }

    }

    public static List<Bookmark> parseBookmarks(Element root ) {
        String vers= root.getAttribute("version");
        return parseBookmarks( root, vers, 1 );
    }

    /**
     * parse the bookmarks, checking to see what version scheme should be used.
     * @param root the root node, from which the version scheme should be read
     * @param remoteLevel if >0, then allow remote to be retrieved (this many levels)
     * @return
     */
    public static List<Bookmark> parseBookmarks(Element root, int remoteLevel ) {
        logger.log(Level.FINE, "parseBookmarks {0}", remoteLevel);
        String vers= root.getAttribute("version");
        return parseBookmarks( root, vers, remoteLevel );
    }

    /**
     * parse the bookmarks in the element root into a list of folders and bookmarks.
     * The root element should be a bookmark-list containing <bookmark-folder> and <bookmark>
     * @param root
     * @param vers null or the version string.  If null, then check for a version attribute.
     * @return
     */
    public static List<Bookmark> parseBookmarks( Element root, String vers, int remoteLevel ) {
        if ( vers==null ) {
            vers= root.getAttribute("version");
        }
        if ( ! root.getNodeName().equals("bookmark-list") ) {
            throw new IllegalArgumentException( String.format( "Expected XML element to be \"bookmark-list\" not \"%s\"", root.getNodeName() ) );
        }
        ArrayList<Bookmark> result = new ArrayList<Bookmark>();
        NodeList list = root.getChildNodes();
        Bookmark lastBook=null;
        for (int i = 0; i < list.getLength(); i++) {
            Node n = list.item(i);
            if ( ! ( n instanceof Element ) ) continue;
            try {
                Bookmark book = parseBookmark(n,vers,remoteLevel );
                result.add(book);
                lastBook= book;
            } catch (Exception ex) {
                try {
                    parseBookmark( n, vers, remoteLevel );
                } catch (UnsupportedEncodingException ex1) {
                    Logger.getLogger(Bookmark.class.getName()).log(Level.SEVERE, null, ex1);
                } catch (IOException ex1) {
                    Logger.getLogger(Bookmark.class.getName()).log(Level.SEVERE, null, ex1);
                }
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
    public static String formatBooksOld(List<Bookmark> bookmarks) {
        StringBuilder buf = new StringBuilder();

        buf.append("<bookmark-list>\n");
        for (Bookmark o : bookmarks) {
            buf.append( formatBookmark(o) );
        }
        buf.append("</bookmark-list>\n");
        return buf.toString();

    }

    /**
     * format the bookmarks into xml for persistent storage.
     * @param bookmarks List of Bookmark.List or Bookmark
     * @return
     */
    public static String formatBooks(List<Bookmark> bookmarks) {
        ByteArrayOutputStream baos= new ByteArrayOutputStream();
        formatBooks( baos,bookmarks );
        try {
            return baos.toString("UTF-8");
        } catch ( UnsupportedEncodingException ex ) {
            throw new RuntimeException(ex);
        }

    }

    /**
     * format the bookmarks into xml for persistent storage.
     * @param bookmarks List of Bookmark.List or Bookmark
     * @return
     */
    public static void formatBooks( OutputStream out, List<Bookmark> bookmarks ) {

        try {
            Document doc= DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();

            Element e= doc.createElement("bookmark-list");
            e.setAttribute( "version", "1.1" );

            for (Bookmark o : bookmarks) {
                formatBookmark(doc,e,o);
            }
            doc.appendChild(e);

            DOMImplementationLS ls = (DOMImplementationLS)
                            doc.getImplementation().getFeature("LS", "3.0");
            LSOutput output = ls.createLSOutput();
            output.setEncoding("UTF-8");
            output.setByteStream(out);
            LSSerializer serializer = ls.createLSSerializer();

            try {
                if (serializer.getDomConfig().canSetParameter("format-pretty-print", Boolean.TRUE)) {
                    serializer.getDomConfig().setParameter("format-pretty-print", Boolean.TRUE);
                }
            } catch (Error error) {
                // Ed's nice trick for finding the implementation
                //String name = serializer.getClass().getSimpleName();
                //java.net.URL u = serializer.getClass().getResource(name+".class");
                //System.err.println(u);
                error.printStackTrace();
            }
            serializer.write(doc, output);

        } catch ( IOException ex ) {
            throw new RuntimeException(ex);
        } catch ( ParserConfigurationException ex ) {
            throw new RuntimeException(ex);
        }
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

    public static void formatBookmark( Document doc, Element parent, Bookmark bookmark ) throws IOException {
        if (bookmark instanceof Bookmark.Item) {
            Bookmark.Item b = (Bookmark.Item) bookmark;

            Element book= doc.createElement("bookmark");
            Element title= doc.createElement("title");
            title.appendChild( doc.createTextNode( b.getTitle() ));
            book.appendChild(title);
            Element url= doc.createElement("uri");
            url.appendChild( doc.createTextNode( b.getUri() ) );
            book.appendChild(url);
            if ( b.icon!=null ) {
                Element icon= doc.createElement("icon");
                icon.appendChild( doc.createTextNode( encodeImage((BufferedImage) b.icon.getImage()) ) );
                book.appendChild(icon);
            }
            if ( b.description!=null && b.description.length()>0 ) {
                Element desc= doc.createElement("description");
                desc.appendChild( doc.createTextNode( b.getDescription() ) );
                book.appendChild(desc);
            }
            if ( b.descriptionUrl!=null ) {
                Element desc= doc.createElement("description-url");
                desc.appendChild( doc.createTextNode( b.getDescriptionUrl().toString() ) );
                book.appendChild(desc);
            }
            if ( b.isHidden() ) {
                Element desc= doc.createElement("hidden");
                desc.appendChild( doc.createTextNode( "true" ) );
                book.appendChild(desc);
            }
            parent.appendChild(book);

        } else if (bookmark instanceof Bookmark.Folder) {
            Bookmark.Folder f = (Bookmark.Folder) bookmark;

            Element folder= doc.createElement("bookmark-folder");
            if ( f.getRemoteUrl()!=null ) {
                folder.setAttribute( "remoteUrl", f.getRemoteUrl() );
            }

            Element titleEle= doc.createElement("title");
            titleEle.appendChild( doc.createTextNode( f.getTitle() ) );
            folder.appendChild(titleEle);

            if ( f.icon!=null ) {
                Element icon= doc.createElement("icon");
                icon.appendChild( doc.createTextNode( encodeImage( (BufferedImage)f.getIcon().getImage() ) ) );
                folder.appendChild(icon);
            }
            if (f.description != null && f.description.length()>0) {
                Element desc= doc.createElement("description");
                desc.appendChild( doc.createTextNode( f.getDescription() ) );
                folder.appendChild(desc);
            }
            if ( f.descriptionUrl!=null ) {
                Element desc= doc.createElement("description-url");
                desc.appendChild( doc.createTextNode( f.getDescriptionUrl().toString() ) );
                folder.appendChild(desc);
            }
            if ( f.isHidden() ) {
                Element desc= doc.createElement("hidden");
                desc.appendChild( doc.createTextNode( "true" ) );
                folder.appendChild(desc);
            }
            Element list= doc.createElement("bookmark-list");
            for ( Bookmark book: f.getBookmarks() ) {
                formatBookmark( doc, list, book );
            }
            folder.appendChild(list);

            parent.appendChild(folder);

        }
        return;

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
                Bookmark.Item b = (Bookmark.Item) bookmark;
                buf.append("  <bookmark>\n");
                buf.append("     <title>").append(URLEncoder.encode(b.getTitle(), "UTF-8")).append("</title>\n");
                if (b.icon != null) buf.append("     <icon>").append(encodeImage((BufferedImage) b.icon.getImage())).append("</icon>\n");
                if (b.description != null) buf.append("     <description>").append( URLEncoder.encode(b.getDescription(), "UTF-8")).append("</description>\n");
                if (b.descriptionUrl !=null ) buf.append("      <description-url>").append( b.getDescriptionUrl() ).append("</description-url>\n");
                buf.append("     <url>").append(URLEncoder.encode(b.getUri(), "UTF-8")).append("</url>\n");
                if ( bookmark.isHidden() ) {
                    buf.append("     <hidden>true</hidden>\n");
                }
                buf.append("  </bookmark>\n");
            } else if (bookmark instanceof Bookmark.Folder) {
                Bookmark.Folder f = (Bookmark.Folder) bookmark;
                String title= f.getTitle();
                if ( f.getRemoteUrl()!=null ) {
                    buf.append("  <bookmark-folder remoteUrl=\"").append(URLEncoder.encode(f.getRemoteUrl(), "UTF-8")).append("\">\n");
                } else {
                    buf.append("  <bookmark-folder>\n");
                }
                buf.append("    <title>").append(URLEncoder.encode(title, "UTF-8")).append("</title>\n");
                if (f.icon != null) buf.append("     <icon>").append(encodeImage((BufferedImage) f.icon.getImage())).append("</icon>\n");
                if ( bookmark.isHidden() ) {
                    buf.append("     <hidden>true</hidden>\n");
                }
                if (f.description != null) buf.append("     <description>").append( URLEncoder.encode(f.getDescription(), "UTF-8")).append("</description>\n");
                if (f.descriptionUrl !=null ) buf.append("      <description-url>").append( f.getDescriptionUrl() ).append("</description-url>\n");
                buf.append(formatBooksOld(f.getBookmarks()));
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
        //String data = "<!-- note title is not supported yet --><bookmark-list>    <bookmark>        <title>demo autoplot</title>        <url>http://autoplot.org/autoplot.vap</url>    </bookmark>    <bookmark>        <title>Storm Event</title>        <url>http://cdaweb.gsfc.nasa.gov/cgi-bin/opendap/nph-dods/istp_public/data/genesis/3dl2_gim/2003/genesis_3dl2_gim_20030501_v01.cdf.dds?Proton_Density</url>    </bookmark></bookmark-list>";

        //Reader in = new BufferedReader(new StringReader(data));

        Reader in = new FileReader("/home/jbf/CDAWebShort.xml");

        DocumentBuilder builder;
        builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        InputSource source = new InputSource(in);
        Document document = builder.parse(source);

        List<Bookmark> bs= parseBookmarks(document.getDocumentElement());
        for ( Bookmark b: bs ) {
            System.err.println("bookmark: "+ b);
            if ( b instanceof Bookmark.Folder ) {
                System.err.println(" -->" + ((Bookmark.Folder)b).getBookmarks());
            } else {

            }
        }
    }

    private static int seq= 0;

    private Bookmark(String title) {
        this.title = title;
        this.id= String.valueOf(++seq);
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

    /**
     * id property for performing copies and moves in manager.  Before we were doing it by title, etc.
     */
    String id= "";

    public String getId() {
        return this.id;
    }

    public void setId( String id ) {
        this.id= id;
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

    protected URL descriptionUrl=null;

    public URL getDescriptionUrl() {
        return descriptionUrl;
    }

    public void setDescriptionUrl(URL descriptionUrl) {
        URL oldValue= this.descriptionUrl;
        this.descriptionUrl = descriptionUrl;
        propertyChangeSupport.firePropertyChange( "descriptionUrl", oldValue, description );
    }

    /**
     * icons associated with the bookmark.  This was wishful thinking, and wasn't fully developed.  Note we have since
     * decided that Autoplot.org should compute icons if they are desired.
     */
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


    /**
     * 
     */
    private boolean hidden= false;

    public void setHidden( boolean hidden ) {
        this.hidden= hidden;
    }

    public boolean isHidden() {
        return this.hidden;
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
         * null indicates that this this a not a remote bookmark.
         */
        String remoteUrl= null;

        public void setRemoteUrl( String url ) {
            this.remoteUrl= url;
        }

        public String getRemoteUrl( ) {
            return this.remoteUrl;
        }

        public static int REMOTE_STATUS_NOT_LOADED= -1;
        public static int REMOTE_STATUS_SUCCESSFUL= 0;
        public static int REMOTE_STATUS_UNSUCCESSFUL= 1;

        /**
         * remote status indicator.
         * -1 not loaded
         * 0 successful
         * 1 unsuccessful.
         */
        int remoteStatus= REMOTE_STATUS_NOT_LOADED;

        public void setRemoteStatus( int status ) {
            this.remoteStatus= status;
        }

        public int getRemoteStatus( ) {
            return this.remoteStatus;
        }

        String remoteStatusMsg= "";

        public String getRemoteStatusMsg() {
            return this.remoteStatusMsg;
        }

        public void setRemoteStatusMsg( String msg ) {
            this.remoteStatusMsg= msg;
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
                        && ( that.getParent()==null || ( this.getParent()!=null && that.getParent().getTitle().equals(this.getParent().getTitle()) ) );
            } else {
                return false;
            }
        }

        public Bookmark copy() {
            Bookmark.Folder result = new Bookmark.Folder(getTitle());
            result.description= this.description;
            result.remoteUrl= remoteUrl;
            result.remoteStatus= remoteStatus;
            result.bookmarks = new ArrayList<Bookmark>(this.bookmarks);
            return result;
        }

        @Override
        public String toString() {
            return getTitle() + ( remoteStatus!=0 ? "*" : "" );
        }
    }

    public static class Item extends Bookmark {

        /** Creates a new instance of Bookmark */
        public Item(String suri) {
            super(suri);
            this.uri = suri;
        }
        /**
         * Holds value of property uri.
         */
        private String uri;

        /**
         * Getter for property uri.
         * @return Value of property uri.
         */
        public String getUri() {
            return this.uri;
        }

        /**
         * Setter for property uri.
         * @param uri New value of property uri.
         */
        public void setUri(String uri) {
            String oldUrl = this.uri;
            this.uri = uri;
            propertyChangeSupport.firePropertyChange("uri", oldUrl, uri);
        }

        @Override
        public int hashCode() {
            return uri.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Bookmark.Item) {
                Bookmark.Item that= (Bookmark.Item)obj;
                return that.uri.equals(this.uri)
                        && (that.getParent()==null || this.getParent()==null || that.getParent().getTitle().equals(this.getParent().getTitle()) );
            } else {
                return false;
            }
        }

        /**
         * copy the bookmark.  Its title, description and URI is copied.  Note the parent is not copied, since it's use
         * depends on the context where it is being used.
         * @return
         */
        public Bookmark copy() {
            Bookmark.Item result = new Bookmark.Item(getUri());
            result.setTitle(getTitle());
            result.description= this.description;
            return result;
        }
    }
}
