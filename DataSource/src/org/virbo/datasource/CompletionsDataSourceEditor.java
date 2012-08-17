/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * CompletionsDataSourceEditor.java
 *
 * Created on Nov 5, 2010, 2:17:46 PM
 */

package org.virbo.datasource;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Window;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import org.das2.util.monitor.ProgressMonitor;

/**
 * Generic Editor based on completions was first developed with JunoWavesDataSource, now will be used instead of completions
 * when file icon is used.
 * @author jbf
 */
public class CompletionsDataSourceEditor extends javax.swing.JPanel implements DataSourceEditorPanel {

    String suri;

    List<JCheckBox> opsCbs;
    List<JComboBox> opsComboBoxes;


    /** Creates new form CompletionsDataSourceEditor */
    public CompletionsDataSourceEditor() {
        initComponents();
    }

    private CompletionContext prepareContext( String surl1, int carotPos ) {

        CompletionContext cc = new CompletionContext();

        int qpos = surl1.lastIndexOf('?', carotPos);
        if ( qpos==-1 && carotPos==surl1.length() ) {
            surl1= surl1+"?";
            qpos= surl1.length()-1;
            carotPos= surl1.length();
        }

        cc.surl = surl1;
        cc.surlpos = carotPos; //resourceUriCarotPos

        if ( qpos != -1 && qpos < carotPos ) { // in query section
            if (qpos == -1) {
                qpos = surl1.length();
            }

            int eqpos = surl1.lastIndexOf('=', carotPos - 1);
            int amppos = surl1.lastIndexOf('&', carotPos - 1);
            if (amppos == -1) {
                amppos = qpos;
            }

            if (eqpos > amppos) {
                cc.context = CompletionContext.CONTEXT_PARAMETER_VALUE;
                cc.completable = surl1.substring(eqpos + 1, carotPos);
                cc.completablepos = carotPos - (eqpos + 1);
            } else {
                cc.context = CompletionContext.CONTEXT_PARAMETER_NAME;
                cc.completable = surl1.substring(amppos + 1, carotPos);
                cc.completablepos = carotPos - (amppos + 1);
                if (surl1.length() > carotPos && surl1.charAt(carotPos) != '&') {  // insert implicit "&"
                    surl1 = surl1.substring(0, carotPos) + '&' + surl1.substring(carotPos);
                }

            }
        } else {
            throw new IllegalArgumentException("we aren't in the query section");
        }

        return cc;
    }

    private List<CompletionContext> getCompletions( DataSourceFactory factory, String surl1, CompletionContext cc, ProgressMonitor mon ) throws URISyntaxException, Exception {

        URISplit split = URISplit.parse(surl1);

        List<CompletionContext> result;

        if (cc.context == CompletionContext.CONTEXT_PARAMETER_NAME) {

            URI uri = DataSetURI.getURI( CompletionContext.get(CompletionContext.CONTEXT_FILE, cc) );

            cc.resourceURI= DataSetURI.getResourceURI(uri);
            cc.params = split.params;

            result = factory.getCompletions(cc, mon );

        }  else if (cc.context == CompletionContext.CONTEXT_PARAMETER_VALUE) {
            URI uri = DataSetURI.getURI(CompletionContext.get(CompletionContext.CONTEXT_FILE, cc));

            cc.resourceURI= DataSetURI.getResourceURI(uri);
            cc.params = split.params;

            if (factory == null) {
                throw new IllegalArgumentException("unable to find data source factory");
            }

            result = factory.getCompletions(cc, mon);

        } else {
            throw new IllegalArgumentException("we aren't in the query section");

        }

        return result;

    }

    public void populateFromCompletions( DataSourceFactory dsf, ProgressMonitor mon ) throws URISyntaxException, Exception {

        int i= suri.indexOf("?");
        if ( i==-1 ) i= suri.length(); else i=i+1;

        URISplit split= URISplit.parse(suri);
        Map<String,String> map= URISplit.parseParams(split.params);

        CompletionContext cc= prepareContext(  suri, i );

        List<CompletionContext> first= getCompletions( dsf, suri, cc, mon );

        opsCbs= new ArrayList<JCheckBox>();
        opsComboBoxes= new ArrayList<JComboBox>();
        
        for ( CompletionContext cc1: first ) {

            String ss= CompletionContext.insert(cc, cc1);

            JPanel optPanel= new JPanel( new BorderLayout() );

            JCheckBox jcheckBox= new JCheckBox( cc1.label );
            optPanel.add( BorderLayout.WEST, jcheckBox );

            opsCbs.add(jcheckBox);

            int pos= ss.indexOf(cc1.completable);
            if ( pos>-1 ) pos+=cc1.completable.length(); // carot immediately following "<parmName>="

            CompletionContext cc2= prepareContext(  ss, pos>-1 ? pos : ss.length() );

            List<CompletionContext> second= getCompletions( dsf, ss, cc2, mon );

            List options= new ArrayList();

            String key= cc1.completable;
            if ( key!=null ) {
                int ii= key.indexOf("=");
                if (ii>-1 ) key= key.substring(0,ii);
            }
            String val= map.get( key );
            if ( val!=null ) {
                //jcheckBox.setSelected(true);
            }

            String sel= null;
            int isel= -1;
            for ( int ii=0; ii<second.size(); ii++ ) {
                CompletionContext cc3= second.get(ii);
                String ss2= CompletionContext.insert( cc2, cc3 );
                if ( cc3.completable.equals(val) ) isel= ii;
                if ( cc3.label.startsWith( cc3.completable+":" ) ) {
                    options.add( cc3.label );
                } else {
                    options.add( cc3.completable + ": " + cc3.label );
                }
                
            }

            JComboBox jopts=  new JComboBox( new Vector(options) );
            optPanel.add( BorderLayout.CENTER, jopts );
            if ( isel!=-1 ) {
                jopts.setSelectedIndex(isel);
                jcheckBox.setSelected(true);
            }

            opsComboBoxes.add(jopts);

            optPanel.setMaximumSize( new Dimension(10000,16) );

            optionsPanel.add( optPanel );
            optionsPanel.add( Box.createVerticalStrut(8) );
        }

        optionsPanel.add( Box.createGlue() );
    }

    public JPanel getPanel() {
        return this;
    }

    public void setURI(String uri) {
        this.suri= uri;
        URISplit split= URISplit.parse(suri);
        Map<String,String> params= URISplit.parseParams( split.params );
        for ( String s: params.keySet() ) {
            String v= params.get(s);
            for ( int i=0; i<opsCbs.size(); i++ ) {
                if ( opsCbs.get(i).getText().equals(s+"=") ) {
                    opsCbs.get(i).setSelected(true);
                    opsComboBoxes.get(i).setSelectedItem(v);
                }
            }
        }
    }

    public String getURI() {
        String base= this.suri;
        int j= base.indexOf("?");
        if ( j!=-1 ) {
            base= base.substring(0,j);
        }
        boolean amp= false;
        for ( int i=0; i<opsCbs.size(); i++ ) {
            if ( opsCbs.get(i).isSelected() ) {
                String paramName= opsCbs.get(i).getText();
                String paramValue= String.valueOf( opsComboBoxes.get(i).getSelectedItem() );
                int icolon= paramValue.indexOf(":");
                if ( icolon!=-1 ) {
                    paramValue= paramValue.substring(0,icolon);
                }
                if ( amp ) {
                    base+="&";
                } else {
                    base += "?";
                    amp= true;
                }
                base+= paramName;
                base+= paramValue;
            }
        }
        this.suri= base;
        return this.suri;
    }

    public boolean prepare(String uri, Window parent, ProgressMonitor mon) throws Exception {
        this.suri= uri;
        DataSourceFactory dsf= DataSetURI.getDataSourceFactory( new URI(uri), mon);
        populateFromCompletions( dsf, mon );
        return true;
    }

    public boolean reject(String uri) throws Exception {
        URISplit split= URISplit.parse(uri);
        if ( split.file==null || split.file.equals("file:///") ) {
            return true;
        } else {
            return false;
        }
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
        optionsPanel = new javax.swing.JPanel();

        jLabel1.setText("Experimental editor based on completions");

        optionsPanel.setLayout(new javax.swing.BoxLayout(optionsPanel, javax.swing.BoxLayout.Y_AXIS));

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(jLabel1)
                .addContainerGap(130, Short.MAX_VALUE))
            .add(optionsPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 400, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(jLabel1)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                .add(optionsPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 277, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel jLabel1;
    private javax.swing.JPanel optionsPanel;
    // End of variables declaration//GEN-END:variables

    public void markProblems(List<String> problems) {
        
    }


}
