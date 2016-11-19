
package org.autoplot;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.das2.components.DasProgressPanel;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.util.LoggerManager;
import org.das2.util.filesystem.FileSystem;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.python.util.InteractiveInterpreter;
import org.virbo.autoplot.JythonUtil;
import org.virbo.autoplot.dom.Application;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.URISplit;
import org.virbo.jythonsupport.ui.Util;

/**
 *
 * @author jbf
 */
public class BatchMaster extends javax.swing.JPanel {

    private final Logger logger= LoggerManager.getLogger("jython.batchmaster");
    
    /**
     * Creates new form MiracleMashMachine
     * @param dom
     */
    public BatchMaster( final Application dom ) {
        initComponents();
        /**
         * register the browse trigger to the same action, because we always browse.
         */
        dataSetSelector1.registerBrowseTrigger( "(.*)\\.jy(\\?.*)?", new AbstractAction( "Review Script" ) {
            @Override
            public void actionPerformed( ActionEvent ev ) {
                org.das2.util.LoggerManager.logGuiEvent(ev);                    
                String s= dataSetSelector1.getValue();
                Map<String,String> args;
                try {
                    URISplit split= URISplit.parse(s);        //bug 1408--note runScript doesn't account for changes made to the GUI.
                    args= URISplit.parseParams(split.params);
                    Map<String,String> env= new HashMap<>();
                    File scriptFile= DataSetURI.getFile(s,new NullProgressMonitor());
                    if ( JOptionPane.OK_OPTION==JythonUtil.showScriptDialog( 
                            BatchMaster.this, 
                            new HashMap<String, Object>(), 
                            scriptFile, 
                            args, 
                            enabled, 
                            split.resourceUri ) ) {
                        // do nothing.
                        
                    }
                } catch ( IOException ex ) { 
                    throw new RuntimeException(ex);
                }
            }
        });
        
        dataSetSelector1.registerActionTrigger( "(.*)\\.jy(\\?.*)?", new AbstractAction( "Review Script" ) {
            @Override
            public void actionPerformed( ActionEvent ev ) {
                org.das2.util.LoggerManager.logGuiEvent(ev);                    
                try {
                    String scriptName= dataSetSelector1.getValue();
                    if ( !scriptName.endsWith(".jy") ) {
                        JOptionPane.showMessageDialog( BatchMaster.this, "script must end in .jy: "+scriptName );
                        return;
                    }

                    URISplit split= URISplit.parse(scriptName);
                    pwd= split.path;

                    Map<String,String> params= URISplit.parseParams(split.params);
                    Map<String,Object> env= new HashMap<>();

                    DasProgressPanel monitor= DasProgressPanel.createFramed( SwingUtilities.getWindowAncestor(BatchMaster.this), "download script");
                    File scriptFile= DataSetURI.getFile( split.file, monitor );
                    String script= readScript( scriptFile );

                    Map<String,org.virbo.jythonsupport.JythonUtil.Param> parms= Util.getParams( env, script, URISplit.parseParams(split.params), new NullProgressMonitor() );

                    String[] items= new String[parms.size()+1];
                    int i=0;
                    items[0]="";
                    for ( Entry<String,org.virbo.jythonsupport.JythonUtil.Param> p: parms.entrySet() ) {
                        items[i+1]= p.getKey();
                        i=i+1;
                    }
                    ComboBoxModel m1= new DefaultComboBoxModel(Arrays.copyOfRange(items,1,items.length));
                    param1NameCB.setModel(m1);
                    ComboBoxModel m2= new DefaultComboBoxModel(items);
                    param2NameCB.setModel(m2);
                } catch (IOException ex) {
                    Logger.getLogger(BatchMaster.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        });
        
        dataSetSelector1.setPromptText("Enter the name of a Jython script");
        dataSetSelector1.setRecent( Collections.singletonList("http://autoplot.org/data/script/examples/parameters.jy") );
        
        Map<String,String> recentJy= dom.getController().getApplicationModel().getRecent("*.jy",20);
        List<String> recentUris= new ArrayList<>(recentJy.size());
        for ( Entry<String,String> recentItem : recentJy.entrySet() ) {
            recentUris.add( recentItem.getKey() );
        }
        dataSetSelector1.setRecent( recentUris );
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane2 = new javax.swing.JScrollPane();
        jList2 = new javax.swing.JList<>();
        goButton = new javax.swing.JButton();
        jScrollPane3 = new javax.swing.JScrollPane();
        param1Values = new javax.swing.JTextArea();
        jScrollPane1 = new javax.swing.JScrollPane();
        param2Values = new javax.swing.JTextArea();
        dataSetSelector1 = new org.virbo.datasource.DataSetSelector();
        jButton2 = new javax.swing.JButton();
        messageLabel = new javax.swing.JLabel();
        jLabel1 = new javax.swing.JLabel();
        param1NameCB = new javax.swing.JComboBox<>();
        param2NameCB = new javax.swing.JComboBox<>();

        jList2.setModel(new javax.swing.AbstractListModel<String>() {
            String[] strings = { "Item 1", "Item 2", "Item 3", "Item 4", "Item 5" };
            public int getSize() { return strings.length; }
            public String getElementAt(int i) { return strings[i]; }
        });
        jScrollPane2.setViewportView(jList2);

        goButton.setText("Go!");
        goButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                goButtonActionPerformed(evt);
            }
        });

        param1Values.setColumns(20);
        param1Values.setRows(5);
        jScrollPane3.setViewportView(param1Values);

        param2Values.setColumns(20);
        param2Values.setRows(5);
        jScrollPane1.setViewportView(param2Values);

        jButton2.setText("Test1");
        jButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton2ActionPerformed(evt);
            }
        });

        messageLabel.setText("Load up those parameters and hit Go!");

        jLabel1.setText("<html>This is an experiment to see if a tool can be developed to generate inputs for scripts.  Specify the parameter name and values to assign, and likewise with a second parameter, if desired.");

        param1NameCB.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { " " }));

        param2NameCB.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { " " }));

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane3)
                    .addComponent(param1NameCB, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 264, Short.MAX_VALUE)
                    .addComponent(param2NameCB, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
            .addComponent(dataSetSelector1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addComponent(messageLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jButton2)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(goButton, javax.swing.GroupLayout.PREFERRED_SIZE, 62, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(dataSetSelector1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(param1NameCB, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(param2NameCB, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 225, Short.MAX_VALUE)
                    .addComponent(jScrollPane1))
                .addGap(9, 9, 9)
                .addComponent(messageLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(goButton)
                    .addComponent(jButton2))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void goButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_goButtonActionPerformed
        Runnable run= new Runnable() {
            public void run() {
                try {
                    goButton.setEnabled(false);
                    doIt();
                } catch (IOException ex) {
                    messageLabel.setText(ex.getMessage());
                }
            }
        };
        new Thread(run).start();
    }//GEN-LAST:event_goButtonActionPerformed

    private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton2ActionPerformed
        dataSetSelector1.setValue("http://autoplot.org/data/script/examples/parameters.jy");
        param1NameCB.setSelectedItem("ii");
        param1Values.setText("2\n4\n8\n");
        param2NameCB.setSelectedItem("ff");
        param2Values.setText("1.\n2.\n3.\n4.\n5.\n6.\n7.\n8.\n9.\n10.\n");
    }//GEN-LAST:event_jButton2ActionPerformed

    /**
     * TODO: this is not complete!
     * @param interp
     * @param paramDescription
     * @param paramName
     * @param f1
     * @throws IOException 
     */
    private void setParam( InteractiveInterpreter interp, org.virbo.jythonsupport.JythonUtil.Param paramDescription, 
            String paramName, String f1 ) throws IOException {
        if ( paramDescription==null ) {
            throw new IllegalArgumentException("expected to see parameter description!");
        }
        switch (paramDescription.type) {
            case 'U':
                URI uri;
                try {
                    URISplit split= URISplit.parse(f1);
                    if ( split.path==null ) {
                        uri= new URI( pwd + f1 );
                    } else {
                        uri= new URI(f1);
                    }
                } catch ( URISyntaxException ex ) {
                    throw new IOException(ex);
                }   interp.set("_apuri", uri );
                interp.exec("autoplot.params[\'"+paramName+"\']=_apuri");
                break;
            case 'A':
                interp.exec("autoplot.params[\'"+paramName+"\']=\'"+f1+"\'");
                break;
            case 'T':
                try {
                    DatumRange timeRange= DatumRangeUtil.parseTimeRange(f1);
                    interp.set("_apdr", timeRange );
                    interp.exec("autoplot.params[\'"+paramName+"\']=_apdr");
                } catch (ParseException ex) {
                    Logger.getLogger(BatchMaster.class.getName()).log(Level.SEVERE, null, ex);
                }   break;
            default:
                interp.exec("autoplot.params[\'"+paramName+"\']="+f1);
                break;
        }
        
    }
    
    private String pwd;
    
    /**
     * read the script into a string
     * @param f
     * @return
     * @throws FileNotFoundException
     * @throws IOException 
     */
    private String readScript( File f ) throws FileNotFoundException, IOException {
        StringBuilder build= new StringBuilder();
        BufferedReader r;
        r = new BufferedReader( new FileReader(f) );
        try {    
            String line= r.readLine();
            while ( line!=null ) {
                build.append(line).append("\n");
                line= r.readLine();
            }
        } finally {
            r.close();
        }        
        return build.toString();
    }
    
    public synchronized void doIt() throws IOException {
        ProgressMonitor monitor= DasProgressPanel.createFramed("batchMaster");
        try {
            String scriptName= dataSetSelector1.getValue();
            if ( !scriptName.endsWith(".jy") ) {
                JOptionPane.showMessageDialog( this, "script must end in .jy: "+scriptName );
                return;
            }

            String[] ff1= param1Values.getText().split("\n");

            monitor.setTaskSize(ff1.length);
            monitor.started();
            
            URISplit split= URISplit.parse(scriptName);
            pwd= split.path;
            
            Map<String,String> params= URISplit.parseParams(split.params);
            Map<String,Object> env= new HashMap<>();
                    
            File scriptFile= DataSetURI.getFile( split.file, monitor.getSubtaskMonitor("download script") );
            String script= readScript( scriptFile );
            
            Map<String,org.virbo.jythonsupport.JythonUtil.Param> parms= Util.getParams( env, script, URISplit.parseParams(split.params), new NullProgressMonitor() );

            InteractiveInterpreter interp = JythonUtil.createInterpreter( true, false );
            interp.exec("import autoplot");
            
            int i1=0;
            for ( String f1 : ff1 ) {
                try {
                    monitor.setProgressMessage(f1);
                    if ( monitor.isCancelled() ) break;
                    monitor.setTaskProgress(monitor.getTaskProgress()+1);
                    if ( f1.trim().length()==0 ) continue;
                    interp.set( "monitor", monitor.getSubtaskMonitor(f1) );
                    String paramName= param1NameCB.getSelectedItem().toString();
                    if ( !parms.containsKey(paramName) ) {
                        if ( paramName.trim().length()==0 ) {
                            throw new IllegalArgumentException("param1Name not set");
                        }
                    }
                    setParam( interp, parms.get(paramName), paramName, f1 );
                    
                    if ( param2NameCB.getSelectedItem().toString().trim().length()==0 ) {
                        interp.execfile( new FileInputStream(scriptFile), scriptFile.getName() );
                    } else {
                        String[] ff2= param2Values.getText().split("\n");
                        int i2=0;
                        for ( String f2: ff2 ) {
                            if ( f2.trim().length()==0 ) continue;
                            paramName= param2NameCB.getSelectedItem().toString();
                            setParam( interp, parms.get(paramName), paramName, f2 );
                            interp.execfile( new FileInputStream(scriptFile), scriptFile.getName() );
                            i2=i2+f2.length()+1;
                        }
                    }

                } catch (IOException ex) {
                    Logger.getLogger(BatchMaster.class.getName()).log(Level.SEVERE, null, ex);
                }
                i1=i1+f1.length()+1;
            }
        } finally {
            monitor.finished();
            goButton.setEnabled(true);
        }
    }
    
    public static void main( String[] args ) {
        JDialog dia= new JDialog();
        dia.setResizable(true);
        BatchMaster mmm= new BatchMaster(new Application());
        dia.setContentPane( mmm );
        mmm.param1NameCB.setSelectedItem("ie");
        mmm.dataSetSelector1.setValue("/home/jbf/ct/autoplot/script/demos/paramTypes.jy");
        mmm.param1Values.setText("1\n2\n3\n");
        dia.pack();
        dia.setVisible(true);
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private org.virbo.datasource.DataSetSelector dataSetSelector1;
    private javax.swing.JButton goButton;
    private javax.swing.JButton jButton2;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JList<String> jList2;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JLabel messageLabel;
    private javax.swing.JComboBox<String> param1NameCB;
    private javax.swing.JTextArea param1Values;
    private javax.swing.JComboBox<String> param2NameCB;
    private javax.swing.JTextArea param2Values;
    // End of variables declaration//GEN-END:variables
}
