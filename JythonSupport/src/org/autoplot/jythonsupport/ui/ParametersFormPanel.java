
package org.autoplot.jythonsupport.ui;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.util.LoggerManager;
import org.python.core.Py;
import org.python.core.PyDictionary;
import org.python.util.PythonInterpreter;
import org.autoplot.datasource.DataSetSelector;
import org.autoplot.datasource.TimeRangeTool;
import org.autoplot.datasource.WindowManager;
import org.autoplot.jythonsupport.JythonUtil;
import org.das2.util.ColorUtil;
import java.awt.Color;
import java.net.URL;
import javax.swing.JColorChooser;
import org.autoplot.datasource.RecentComboBox;
import org.autoplot.jythonsupport.JythonUtil.ScriptDescriptor;
import org.autoplot.jythonsupport.Param;
import org.das2.datum.UnitsUtil;
import org.das2.util.FileUtil;

/**
 * GUI component for controlling script parameters.  
 * @author jbf
 */
public class ParametersFormPanel {

    FormData fd;
    Map<String,String> params;
    
    public ParametersFormPanel() {
        fd= new FormData();
    }   

    private static final Logger logger= LoggerManager.getLogger("jython.form");

    private static boolean isBoolean( List<Object> parms ) {
        return parms.size()==2 && ( parms.contains("T") && parms.contains("F") || parms.contains(0) && parms.contains(1) );
    }
    
    private static JComponent getSpacer() {
        JComponent spacer= new JLabel("  ");
        spacer.setSize( new Dimension(20,16) );
        spacer.setMinimumSize( new Dimension(20,16) );
        spacer.setPreferredSize( new Dimension(20,16) );
        return spacer;
    }
    
    /**
     * represent the data on the form.
     */
    public static class FormData {
        ArrayList<JComponent> tflist;
        ArrayList<String> paramsList;
        ArrayList<String> deftsList;
        ArrayList<Object> deftObjectList;
        ArrayList<Character> typesList;
        public int count;
        
        /**
         * Convert the parameter to Jython types.
         *<blockquote><pre>
         * T (TimeRange)
         * A (String)
         * F (Double or Integer)
         * R (URI)
         * L (URL) a resource URL (local file or web file).
         * D Datum
         * S DatumRange
         * </pre></blockquote>
         * 
         * @param interp the interpreter
         * @param param the param name
         * @param value the param value
         * @throws java.text.ParseException
         */
        public void implement( PythonInterpreter interp, String param, String value ) throws ParseException {
            PyDictionary paramsDictionary= ((PyDictionary)interp.get( "params" ));
            for ( int i=0; i<paramsList.size(); i++ ) {
                if ( paramsList.get(i).equals(param) ) {
                    Object deft= deftObjectList.get(i);
                    Character type= typesList.get(i);
                    if ( type.equals('T') && value.length()>1 && value.charAt(0)!='\'' && value.charAt(value.length()-1)!='\'' ) {
                        paramsDictionary.__setitem__( param, Py.java2py( value ) );
                    } else if (type.equals('D') ) {
                        paramsDictionary.__setitem__( param, Py.java2py( ((Datum)deft).getUnits().parse(value) ) );
                    } else if ( type.equals('S') ) {
                        paramsDictionary.__setitem__( param, Py.java2py( DatumRangeUtil.parseDatumRange(value,(DatumRange)deft ) ) );
                    } else if ( type.equals('U') ) {
                        try {
                            paramsDictionary.__setitem__( param, Py.java2py( new java.net.URI( value ) ) );
                        } catch (URISyntaxException ex) {
                            throw new ParseException( "URI is not formed properly",0);
                        }
                    } else if ( type.equals('L') ) {
                        try {
                            paramsDictionary.__setitem__( param, Py.java2py( new java.net.URL( value ) ) );
                        } catch (MalformedURLException ex) {
                            logger.log(Level.SEVERE, null, ex);
                        }
                    } else if ( type.equals('A') ) {
                        value= org.autoplot.jythonsupport.Util.popString(value);
                        paramsDictionary.__setitem__( param, Py.java2py( value ) );
                    } else if ( type.equals('R') ) {
                        value= org.autoplot.jythonsupport.Util.popString(value);
                        try {                        
                            paramsDictionary.__setitem__( param, Py.java2py( new URI(value) ) );
                        } catch (URISyntaxException ex) {
                            logger.log(Level.SEVERE, null, ex);
                        }
                    } else if ( type.equals('C') ) {
                        value= org.autoplot.jythonsupport.Util.popString(value);
                        paramsDictionary.__setitem__( param, Py.java2py( ColorUtil.decodeColor(value) ) );

                    } else {
                        interp.exec( String.format("params['%s']=%s", param, value ) ); // TODO: nasty/clever code handles float vs int.
                    }
                    return;
                }
                
            }

            logger.log(Level.WARNING, "unable to find variable ''{0}''", param);
        }
    }
    
    /**
     * there's nasty code which looks for "val1:explaination of val" , and this
     * destroys URL values.  Check to see if these look like URLs.
     * @param s
     * @return 
     */
    private static boolean appearsToBeUrl( String s ) {
        s= s.trim();
        if ( s.startsWith("vap+") ||
                s.startsWith("file:") ||
                s.startsWith("http:") ||
                s.startsWith("https:") ||
                s.startsWith("ftp:") ||
                s.startsWith("sftp:") ) {
            return true;
        } else {
            return false;
        }
    }
    
    /**
     * extract the data from the form into params. Note, strings and URIs are 
     * quoted, not sure why.
     * @param fd form data containing GUI references
     * @param params map to contain the settings for each parameter, reading from the GUI.
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
                if ( jc instanceof RecentComboBox ) {
                    ((RecentComboBox)jc).addToRecent(value); //TODO: why must I manually do this???
                }
                int i= value.indexOf(':');
                if ( i>-1 ) {
                    if ( appearsToBeUrl(value) ) {
                        value= value.trim(); // Yeah kludge code, but for sure someone (me) is going to do this. --JF
                    } else {
                        if ( fd.typesList.get(j).equals('T') || fd.typesList.get(j).equals('A') ) { //TODO: jupiter: 2029-02-02T00:00 to  2029-02-12T00:00  
                            value= value.trim();
                        } else {
                            value= value.substring(0,i).trim();
                        }
                    }
                }
            } else if ( jc instanceof JCheckBox ) {
                value= ((JCheckBox)jc).isSelected() ? "T" : "F";
            } else {
                throw new IllegalArgumentException("the code needs attention: component for "+name+" not supported ");
            }
            String deft= fd.deftsList.get(j);
            char type= fd.typesList.get(j);

            if ( type=='F' ) {
                if ( value.equals("F") ) {
                    value="False";
                } else if (value.equals("T") ) {
                    value="True";
                }
            }
            if ( !value.equals(deft) || params.containsKey(name) || name.equals("timerange") ) {
                switch (type) {
                    case 'A':
                        value= value.replaceAll("\'", "");
                        if ( !( value.startsWith("'") && value.endsWith("'") ) ) {
                            value=  "'" + value + "'";
                        }   
                        params.put( name, value );
                        break;
                    case 'R':
                    case 'L':
                        if ( !( value.startsWith("'") && value.endsWith("'") ) ) {
                            value=  "'" + value + "'";
                        }   
                        params.put( name, value );
                        break;
                    default:
                        params.put( name, value );
                        break;
                }
            }
        }
    
    }
            
    /**
     * Populates the JPanel with options.  See org.autoplot.jythonsupport.ui.Util.createForm.
     * @param env environment variables such as PWD and dom.
     * @param f the file containing the script.
     * @param params map containing any settings for the variables.
     * @param paramsPanel JPanel to populate with the GUI items. (Can be null.)
     * @return the FormData from the initial view, since some clients will not show a GUI when there are no parameters.
     * @throws java.io.IOException when reading the file.
     */
    public FormData doVariables( Map<String,Object> env, File f, Map<String,String> params, final JPanel paramsPanel ) throws IOException {
        String src= FileUtil.readFileToString(f);
        return doVariables( env, src, params,paramsPanel);
    }
    
    /**
     * Repopulates the JPanel with options, to be used when the parameters can change the params that are read in.
     * @param env environment variables such as PWD and dom. 
     * @param src the script loaded into a string.
     * @param params map containing any settings for the variables.
     * @param paramsPanel the GUI which needs to be revalidated.
     */
    public void redoVariables( Map<String,Object> env, String src, Map<String,String> params, final JPanel paramsPanel ) {
        paramsPanel.removeAll();
        resetVariables( fd, params );
        doVariables( env, src, params, paramsPanel );
        paramsPanel.revalidate();
        paramsPanel.repaint();
    }
    
    /**
     * return spacer of width size.
     * @param size
     * @return 
     */
    private JComponent getSpacer( int size ) {
        JComponent spacer= new JLabel(" ");
        spacer.setSize( new Dimension(size,16) );
        spacer.setMinimumSize( new Dimension(size,16) );
        spacer.setPreferredSize( new Dimension(size,16) );
        return spacer;
    }
    
    public FormData doVariables( final String src, Map<String,String> params, final JPanel zparamsPanel ) {
        return doVariables( null, src, params, zparamsPanel );
    }
    
    /**
     * Populates the JPanel with options.  See org.autoplot.jythonsupport.ui.Util.createForm, this is only used 
     * with the .jyds.  TODO: Fix this!!!
     * 
     * @param env null or an map containing variables like "dom" and "PWD"
     * @param src the script loaded into a string.
     * @param params map containing any settings for the variables.
     * @param zparamsPanel JPanel to populate with the GUI items. (Can be null.)
     * @see org.autoplot.jythonsupport.ui.Util#getParams(java.util.Map, java.lang.String, java.util.Map, org.das2.util.monitor.ProgressMonitor) 
     * @return the FormData from the initial view, since some clients will not show a GUI when there are no parameters.
     */
    public FormData doVariables( final Map<String,Object> env, final String src, Map<String,String> params, final JPanel zparamsPanel ) {
        if ( params==null ) params= Collections.emptyMap();
        this.params= new HashMap(params);

        logger.entering( "ParametersFormPanel", "doVariables", new Object[] { env, src, params } );
        boolean hasVars;
        fd.tflist= new ArrayList();
        fd.paramsList= new ArrayList();
        fd.deftsList= new ArrayList();
        fd.deftObjectList= new ArrayList();
        fd.typesList= new ArrayList();
        
        JScrollPane jp= new JScrollPane();
        if ( zparamsPanel!=null ) zparamsPanel.add( jp );
        final JPanel paramsPanel= new JPanel();
        jp.getViewport().add(paramsPanel);
        paramsPanel.setLayout(new javax.swing.BoxLayout(paramsPanel, javax.swing.BoxLayout.Y_AXIS));
        
        try {
            ScriptDescriptor sd= JythonUtil.describeScript( env, src, params );
            List<Param> parms= sd.getParams(); //getParams( env, src, this.params, new NullProgressMonitor() );

            boolean hasMeta= false;
            if ( sd.getTitle().length()>0 ) {
                paramsPanel.add( new JLabel("<html><H2>"+sd.getTitle()+"</H2></html>") );
                hasMeta= true;
            }
            if ( sd.getDescription().length()>0 ) {
                Object opwd= env.get("PWD");
                URL context= null;
                if ( opwd!=null ) {
                    context= new URL( String.valueOf( opwd ) );
                }
                paramsPanel.add( new JLinkyLabel( context,"<html><div width=600>"+sd.getDescription()+"</div></html>") );
                hasMeta= true;
            }
            if ( !hasMeta ) {
                paramsPanel.add( new JLabel("<html>This script has the following input parameters.  Buttons on the right show default values.<br><br></html>") );
            } else {
                paramsPanel.add( new JLabel("<html><br><br></html>") );  
            } 

            for ( Param parm : parms ) {
                
                String vname= parm.name;                
                String label;

                if ( parm.enums!=null && parm.deft.getClass()!=parm.enums.get(0).getClass() ) {
                    logger.warning("type of enumeration doesn't match default value.");
                }
                
                JComponent ctf;

                boolean hasLabels= parm.constraints!=null && parm.constraints.containsKey("labels");
                boolean isBool= parm.enums!=null && isBoolean( parm.enums ) && !hasLabels;

                if ( parm.enums!=null ) {
                    boolean okay=false;
                    if ( !Py.java2py(parm.enums.get(0)).getClass().isAssignableFrom( Py.java2py(parm.deft).getClass() ) ) {
                        logger.log(Level.WARNING, "parameter enumeration does not match type of default ("+parm.enums.get(0).getClass() +") for \"{0}\"", vname);
                    } else {
                        for ( Object o: parm.enums ) {
                            if ( parm.deft.equals(o) ) okay=true;
                        }
                        if ( !okay ) logger.log(Level.WARNING, "parameter enumeration does not contain the default for \"{0}\"", vname);
                    }
                }
                        
                String colon= isBool ? "" : ":";

                if ( parm.doc==null ) {
                    label= vname+ colon;
                } else {
                    String doc= parm.doc;
                    if ( doc.startsWith("'") ) doc= doc.substring(1,doc.length()-1);// pop off the quotes
                    if ( !parm.label.equals(parm.name) ) {
                        doc= doc + " ("+parm.label+" inside the script)";
                    }
                    if ( doc.length()>0 ) {
                        label= "<html>" + parm.name + ", <i>" + doc + "</i>"+colon+"</html>";
                    } else {
                        label= "<html>" + parm.name + colon+"</html>";
                    }
                }      
                
                if ( !isBool ) {
                    JPanel labelPanel= new JPanel();
                    labelPanel.setLayout( new BoxLayout( labelPanel, BoxLayout.X_AXIS ) );
                    JLabel l= new JLabel( label );
                    labelPanel.add( getSpacer() );
                    labelPanel.add( l );
                    labelPanel.setAlignmentX( JComponent.LEFT_ALIGNMENT );
                    paramsPanel.add( labelPanel );
                } else {
                    paramsPanel.add( Box.createVerticalStrut( paramsPanel.getFont().getSize() / 2 ) ); //TODO: verify.
                }

                JPanel valuePanel= new JPanel(  );
                valuePanel.setLayout( new BoxLayout( valuePanel, BoxLayout.X_AXIS ) );
                if ( !isBool ) valuePanel.add( getSpacer() );

                List<Object> values;
                if ( parm.examples!=null ) {
                    values= parm.examples;
                } else {
                    values= parm.enums;
                }
                
                // https://sourceforge.net/p/autoplot/feature-requests/796/
                // little kludge: if the default is a timerange, then assume it must always be a timerange.
                char type= parm.type;
                if ( type=='S' ) {
                    if ( UnitsUtil.isTimeLocation( ((DatumRange)parm.deft).getUnits() ) ) {
                        type='T';
                    }
                }
                
                switch (type) {
                    case 'U':
                        {
                            final DataSetSelector sel= new DataSetSelector();
                            sel.setPlotItButtonVisible(false);
                            String val;
                            if (params.get(vname)!=null ) {
                                val= params.get(vname);
                                if ( val.startsWith("'") ) val= val.substring(1);
                                if ( val.endsWith("'") ) val= val.substring(0,val.length()-1);
                            } else {
                                val= String.valueOf( parm.deft );
                                params.put( vname, val );
                            }       
                            sel.setRecent( DataSetSelector.getDefaultRecent() );
                            sel.setValue( val );
                            valuePanel.add( getSpacer(7) );  // kludge.  Set on Jeremy's home Ubuntu
                            valuePanel.add( sel );
                            sel.setValue( val );
                            valuePanel.add( getSpacer(10) ); // put a little space in after the selector as well.
                            ctf= sel;
                            break;
                        }
                    case 'R':
                    case 'L':
                        {
                            final DataSetSelector sel= new DataSetSelector();
                            sel.setPlotItButtonVisible(false);
                            sel.setSuggestFiles(true);
                            sel.setSuggestFsAgg(false);
                            sel.setDisableDataSources(true);
                            String surl= parm.deft.toString();
                            int i= surl.lastIndexOf(".");
                            int j= surl.lastIndexOf("/");
                            if ( i>j ) {
                                String ext= surl.substring(i);
                                sel.setAcceptPattern(".*\\"+ext);
                            }
                            
                            String val;
                            if (params.get(vname)!=null ) {
                                val= params.get(vname);
                                if ( val.startsWith("'") ) val= val.substring(1);
                                if ( val.endsWith("'") ) val= val.substring(0,val.length()-1);
                            } else {
                                val= String.valueOf( parm.deft );
                                params.put( vname, val );
                            }       
                            sel.setRecent( DataSetSelector.getDefaultRecent() );
                            sel.setValue( val );
                            valuePanel.add( getSpacer(7) );  // kludge.  Set on Jeremy's home Ubuntu
                            valuePanel.add( sel );
                            sel.setValue( val );
                            valuePanel.add( getSpacer(10) ); // put a little space in after the selector as well.
                            ctf= sel;
                            break;
                        }                        
                    case 'T':
                        {
                            String val;
                            if ( params.get(vname)!=null ) {
                                val= params.get(vname);
                                if ( val.startsWith("'") ) val= val.substring(1);
                                if ( val.endsWith("'") ) val= val.substring(0,val.length()-1);
                            } else {
                                val= String.valueOf( parm.deft );
                                params.put( vname, val );
                            }       
                            final RecentComboBox tcb= new RecentComboBox();
                            tcb.setPreferenceNode( RecentComboBox.PREF_NODE_TIMERANGE );
                            Dimension x= tcb.getPreferredSize();
                            x.width= Integer.MAX_VALUE;
                            tcb.setMaximumSize(x);
                            tcb.setAlignmentX( JComponent.LEFT_ALIGNMENT );
                            tcb.setText( val );
                            ctf= tcb;
                            Icon fileIcon= new javax.swing.ImageIcon( Util.class.getResource("/org/autoplot/datasource/calendar.png"));
                            JButton button= new JButton( fileIcon );
                            button.addActionListener((ActionEvent e) -> {
                                TimeRangeTool tt= new TimeRangeTool();
                                tt.setSelectedRange(tcb.getSelectedItem().toString());
                                int r= WindowManager.showConfirmDialog( paramsPanel, tt, "Select Time Range", JOptionPane.OK_CANCEL_OPTION );
                                if ( r==JOptionPane.OK_OPTION) {
                                    tcb.setSelectedItem(tt.getSelectedRange());
                                }
                            });     
                            button.setToolTipText("Time Range Tool");
                            valuePanel.add( tcb );
                            button.setAlignmentX( JComponent.LEFT_ALIGNMENT );
                            valuePanel.add( button );
                            break;
                        }
                    case 'C':
                        {
                            String val;
                            if ( params.get(vname)!=null ) {
                                val= params.get(vname);
                                if ( val.startsWith("'") ) val= val.substring(1);
                                if ( val.endsWith("'") ) val= val.substring(0,val.length()-1);
                            } else {
                                val= ColorUtil.encodeColor( (Color)parm.deft );
                                params.put( vname, val );
                            }       
                            final JComponent fjcf;
                                                        
                            if ( values!=null && values.size()>0 ) {
                                Object[] labels;
                                if ( parm.examples!=null ) {
                                    labels= parm.examples.toArray();
                                } else {
                                    labels= values.toArray();
                                }
                                if ( hasLabels ) {
                                    Object olabels= parm.constraints.get("labels");
                                    if ( olabels instanceof List ) {
                                        List labelsList= (List)olabels;
                                        boolean useLabels= false; // only use labels if they add information.
                                        for ( int i=0; i<values.size(); i++ ) {
                                            if ( !String.valueOf(values.get(i)).equals(labelsList.get(i)) ) {
                                                useLabels= true;
                                            }
                                        }
                                        labels= new String[values.size()];
                                        for ( int i=0; i<values.size(); i++ ) {
                                            labels[i]= useLabels ? values.get(i)+": "+labelsList.get(i) : values.get(i);
                                        }
                                    }
                                }
                                for ( int i=0; i<labels.length; i++ ) {
                                    labels[i]= ColorUtil.encodeColor( (Color)labels[i] );
                                }
                                JComboBox jcb= new JComboBox(labels);
                                jcb.setEditable(false);
                                int index= values.indexOf(ColorUtil.decodeColor(val));
                                if ( index>-1 ) {
                                    jcb.setSelectedIndex(index);
                                } else {
                                    jcb.setSelectedItem(val);
                                }
                                if ( !jcb.getSelectedItem().toString().startsWith( val ) ) {
                                    logger.fine("uh-oh.");
                                }
                                ctf= jcb;
                                jcb.addActionListener((ActionEvent e) -> {
                                    redoVariables( env, src, ParametersFormPanel.this.params, zparamsPanel );
                                });
                                Dimension x= ctf.getPreferredSize();
                                x.width= Integer.MAX_VALUE;
                                ctf.setMaximumSize(x);
                                ctf.setAlignmentX( JComponent.LEFT_ALIGNMENT );
                                                              
                            } else {
                                final JTextField tf= new JTextField();
                                Dimension x= tf.getPreferredSize();
                                x.width= Integer.MAX_VALUE;
                                tf.setMaximumSize(x);
                                tf.setAlignmentX( JComponent.LEFT_ALIGNMENT );
                                tf.setText( val );
                                ctf= tf;    
                            }
                            fjcf= ctf;
                            valuePanel.add( ctf );
                            
                            if ( values==null || values.isEmpty() ) {
                                Icon fileIcon= new javax.swing.ImageIcon( Util.class.getResource("/org/autoplot/datasource/calendar.png"));
                                JButton button= new JButton( fileIcon );
                                button.addActionListener((ActionEvent e) -> {
                                    Color c;
                                    String t;
                                    if ( fjcf instanceof JComboBox ) {
                                        t= (String)(((JComboBox)fjcf).getSelectedItem());
                                    } else {
                                        t= ((JTextField)fjcf).getText();
                                    }
                                    try {
                                        c= ColorUtil.decodeColor( t );
                                    } catch ( IllegalArgumentException ex ) {
                                        c= Color.GRAY;
                                    }
                                    c = JColorChooser.showDialog( paramsPanel, "color", c );
                                    if ( c!=null ) {
                                        if ( fjcf instanceof JComboBox ) {
                                            ((JComboBox)fjcf).setSelectedItem(ColorUtil.encodeColor(c));
                                        } else {
                                            ((JTextField)fjcf).setText( ColorUtil.encodeColor(c) );
                                        }

                                    }
                                });     
                                button.setToolTipText("Colorpicker");
                                button.setAlignmentX( JComponent.LEFT_ALIGNMENT );
                                valuePanel.add( button );
                            }
                            break;
                        }                        
                    default:
                        {
                            String val;
                            Object oval= params.get(vname);
                            if ( oval!=null ) {
                                if ( oval instanceof String ) {
                                    val= (String)oval;
                                    if ( val.startsWith("'") ) val= val.substring(1);
                                    if ( val.endsWith("'") ) val= val.substring(0,val.length()-1);
                                } else {
                                    throw new IllegalArgumentException("param should be a string: "+vname);
                                }
                            } else {
                                val= String.valueOf( parm.deft );
                                params.put( vname, val );
                            }       
                            if ( values!=null && values.size()>0 ) {
                                if ( isBool ) {
                                    JCheckBox jcb= new JCheckBox( label );
                                    jcb.setSelected( val.equals("T") || val.equals("1") || val.equals("True") );
                                    jcb.addActionListener((ActionEvent e) -> {
                                        redoVariables( env, src, ParametersFormPanel.this.params, zparamsPanel );
                                    });
                                    ctf= jcb;
                                } else {
                                    Object[] labels= values.toArray();
                                    if ( hasLabels ) {
                                        Object olabels= parm.constraints.get("labels");
                                        if ( olabels instanceof List ) {
                                            List labelsList= (List)olabels;
                                            boolean useLabels= false; // only use labels if they add information.
                                            for ( int i=0; i<values.size(); i++ ) {
                                                if ( !String.valueOf(values.get(i)).equals(labelsList.get(i)) ) {
                                                    useLabels= true;
                                                }
                                            }                                            
                                            labels= new String[values.size()];
                                            for ( int i=0; i<values.size(); i++ ) {
                                                labels[i]= useLabels ? values.get(i)+": "+labelsList.get(i) : String.valueOf( values.get(i) );
                                            }
                                        }
                                    }
                                    JComboBox jcb= new JComboBox(labels);
                                    
                                    if ( parm.examples==null || parm.examples.isEmpty() ) {
                                        jcb.setEditable(false);
                                    } else {
                                        jcb.setEditable(true);
                                    }
                                    
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
                                    int index= values.indexOf(oval);
                                    if ( index>-1 ) {
                                        jcb.setSelectedIndex(index);
                                    } else {
                                        jcb.setSelectedItem(oval);
                                    }
                                    if ( parm.examples==null && !jcb.getSelectedItem().toString().startsWith(oval.toString()) ) {
                                        logger.fine("uh-oh.");
                                    }
                                    ctf= jcb;                                    
                                    jcb.addActionListener((ActionEvent e) -> {
                                        redoVariables( env, src, ParametersFormPanel.this.params, zparamsPanel );
                                    });
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
                            break;
                        }
                }

                boolean shortLabel= ( parm.type=='R' || String.valueOf(parm.deft).length()>22 ) ;
                final String fdeft= shortLabel ? "default" : String.valueOf(parm.deft);
                
                final String fvalue= ( isBool && parm.deft instanceof Integer ) ? ( parm.deft.equals(0) ? "F" : "T" ) :  String.valueOf(parm.deft);
                final JComponent ftf= ctf;
                JButton defaultButton= new JButton( new AbstractAction(  isBool ? fvalue : fdeft ) {
                    @Override
                    public void actionPerformed( ActionEvent e ) {
                        if ( ftf instanceof DataSetSelector ) {
                            ((DataSetSelector)ftf).setValue(fvalue);
                        } else if ( ftf instanceof JComboBox ) {
                            JComboBox jcb= ((JComboBox)ftf);
                            for ( int i=0; i<jcb.getItemCount(); i++ ) {
                                String item= jcb.getItemAt(i).toString();
                                if ( item.contains(":") ) {
                                    if ( item.startsWith( fvalue + ":" ) ) {
                                        jcb.setSelectedIndex(i);
                                    }
                                } else {
                                    if ( fvalue.equals( jcb.getItemAt(i).toString() ) ) {
                                        jcb.setSelectedIndex(i);
                                    }
                                }
                            }
                        } else if ( ftf instanceof JCheckBox ) {
                            ((JCheckBox)ftf).setSelected( fvalue.startsWith("T") );
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
                if ( parm.type=='C' ) {
                    fd.deftsList.add( ColorUtil.encodeColor( (Color)parm.deft ) );
                } else {
                    fd.deftsList.add( String.valueOf( parm.deft ) );
                }
                fd.deftObjectList.add( parm.deft );
                fd.typesList.add( parm.type );

            }

            hasVars= parms.size()>0;

            if ( !hasVars ) {
                JLabel l= new JLabel("<html><i>(no input parameters)</i></html>");
                l.setToolTipText("This looks through the code for getParam calls, and no conforming calls were found");
                paramsPanel.add( l );
            } 
            
            paramsPanel.add( Box.createVerticalStrut( paramsPanel.getFont().getSize() * 2 ) );
            paramsPanel.revalidate();

            fd.count= fd.paramsList.size();
                    
        } catch (IOException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }
        return fd;

    }
    
    /**
     * return the current state of the form data.
     * @return 
     */
    public FormData getFormData() {
        return this.fd;
    }

}
