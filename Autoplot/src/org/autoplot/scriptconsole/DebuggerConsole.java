/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.scriptconsole;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.python.core.Py;
import org.python.core.PyFile;
import org.python.core.PyObject;
import org.python.core.PyString;
import org.python.core.PyStringMap;
import org.python.util.PythonInterpreter;
import org.das2.qds.ops.Ops;

/**
 * little GUI for printing the PDB output and sending commands to it.
 * See https://autoplot.org/developer.jython.pdb
 * @author jbf
 */
public class DebuggerConsole extends javax.swing.JPanel {

    private static final PipedOutputStream myout = new PipedOutputStream();
    private static JDialog dialog;
    
    private static Thread workerThread=null;
    private static java.util.concurrent.BlockingQueue<String> queue;
    
    private static final Logger logger= Logger.getLogger("autoplot.jython.console");
    
    private static PipedInputStream pin;
    
    private PyObject printObj;
    
    /**
     * set this to true to evaluate expressions on event thread.  This fails off the event thread, but I'm not sure why.
     * On the event thread, things hang when I try to do tooltip lookups.
     * 
     * The problem is I need to have a single thread that sends messages to the PipedOutputStream.  Ed points out
     * java.util.concurrent.BlockingQueue, which could be used to post "step" and "where" messages to the single thread.
     * 
     * Ed also proposes that this be rewritten to make an InputStream that you post messages to, and short
     * of that you would just post messages to an object defined here.
     * 
     */
    
    private void initChannels() {
        try {
            queue= new LinkedBlockingQueue<>();
            Runnable run= new Runnable() {
                @Override
                public void run() {
                    Runnable doRun;
                    while ( true ) {
                        try {
                            final String cmd= queue.take();
                            doRun= new Runnable() { public void run() {
                                try {
                                    myout.write(cmd.getBytes());
                                    myout.flush();
                                    print(cmd);
                                } catch (IOException ex) {
                                    if ( ex.getMessage().contains("Read end dead") ) {
                                        finished();
                                        logger.log(Level.FINER, null, ex);
                                    } else {
                                        logger.log(Level.SEVERE, null, ex);
                                    }
                                }
                            } };                            
                            doRun.run();
                        } catch (InterruptedException ex) {
                            logger.log(Level.SEVERE, null, ex);
                        }
                        
                    }
                }
            };
            workerThread= new Thread(run,"debuggerConsoleWorker");
            workerThread.start();
            pin= new PipedInputStream(myout);
            Py.getSystemState().stdin= new PyFile( pin ); 
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }        
    }
    
    private static DebuggerConsole instance;
    
    /**
     * get the single instance of the DebuggerConsole.
     * @param panel panel to use for the focus of the JDialog created the first time.
     * @return the DebuggerConsole.
     */
    public static DebuggerConsole getInstance( JPanel panel ) {
        if ( instance==null ) {
            instance= new DebuggerConsole();
            instance.initChannels();
            JDialog d= new JDialog( SwingUtilities.getWindowAncestor(panel), "Jython Debugger" );
            d.setModal(false);
            d.getContentPane().add(instance);
            d.pack();
            d.setVisible(true);
            d.setDefaultCloseOperation( JDialog.HIDE_ON_CLOSE );
            dialog= d;
        } else {
            dialog.setVisible(true);
        }
        
        return instance;
    }
    
    /**
     * set the interpreter we to control.
     * @param out 
     */
    public void setInterp( PythonInterpreter out ) {
        this.out= out;
        Py.getSystemState().stdin= new PyFile( pin );
    }
    
    /**
     * Creates new form DebuggerConsole
     */
    private DebuggerConsole( ) {
        initComponents();
    }
    
    PythonInterpreter out;
    
    public void println( String s ) {
        print(s);
        jythonOutputTextArea.append("\n");
    }
    
    public void print( String s ) {
        jythonOutputTextArea.append(s);
    }
    
    /**
     * standard mode output.  
     * @see ScriptPanelSupport
     */
    protected final Object STATE_OPEN="OPEN";
    /**
     * collect the prompt so that it is not echoed.
     * @see ScriptPanelSupport
     */
    protected final Object STATE_FORM_PDB_PROMPT="PROMPT";
    /**
     * collect the prompt so that it is not echoed.
     * @see ScriptPanelSupport
     */
    protected final Object STATE_RETURN_INIT_PROMPT="RETURN";
    /**
     * pdb output encountered.
     * @see ScriptPanelSupport
     */
    protected final Object STATE_PDB="PDB";

    /**
     * pdb response
     * @see ScriptPanelSupport
     */
    protected final Object STATE_FORM_PDB_RESPONSE="RESPONSE";
            
    
    private String getCharsForState( String s, Object state ) {
        StringBuilder result= new StringBuilder();
        if ( state.toString().equals(STATE_OPEN) ) {
            for ( int i=0; i<s.length(); i++ ) result.append("O");
        } else if ( state.toString().equals(STATE_FORM_PDB_PROMPT) ) {
            for ( int i=0; i<s.length(); i++ ) result.append("P");
        } else if ( state.toString().equals(STATE_FORM_PDB_RESPONSE) ) {
            for ( int i=0; i<s.length(); i++ ) result.append("R");
        } else if ( state.toString().equals(STATE_PDB) ) {
            for ( int i=0; i<s.length(); i++ ) result.append("D");
        } else if ( state.toString().equals(STATE_RETURN_INIT_PROMPT) ) {
            for ( int i=0; i<s.length(); i++ ) result.append("I");
        }
        return result.toString();
    }
    
    public void print( String s, Object state ) {
        currentModeLabel.setText( state.toString() );
        jythonOutputTextArea.append(s);
        String charsForState= getCharsForState( s, state );
        jythonStateTextArea.append(charsForState);
        if ( s.endsWith("\n") ) {
            jythonStateTextArea.append("\n");
            jythonOutputTextArea.setCaretPosition( jythonOutputTextArea.getDocument().getLength());
            jythonStateTextArea.setCaretPosition( jythonStateTextArea.getDocument().getLength());
        }
    }
    
    /**
     * This method is called from within the constructor to initialize the form. WARNING: Do NOT modify this code. The content of
     * this method is always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        nextButton = new javax.swing.JButton();
        upButton = new javax.swing.JButton();
        whereButton = new javax.swing.JButton();
        jSplitPane1 = new javax.swing.JSplitPane();
        jScrollPane1 = new javax.swing.JScrollPane();
        jythonOutputTextArea = new javax.swing.JTextArea();
        jScrollPane2 = new javax.swing.JScrollPane();
        jythonStateTextArea = new javax.swing.JTextArea();
        stepButton = new javax.swing.JButton();
        pdbInput = new javax.swing.JTextField();
        continueButton = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        currentModeLabel = new javax.swing.JLabel();

        nextButton.setText("Next");
        nextButton.setToolTipText("step to the next line, stepping over called function");
        nextButton.setEnabled(false);
        nextButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nextButtonActionPerformed(evt);
            }
        });

        upButton.setText("Up");
        upButton.setToolTipText("Continue until returning to the caller");
        upButton.setEnabled(false);
        upButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                upButtonActionPerformed(evt);
            }
        });

        whereButton.setText("Where");
        whereButton.setToolTipText("Refresh the debug position (Currently not functional)");
        whereButton.setEnabled(false);
        whereButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                whereButtonActionPerformed(evt);
            }
        });

        jSplitPane1.setDividerLocation(300);

        jythonOutputTextArea.setColumns(20);
        jythonOutputTextArea.setFont(new java.awt.Font("DejaVu Sans", 0, 10)); // NOI18N
        jythonOutputTextArea.setRows(5);
        jScrollPane1.setViewportView(jythonOutputTextArea);

        jSplitPane1.setLeftComponent(jScrollPane1);

        jythonStateTextArea.setColumns(20);
        jythonStateTextArea.setFont(new java.awt.Font("FreeMono", 0, 10)); // NOI18N
        jythonStateTextArea.setRows(5);
        jScrollPane2.setViewportView(jythonStateTextArea);

        jSplitPane1.setRightComponent(jScrollPane2);

        stepButton.setText("Step");
        stepButton.setToolTipText("Run the current line stepping to the next line or into a function.");
        stepButton.setEnabled(false);
        stepButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                stepButtonActionPerformed(evt);
            }
        });

        pdbInput.setToolTipText("Enter expression, return will evaluate");
        pdbInput.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pdbInputActionPerformed(evt);
            }
        });

        continueButton.setText("Continue");
        continueButton.setToolTipText("Continue execution until breakpoint is hit.  (Currently not functional)");
        continueButton.setEnabled(false);
        continueButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                continueButtonActionPerformed(evt);
            }
        });

        jLabel1.setText("Current Mode:");

        currentModeLabel.setText("currentModeLabel");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jSplitPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 586, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(stepButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(nextButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(upButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(whereButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(continueButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(pdbInput))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(currentModeLabel)
                        .addGap(0, 0, Short.MAX_VALUE))))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(nextButton)
                    .addComponent(upButton)
                    .addComponent(whereButton)
                    .addComponent(stepButton)
                    .addComponent(pdbInput, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(continueButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(currentModeLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jSplitPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 252, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void nextButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nextButtonActionPerformed
        next();
    }//GEN-LAST:event_nextButtonActionPerformed

    private void upButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_upButtonActionPerformed
        up();
    }//GEN-LAST:event_upButtonActionPerformed

    private void whereButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_whereButtonActionPerformed
        queue.add("where\n");
    }//GEN-LAST:event_whereButtonActionPerformed

    private void stepButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_stepButtonActionPerformed
        queue.add("step\n");
    }//GEN-LAST:event_stepButtonActionPerformed

    private void pdbInputActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pdbInputActionPerformed
        queue.add(pdbInput.getText()+"\n");
    }//GEN-LAST:event_pdbInputActionPerformed

    private void continueButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_continueButtonActionPerformed
        continu();
    }//GEN-LAST:event_continueButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton continueButton;
    private javax.swing.JLabel currentModeLabel;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JSplitPane jSplitPane1;
    private javax.swing.JTextArea jythonOutputTextArea;
    private javax.swing.JTextArea jythonStateTextArea;
    private javax.swing.JButton nextButton;
    private javax.swing.JTextField pdbInput;
    private javax.swing.JButton stepButton;
    private javax.swing.JButton upButton;
    private javax.swing.JButton whereButton;
    // End of variables declaration//GEN-END:variables

    public void next() {
        queue.add("next\n");
    }
    
    public void up() {
        queue.add("up\n");
    }
    
    public void continu() {
        queue.add("continue\n");
    }
    
    /**
     * set the expression to evaluate
     * @param expr 
     * @return the evaluation, if possible.
     */
    public PyObject setEval( String expr ) {
        expr= expr.trim();
        PyObject lo= out.getLocals();
        if ( lo instanceof PyStringMap ) {
            PyStringMap psm= (PyStringMap)lo;
            printObj= psm.get(new PyString(expr) );
        }
        if ( printObj==null || printObj==Py.None ) {
            if ( Ops.safeName(expr).equals(expr) ) {
                printObj= new PyString("Name error: "+expr);
            } else {
                printObj= new PyString("expressions cannot be evaluated");
            }
        }
        return printObj;
        //if ( printObj==null ) {
        //    printObj= out.eval(expr);
        //}
    }

    /**
     * get the evaluation
     * @param expr 
     */
    public PyObject getEval() {
        return printObj;
    }

    void finished() {
        stepButton.setEnabled(false);
        nextButton.setEnabled(false);
        upButton.setEnabled(false);
        whereButton.setEnabled(false);
        continueButton.setEnabled(false);
        pdbInput.setEnabled(false);
    }
    
    void started() {
        stepButton.setEnabled(true);
        nextButton.setEnabled(true);
        upButton.setEnabled(true);
        whereButton.setEnabled(true);
        continueButton.setEnabled(true);
        pdbInput.setEnabled(true);
    }
}
