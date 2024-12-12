
/*
 * CompletionsDataSourceEditor.java
 *
 * Created on Nov 5, 2010, 2:17:46 PM
 */

package org.autoplot.datasource;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.ProgressMonitor;

/**
 * Generic Editor based on completions was first developed with 
 * JunoWavesDataSource, now will be used instead of completions
 * when file icon is used.
 * @author jbf
 */
public class CompletionsDataSourceEditor extends javax.swing.JPanel implements DataSourceEditorPanel {
    
    private static final Logger logger= LoggerManager.getLogger("apdss");
    
    /**
     * maximum length of vap+xxx: in URIs.
     */
    private static final int MAX_VAP_PREFIX = 14;

    String suri;
    /**
     * true indicates that this type of URI does not have a file component.
     */
    boolean suriNoFile= false;

    List<JCheckBox> opsCbs;
    List<Control> opsComboBoxes;
    JComboBox arg0Cbs=null;
    String arg0Extra=null;
    JTextField arg0ExtraTF=null;
    Control arg0ComboBox=null;

    private static interface Control {
        String getValue();
        void setValue(String s);
    }
    private static Control getFromComboBox( JComboBox tcb ) {
        return new Control() {
            @Override
            public String getValue() { 
                return (String)tcb.getSelectedItem();
            };
            @Override
            public void setValue( String s ) {
                tcb.setSelectedItem(s);
            }
        };
    }
    
    /** Creates new form CompletionsDataSourceEditor */
    public CompletionsDataSourceEditor() {
        initComponents();
        jScrollPane1.getVerticalScrollBar().setUnitIncrement( getFont().getSize() );
    }

    /**
     * return true if surl is not a file (like with vap+cdaweb:...);
     * @param surl1
     * @return true if the URI is not a file.
     */
    private boolean isNotFile( String surl1 ) {
        URISplit split= URISplit.parse(surl1);
        return split.file==null || split.file.equals("") || split.file.equals("file:///");
    }
    
    /**
     * prepare the completion context object.
     * @param surl1 the uri.
     * @param carotPos the position of the carot.
     * @return the completion context object.
     */
    private CompletionContext prepareContext( String surl1, int carotPos ) {

        CompletionContext cc = new CompletionContext();

        int qpos = surl1.lastIndexOf('?', carotPos);
        if ( qpos==-1 && carotPos==surl1.length() ) { // note qpos will never equal -1 because of calling code.
            int icolon= surl1.indexOf(":");
            if ( suriNoFile ) {
                surl1= surl1.substring(0,icolon)+":?"+surl1.substring(icolon+1);
                qpos= icolon+1;
            } else {
                surl1= surl1+"?";
                qpos= surl1.length()-1;
            }
            carotPos= surl1.length();
        }

        cc.surl = surl1;
        cc.surlpos = carotPos; //resourceUriCarotPos

        if ( qpos != -1 && qpos < carotPos ) { // in query section

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
                //if (surl1.length() > carotPos && surl1.charAt(carotPos) != '&') {  // insert implicit "&"
                //    surl1 = surl1.substring(0, carotPos) + '&' + surl1.substring(carotPos);
                //}

            }
        } else {
            //throw new IllegalArgumentException("we aren't in the query section");
        }

        return cc;
    }

    private List<CompletionContext> getCompletions( DataSourceFactory factory, String surl1, CompletionContext cc, ProgressMonitor mon ) throws URISyntaxException, Exception {

        URISplit split = URISplit.parse(surl1);

        List<CompletionContext> result;

        if (cc.context == CompletionContext.CONTEXT_PARAMETER_NAME) {
            String resourceUri= CompletionContext.get(CompletionContext.CONTEXT_FILE, cc);
            if ( resourceUri!=null ) {
                URI uri = DataSetURI.getURI( CompletionContext.get(CompletionContext.CONTEXT_FILE, cc) );
                cc.resourceURI= DataSetURI.getResourceURI(uri);
            }
            cc.params = split.params;

            result = factory.getCompletions(cc, mon );

        }  else if (cc.context == CompletionContext.CONTEXT_PARAMETER_VALUE) {
            if ( suriNoFile ) {
                cc.resourceURI= null;
            } else {
                URI uri;
                uri= DataSetURI.getURI(CompletionContext.get(CompletionContext.CONTEXT_FILE, cc));
                cc.resourceURI= DataSetURI.getResourceURI(uri);
            }

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

    private void populateFromCompletions( DataSourceFactory dsf, ProgressMonitor mon ) throws URISyntaxException, Exception {

        int i= suri.indexOf("?");
        if ( i==-1 ) {
            int icolon= suri.indexOf(":");
            if ( suriNoFile && icolon>-1 && icolon<MAX_VAP_PREFIX ) {
                suri= suri.substring(0,icolon)+":?"+suri.substring(icolon+1);
                i= icolon+2;
            } else {
                suri= suri+"?";
                i= suri.length();
            }
        } else {
            i=i+1;
        }

        URISplit split= URISplit.parse(suri);
        Map<String,String> map= URISplit.parseParams(split.params);

        CompletionContext cc= prepareContext(  suri, i );

        List<CompletionContext> first= getCompletions( dsf, suri, cc, mon );

        List<CompletionContext> arg0= new ArrayList();
        for ( CompletionContext cc1: first ) {
            if ( cc1.implicitName!=null && cc1.implicitName.equals("arg_0") ) {
                arg0.add(cc1);
            }
        }
        first.removeAll(arg0);

        opsCbs= new ArrayList<>();
        opsComboBoxes= new ArrayList<>();

        boolean empty= true;

        if ( arg0.size()>0 ) {
            JPanel optPanel= new JPanel( new BorderLayout() );
            empty= false;

            String val= map.get("arg_0");
            if ( val!=null ) {
                int ib= val.indexOf("[");
                if ( ib>-1 && val.endsWith("]") ) { // vap+hdf5:file:///home/jbf/Linux/Download/gnc_B_July_16_2012.hdf5.mat?EFW_Uncomp_U[0]
                    arg0Extra= val.substring(ib);
                }
            }

            int isel=-1;
            List<String> arg0options= new ArrayList();
            for ( int ii=0; ii<arg0.size(); ii++ ) {
                if ( arg0.get(ii).completable.equals(arg0.get(ii).label) ) {
                    arg0.get(ii).label= null;
                }
                if ( arg0.get(ii).completable.equals("") ) {
                    String s= arg0.get(ii).label;
                    if ( s!=null && s.trim().length()>0 ) {
                        optPanel.add( BorderLayout.NORTH, new JLabel(s) );
                    }
                }
                if ( arg0.get(ii).label!=null ) {
                    //if ( arg0.get(ii).completable.length()==0 ) {
                    //    arg0options.add( arg0.get(ii).label );  // "Select parameter to plot:"
                    //} else {
                        arg0options.add( arg0.get(ii).completable + ": " +arg0.get(ii).label );
                    //}
                } else {
                    arg0options.add( arg0.get(ii).completable );
                }
                if ( arg0.get(ii).completable.equals(val) ) {
                    isel= ii;
                }
            }


            JComboBox jopts= new JComboBox( arg0options.toArray() );
            optPanel.add( BorderLayout.CENTER, jopts );
            if ( isel!=-1 ) {
                jopts.setSelectedIndex(isel);
            }
            
            arg0ComboBox= getFromComboBox(jopts);
            
            if ( arg0Extra!=null ) {
                arg0ExtraTF= new JTextField(12);
                arg0ExtraTF.setText(arg0Extra);
                arg0ExtraTF.setToolTipText( "subset specifier like [2:] or [-100:]");
                optPanel.add( BorderLayout.EAST, arg0ExtraTF );
            }

            arg0Cbs= jopts;

            optPanel.setMaximumSize( new Dimension(10000,16) );

            optionsPanel.add( optPanel );
            optionsPanel.add( Box.createVerticalStrut(8) );
            
        }
        
        
        for ( CompletionContext cc1: first ) {

            String ss= CompletionContext.insert(cc, cc1);

            //TODO: make this look like the jython data source dialog!
            JPanel optPanel= new JPanel( new BorderLayout() );

            final JCheckBox jcheckBox= new JCheckBox( cc1.label );
            optPanel.add( BorderLayout.WEST, jcheckBox );

            opsCbs.add(jcheckBox);
            empty= false;

            if ( cc1.doc!=null && cc1.doc.trim().length()>0 ) {
                optPanel.add( BorderLayout.NORTH, new JLabel( cc1.doc ) );
                //jcheckBox.setToolTipText( cc1.doc );
            }
            int pos= ss.indexOf(cc1.completable);
            if ( pos>-1 ) pos+=cc1.completable.length(); // carot immediately following "<parmName>="

            CompletionContext cc2= prepareContext(  ss, pos>-1 ? pos : ss.length() );

            if ( cc2.context!=null ) {
                List<CompletionContext> second= getCompletions( dsf, ss, cc2, mon );

                List options= new ArrayList();

                String key= cc1.completable;
                if ( key!=null ) {
                    int ii= key.indexOf("=");
                    if (ii>-1 ) key= key.substring(0,ii);
                } else {
                    logger.warning("bad key in uri");
                    continue;
                }
                
                String val= map.get( key );
                if ( val!=null ) {
                    //jcheckBox.setSelected(true);
                }

                //String sel= null;
                int isel= -1;
                String deft=null;

                for ( int ii=0; ii<second.size(); ii++ ) {
                    CompletionContext cc3= second.get(ii);
                    //String ss2= CompletionContext.insert( cc2, cc3 );
                    if ( cc3.completable.equals(val) ) isel= ii;
                    if ( cc3.label.startsWith( cc3.completable+":" ) ) {
                        options.add( cc3.label );
                    } else {
                        if ( cc3.completable.equals(cc3.label) ) {
                            cc3.label= null;
                        }                        
                        if ( cc3.completable.equals(cc3.label ) ) {
                            options.add( cc3.label );
                        } else {
                            if ( cc3.label==null ) {
                                options.add( cc3.completable );
                            } else {
                                options.add( cc3.completable + ": " + cc3.label );
                            }
                        }
                    }
                    if ( cc3.completable.startsWith("<double")
                            || cc3.completable.startsWith("<int") ) {
                        deft= "";
                        jcheckBox.setToolTipText( ( cc1.doc!=null ? (cc1.doc+" ") : "" ) + cc3.completable );
                    }

                }

                JComponent control;
                if ( key.equals(URISplit.PARAM_TIME_RANGE) ) {      
                    JPanel valuePanel= new JPanel(  );
                    valuePanel.setLayout( new BoxLayout( valuePanel, BoxLayout.X_AXIS ) );
                    final RecentComboBox tcb= new RecentComboBox();
                    tcb.setPreferenceNode( RecentComboBox.PREF_NODE_TIMERANGE );
                    Dimension x= tcb.getPreferredSize();
                    x.width= Integer.MAX_VALUE;
                    tcb.setMaximumSize(x);
                    tcb.setAlignmentX( JComponent.LEFT_ALIGNMENT );
                    tcb.setText( val );
                    Icon fileIcon= new javax.swing.ImageIcon( 
                        CompletionsDataSourceEditor.class.getResource("/org/autoplot/datasource/calendar.png") );
                    JButton button= new JButton( fileIcon );
                    button.addActionListener((ActionEvent e) -> {
                        TimeRangeTool tt= new TimeRangeTool();
                        tt.setSelectedRange(tcb.getSelectedItem().toString());
                        int r= WindowManager.showConfirmDialog( this, tt, "Select Time Range", JOptionPane.OK_CANCEL_OPTION );
                        if ( r==JOptionPane.OK_OPTION) {
                            tcb.setSelectedItem(tt.getSelectedRange());
                        }
                    });     
                    button.setToolTipText("Time Range Tool");
                    valuePanel.add( tcb );
                    button.setAlignmentX( JComponent.LEFT_ALIGNMENT );
                    valuePanel.add( button );

                    control= valuePanel;
                    
                    Control c= getFromComboBox( tcb );
                    opsComboBoxes.add( c );
                    
                } else {
                    final JComboBox jopts=  new JComboBox( options.toArray() );
                    jopts.setEditable(true);
                    optPanel.add( BorderLayout.CENTER, jopts );
                    if ( isel!=-1 ) {
                        jopts.setSelectedIndex(isel);
                        jcheckBox.setSelected(true);
                    } else {
                        if ( deft!=null ) {
                            jopts.setSelectedItem(deft);
                        }
                    }

                    jcheckBox.addItemListener( new ItemListener() {
                        @Override
                        public void itemStateChanged(ItemEvent e) {
                            jopts.setEnabled( jcheckBox.isSelected());
                        }
                    } );
                    jopts.setEnabled( jcheckBox.isSelected());

                    opsComboBoxes.add( getFromComboBox(jopts) );

                    optPanel.setMaximumSize( new Dimension(10000,16) );
                    
                    control= optPanel;
                }

                optionsPanel.add( control );
                optionsPanel.add( Box.createVerticalStrut(8) );
            } else {
                opsComboBoxes.add( null );
            }
            
        }

        if ( empty ) {
            String id= split.vapScheme==null ? ( "for " + split.ext ) : split.vapScheme;
            optionsPanel.add( new JLabel("<html><i>Data source "+ id + " provides no completions, so presumably there are no options available.</i></html>"));
        }

        optionsPanel.add( Box.createGlue() );
    }

    @Override
    public JPanel getPanel() {
        return this;
    }

    @Override
    public void setURI(String uri) {
        this.suri= uri;
        this.suriNoFile=isNotFile(uri);
        URISplit split= URISplit.parse(suri);
        Map<String,String> params= URISplit.parseParams( split.params );
        for ( Entry<String,String> e: params.entrySet() ) {
            String s= e.getKey();
            String v= e.getValue();
            for ( int i=0; i<opsCbs.size(); i++ ) {
                if ( opsCbs.get(i).getText().equals(s+"=") ) {
                    opsCbs.get(i).setSelected(true);
                    if ( opsComboBoxes.get(i)!=null ) {
                        opsComboBoxes.get(i).setValue(v);
                    } else {
                        
                    }
                }
            }
        }
    }

    @Override
    public String getURI() {
        StringBuilder base= new StringBuilder( this.suri );
        int j= base.indexOf("?");
        if ( j==-1 || suriNoFile ) {
            int icolon= suri.indexOf(':');
            if ( suriNoFile && icolon>-1 && icolon<MAX_VAP_PREFIX ) {
                base= new StringBuilder( base.substring(0,icolon+1) );
            }
        } else {
            base= new StringBuilder( base.substring(0,j) );
        }

        boolean amp= false;

        if ( arg0Cbs!=null ) {
            if ( !suriNoFile ) {
                base.append( "?" );
            }
            String s= String.valueOf( arg0Cbs.getSelectedItem() );
            int i= s.indexOf(": ");
            if ( i>-1 ) s= s.substring(0,i);
            base.append( s );
            amp= true;
        }

        if ( arg0ExtraTF!=null ) {
            if ( arg0ExtraTF.getText().trim().length()>0 ) {
                base.append( arg0ExtraTF.getText().trim() );
            }
        }

        for ( int i=0; i<opsCbs.size(); i++ ) {
            if ( opsCbs.get(i).isSelected() ) {
                String paramName= opsCbs.get(i).getText();
                if ( paramName.endsWith("=") ) paramName= paramName.substring(0,paramName.length()-1);
                String paramValue;
                if ( opsComboBoxes.get(i)!=null ) {
                    paramValue= String.valueOf( opsComboBoxes.get(i).getValue() );
                } else {
                    paramValue= "????";
                }
                int icolon= paramValue.indexOf(':');
                if ( icolon!=-1 && !paramName.equalsIgnoreCase( URISplit.PARAM_TIME_RANGE ) ) {
                    paramValue= paramValue.substring(0,icolon);
                }
                if ( amp ) {
                    base.append( "&" );
                } else {
                    if ( !suriNoFile ) {
                        base.append( "?" );
                    }
                    amp= true;
                }
                base.append( paramName ); //TODO: is there an equals here?
                base.append( "=" );
                base.append( paramValue );
            }
        }

        this.suri= base.toString();
        return this.suri;
    }

    @Override
    public boolean prepare(String uri, Window parent, ProgressMonitor mon) throws Exception {
        this.suri= uri;
        this.suriNoFile=isNotFile(uri);        
        DataSourceFactory dsf= DataSetURI.getDataSourceFactory( DataSetURI.getURI(uri), mon);
        if ( dsf==null ) {
            throw new UnrecognizedDataSourceException(uri);
        }
        populateFromCompletions( dsf, mon );
        return true;
    }

    @Override
    public boolean reject(String uri) throws Exception {
        URISplit split= URISplit.parse(uri);
        if ( split.file==null || split.file.equals("file:///") ) { //TODO: other parts of the code appear to allow non-file URIs...
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
        jScrollPane1 = new javax.swing.JScrollPane();
        optionsPanel = new javax.swing.JPanel();

        jLabel1.setText("<html>Autoplot has attempted to create a GUI editor based on the completions of the data source. ");
        jLabel1.setVerticalAlignment(javax.swing.SwingConstants.TOP);

        optionsPanel.setAlignmentY(0.0F);
        optionsPanel.setLayout(new javax.swing.BoxLayout(optionsPanel, javax.swing.BoxLayout.Y_AXIS));
        jScrollPane1.setViewportView(optionsPanel);

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(jLabel1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 585, Short.MAX_VALUE)
            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 585, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .add(jLabel1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
                .add(0, 377, Short.MAX_VALUE))
            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                    .add(35, 35, 35)
                    .add(jScrollPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 359, Short.MAX_VALUE)))
        );
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel jLabel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JPanel optionsPanel;
    // End of variables declaration//GEN-END:variables

    @Override
    public void markProblems(List<String> problems) {
        
    }


}
