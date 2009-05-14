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
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.DasApplication;
import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.XMLEncoder;
import java.io.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.logging.LogRecord;
import java.util.logging.XMLFormatter;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.multipart.ByteArrayPartSource;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.StringPart;
import org.das2.system.ExceptionHandler;
import org.das2.util.AboutUtil;

/**
 *
 * @author  jbf
 */
public final class GuiExceptionHandler implements ExceptionHandler {

    //private static JDialog dialog;
    //private static JTextArea messageArea;
    //private static JTextArea traceArea;
    private static final String UNCAUGHT = "An unexpected error has occurred.  " +
        "The system may not be able to recover properly.  You can inspect" +
        "information about the crash with the Show Details button, and" +
        "submit an automatic bug entry.";

    private JButton submitButton;
    
    /* this is public so that the AWT thread can create it */
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

    private void showExceptionDialog(final Throwable t, String extraInfo) {
        String errorMessage = extraInfo + t.getClass().getName() + "\n"
            + (t.getMessage() == null ? "" : t.getMessage());        
        final JDialog dialog = new JDialog( DasApplication.getDefaultApplication().getMainFrame() );        
        dialog.setTitle("Error in das2");
        dialog.setModal(false);
        dialog.setResizable(true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        final JTextArea messageArea = new JTextArea(10, 40);
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);
        messageArea.setEditable(false);
        messageArea.setText(errorMessage);
        JScrollPane message = new JScrollPane(messageArea);
        message.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(message, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton ok = new JButton("Ok");
        final JToggleButton details = new JToggleButton("Show Details");
        buttonPanel.add(ok);
        buttonPanel.add(details);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        dialog.getContentPane().add(mainPanel, BorderLayout.CENTER);
        
        final JTextArea traceArea = new JTextArea(10, 40);
        traceArea.setLineWrap(false);
        traceArea.setEditable(false);
        traceArea.setTabSize(4);
        
        StringWriter writer = new StringWriter();
        t.printStackTrace(new PrintWriter(writer));
        traceArea.setText(writer.toString());
        
        final JPanel stackPane = new JPanel(new BorderLayout());
        stackPane.add(new JScrollPane(traceArea), BorderLayout.NORTH);
        stackPane.setBorder(new javax.swing.border.EmptyBorder(10, 10, 10, 10));
        JPanel buttonPanel2 = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel2.setBorder(new javax.swing.border.EmptyBorder(10, 0, 0, 0));
        JButton dump = new JButton("Dump to STDERR");
        JButton save = new JButton("Save to file");
        JButton submit= new JButton("Submit RTE");

        buttonPanel2.add(dump);
        buttonPanel2.add(save);
        buttonPanel2.add(submit);

        submitButton= submit;
        
        stackPane.add(buttonPanel2, BorderLayout.SOUTH);
        Dimension size = message.getPreferredSize();
        size.width = stackPane.getPreferredSize().width;
        message.setPreferredSize(size);
        
        ok.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                dialog.dispose();
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
                System.err.print(text);
            }
        });

        save.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    JFileChooser chooser = new JFileChooser();
                    int result = chooser.showSaveDialog(dialog);
                    if (result == chooser.APPROVE_OPTION) {
                        File selected = chooser.getSelectedFile();
                        PrintWriter out = new PrintWriter(new FileOutputStream(selected));
                        t.printStackTrace(out);
                        out.close();
                    }
                }
                catch (IOException ioe) {
                    handle(ioe);
                }
            }
        });

        submit.addActionListener( new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    submitRuntimeException(t);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog( stackPane, "Unable to send the data! "+ex );
                }
            }
        });
        
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
    }

    private static int hashCode( StackTraceElement e ) {
        int result = 31*e.getClassName().hashCode() + e.getMethodName().hashCode();
        result = 31*result + e.getLineNumber();
        return result;
    }

    private LogConsole lc;

    public void setLogConsole( LogConsole lc ) {
        this.lc= lc;
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

    private void formatException( StringBuffer sb, Throwable th ) {
	    sb.append("  <exception>\n");
        sb.append("    <type>" );
        escape( sb, th.getClass().getName() );
        sb.append("    </type>" );
	    sb.append("    <message>");
	    escape(sb, th.toString() );
	    sb.append("</message>\n");
	    StackTraceElement trace[] = th.getStackTrace();
	    for (int i = 0; i < trace.length; i++) {
	 	StackTraceElement frame = trace[i];
		sb.append("    <frame>\n");
		sb.append("      <class>");
		escape(sb, frame.getClassName());
		sb.append("</class>\n");
		sb.append("      <method>");
		escape(sb, frame.getMethodName());
		sb.append("</method>\n");
 	        // Check for a line number.
		if (frame.getLineNumber() >= 0) {
		    sb.append("      <line>");
		    sb.append(frame.getLineNumber());
		    sb.append("</line>\n");
		}
	        sb.append("    </frame>\n");
	    }
        sb.append("    <toString><![CDATA[\n");
        StringWriter sw= new StringWriter();
        th.printStackTrace( new PrintWriter( sw ) );
        sb.append( sw.toString() );
        sb.append("]]>\n");
        sb.append("    </toString>\n");
	    sb.append("  </exception>\n");

    }

    private void formatBuildInfos( StringBuffer buf, List<String> bis ) {
        buf.append( "  <buildInfos>\n");
        for ( String s: bis ) {
            buf.append( "    <jar>" );
            escape(buf,s);
            buf.append( "</jar>\n" );
        }
        buf.append( "  </buildInfos>\n");

    }

    private void formatSysProp( StringBuffer buf, String prop ) {
        buf.append("     <property name=\""+prop+"\" value=\"" + System.getProperty(prop) + "\" />\n" );
    }
    private void formatPlatform( StringBuffer buf ) {
        buf.append("  <platform>\n");
        formatSysProp( buf, "java.version" );
        formatSysProp( buf, "java.vendor" );
        formatSysProp( buf, "os.name" );
        formatSysProp( buf, "os.arch" );
        formatSysProp( buf, "os.version" );
        buf.append("  </platform>\n");
    }
    
    private String formatReport( Throwable t, List<String> bis, List<LogRecord> recs ) {
        StringBuffer buf= new StringBuffer();
        buf.append("<?xml version=\"1.0\"");


        buf.append(" encoding=\"");
        buf.append("US-ASCII");
        buf.append("\"");
        buf.append(" ?>\n");

        buf.append("<exceptionReport>\n");

        formatException( buf, t );

        formatBuildInfos( buf, bis );

        formatPlatform( buf );
        
        if ( recs!=null ) {
            buf.append( "  <log>");
            XMLFormatter formatter= new XMLFormatter();
            for ( LogRecord lr: recs ) {
                buf.append( formatter.format(lr) );
            }
            buf.append( "  </log>");
        }
        buf.append("</exceptionReport>\n");
        return buf.toString();
    }

    public void submitRuntimeException( Throwable t) throws IOException {
        int rteHash= 0;
        StackTraceElement[] ee= t.getStackTrace();
        for ( int i=ee.length-1; i>=0 && i>ee.length-5; i-- ) {
            rteHash= 31*rteHash + hashCode(ee[i]);
        }
        rteHash= Math.abs(rteHash);
        
        Date now = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String eventId= sdf.format( now );

        List<String> bis= null;
        try {
             bis = AboutUtil.getBuildInfos();
        } catch (IOException ex) {
            Logger.getLogger(GuiExceptionHandler.class.getName()).log(Level.SEVERE, null, ex);
        }

        List<LogRecord> recs=null;

        if ( lc!=null ) recs= lc.records;

        String report= formatReport(t, bis, recs);

        String url =
         "http://aurora.gmu.edu:8080/RTEReceiver/LargeUpload.jsp";

        HttpClient client = new HttpClient();
        PostMethod postMethod = new PostMethod(url);

        Part[] parts= {
            new StringPart( "secret", "secret" ),
            new StringPart( "todo", "upload" ),
            new FilePart( "uploadfile", new ByteArrayPartSource("rte_"+rteHash+"_" + eventId + ".xml", report.getBytes() ) ),
        };

        postMethod.setRequestEntity(
                new MultipartRequestEntity( parts, postMethod.getParams() ));

        int statusCode1 = client.executeMethod(postMethod);
        if ( statusCode1==200 ) {
            this.submitButton.setEnabled(false);
        } else {
            System.err.println( postMethod.getStatusLine() );
        }

        postMethod.releaseConnection();


    }

    public static void main( String[] args ) {
        new GuiExceptionHandler().handle( new RuntimeException("Bad Deal!") );
    }

}
