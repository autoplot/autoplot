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

package org.autoplot.scriptconsole;

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
import org.das2.DasApplication;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.lang.Thread.State;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.multipart.ByteArrayPartSource;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.StringPart;
import org.autoplot.APSplash;
import org.das2.datum.LoggerManager;
import org.das2.util.ExceptionHandler;
import org.das2.util.AboutUtil;
import org.das2.util.Base64;
import org.python.core.PyException;
import org.autoplot.AppManager;
import org.autoplot.ApplicationModel;
import org.autoplot.AutoplotUI;
import org.autoplot.AutoplotUtil;
import org.autoplot.ScriptContext;
import org.autoplot.dom.Application;
import org.autoplot.dom.DomNode;
import org.autoplot.state.SerializeUtil;
import org.autoplot.state.StatePersistence;
import org.autoplot.state.UndoRedoSupport;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;

/**
 * This is the original das2 Exception handler dialog, but modified to
 * support submitting an error report to a server.
 *
 * The server is hard-coded to be https://jfaden.net/RTEReceiver/LargeUpload.jsp,
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
    
    private static final Logger logger= LoggerManager.getLogger("autoplot.util");
    
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
    public static final String USER_ID= "USER_ID";
    public static final String EMAIL="EMAIL";
    public static final String FOCUS_URI="FOCUS_URI";
    public static final String PENDING_FOCUS_URI="PENDING_FOCUS_URI";    
    public static final String APP_COUNT="APP_COUNT";    // number of apps/pngwalks open
    public static final String INCLDOM= "INCLDOM";
    public static final String INCLSCREEN= "INCLSCREEN";
    public static final String APP_MODEL= "APP_MODEL"; // application model
    public static final String UNDO_REDO_SUPPORT= "UNDO_REDO_SUPPORT"; // the DOM.
    public static final String THROWABLE="throwable";
    public static final String BUILD_INFO="build_info";
    public static final String LOG_RECORDS= "log_records"; // list of log records.    
    
    private ApplicationModel appModel=null;
    private UndoRedoSupport undoRedoSupport= null;

    public GuiExceptionHandler() {
    }

    public void handle(Throwable t) {
        if ( DasApplication.getDefaultApplication().isHeadless() ) {
            t.printStackTrace();
        }
        else {
            checkJythonError(t);
            showExceptionDialog(t, "");
        }
    }
    
    public void handleUncaught(Throwable t) {
        if ( DasApplication.getDefaultApplication().isHeadless() ) {
            t.printStackTrace();
        }
        else {
            checkJythonError(t);
            showExceptionDialog(t, UNCAUGHT);
        }
    }

    JythonScriptPanel scriptPanel=null;
    
    /**
     * indicate the script panel where errors can be shown.
     * @param scriptPanel 
     */
    public void setScriptPanel( JythonScriptPanel scriptPanel ) {
        this.scriptPanel= scriptPanel;
    }
    
    /**
     * if the error is a Jython exception, then show its location in the editor.
     * @param t 
     */
    private void checkJythonError( Throwable t ) {
        if ( t instanceof PyException && scriptPanel!=null ) {
            scriptPanel.support.annotateError( (PyException)t, 0, null );
        } else if ( scriptPanel!=null ) {
            scriptPanel.support.annotateError( t );
        }
    }
    
    private boolean checkOutOfMemoryError( Throwable t ) {
        if ( t==null ) return false;
        if ( t instanceof OutOfMemoryError ) {
            return true;
        } else if ( t instanceof PyException ) {
            if ( ((PyException)t).toString().contains("java.lang.OutOfMemory") ) {
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }
    
    private final Map<Integer,DiaDescriptor> dialogs= Collections.synchronizedMap( new HashMap<Integer, DiaDescriptor>() );

    private DiaDescriptor createDialog( final Throwable throwable, final boolean uncaught ) {
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
        final JTextArea messageArea = new JTextArea(12, 60);
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

        final JTextArea traceArea = new JTextArea(10, 60);
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
            @Override
            public void actionPerformed(ActionEvent e) {
                dialog.dispose();
                dialogs.remove( diaDescriptor.hash );
            }
        });

        details.addActionListener(new ActionListener() {
            @Override
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
            @Override
            public void actionPerformed(ActionEvent e) {
                String text = traceArea.getText();
                System.err.print(text); // Note this is useful when debugging in an IDE.
            }
        });

        submit.addActionListener( new ActionListener() {
            @Override
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
    
    private void showExceptionDialog( final Throwable t, String extraInfo ) {

        final boolean isUncaught= extraInfo.equals(UNCAUGHT);

        int hash= hashCode(t);
        
        DiaDescriptor dia1= dialogs.get(hash);
        
        String errorMessage = extraInfo + t.getClass().getName() + "\n"
            + (t.getMessage() == null ? "" : t.getMessage());

        // kludge for Jython errors
        if ( t.getClass().getName().contains("PyException" ) || t.getClass().getName().contains("PySyntaxError" )) {
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
            if ( ( dia1.hits % 100 ) == 0 ) {
                // this is a catastrophic problem, often where an event is thrown on the 
                // GUI thread itself.  Dump to stdout and reset.
                System.err.println("== Error hit "+dia1.hits +" times");
                t.printStackTrace( System.err );
            }
        }
        
        if ( checkOutOfMemoryError(t) ) {
            errorMessage= errorMessage + "\n\nThe wiki page at \"https://github.com/autoplot/documentation/md/outOfMemory.md\" might be helpful in resolving this issue.";
        }

        
        
        
        
        
        if ( dia1==null ) {
            dia1= createDialog( t, isUncaught );  // https://sourceforge.net/p/autoplot/bugs/2347/
        }

        
        
        
        
        
        final JDialog dialog = dia1.dialog;

        dialogs.put( hash, dia1 );
        dia1.hash= hash;

        dia1.textArea.setText(errorMessage);
        
        dialog.pack();
        dialog.setLocationRelativeTo( DasApplication.getDefaultApplication().getMainFrame() );
        dialog.setVisible(true);


    }

    /**
     * create a hashCode identifying the stack trace location.
     * @param ee the stack trace.
     * @return the hash
     */
    public static int hashCode( StackTraceElement[] ee ) {        
        int rteHash= 0;
        for ( int i=0; i<ee.length && i<5; i++ ) {
            rteHash= 31*rteHash + hashCode(ee[i]);
        }
        rteHash= Math.abs(rteHash) + ( ee.length>0 ? ee[0].getLineNumber() : 0 );
        return rteHash;
    }
    
    /**
     * create a hashCode identifying the stack trace location found
     * within the throwable.
     * @param t the throwable
     * @return the hash
     */
    public static int hashCode( Throwable t ) {
//        if ( t.getCause()!=null ) {
//            t= t.getCause();
//        }
        StackTraceElement[] ee= t.getStackTrace();
        return hashCode(ee);
    }

    private static int hashCode( StackTraceElement e ) {
        try {
            int result = 31*e.getClassName().hashCode() + e.getMethodName().hashCode();
            return result;
        } catch ( NullPointerException ex ) {
            return 1;
        }
    }

    private LogConsole lc;

    public void setLogConsole( LogConsole lc ) {
        this.lc= lc;
    }

    private String focusURI;
    public void setFocusURI( String uri ) {
        this.focusURI= uri;
    }

    private static void formatException( Document doc, Element parent, Throwable th ) {
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

    private static void formatBuildInfos( Document doc, Element parent, List<String> bis ) {
        Element pp= doc.createElement("buildInfos");
        try {
            Element tag= doc.createElement("releaseTag");
            tag.appendChild( doc.createTextNode(AboutUtil.getReleaseTag()) );
            pp.appendChild(tag);
        } catch ( IOException ex ) { logger.log(Level.WARNING,"",ex); }
        try {
            Element url= doc.createElement("buildUrl");
            url.appendChild( doc.createTextNode(AboutUtil.getJenkinsURL() ) );
            pp.appendChild(url);
        } catch ( IOException ex ) { logger.log(Level.WARNING,"",ex); }
        for ( String s: bis ) {
            Element jar= doc.createElement("jar");
            jar.appendChild( doc.createTextNode(s) );
            pp.appendChild(jar);
        }
        parent.appendChild(pp);

    }

    private static void formatSysProp( Document doc, Element parent,String prop ) {
        Element ele= doc.createElement("property");
        ele.setAttribute( "name", prop );
        String v= System.getProperty(prop);
        if ( v!=null ) {
            ele.setAttribute( "value",v );
        } else {
            ele.setAttribute( "value","(null)" );
        }
        parent.appendChild(ele);
    }

    private static void formatPlatform( Document doc, Element parent  ) {
        Element p= doc.createElement("platform");
        formatSysProp( doc, p, "java.version" );
        formatSysProp( doc, p, "java.vendor" );
        formatSysProp( doc, p, "os.name" );
        formatSysProp( doc, p, "os.arch" );
        formatSysProp( doc, p, "os.version" );
        formatSysProp( doc, p, "javawebstart.version" );
        formatSysProp( doc, p, AutoplotUI.SYSPROP_AUTOPLOT_RELEASE_TYPE );
        formatSysProp( doc, p, AutoplotUI.SYSPROP_AUTOPLOT_DISABLE_CERTS );
        
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
    public static final String AUTOPLOTRELEASETYPE = "autoplot.release.type";

    /**
     * format thread states and stack traces.
     * @param doc
     * @param parent 
     */
    private static void formatThreads( Document doc, Element parent ) {
        Element ele= doc.createElement("threads");
        Map<Thread,StackTraceElement[]> threads= Thread.getAllStackTraces();
        for ( Entry<Thread,StackTraceElement[]> ent: threads.entrySet() ) {
            Thread thread= ent.getKey();
            if ( thread.getName().matches("RequestProcessor\\[\\d+\\]") && thread.getState()==State.WAITING ) {
                continue;
            }
            Element ele1= doc.createElement("thread");
            ele1.setAttribute( "name", thread.getName() );
            ele1.setAttribute( "state", thread.getState().toString() );
            Element ste= doc.createElement("stackTrace");
            ele1.appendChild( ste );
            StackTraceElement[] st= ent.getValue();
            StringBuilder b= new StringBuilder();
            for (StackTraceElement st1 : st) {
                b.append(st1.toString()).append("\n");                
            }
            ste.appendChild( doc.createTextNode( b.toString() ) );
            ele.appendChild(ele1);
        }
        parent.appendChild(ele);
    }
    
    private static void formatUndos( Document doc, Element parent, UndoRedoSupport undo ) {
        Element ele= doc.createElement("states");
        for ( int i= undo.getDepth()-1; i>0; i-- ) {
            Element ele1= doc.createElement("undo");
            ele1.setAttribute("pos", String.valueOf(i) );
            ele1.appendChild( doc.createTextNode(undo.getLongUndoDescription(i) ) );
            ele.appendChild(ele1);
        }
        parent.appendChild(ele);
    }

    /**
     * data is a map containing the keys:<ul>
     * <li>THROWABLE, the throwable
     * <li>BUILD_INFO, string array of human-readable build information
     * <li>LOG_RECORDS, list of log records.
     * <li>USER_ID, user id.
     * <li>EMAIL, email.
     * <li>FOCUS_URI the current focus uri.
     * <li>PENDING_FOCUS_URI the pending focus uri 
     * <li>APP_COUNT the number of instances running.
     * <li>INCLSCREEN Boolean.TRUE if the user should include a screen shot.
     * <li>APP_MODEL the application object.
     * </ul>
     * @param data map of data
     * @param uncaught true if the exception was uncaught
     * @param userComments additional comments from the user.
     * @return the formatted report.
     */    
    public static String formatReport( Map<String,Object> data, boolean uncaught, String userComments ) {
        Throwable t= (Throwable)data.get(THROWABLE);
        if ( t==null ) t= new RuntimeException("");
        List<String> bis= (List<String>) data.get( BUILD_INFO );
        if ( bis==null ) bis= Collections.emptyList();
        List<LogRecord> recs= (List<LogRecord>) data.get( LOG_RECORDS );
        if ( recs==null ) recs= Collections.emptyList();
        return formatReport( t, bis, recs, data, uncaught, userComments );
    }
    
    /**
     * return the data contained, and empty string if no value or null is found.
     */
    private static String getStringData( Map<String,Object> data, String name ) {
        Object o= data.get(name);
        if ( o==null ) {
            return "";
        } else {
            return String.valueOf(o);
        }
    }
    
    /**
     * data is a map containing the keys:<ul>
     * <li>USER_ID, user id
     * <li>EMAIL, email
     * <li>FOCUS_URI the current focus uri.
     * <li>PENDING_FOCUS_URI the pending focus uri 
     * <li>APP_COUNT the number of instances running.
     * <li>INCLSCREEN Boolean.TRUE if the user should include a screen shot.
     * <li>APP_MODEL the application object.
     * </ul>
     * 
     * @param t the throwable
     * @param bis list of build information
     * @param recs list of log records
     * @param data map of data
     * @param uncaught true if the exception was uncaught
     * @param userComments additional comments from the user.
     * @return 
     */
    private static String formatReport( Throwable t, List<String> bis, List<LogRecord> recs, Map<String,Object> data, boolean uncaught, String userComments ) {

        ByteArrayOutputStream out= new ByteArrayOutputStream();

        try {
            Document doc= DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();

            Element e= doc.createElement("exceptionReport");
            e.setAttribute( "version", "1.1" );

            doc.appendChild(e);
            Element app= doc.createElement("applicationId");
            app.appendChild( doc.createTextNode("autoplot") );

            e.appendChild(app);

            Element v= doc.createElement("applicationVersion");
            v.appendChild( doc.createTextNode( APSplash.getVersion()) );

            e.appendChild(v);
            
            Element user= doc.createElement("userComments");
            user.appendChild( doc.createTextNode(userComments) );
            e.appendChild(user);

            Element userN= doc.createElement("userName");
            userN.appendChild( doc.createTextNode( getStringData( data, USER_ID ) ) );
            e.appendChild(userN);

            Element mail= doc.createElement("email");
            mail.appendChild( doc.createTextNode( getStringData( data, EMAIL ) ) );
            e.appendChild(mail);

            Element focus= doc.createElement("focusUri");
            focus.appendChild( doc.createTextNode( getStringData( data, FOCUS_URI) ) );
            e.appendChild(focus);

            Element ele;
            ele= doc.createElement("pendingFocusUri");
            ele.appendChild( doc.createTextNode( getStringData( data, PENDING_FOCUS_URI ) ) );
            e.appendChild(ele);

            ele= doc.createElement("appCount");
            ele.appendChild( doc.createTextNode( getStringData( data, APP_COUNT ) ) );
            e.appendChild(ele);
            
            if ( data.get(INCLDOM)==null || (Boolean)data.get( INCLDOM ) ) {
                ApplicationModel appModel= (ApplicationModel) data.get( APP_MODEL );
                ele= doc.createElement("sandbox");
                ele.appendChild( doc.createTextNode( appModel.isSandboxed() ? "true" : "false" ) );
                e.appendChild(ele);
            }
            
            formatException( doc, e, t );

            formatBuildInfos( doc, e, bis );

            formatPlatform( doc, e );

            ele= doc.createElement( "uncaught" );
            ele.appendChild( doc.createTextNode( String.valueOf(uncaught) ) );
            e.appendChild(ele);

            if ( data.get(INCLDOM)==null || (Boolean)data.get( INCLDOM ) ) {
                ApplicationModel appModel= (ApplicationModel) data.get( APP_MODEL );
                if ( appModel!=null ) {
                    Application state= (Application)appModel.getDocumentModel();

                    Element app1 = SerializeUtil.getDomElement( doc, (DomNode)state, StatePersistence.currentScheme(), false );

                    Element vap= doc.createElement("vap");
                    vap.appendChild(app1);

                    vap.setAttribute( "domVersion", StatePersistence.currentScheme().getId() );
                    try {
                        vap.setAttribute( "appVersionTag", AboutUtil.getReleaseTag() );
                    } catch ( IOException ex ) {
                        vap.setAttribute( "appVersionTag", ex.getMessage() );
                    }

                    Element dom= doc.createElement("dom");
                    dom.appendChild(vap);

                    e.appendChild(vap);

                }

                formatThreads(doc,e);
                
                UndoRedoSupport undoRedoSupport= (UndoRedoSupport)data.get( UNDO_REDO_SUPPORT );
                if ( undoRedoSupport!=null ) {
                    formatUndos( doc, e, undoRedoSupport );
                }
            }

            if ( data.get(INCLSCREEN)!=null && (Boolean)data.get( INCLSCREEN ) ) {
                ApplicationModel appModel= (ApplicationModel) data.get( APP_MODEL );
                if ( appModel!=null ) {
                    Window w= SwingUtilities.getWindowAncestor( appModel.getCanvas() );

                    BufferedImage img= new BufferedImage( w.getWidth(), w.getHeight(), BufferedImage.TYPE_INT_RGB );

                    w.print( img.getGraphics() );

                    ByteArrayOutputStream baos= new ByteArrayOutputStream();

                    try {
                        ImageIO.write( img, "png", baos );
                        baos.close();

                        byte[] array= baos.toByteArray();

                        String image64= Base64.getEncoder().encodeToString(array);

                        Element screen= doc.createElement("screenshot");
                        screen.setAttribute( "mimetype", "image/png" );

                        screen.appendChild( doc.createTextNode( "\n"+image64+"\n" ) );
                        e.appendChild(screen);

                    } catch ( IOException ex ) {
                        logger.log(Level.WARNING, null, ex );
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

//        byte[] bytes= out.toByteArray();
//        for ( int i=0; i<bytes.length-2; i++ ) {
//            if ( ((int)bytes[i] & 0xFF)==0xE2 ) {
//                if ( ((int)bytes[i+1] & 0xFF)==0x86 ) {
//                    if ( ((int)bytes[i+2] & 0xFF)==0x92 ) {
//                        System.err.println("here is the right arrow at index="+i);
//                    }
//                }
//            }
//        }
        // the above code verifies that Windows properly encodes the byte stream.
                
        String s;
        try {
            s= out.toString("UTF-8");
        } catch ( UnsupportedEncodingException ex ) {
            s= out.toString(); 
        }
        return s;
    }

    List<LogRecord> recs;
    List<String> bis;
    Map<String,Object> map;
    boolean uncaught;
    Throwable t;

    private synchronized String getReport( GuiExceptionHandlerSubmitForm form ) {
        String id= form.getUsernameTextField().getText().replaceAll(" ","_");
        if ( id.trim().equals("") ) id= "anon";
        map.put( USER_ID, id );

        String email= form.getEmailTextField().getText();
        map.put( EMAIL, email );

        map.put( INCLSCREEN, form.isAllowScreenshot() );
        String report= formatReport( t, bis, recs, map, uncaught, form.getUserTextArea().getText() );
        logger.log(Level.FINE, "indexOf arrow= {0}", report.indexOf( (char)8594 ));
        return report;

    }
        
    
    public void submitRuntimeException( Throwable t, boolean uncaught ) {
        submitDialog( t, uncaught, "Submit Runtime Error Exception Report" );
    }
    
    public void submitFeedback( Throwable t ) {
        submitDialog( t, false, "Submit Feedback" );
    }
    
    private void submitDialog( Throwable t, boolean uncaught, String message ) {
        
        int rteHash;
        rteHash= hashCode( t );
        
        Date now = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String eventId= sdf.format( now );

        String pendingFocus="N/A"; // many RTEs come from the selector where the string is not processed.
        Window w= ScriptContext.getViewWindow();
        if ( w instanceof AutoplotUI ) {
            AutoplotUI app= ((AutoplotUI)w);
            pendingFocus= app.getDataSetSelector().getValue();
        }     
        int appCount= AppManager.getInstance().getApplicationCount();
        
        bis= null;
        try {
            bis = AboutUtil.getBuildInfos();
        } catch (IOException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }

        recs=null;

        if ( lc!=null ) recs= lc.records;

        map=new HashMap();

        map.put( APP_MODEL, appModel );
        map.put( UNDO_REDO_SUPPORT, undoRedoSupport );
        
        String id;
        id= System.getProperty("user.name");

        map.put( USER_ID, id );
        map.put( EMAIL, "" );
        map.put( FOCUS_URI, focusURI );
        map.put( PENDING_FOCUS_URI, pendingFocus );
        map.put( APP_COUNT, appCount );
        
        this.uncaught= uncaught;
        this.t= t;

        String report= formatReport( t, bis, recs, map, uncaught, "USER COMMENTS" );

        String url = "https://cottagesystems.com/RTEReceiver/LargeUpload.jsp";

        GuiExceptionHandlerSubmitForm form= new GuiExceptionHandlerSubmitForm();
        form.setGuiExceptionHandler( this );

        boolean notsent= true;

        while ( notsent ) {

            form.getDataTextArea().setText( report );

            form.getUsernameTextField().setText( (String)map.get(USER_ID) );
            form.getEmailTextField().setText( (String)map.get(EMAIL) );

            String[] choices= { "Copy to Clipboard", "Save to File", "Cancel", "Switch to Email", "Submit" };
            
            boolean useEmail= "T".equals( System.getProperty("autoplot.emailrte") );
            if ( useEmail ) {
                choices[4]= "Submit Email";
                choices[3]= "Switch to Post";
            }
            
            Component parent= appModel==null ? null : SwingUtilities.getWindowAncestor(appModel.getCanvas());
            Icon icon= new ImageIcon( AutoplotUtil.getAutoplotIcon() );
            int option= JOptionPane.showOptionDialog( parent, form, message,
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, icon, choices, choices[4] )  ;
            switch (option) {
                case 2:
                    return;
                case 1:
                    // save to file
                    
                    report= getReport( form );
                    JFileChooser chooser= new JFileChooser();
                    chooser.setFileFilter( new FileNameExtensionFilter("xml files", new String[] { "xml" } ) );
                    String fname= String.format( "rte_%010d_%s_%s.xml", rteHash, eventId, id );
                    chooser.setSelectedFile( new File(fname) );
                    if ( chooser.showSaveDialog(form) == JFileChooser.APPROVE_OPTION ) {
                        try {
                            File f= chooser.getSelectedFile();
                            PrintWriter out=null;
                            try {
                                out= new PrintWriter(f);
                                out.write(report);
                            } finally {
                                if ( out!=null ) out.close();
                            }
                            notsent= false;
                        } catch ( IOException ex ) {
                            JOptionPane.showMessageDialog( null, ex.toString() );
                        }
                    }   
                    break;
                case 3:
                    // switch to email
                    if ( useEmail ) {
                        System.setProperty( "autoplot.emailrte", "F" );
                    } else {
                        System.setProperty( "autoplot.emailrte", "T" );
                    }   
                    break;
                case 4:
                    // submit
                    report= getReport( form );
                    if ( useEmail ) {
                        try {
                            SendEmailFeedback.sendEmail(report);
                            notsent= false;
                        } catch (AddressException ex) {
                            JOptionPane.showMessageDialog( null, ex.toString() );
                        } catch (MessagingException ex) {
                            JOptionPane.showMessageDialog( null, ex.toString() );
                        }
                        
                    } else {
                        //TODO soon: this needs to be done off the event thread.  It causes the app to hang when there is no internet.
                        report= formatReport( t, bis, recs, map, uncaught, form.getUserTextArea().getText() );
                        
                        String sid= (String)map.get("USER_ID");
                        sid= safe( sid.replaceAll(" ","").replaceAll("_","") );
                        fname=  String.format( "rte_%010d_%s_%s.xml", rteHash, eventId, sid );
                        
                        HttpClient client = new HttpClient();
                        client.getHttpConnectionManager().getParams().setConnectionTimeout(3000);
                        PostMethod postMethod = new PostMethod(url);
                        
                        Charset ch= Charset.forName("UTF-8");
                        Part[] parts= {
                            new StringPart( "secret", "secret" ),
                            new StringPart( "todo", "upload" ),
                            new FilePart( "uploadfile", new ByteArrayPartSource( fname, report.getBytes( ch ) ), "text/xml", ch.name() ),
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
                    }   
                    break;
                case 0:
                    id= form.getUsernameTextField().getText().replaceAll(" ","_");
                    if ( id.trim().equals("") ) id= "anon";
                    map.put( USER_ID, id );
                    String email= form.getEmailTextField().getText();
                    map.put( EMAIL, email );
                    report= formatReport( t, bis, recs, map, uncaught, form.getUserTextArea().getText() );
                    StringSelection stringSelection = new StringSelection(report);
                    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                    clipboard.setContents(stringSelection, new ClipboardOwner() {
                        @Override
                        public void lostOwnership(Clipboard clipboard, Transferable contents) {
                        }
                    } );
                    // make them hit cancel...
                    break;
                default:
                    break;
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

    /**
     * try to exercise any synchronization problems we would see, after remove excessive synchronization blocks.
     */
    private static void syncroTest() {
        final ExceptionHandler eh= new GuiExceptionHandler();
        Runnable run1= new Runnable() {
            @Override
            public void run() {
                for ( int i=0; i<10; i++ ) {
                    eh.handle( new RuntimeException("sync test ex 1!") ); 
                }
            }
        };
        Runnable run2= new Runnable() {
            @Override
            public void run() {
                for ( int i=0; i<10; i++ ) {
                    eh.handle( new RuntimeException("sync test ex 2!") ); 
                }
            }
        };
        new Thread(run1).start();
        new Thread(run2).start();
        
    }
    public static void main( String[] args ) {
        syncroTest();
        ExceptionHandler eh= new GuiExceptionHandler();
        eh.handle( new RuntimeException("Bad Deal!") ); // these three have different hash codes and are considered different.
        eh.handle( new RuntimeException("Bad Deal!") );
        eh.handle( new RuntimeException("Bad Deal!") );
        for ( int i=0; i<3; i++ ) {
            eh.handle( new RuntimeException("Bad Deal 2!") ); // these three have the same hash codes and are considered the same.
        }

        ExceptionHandler eh2= new GuiExceptionHandler();
        eh2.handleUncaught( new RuntimeException("Bad Deal!") );
    }

}
