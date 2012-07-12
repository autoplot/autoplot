/* File: DasExceptionHandler.java
 * Copyright (C) 2002-2003 The University of Iowa
 * Created by: Jeremy Faden <jbf@space.physics.uiowa.edu>
 *             Jessica Swanner <jessica@space.physics.uiowa.edu>
 *             Edward E. West <eew@space.physics.uiowa.edu>
 *
 * This file is part of the das2 library.
 *
 * das2 is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.virbo.autoplot.scriptconsole;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.DasApplication;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.LogRecord;
import java.util.logging.XMLFormatter;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.multipart.ByteArrayPartSource;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.StringPart;
import org.das2.util.ExceptionHandler;
import org.das2.util.AboutUtil;
import org.das2.util.Base64;
import org.virbo.autoplot.ApplicationModel;
import org.virbo.autoplot.dom.Application;
import org.virbo.autoplot.dom.DomNode;
import org.virbo.autoplot.state.SerializeUtil;
import org.virbo.autoplot.state.StatePersistence;
import org.virbo.autoplot.state.UndoRedoSupport;
import org.virbo.autoplot.state.Vap1_07Scheme;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;

/**
 * This is the original das2 Exception handler dialog, but modified to
 * support submitting an error report to a server.
 *
 * The server is hard-coded to be http://www.papco.org:8080/RTEReceiver/LargeUpload.jsp,
 * TODO: add runtime property to set this.  This client will submit a file containing the
 * report to the server.  The filename is a client-side calculated hash of the stack trace
 * and timestamp.  The server is expecting a multi-part post, containing:
 *   "secret"="secret"
 *   "todo"="upload"
 *   "uploadfile"= the file to upload.
 * TODO: refactor the error reporting stuff because it should be useful for headless
 * applications as well.
 *
 * @author  jbf
 */
public final class GuiExceptionHandler implements ExceptionHandler {
    
    private static final String CUSTODIAN = "faden@cottagesystems.com";

    //private static JDialog dialog;
    //private static JTextArea messageArea;
    //private static JTextArea traceArea;
    private static final String UNCAUGHT = "An unexpected error has occurred. " +
        "The system may not be able to recover properly.  You can inspect " +
        "information about the crash with the Show Details button below, and " +
        "submit an error report.\n\n" +
        "This submission to the Autoplot developers will include information about the program state " +
        "including data URIs, undos, source code version tags," +
        "and platform information.\n\n";

    private JButton submitButton;
    private static final String USER_ID= "USER_ID";
    private static final String EMAIL="EMAIL";
    private static final String FOCUS_URI="FOCUS_URI";
    private static final String INCLDOM= "INCLDOM";
    private static final String INCLSCREEN= "INCLSCREEN";

    private ApplicationModel appModel=null;
    private UndoRedoSupport undoRedoSupport= null;

    public GuiExceptionHandler() {
    }

    public void handle(Throwable t) {
        if ( DasApplication.getDefaultApplication().isHeadless() ) {
            t.printStackTrace();
        }
        else {
            showExceptionDialog(t, "");
        }
    }
    
    public void handleUncaught(Throwable t) {
        if ( DasApplication.getDefaultApplication().isHeadless() ) {
            t.printStackTrace();
        }
        else {
            showExceptionDialog(t, UNCAUGHT);
        }
    }

    private Map<Integer,DiaDescriptor> dialogs= new HashMap<Integer, DiaDescriptor>();

    private synchronized DiaDescriptor createDialog( final Throwable throwable, final boolean uncaught ) {
        final DiaDescriptor diaDescriptor= new DiaDescriptor();
        diaDescriptor.hits=1;

        final JDialog dialog = new JDialog( DasApplication.getDefaultApplication().getMainFrame() );
        if ( !uncaught ) {
            dialog.setTitle("Error Notification");
        } else {
            dialog.setTitle("Runtime Error Occurred");
        }

        dialog.setModal(false);
        dialog.setResizable(true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        final JTextArea messageArea = new JTextArea(12, 40);
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);
        messageArea.setEditable(false);

        JScrollPane message = new JScrollPane(messageArea);
        message.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(message, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton ok = new JButton("Ok");
        final JToggleButton details = new JToggleButton("Show Details");

        JButton submit= new JButton("Submit Error Report...");
        submit.setToolTipText("<html>Submit exception, platform information, source tags, and possibly log records to RTE server</html>");

        buttonPanel.add(submit);
        buttonPanel.add(details);
        buttonPanel.add(ok);

        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        dialog.getContentPane().add(mainPanel, BorderLayout.CENTER);

        final JTextArea traceArea = new JTextArea(10, 40);
        traceArea.setLineWrap(false);
        traceArea.setEditable(false);
        traceArea.setTabSize(4);

        diaDescriptor.textArea= messageArea;
        
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        traceArea.setText(writer.toString());

        final JPanel stackPane = new JPanel(new BorderLayout());
        stackPane.add(new JScrollPane(traceArea), BorderLayout.NORTH);
        stackPane.setBorder(new javax.swing.border.EmptyBorder(10, 10, 10, 10));
        JPanel buttonPanel2 = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel2.setBorder(new javax.swing.border.EmptyBorder(10, 0, 0, 0));
        JButton dump = new JButton("Dump to Console");

        buttonPanel2.add(dump);

        submitButton= submit;

        stackPane.add(buttonPanel2, BorderLayout.SOUTH);
        Dimension size = message.getPreferredSize();
        size.width = stackPane.getPreferredSize().width;
        message.setPreferredSize(size);

        ok.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                dialog.dispose();
                dialogs.remove( diaDescriptor.hash );
            }
        });

        details.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (details.isSelected()) {
                    details.setText("Less Details");
                    dialog.getContentPane().add(stackPane, BorderLayout.SOUTH);
                    dialog.pack();
                }
                else {
                    details.setText("More Details");
                    dialog.getContentPane().remove(stackPane);
                    dialog.pack();
                }
            }
        });

        dump.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String text = traceArea.getText();
                System.err.print(text); // Note this is useful when debugging in an IDE.
            }
        });

        submit.addActionListener( new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                submitRuntimeException(throwable,uncaught);
            }
        });

        diaDescriptor.dialog= dialog;
        return diaDescriptor;
    }

    public void setApplicationModel(ApplicationModel appModel ) {
        this.appModel= appModel;
    }

    public void setUndoRedoSupport( UndoRedoSupport undoRedoSupport ) {
        this.undoRedoSupport= undoRedoSupport;
    }

    String updateText( GuiExceptionHandlerSubmitForm form, String userComments ) {
        map.put( INCLDOM, form.isAllowDom() );
        map.put( INCLSCREEN, form.isAllowScreenshot() );
        map.put( EMAIL, form.getEmailTextField().getText() );
        map.put( USER_ID, form.getUsernameTextField().getText().replaceAll(" ","_") );

        return formatReport( t, bis, recs, map, uncaught, userComments );
    }

    static class DiaDescriptor {
        JDialog dialog;
        int hits;
        JTextArea textArea;
        int hash;
    }
    
    private synchronized void showExceptionDialog( final Throwable t, String extraInfo ) {

        final boolean uncaught= extraInfo.equals(UNCAUGHT);

        int hash= hashCode(t);
        
        DiaDescriptor dia1= dialogs.get(hash);
        
        String errorMessage = extraInfo + t.getClass().getName() + "\n"
            + (t.getMessage() == null ? "" : t.getMessage());

        // kludge for Jython errors
        if ( t.getClass().getName().contains("PyException" ) ) {
            String[] ss= t.toString().split("\n");
            int i=0;
            if ( ss[i].contains("Traceback") ) i++;
            if ( ss.length>0 && ss[i].contains( "in ?") ) i++;
            if ( ss.length > i ) {
                StringBuilder msg= new StringBuilder(ss[i++]);
                while ( i<ss.length ) msg.append("\n").append(ss[i++]);
                errorMessage+= msg.toString();
            }
        }

        if ( dia1!=null ) {
            errorMessage= errorMessage + "\n\nError hit "+(1+dia1.hits)+ " times" ;
            dia1.hits++;
        }

        if ( dia1==null ) {
            dia1= createDialog( t, uncaught );
        }

        final JDialog dialog = dia1.dialog;

        dialogs.put( hash, dia1 );
        dia1.hash= hash;

        dia1.textArea.setText(errorMessage);
        
        dialog.pack();
        dialog.setLocationRelativeTo( DasApplication.getDefaultApplication().getMainFrame() );
        dialog.setVisible(true);


    }

    private static int hashCode( Throwable t ) {
        int rteHash= 0;
        if ( t.getCause()!=null ) {
            t= t.getCause();
        }

        StackTraceElement[] ee= t.getStackTrace();
        for ( int i=0; i<ee.length && i<5; i++ ) {
            rteHash= 31*rteHash + hashCode(ee[i]);
        }
        rteHash= Math.abs(rteHash) + ( ee.length>0 ? ee[0].getLineNumber() : 0 );
        return rteHash;
    }

    private static int hashCode( StackTraceElement e ) {
        int result = 31*e.getClassName().hashCode() + e.getMethodName().hashCode();
        return result;
    }

    private LogConsole lc;

    public synchronized void setLogConsole( LogConsole lc ) {
        this.lc= lc;
    }

    private String focusURI;
    public synchronized void setFocusURI( String uri ) {
        this.focusURI= uri;
    }

    // Append to the given StringBuffer an escaped version of the
    // given text string where XML special characters have been escaped.
    // For a null string we append "<null>"
    // from java.util.logging.XMLFormatter
    private void escape(StringBuffer sb, String text) {
        if (text == null) {
            text = "<null>";
        }
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '<') {
                sb.append("&lt;");
            } else if (ch == '>') {
                sb.append("&gt;");
            } else if (ch == '&') {
                sb.append("&amp;");
            } else {
                sb.append(ch);
            }
        }
    }

    private void formatException( Document doc, Element parent, Throwable th ) {
        Element ex= doc.createElement("exception");
        Element type= doc.createElement("type");
        type.appendChild( doc.createTextNode(th.getClass().getName()) );
        
        ex.appendChild(type);
        
        Element msg= doc.createElement("message");
        msg.appendChild( doc.createTextNode(th.toString()) );
        
        ex.appendChild(msg);

        int hash = hashCode(th);
        Element hashe= doc.createElement("hash");
        hashe.appendChild( doc.createTextNode( String.valueOf(hash) ) );

        ex.appendChild(hashe);

        StackTraceElement[] stes= th.getStackTrace();

        Element location;
        location= doc.createElement("location");

        if ( stes.length>0 ) {
            StackTraceElement ste = stes[0];
    
            Element ele= doc.createElement("class");
            ele.appendChild( doc.createTextNode(ste.getClassName()));
            location.appendChild(ele);
            ele= doc.createElement("method");
            ele.appendChild( doc.createTextNode(ste.getMethodName()) );
            location.appendChild(ele);
            ele= doc.createElement("file");
            ele.appendChild( doc.createTextNode(ste.getFileName()) );
            location.appendChild(ele);
            ele= doc.createElement("lineNumber");
            ele.appendChild( doc.createTextNode( String.valueOf(ste.getLineNumber()) ) );
            location.appendChild(ele);

            ex.appendChild(location);
            
            ele= doc.createElement("toString");
            StringWriter sw = new StringWriter();
            th.printStackTrace(new PrintWriter(sw));

            ele.appendChild( doc.createTextNode( "\n"+sw.toString() ) );

            ex.appendChild(ele);

        } else {
            Element ele= doc.createElement("noStackTrace");
            location.appendChild(ele);
            ex.appendChild(location);

        }


        parent.appendChild(ex);
    }

    private void formatBuildInfos( Document doc, Element parent, List<String> bis ) {
        Element pp= doc.createElement("buildInfos");
        for ( String s: bis ) {
            Element jar= doc.createElement("jar");
            jar.appendChild( doc.createTextNode(s) );
            pp.appendChild(jar);
        }
        parent.appendChild(pp);

    }

    private void formatSysProp( Document doc, Element parent,String prop ) {
        Element ele= doc.createElement("property");
        ele.setAttribute( "name", prop );
        ele.setAttribute( "value",System.getProperty(prop) );
        parent.appendChild(ele);
    }

    private void formatPlatform( Document doc, Element parent  ) {
        Element p= doc.createElement("platform");
        formatSysProp( doc, p, "java.version" );
        formatSysProp( doc, p, "java.vendor" );
        formatSysProp( doc, p, "os.name" );
        formatSysProp( doc, p, "os.arch" );
        formatSysProp( doc, p, "os.version" );
        DecimalFormat nf = new DecimalFormat("0.0");
        String mem = nf.format(Runtime.getRuntime().maxMemory() / (1024 * 1024));
        String tmem= nf.format(Runtime.getRuntime().totalMemory() / (1024 * 1024));
        String fmem= nf.format(Runtime.getRuntime().freeMemory() / (1024 * 1024));

        Element ele;
        ele= doc.createElement("property");
        ele.setAttribute("runtime.maxMemory", String.valueOf(mem)+" Mb");
        p.appendChild(ele);
        ele= doc.createElement("property");
        ele.setAttribute("runtime.totalMemory", String.valueOf(tmem)+" Mb");
        p.appendChild(ele);
        ele= doc.createElement("property");
        ele.setAttribute("runtime.freeMemory", String.valueOf(fmem)+" Mb");
        p.appendChild(ele);

        parent.appendChild(p);

    }

    private void formatUndos( Document doc, Element parent, UndoRedoSupport undo ) {
        Element ele= doc.createElement("states");
        for ( int i= undo.getDepth()-1; i>0; i-- ) {
            Element ele1= doc.createElement("undo");
            ele1.setAttribute("pos", String.valueOf(i) );
            ele1.appendChild( doc.createTextNode(undo.getLongUndoDescription(i) ) );
            ele.appendChild(ele1);
        }
        parent.appendChild(ele);
    }

    private String formatReport( Throwable t, List<String> bis, List<LogRecord> recs, Map<String,Object> data, boolean uncaught, String userComments ) {

        ByteArrayOutputStream out= new ByteArrayOutputStream();

        try {
            Document doc= DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();

            Element e= doc.createElement("exceptionReport");
            e.setAttribute( "version", "1.1" );

            doc.appendChild(e);
            Element app= doc.createElement("applicationId");
            app.appendChild( doc.createTextNode("autoplot") );

            e.appendChild(app);

            Element user= doc.createElement("userComments");
            user.appendChild( doc.createTextNode(userComments) );
            e.appendChild(user);

            Element userN= doc.createElement("userName");
            userN.appendChild( doc.createTextNode((String)data.get(USER_ID)) );
            e.appendChild(userN);

            Element mail= doc.createElement("email");
            mail.appendChild( doc.createTextNode((String)data.get(EMAIL)) );
            e.appendChild(mail);

            Element focus= doc.createElement("focusUri");
            focus.appendChild( doc.createTextNode((String)data.get(FOCUS_URI)) );
            e.appendChild(focus);

            formatBuildInfos( doc, e, bis );

            formatPlatform( doc, e );

            formatException( doc, e, t );

            Element ele= doc.createElement( "uncaught" );
            ele.appendChild( doc.createTextNode( String.valueOf(uncaught) ) );
            e.appendChild(ele);

            if ( data.get(INCLDOM)==null || (Boolean)data.get( INCLDOM ) ) {
                if ( appModel!=null ) {
                    Application state= (Application)appModel.getDocumentModel();

                    Element app1 = SerializeUtil.getDomElement( doc, (DomNode)state, StatePersistence.currentScheme() );

                    Element vap= doc.createElement("vap");
                    vap.appendChild(app1);

                    vap.setAttribute( "domVersion", StatePersistence.currentScheme().getId() );
                    try {
                        vap.setAttribute( "appVersionTag", AboutUtil.getReleaseTag() );
                    } catch ( IOException ex ) {
                        vap.setAttribute(  "appVersionTag", ex.getMessage() );
                    }

                    Element dom= doc.createElement("dom");
                    dom.appendChild(vap);

                    e.appendChild(vap);

                }

                if ( undoRedoSupport!=null ) {
                    formatUndos( doc, e, undoRedoSupport );
                }
            }

            if ( data.get(INCLSCREEN)!=null && (Boolean)data.get( INCLSCREEN ) ) {

                if ( appModel!=null ) {
                    Window w= SwingUtilities.getWindowAncestor( appModel.getCanvas() );

                    BufferedImage img= new BufferedImage( w.getWidth(), w.getHeight(), BufferedImage.TYPE_INT_RGB );

                    w.print( img.getGraphics() );

                    ByteArrayOutputStream baos= new ByteArrayOutputStream();

                    try {
                        ImageIO.write( img, "png", baos );
                        baos.close();

                        byte[] array= baos.toByteArray();

                        String image64= Base64.encodeBytes(array);

                        Element screen= doc.createElement("screenshot");
                        screen.setAttribute( "mimetype", "image/png" );

                        screen.appendChild( doc.createTextNode( "\n"+image64+"\n" ) );
                        e.appendChild(screen);

                    } catch ( IOException ex ) {
                        ex.printStackTrace();
                    }

                } else {
                    System.err.println("couldnt find appModel");
                }
                
            }

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
        } catch ( ParserConfigurationException ex ) {
            
        }

        try {
            out.close();
        } catch ( IOException ex ) {
            ex.printStackTrace();
        }

        return out.toString();
    }

    javax.swing.filechooser.FileFilter getFileNameExtensionFilter( final String desc, final String[] exts ) {
        return new javax.swing.filechooser.FileFilter() {
            public boolean accept(File pathname) {
                if ( pathname.isFile() ) return true;
                for ( int i=0; i<exts.length; i++ ) {
                    if ( exts[i].length()>1 && exts[i].charAt(0)=='.' ) {
                        return ( pathname.toString().endsWith( exts[i] ) );
                    } else {
                        return ( pathname.toString().endsWith( "." + exts[i] ) );
                    }
                    
                }
                return false;
            }

            @Override
            public String getDescription() {
                return desc;
            }
        };
    }

    List<LogRecord> recs;
    List<String> bis;
    Map<String,Object> map;
    boolean uncaught;
    Throwable t;

    public synchronized void submitRuntimeException( Throwable t, boolean uncaught ) {
        int rteHash;
        rteHash= hashCode( t );
        
        Date now = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String eventId= sdf.format( now );

        bis= null;
        try {
             bis = AboutUtil.getBuildInfos();
        } catch (IOException ex) {
            Logger.getLogger(GuiExceptionHandler.class.getName()).log(Level.SEVERE, null, ex);
        }

        recs=null;

        if ( lc!=null ) recs= lc.records;

        map=new HashMap();

        String id= "anon";
        id= System.getProperty("user.name");

        map.put( USER_ID, id );
        map.put( EMAIL, "" );
        map.put( FOCUS_URI, focusURI );

        this.uncaught= uncaught;
        this.t= t;

        String report= formatReport( t, bis, recs, map, uncaught, "USER COMMENTS" );

        String url =
         "http://papco.org:8080/RTEReceiver/LargeUpload.jsp";

        GuiExceptionHandlerSubmitForm form= new GuiExceptionHandlerSubmitForm();
        form.setGuiExceptionHandler( this );

        boolean notsent= true;

        while ( notsent ) {

            form.getDataTextArea().setText( report );

            form.getUsernameTextField().setText( (String)map.get(USER_ID) );
            form.getEmailTextField().setText( (String)map.get(EMAIL) );

            String[] choices= { "Copy to Clipboard", "Save to File", "Cancel", "Submit" };
            Component parent= appModel==null ? null : SwingUtilities.getWindowAncestor(appModel.getCanvas());
            int option= JOptionPane.showOptionDialog( parent, form, "Submit Exception Report",
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, choices, choices[3] )  ;
            if ( option==2 ) {
                return;
            } else if ( option==1 ) {
                id= form.getUsernameTextField().getText().replaceAll(" ","_");
                if ( id.trim().equals("") ) id= "anon";
                map.put( USER_ID, id );

                String email= form.getEmailTextField().getText();
                map.put( EMAIL, email );

                map.put( INCLSCREEN, form.isAllowScreenshot() );

                report= formatReport( t, bis, recs, map, uncaught, form.getUserTextArea().getText() );

                JFileChooser chooser= new JFileChooser();
                chooser.setFileFilter( this.getFileNameExtensionFilter("xml files", new String[] { ".xml" } ) );
                String fname= String.format( "rte_%010d_%s_%s.xml", new Integer(rteHash), eventId, id );
                chooser.setSelectedFile( new File(fname) );
                if ( chooser.showSaveDialog(form) == JFileChooser.APPROVE_OPTION ) {
                    try {
                        File f= chooser.getSelectedFile();
                        PrintWriter out= new PrintWriter(f);
                        out.write(report);
                        out.close();
                        notsent= false;
                    } catch ( IOException ex ) {
                        JOptionPane.showMessageDialog( null, ex.toString() );
                    }
                }

            } else if ( option==3 ) {

            //TODO soon: this needs to be done off the event thread.  It causes the app to hang when there is no internet.
                id= form.getUsernameTextField().getText().replaceAll(" ", "_");
                if ( id.trim().equals("") ) id= "anon";
                map.put( USER_ID, id );

                String email= form.getEmailTextField().getText();
                map.put( EMAIL, email );

                report= formatReport( t, bis, recs, map, uncaught, form.getUserTextArea().getText() );
                String fname=  String.format( "rte_%010d_%s_%s.xml", new Integer(rteHash), eventId, id );

                HttpClient client = new HttpClient();
                client.getHttpConnectionManager().getParams().setConnectionTimeout(3000);
                PostMethod postMethod = new PostMethod(url);

                Part[] parts= {
                    new StringPart( "secret", "secret" ),
                    new StringPart( "todo", "upload" ),
                    new FilePart( "uploadfile", new ByteArrayPartSource( fname, report.getBytes() ) ),
                };

                postMethod.setRequestEntity(
                        new MultipartRequestEntity( parts, postMethod.getParams() ));

                try {
                    int statusCode1 = client.executeMethod(postMethod);
                    if ( statusCode1==200 ) {
                        if ( this.submitButton!=null ) this.submitButton.setEnabled(false);
                        notsent= false;
                        postMethod.releaseConnection();
                    } else {
                        postMethod.releaseConnection();
                        JOptionPane.showMessageDialog( null, "<html>I/O Exception when posting to<br>"+url+":<br><br>"+postMethod.getStatusLine()+"<br><br>Consider save to file and email to faden@cottagesystems.com" );
                    }

                } catch ( IOException ex ) {
                    JOptionPane.showMessageDialog( null, "<html>I/O Exception when posting to<br>"+url+":<br><br>"+ex.toString()+"<br><br>Consider save to file and email to "+CUSTODIAN );
                }
            } else if ( option==0 ) {
                id= form.getUsernameTextField().getText().replaceAll(" ","_");
                if ( id.trim().equals("") ) id= "anon";
                map.put( USER_ID, id );

                String email= form.getEmailTextField().getText();
                map.put( EMAIL, email );

                report= formatReport( t, bis, recs, map, uncaught, form.getUserTextArea().getText() );

                StringSelection stringSelection = new StringSelection(report);
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                clipboard.setContents(stringSelection, new ClipboardOwner() {
                    public void lostOwnership(Clipboard clipboard, Transferable contents) {
                    }
                } );
                // make them hit cancel...
            }
        } // while notsent
    }


    public static String safe(String s) {
        try {
            if ( s==null ) {
                return "null";
            } else {
                return URLEncoder.encode(s, "UTF-8");
            }
        } catch (UnsupportedEncodingException ex) {
            throw new RuntimeException(s); // shouldn't happen UTF-8 should always be available.
        }
    }

    public static void main( String[] args ) {
        ExceptionHandler eh= new GuiExceptionHandler();
        eh.handle( new RuntimeException("Bad Deal!") );
        eh.handle( new RuntimeException("Bad Deal!") );
        eh.handle( new RuntimeException("Bad Deal!") );
        for ( int i=0; i<3; i++ ) {
            eh.handle( new RuntimeException("Bad Deal 2!") );
        }

        ExceptionHandler eh2= new GuiExceptionHandler();
        eh2.handleUncaught( new RuntimeException("Bad Deal!") );
    }

}
