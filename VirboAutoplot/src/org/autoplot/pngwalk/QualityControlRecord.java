package org.autoplot.pngwalk;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.CharacterIterator;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.text.StringCharacterIterator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.das2.util.filesystem.FileObject;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.filesystem.WriteCapability;
import org.virbo.autoplot.dom.DebugPropertyChangeSupport;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.SAXException;

/**
 *
 * @author Ed Jackson
 */

public class QualityControlRecord {

    private static final Logger logger= org.das2.util.LoggerManager.getLogger("autoplot.pngwalk");
    
    public static enum Status {
        OK ("OK"), PROBLEM ("Problem"), IGNORE ("Ignore"), UNKNOWN ("Unknown");
        private String sval;
        Status (String s) {
            sval=s;
        }
        @Override
        public String toString() {return sval;}
        public String filenameExtension() { return "." + sval.toLowerCase(Locale.ENGLISH); }
        public static Status fromString(String s) {
            if (s.toLowerCase(Locale.ENGLISH).equals("ok")) return Status.OK;
            else if (s.toLowerCase(Locale.ENGLISH).equals("problem")) return Status.PROBLEM;
            else if (s.toLowerCase(Locale.ENGLISH).equals("ignore")) return Status.IGNORE;
            else return Status.UNKNOWN;
        }
    }

    private boolean initialized = false;    //to support lazy initialization

    private TreeSet comments;               //list of comments, use treeset to sort by date
    private Status currentStatus;           //current review status
    private Date changeDate;                //time of last modification
    private ReviewComment newComment;       //new commentText to be appended to list on write
    private FileObject recordFile=null;
    private URI imageURI;

    private static URI qcFolder;    // We only have one open at a time, but keep that session open
    private static FileSystem qcfs;  //The corresponding filesystem object
    // DON'T use qcfs directly; get it via getFileSystem
    
    private PropertyChangeSupport pcs = new DebugPropertyChangeSupport(this);
    public static final String PROP_STATUS = "status";

    private static Schema schema;
    private static Validator validator;
    private SimpleDateFormat utcDateFormat;

    private static final String XMLNS = "http://virbo.org/schema/pngwalkQC";

    private static Map<URI,QualityControlRecord> cache;
    private static URI cacheURI;
    
    static {
        try {
            String language = XMLConstants.W3C_XML_SCHEMA_NS_URI;
            SchemaFactory factory = SchemaFactory.newInstance(language);
            URL schemaURL = QualityControlRecord.class.getResource("pngwalkQC.xsd");
            schema = factory.newSchema(schemaURL);
            validator = schema.newValidator();
        } catch(SAXException ex) {
            System.err.println("Error initializing QC XML schema");
            ex.printStackTrace();
        }
    }

    static {
        cache= new HashMap<URI,QualityControlRecord>();
        cacheURI= null;
    }

    private QualityControlRecord() {
        comments = new TreeSet<ReviewComment>();
        currentStatus = Status.UNKNOWN;
        utcDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'"); // findbugs STCAL_INVOKE_ON_STATIC_DATE_FORMAT_INSTANCE
        utcDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        changeDate = new Date();  // now
    }

    private void initialize() {
        // If there's no recordFile to load, then we don't need to do anything
        if (initialized || recordFile==null) {
            initialized = true;
            return;
        }
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(recordFile.getInputStream());

            if (validator != null) {
                validator.validate(new DOMSource(doc));
            }

            // This shouldn't be necessary as we acquire current status from filename
//            NodeList nodes = doc.getElementsByTagNameNS(XMLNS, "currentStatus");
//            Element e = (Element) nodes.item(0);
//            String nodeVal = ((Node) e.getChildNodes().item(0)).getNodeValue();
//            setStatus(Status.fromString(nodeVal));

            NodeList nodes = doc.getElementsByTagNameNS(XMLNS, "modifiedDate");
            Element e = (Element) nodes.item(0);
            String nodeVal = ((Node) e.getChildNodes().item(0)).getNodeValue();
            setChangeDate(utcDateFormat.parse(nodeVal));

            nodes = doc.getElementsByTagNameNS(XMLNS, "reviewComment");
            for (int i = 0; i < nodes.getLength(); i++) {
                e = (Element) nodes.item(i);
                String reviewer = e.getAttribute("reviewer");
                Date reviewTime = utcDateFormat.parse(e.getAttribute("date"));
                Status reviewStatus = Status.fromString(e.getAttribute("status"));
                String ctxt = "";
                Node commentNode= e.getChildNodes().item(0);
                if (commentNode!=null) ctxt= ((Node)commentNode).getNodeValue();
                appendComment(reviewer, reviewTime, ctxt, reviewStatus);
            }

        } catch (RuntimeException ex) {
            throw (ex);
        } catch (SAXException ex) {
            System.err.println("XML failed to validate: " + recordFile.toString());
            ex.printStackTrace();
        } catch (Exception ex) {
            System.err.println("Error when loading quality control record from XML");
            ex.printStackTrace();
        }

        initialized = true;
    }

    /** Retrieve the quality control record for the named pngwalk image.  It is assumed that
     * the previously saved record, if there is one, exists in the same folder as the image.  If
     * a saved record is not found, a new one will be created.
     * @param imageURI
     * @return
     * @throws UnknownHostException
     * @throws IOException
     */
    public static QualityControlRecord getRecord(URI imageURI) throws UnknownHostException, IOException {
        return getRecord(imageURI, null);
    }

    /** Retrieve the quality control record for the named pngwalk image.  If <code>qcFolder</code>
     * is non-null, it should point to a folder that already exists and contains any saved quality
     * control records; if it is null, the records are assumed to be saved in the same folder as the images.
     * 
     * @param imageURI
     * @param qcFolder
     * @return
     * @throws UnknownHostException
     * @throws IOException
     */
    public static synchronized QualityControlRecord getRecord(URI imageURI, URI qcFolder) throws UnknownHostException, IOException {

        QualityControlRecord rec= cache.get(imageURI);

        if ( qcFolder==cacheURI ) {  // support null==null
        } else {
            if ( qcFolder!=null ) {
                if ( qcFolder!=cacheURI ) {
                    cache.clear();
                }
            } else {
                if ( cacheURI!=null ) {
                    cache.clear();
                }
            }
        }

        if ( rec!=null && qcFolder==cacheURI ) {
            return rec;
        }


        rec = new QualityControlRecord();   // record to be populated/returned

        String imagePath = imageURI.getPath();
        String imageName = imagePath.substring(imagePath.lastIndexOf('/')+1);

        if (qcFolder == null) {
            String s = imageURI.toString(); // bug 3055130 okay
            qcFolder = URI.create(s.substring(0,s.lastIndexOf('/')));
        }

        // Attempt to locate an existing status file
        FileSystem fs=null;
        try {
            fs = (FileSystem) getFileSystem(qcFolder);
        } catch (UnknownHostException ex) {
            logger.log(Level.SEVERE,
                        "Unknown host error when attempting to access quality control folder.", ex);
            throw(ex);
        } catch (IOException ex) {
            // How to distinguish between simple non-existent folder and some other problem accessing file system?      
            logger.log(Level.SEVERE,
                        "I/O error while opening quality control folder",ex);
            throw(ex);
        }

        FileObject testFile=null;
        for ( Status s: new Status[]{Status.OK, Status.PROBLEM, Status.IGNORE}) {  //TODO: poor implementation, do listing and parse.
            //String recordName = imagePath + s.filenameExtension();
            String recordName = imageName + s.filenameExtension();
            testFile = (FileObject) fs.getFileObject(recordName);
            if (testFile.exists()) {
                rec.currentStatus = s;  //Don't call setStatus from here!
                break;
            }
            testFile=null;
        }

        if (testFile != null) {
            //Populate the new record from the stored file
            rec.recordFile = testFile;

        }
        rec.imageURI = imageURI;
        rec.qcFolder = qcFolder;

        cache.put( imageURI, rec );
        if ( cacheURI!=qcFolder ) {
            cacheURI= qcFolder;
        }
        
        return rec;
    }

    /* This feels kludgy.  The idea is to avoid closing the file system if we can keep
     * using it for future transactions.  Closing it closes any underlying session (ftp, sftp, etc)
     * causing repeated access to be slow, and also sometimes unexpected closing of streams.  We
     * can reasonably expect repeated access to one folder, switching only when a new pngwalk is
     * loaded.
     */
    private static FileSystem getFileSystem(URI uri) throws UnknownHostException, IOException {
        if ( qcfs==null || qcFolder == null || !uri.equals(qcFolder)) {
            // if no, close it and get the new one
            //if (qcfs != null) qcfs.close();
            qcFolder = uri;
            qcfs = FileSystem.create(qcFolder);
        }
        return qcfs;
    }
    //private Document doc=null;
    
    public void save() {
        if(currentStatus == Status.UNKNOWN) {
            throw new IllegalArgumentException("Cannot write QC file for record with status \"unknown\"");
        }

        DocumentBuilderFactory dbf;
        DocumentBuilder builder;
        Document doc=null;

        try {
            dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            builder = dbf.newDocumentBuilder();
            doc = builder.newDocument();

            // This is the XML root node
            Element root = doc.createElementNS(XMLNS, "qualityControlRecord");
            doc.appendChild(root);

            appendTextElement(doc, root, "currentStatus", currentStatus.toString());

            appendTextElement(doc, root, "modifiedDate", xmlFormattedDate(changeDate));

            appendTextElement(doc, root, "imageURI", imageURI.toString()); // bug 3055130 okay
            
            if (newComment != null) {
                comments.add(newComment);
                newComment = null;
            }
            Iterator i = comments.iterator();
            while (i.hasNext()) {
                ReviewComment c = (ReviewComment)i.next();
                Element e = doc.createElementNS(XMLNS, "reviewComment");
                e.setAttribute("reviewer", c.reviewer);
                e.setAttribute("date", xmlFormattedDate(c.commentDate));
                e.setAttribute("status", c.reviewStatus.toString());
                Text t = doc.createTextNode(c.commentText);
                e.appendChild(t);
                root.appendChild(e);
            }
            
        } catch (Exception ex) {
            System.err.println("Exception while building XML");
            ex.printStackTrace();
        }

        // Just to be safe, validate.  If an exception occurs here, it's a serious bug
        try {
            if ( validator!=null ) validator.validate(new DOMSource(doc));
        } catch (RuntimeException ex) {
            throw(ex);
        } catch (Exception ex) {
            throw new RuntimeException("Internally generated XML failed to validate!", ex);
        }

        // Now write the XML to an output file.
        try {

            // Prepare the output file
            FileObject oldRecordFile = null;
            Status oldStatus = Status.UNKNOWN;
            if (recordFile != null) {
               String n = recordFile.getNameExt();
               oldStatus = Status.fromString(n.substring(n.lastIndexOf('.')+1));
            } 
            if (recordFile==null || !(oldStatus.toString().equals(currentStatus.toString()))) {
                oldRecordFile = recordFile;
                FileSystem fs;
                try {
                    // This folder should already exist!
                    fs = (FileSystem) getFileSystem(qcFolder);
                } catch (UnknownHostException ex) {
                    logger.log(Level.SEVERE,
                            "Unknown host error when attempting to access quality control folder.", ex);
                    throw (ex);
                } catch (IOException ex) {
                    // How to distinguish between simple non-existent folder and some other problem accessing file system?
                    logger.log(Level.SEVERE,
                            "I/O error while opening quality control folder", ex);
                    throw (ex);
                }
                // Create file object based on status
                String n = imageURI.getPath();
                recordFile = fs.getFileObject(n.substring(n.lastIndexOf('/')+1) + currentStatus.filenameExtension());
                //fs.close(); //TODO: VFS had a session--we might need this
            }

            WriteCapability write= recordFile.getCapability( WriteCapability.class );

            // (over)write status file from xml tree
            if ( write != null ) {

                DOMImplementation impl = doc.getImplementation();
                DOMImplementationLS ls = (DOMImplementationLS) impl.getFeature("LS", "3.0");
                LSSerializer serializer = ls.createLSSerializer();
                LSOutput output = ls.createLSOutput();
                output.setEncoding("UTF-8");

                write.delete();
                OutputStream out= write.getOutputStream();

                output.setByteStream( out );
                try {
                    if (serializer.getDomConfig().canSetParameter("format-pretty-print", Boolean.TRUE)) {
                        serializer.getDomConfig().setParameter("format-pretty-print", Boolean.TRUE);
                    }
                } catch (Error e2) {
                    e2.printStackTrace();
                }
                serializer.write(doc, output);

                out.close();
            } else {
                throw new IOException("file system is not writable: "+recordFile);
            }

            // remove old status file if status has changed
            if (oldRecordFile != null) {
                WriteCapability oldCap= oldRecordFile.getCapability( WriteCapability.class );
                if ( !oldCap.delete() ) {
                    System.err.println("here 123545");
                }
            }

        } catch(RuntimeException ex) {
            // Don't want these in the catch-all block below.
            throw(ex);
        } catch(Exception ex) {
            // TODO: We should handle I/O errors more gently, but this will work for testing.
            throw new RuntimeException("Exception while writing XML record.", ex);
        }
    }

    //Convenience method for use in save
    private void appendTextElement(Document doc, Element base, String name, String text) {
        Element e = doc.createElementNS(XMLNS, name);
        Text t = doc.createTextNode(text);
        e.appendChild(t);
        base.appendChild(e);
    }

    public String getCommentsHTML() {
        if (!initialized) initialize();
        StringBuilder sb = new StringBuilder();

        sb.append("<html><body>");

        Iterator i = comments.iterator();
        while(i.hasNext()) {
            ReviewComment c = (ReviewComment)i.next();
            sb.append("<b>" + c.reviewer + "</b><br/>");
            switch(c.reviewStatus) {
                case OK:
                    sb.append("<font color=\"green\">");
                    break;
                case PROBLEM:
                    sb.append("<font color=\"red\">");
                    break;
                default:
                    sb.append("<font color=\"gray\">");
                    break;
            }
            sb.append(DateFormat.getDateTimeInstance().format(c.commentDate));
            sb.append("</font><br/>");
            // escape stuff that will confuse the html formatting
            StringCharacterIterator ci = new StringCharacterIterator(c.commentText);
            for(char ch = ci.first(); ch != CharacterIterator.DONE; ch = ci.next()) {
                if (ch=='<')
                    sb.append("&lt;");
                else if (ch=='>')
                    sb.append("&gt;");
                else
                    sb.append(ch);
            }           
            sb.append("<br/><hr/>");
        }

        sb.append("</body></html>");
        return sb.toString();
    }

    public Status getStatus() {
        return currentStatus;
    }

    public void setStatus(Status newStatus) {
        if(!initialized) initialize();
        Status oldStatus = currentStatus;
        currentStatus = newStatus;
        if (newComment != null) newComment.reviewStatus = newStatus;
        changeDate.setTime(System.currentTimeMillis());
        pcs.firePropertyChange(PROP_STATUS, oldStatus, newStatus);
    }

    private void setChangeDate(Date d) {
        changeDate = d;
    }

    /** Set the "new" commentText on this record.  This commentText will be appended to the existing
     * list of comments when the record is written.
     *
     * @param reviewer
     * @param commentText
     */
    public void setNewCommentText(String reviewer, String commentText) {
        if (!initialized) initialize();
        if (newComment == null) newComment = new ReviewComment();
        newComment.reviewer = reviewer;
        newComment.commentText = commentText;
        newComment.commentDate.setTime(System.currentTimeMillis());
        newComment.reviewStatus = currentStatus;
        changeDate.setTime(System.currentTimeMillis());
    }

    public String getNewCommentText() {
        if (!initialized) initialize();
        if (newComment == null || newComment.commentText == null) {
            return "";
        } else {
            return newComment.commentText;
        }
    }

    public URI getImageURI() {
        return this.imageURI;
    }
    
    private String xmlFormattedDate(Date d) {
        StringBuilder dateStr = new StringBuilder(utcDateFormat.format(d));
        //dateStr.insert(dateStr.length() - 2, ":");   // in order to comply with xsd dateTime format
        return dateStr.toString();
    }

    private void appendComment(String reviewer, Date when, String cText, Status status) {
        comments.add(new ReviewComment(reviewer, when, cText, status));
    }

    public void addPropertyChangeListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }

    public void removePropertyChangeListener(PropertyChangeListener l) {
        pcs.removePropertyChangeListener(l);
    }

    // A data structure to describe an individual commentText
    private static class ReviewComment implements Comparable {
        String reviewer;    //Name of reviewer
        Date commentDate;
        String commentText;
        Status reviewStatus;

        public ReviewComment() {
            this(null, new Date(), null, Status.UNKNOWN);
        }
       
        public ReviewComment(String reviewer, Date commentDate, String commentText, Status status) {
            if ( commentText==null ) commentText="";
            this.reviewer = reviewer;
            this.commentDate = commentDate;
            this.commentText = commentText;
            this.reviewStatus = status;
        }

        // This implementation of Comparable causes the list to be sorted by date.
        public int compareTo(Object other) {
            int i= commentDate.compareTo( ((ReviewComment)other).commentDate);
            if ( i==0 ) {
                return commentText.compareTo( ((ReviewComment)other).commentText );
            } else {
                return i;
            }
        }

        @Override
        public boolean equals(Object obj) {
            if ( !( obj instanceof ReviewComment ) ) return false;
            return compareTo(obj)==0 && commentText.equals( ((ReviewComment)obj).commentText );
        }

        @Override
        public int hashCode() {
            return commentDate.hashCode() * commentText.hashCode();
        }
    }
}
