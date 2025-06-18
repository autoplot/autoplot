
/*
 * RunScriptPanel.java
 *
 * Created on Jun 21, 2011, 5:22:53 PM
 */

package org.autoplot;

import java.awt.Window;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JCheckBox;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.autoplot.jythonsupport.JythonRefactory;
import org.das2.util.monitor.ProgressMonitor;
import org.python.util.InteractiveInterpreter;
import org.autoplot.datasource.AutoplotSettings;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.jythonsupport.ui.EditorTextPane;
import org.das2.components.DasProgressPanel;

/**
 *
 * @author jbf
 */
public class RunScriptPanel extends javax.swing.JPanel {

    private static final Logger logger= org.das2.util.LoggerManager.getLogger("autoplot.jython");

    org.autoplot.jythonsupport.ui.EditorTextPane textArea;

    /** Creates new form RunScriptPanel */
    public RunScriptPanel() {
        initComponents();
        this.jScrollPane1.getVerticalScrollBar().setUnitIncrement(12);
        textArea = new org.autoplot.jythonsupport.ui.EditorTextPane();
        scriptPanel.add( textArea );
        textArea.setEditable(false);
    }

    public EditorTextPane getTextArea() {
        return textArea;
    }

    public static void runScript( ApplicationModel model, File ff, ProgressMonitor mon ) {
        try {
            InputStream in= new FileInputStream(ff);
            StringBuilder buf= new StringBuilder();
            try (BufferedReader read = new BufferedReader( new InputStreamReader(in) )) {
                String line= read.readLine();
                while ( line!=null ) {
                    buf.append(line).append("\n");
                    line= read.readLine();
                }
            }
            if ( buf.length()>0  ) {
                InteractiveInterpreter interp = 
                        JythonUtil.createInterpreter(true, false, model.getDom(), mon );
                interp.exec( JythonRefactory.fixImports(buf.toString(),ff.getName())  );
            } else {
                throw new IllegalArgumentException("file was empty: "+ff );
            }
            mon.finished();
        } catch (IOException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }

    }

    /**
     * return the checkbox indicating that the script should be added as a tool.
     * @return 
     */
    public JCheckBox getToolsCB() {
        return toolsCB;
    }

    /**
     * load the file into the panel for review.
     * @param ff
     * @throws IOException
     */
    protected void loadFile( final File ff) throws IOException {
        final String src= EditorTextPane.loadFileToString(ff);
        Runnable run= new Runnable() {
            @Override
            public void run() {
                try {
                    Document d = getTextArea().getDocument();
                    d.remove( 0, d.getLength() );
                    d.insertString( 0, src, null );
                    if ( ff.getCanonicalPath().startsWith( new File( AutoplotSettings.settings().resolveProperty(AutoplotSettings.PROP_AUTOPLOTDATA), "tools" ).getCanonicalPath() ) ) {
                        toolsCB.setEnabled(false);
                    }
                    scriptFilename.setText(ff.toString());

                } catch ( IOException | BadLocationException ex ) {

                }
            }
        };
        if ( SwingUtilities.isEventDispatchThread() ) {
            run.run();
        } else {
            SwingUtilities.invokeLater(run);
        }
    }
    
    /**
     * load the file into the panel for review, asynchronously.
     * @param window the dialog parent for a progress bar.
     * @param script the script location.
     * @throws IOException
     */
    protected void loadFileSoon( final Window window, final String script ) throws IOException {
        Runnable run= new Runnable() {
            @Override
            public void run() {
                try {
                    final File ff = DataSetURI.getFile( DataSetURI.getURI(script), DasProgressPanel.createFramed( window,"downloading script"));
                    loadFile(ff);
                } catch (URISyntaxException ex) {
                    logger.log(Level.SEVERE, null, ex);
                } catch (IOException ex) {
                    try {
                        logger.log(Level.SEVERE, null, ex);
                        Document d = getTextArea().getDocument();
                        d.remove( 0, d.getLength() );
                        d.insertString( 0, "unable to load script", null );
                        scriptFilename.setText("unable to load script");
                    } catch (BadLocationException ex1) {
                        Logger.getLogger(RunScriptPanel.class.getName()).log(Level.SEVERE, null, ex1);
                    }
                }
            }
        };
        new Thread(run,"loadScriptAsynchronously").start();
    }
    

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jLabel1 = new javax.swing.JLabel();
        toolsCB = new javax.swing.JCheckBox();
        jScrollPane1 = new javax.swing.JScrollPane();
        scriptPanel = new javax.swing.JPanel();
        scriptFilename = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();

        jLabel1.setText("Run the script:");

        toolsCB.setText("Add to Tools menu");
        toolsCB.setToolTipText("Add to tools menu for convenient access.");

        scriptPanel.setLayout(new java.awt.BorderLayout());
        jScrollPane1.setViewportView(scriptPanel);

        scriptFilename.setText("<html><i>script filename ");

        jLabel2.setText("Make sure the script does not contain malicious code.");
        jLabel2.setToolTipText("<html>Autoplot keeps track of what scripts you have run, and when <br>things change with a script you will be asked to verify<br>the script.  Scripts can be made which delete files, so be<br>sure to only run scripts from people you trust.\n");

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 409, Short.MAX_VALUE)
                    .add(jLabel1)
                    .add(scriptFilename, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 409, Short.MAX_VALUE)
                    .add(layout.createSequentialGroup()
                        .add(12, 12, 12)
                        .add(toolsCB))
                    .add(jLabel2, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 409, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(jLabel1)
                .add(3, 3, 3)
                .add(scriptFilename, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 283, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(toolsCB)
                .add(11, 11, 11)
                .add(jLabel2)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JLabel scriptFilename;
    private javax.swing.JPanel scriptPanel;
    private javax.swing.JCheckBox toolsCB;
    // End of variables declaration//GEN-END:variables


}
