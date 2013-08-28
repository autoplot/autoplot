/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.jythonsupport.ui;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.datasource.DataSetSelector;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.TimeRangeTool;
import org.virbo.datasource.URISplit;
import org.virbo.jythonsupport.JythonUtil;

/**
 *
 * @author jbf
 */
public class Util {
    
    private static final Logger logger= LoggerManager.getLogger("jython");
    
    /**
     * scrape the script for getParameter calls.
     * @param uri location of the script, or resourceURI with script= parameter.
     * @param mon monitor for loading the script
     * @return
     * @throws IOException 
     */
    protected static Map<String,JythonUtil.Param> getParams( URI uri, ProgressMonitor mon ) throws IOException {

        URISplit split= URISplit.parse(uri);
        Map<String,String> params= URISplit.parseParams(split.params);
        String furi;
        if ( params.containsKey("script") ) {
            furi= params.get("script");
        } else {
            furi= split.resourceUri.toString();
        }

        File src = DataSetURI.getFile(furi, mon );

        List<JythonUtil.Param> r2= JythonUtil.getGetParams( new BufferedReader( new FileReader(src) ) );

        Map<String,JythonUtil.Param> result= new LinkedHashMap();

        for ( JythonUtil.Param r : r2 ) {
            result.put( r.name, r );
        }

        return result;

    }

    /**
     * scrape the script for getParameter calls.
     * @param src the script, all in one string.
     * @param mon
     * @return
     * @throws IOException 
     */
    protected static Map<String,JythonUtil.Param> getParams( String src, ProgressMonitor mon ) throws IOException {

        List<JythonUtil.Param> r2= JythonUtil.getGetParams( new BufferedReader( new StringReader(src) ) );

        Map<String,JythonUtil.Param> result= new LinkedHashMap();

        for ( JythonUtil.Param r : r2 ) {
            result.put( r.name, r );
        }

        return result;

    }
    
    /**
     * TODO: params is object?
     * @param parms
     * @return
     */
    private static boolean isBoolean( List<Object> parms ) {
        if ( parms.size()==2 && parms.contains("T") && parms.contains("F") ) {
            return true;
        } else {
            return false;
        }
    }
    
    private static JComponent getSpacer() {
        JComponent spacer= new JLabel("  ");
        spacer.setSize( new Dimension(20,16) );
        spacer.setMinimumSize( new Dimension(20,16) );
        spacer.setPreferredSize( new Dimension(20,16) );
        return spacer;
    }
    
    
    public static class FormData {
        ArrayList<JComponent> tflist;
        ArrayList<String> paramsList;
        ArrayList<String> deftsList;
        ArrayList<Character> typesList;
        public int count;
    }
    
    /**
     * extract the data from the form into params.
     * @param fd
     * @param params 
     */
    public static void resetVariables( FormData fd, Map<String,String> params ) {
        
        for ( int j=0; j<fd.paramsList.size(); j++ ) {
            String name= fd.paramsList.get(j);
            JComponent jc= fd.tflist.get(j);
            String value;
            if ( jc instanceof JTextField ) {
                value= ((JTextField)jc).getText();
            } else if ( jc instanceof DataSetSelector ) {
                value= ((DataSetSelector)jc).getValue();
            } else if ( jc instanceof JComboBox ) {
                value= String.valueOf( ((JComboBox)jc).getSelectedItem() );
            } else if ( jc instanceof JCheckBox ) {
                value= ((JCheckBox)jc).isSelected() ? "T" : "F";
            } else {
                throw new IllegalArgumentException("the code needs attention: component for "+name+" not supported ");
            }
            String deft= fd.deftsList.get(j);
            char type= fd.typesList.get(j);

            if ( !value.equals(deft) || params.containsKey(name) ) {
                if ( type=='A' ) {
                    value= value.replaceAll("\'", "");
                    if ( !( value.startsWith("'") && value.endsWith("'") ) ) {
                        value=  "'" + value + "'";
                    }
                    params.put( name, value );
                } else if ( type=='R' ) {
                    //TODO: check how this ought to be implemented.
                } else {
                    params.put( name, value );
                }
            }
        }
    
    }
            
    /**
     * Populates the JPanel with options.  See org.virbo.jythonsupport.ui.Util.createForm.
     * @param f the file containing the script.
     * @param params map containing any settings for the variables.
     * @return 
     */
    public static FormData doVariables( File f, Map<String,String> params, final JPanel paramsPanel ) throws IOException {
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
        return doVariables(build.toString(),params,paramsPanel);
    }
    
    
    /**
     * Populates the JPanel with options.  See org.virbo.jythonsupport.ui.Util.createForm.
     * @param src the script loaded into a string.
     * @param params map containing any settings for the variables.
     * @return 
     */
    public static FormData doVariables( String src, Map<String,String> params, final JPanel paramsPanel ) {
        Map<String,JythonUtil.Param> parms;

        FormData fd= new FormData();
        
        boolean hasVars;
        fd.tflist= new ArrayList();
        fd.paramsList= new ArrayList();
        fd.deftsList= new ArrayList();
        fd.typesList= new ArrayList();
        
        paramsPanel.setLayout(new javax.swing.BoxLayout(paramsPanel, javax.swing.BoxLayout.Y_AXIS));
        
        try {
            parms= getParams( src, new NullProgressMonitor() );

            paramsPanel.add( new JLabel("<html>This script has the following input parameters.  Buttons on the right show default values.<br><br></html>") );

            for ( Map.Entry<String,JythonUtil.Param> e: parms.entrySet() ) {
                //String s= e.getKey();
                JythonUtil.Param parm= e.getValue();
                
                String vname= parm.name;                
                String label;

                JComponent ctf;

                boolean isBool= isBoolean( parm.enums );

                String colon= isBool ? "" : ":";

                if ( parm.doc==null ) {
                    label= vname+ colon;
                } else {
                    String doc= parm.doc;
                    if ( doc.startsWith("'") ) doc= doc.substring(1,doc.length()-1);// pop off the quotes
                    if ( !parm.label.equals(parm.name) ) {
                        doc= doc + " ("+parm.label+" inside the script)";
                    }
                    label= "<html>" + parm.name + ", <em>" + doc + "</em>"+colon+"</html>";
                }      
                
                if ( !isBool ) {
                    JPanel labelPanel= new JPanel();
                    labelPanel.setLayout( new BoxLayout( labelPanel, BoxLayout.X_AXIS ) );
                    JLabel l= new JLabel( label );
                    labelPanel.add( getSpacer() );
                    labelPanel.add( l );
                    labelPanel.setAlignmentX( JComponent.LEFT_ALIGNMENT );
                    paramsPanel.add( labelPanel );
                }

                JPanel valuePanel= new JPanel(  );
                valuePanel.setLayout( new BoxLayout( valuePanel, BoxLayout.X_AXIS ) );
                if ( !isBool ) valuePanel.add( getSpacer() );

                if ( parm.type=='R' ) {

                    String val= params.get(vname);
                    if ( val!=null ) {
                        if ( val.startsWith("'") ) val= val.substring(1);
                        if ( val.endsWith("'") ) val= val.substring(0,val.length()-1);
                    } else {
                        val= String.valueOf( parm.deft );
                        params.put( vname, val );
                    }

                    final String fval= val;

                    final DataSetSelector sel= new DataSetSelector();
                    sel.setHidePlayButton(true);
                    sel.setSuggestFiles(true);

                    final JTextField tf= new JTextField();
                    Dimension x= tf.getPreferredSize();
                    x.width= Integer.MAX_VALUE;
                    tf.setMaximumSize(x);
                    tf.setUI( tf.getUI() ); // kludge to maybe avoid deadlock.

                    Icon fileIcon= new javax.swing.ImageIcon( Util.class.getResource("/org/virbo/datasource/jython/file2.png"));
                    JButton filesButton= new JButton( fileIcon );
                    filesButton.addActionListener( new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            JFileChooser c= new JFileChooser();
                            URISplit split2= URISplit.parse(fval);
                            if ( split2.scheme.equals("file") ) {
                                c.setSelectedFile( new File( split2.file.substring(7)) );
                            }
                            int r= c.showOpenDialog( paramsPanel );
                            if ( r==JFileChooser.APPROVE_OPTION) {
                                tf.setText("file://"+c.getSelectedFile().toString());
                            }
                        }
                    });
                    tf.setAlignmentX( JComponent.LEFT_ALIGNMENT );

                    tf.setText( val );
                    ctf= tf;
                    valuePanel.add( ctf );
                    filesButton.setAlignmentX( JComponent.LEFT_ALIGNMENT );
                    valuePanel.add( filesButton );
                } else if ( parm.type=='T' ) {
                    String val;
                    if ( params.get(vname)!=null ) {
                        val= params.get(vname);
                        if ( val.startsWith("'") ) val= val.substring(1);
                        if ( val.endsWith("'") ) val= val.substring(0,val.length()-1);
                    } else {
                        val= String.valueOf( parm.deft );
                        params.put( vname, val );
                    }
                    final JTextField tf= new JTextField();
                    Dimension x= tf.getPreferredSize();
                    x.width= Integer.MAX_VALUE;
                    tf.setMaximumSize(x);
                    tf.setAlignmentX( JComponent.LEFT_ALIGNMENT );

                    tf.setText( val );
                    ctf= tf;
                    
                    Icon fileIcon= new javax.swing.ImageIcon( Util.class.getResource("/org/virbo/datasource/calendar.png"));
                    JButton button= new JButton( fileIcon );
                    button.addActionListener( new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            TimeRangeTool tt= new TimeRangeTool();
                            tt.setSelectedRange(tf.getText());
                            int r= JOptionPane.showConfirmDialog( paramsPanel, tt, "Select Time Range", JOptionPane.OK_CANCEL_OPTION );
                            if ( r==JOptionPane.OK_OPTION) {
                                tf.setText(tt.getSelectedRange());
                            }
                        }
                    });
                    button.setToolTipText("Time Range Tool");
                    valuePanel.add( ctf );
                    button.setAlignmentX( JComponent.LEFT_ALIGNMENT );
                    valuePanel.add( button );
                    
                } else {
                    String val;
                    if ( params.get(vname)!=null ) {
                        val= params.get(vname);
                        if ( val.startsWith("'") ) val= val.substring(1);
                        if ( val.endsWith("'") ) val= val.substring(0,val.length()-1);
                    } else {
                        val= String.valueOf( parm.deft );
                        params.put( vname, val );
                    }
                    if ( parm.enums!=null && parm.enums.size()>0 ) {
                        if ( isBoolean( parm.enums ) ) {
                            JCheckBox jcb= new JCheckBox( label );
                            jcb.setSelected( val.equals("T") );
                            ctf= jcb;

                        } else {
                            JComboBox jcb= new JComboBox(parm.enums.toArray());
                            jcb.setEditable(false);
                            Object oval=null;
                            if ( parm.deft instanceof Long ) {
                                oval = Long.valueOf(val);
                            } else if ( parm.deft instanceof Integer ) {
                                oval = Integer.valueOf(val);
                            } else if ( parm.deft instanceof Double ) {
                                oval = Double.valueOf(val);
                            } else if ( parm.deft instanceof Float ) {
                                oval = Float.valueOf(val);
                            } else {
                                oval = val;
                            }
                            jcb.setSelectedItem(oval);
                            
                            ctf= jcb;
                            Dimension x= ctf.getPreferredSize();
                            x.width= Integer.MAX_VALUE;
                            ctf.setMaximumSize(x);
                            ctf.setAlignmentX( JComponent.LEFT_ALIGNMENT );
                        }

                    } else {
                        JTextField tf= new JTextField();
                        Dimension x= tf.getPreferredSize();
                        x.width= Integer.MAX_VALUE;
                        tf.setMaximumSize(x);
                        tf.setAlignmentX( JComponent.LEFT_ALIGNMENT );

                        tf.setText( val );
                        ctf= tf;
                    }
                    
                    valuePanel.add( ctf );
                }

                boolean shortLabel= ( parm.type=='R' || String.valueOf(parm.deft).length()>22 ) ;
                final String fdeft= shortLabel ? "default" : String.valueOf(parm.deft);
                final String fvalue= String.valueOf(parm.deft);
                final JComponent ftf= ctf;
                JButton defaultButton= new JButton( new AbstractAction( fdeft ) {
                    public void actionPerformed( ActionEvent e ) {
                        if ( ftf instanceof DataSetSelector ) {
                            ((DataSetSelector)ftf).setValue(fvalue);
                        } else if ( ftf instanceof JComboBox ) {
                            JComboBox jcb= ((JComboBox)ftf);
                            for ( int i=0; i<jcb.getItemCount(); i++ ) {
                                if ( fvalue.equals( jcb.getItemAt(i).toString() ) ) {
                                    jcb.setSelectedIndex(i);
                                }
                            }
                        } else if ( ftf instanceof JCheckBox ) {
                            ((JCheckBox)ftf).setSelected( fvalue.equals("T") );
                        } else {
                            ((JTextField)ftf).setText(fvalue);
                        }
                    }
                });
                defaultButton.setToolTipText( shortLabel ? String.valueOf(parm.deft) : "Click to reset to default" );
                valuePanel.add( defaultButton );
                valuePanel.add( getSpacer() );
                valuePanel.setAlignmentX( JComponent.LEFT_ALIGNMENT );

                paramsPanel.add( valuePanel );
                fd.tflist.add(ctf);

                fd.paramsList.add( parm.name );
                fd.deftsList.add( String.valueOf( parm.deft ) );
                fd.typesList.add( parm.type );

                hasVars= true;
            }
                
            hasVars= parms.size()>0;

            if ( !hasVars ) {
                JLabel l= new JLabel("<html><em>(no input parameters)</em></html>");
                l.setToolTipText("This looks through the code for getParam calls, and no conforming calls were found");
                paramsPanel.add( l );
            }

            paramsPanel.add( Box.createVerticalGlue() );
            paramsPanel.revalidate();

            fd.count= fd.paramsList.size();
                    
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return fd;

    }
    
}
