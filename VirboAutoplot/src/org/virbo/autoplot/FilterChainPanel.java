/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

/**
 * Dialog to create a chain of filters.  Ideally, this will eventually show the data before and
 * after each filter, provide documentation for each filter, and provide a place for a filter to
 * provide a filter (e.g. FFT window size and window types).
 * @author jbf
 */
public class FilterChainPanel extends JPanel {

    String component="";
    List<String> filters;
    JPanel content= new JPanel();
    JPanel add;

    public String getFilters() {
        StringBuilder result= new StringBuilder();
        if ( !component.equals("") ) result.append(component);

        for ( int i=0; i<filters.size(); i++ ) {
            result.append("|").append( filters.get(i));
        }
        return result.toString();
    }

    public void setFilters( String filterStr ) {
        if ( filterStr.trim().length()==0 ) {
            filters= new LinkedList();
        } else {
            if ( filterStr.charAt(0)=='|') {
                component= "";
                filterStr= filterStr.substring(1);
            } else {
                int i= filterStr.indexOf("|");
                if ( i==-1 ) {
                    component= filterStr;
                    filterStr="";
                } else {
                    component= filterStr.substring(0,i);
                    filterStr= filterStr.substring(i+1);
                }
            }
            if ( filterStr.trim().length()==0 ) {
                filters= new LinkedList();
            } else {
                String[] ss= filterStr.split("\\|");

                LinkedList<String> ff= new LinkedList( Arrays.asList(ss) );

                filters= ff;
            }
        }
        
        init();
    }

    /**
     * get the index of the component in the list.
     * @param sub
     * @return
     */
    private int getIndex( Component sub ) {
        Component[] ccs= sub.getParent().getComponents();
        int ifi=-1;
        for ( int i=0; i<ccs.length; i++ ) {
            if ( ccs[i]==sub ) ifi= i;
        }
        return ifi;
    }

    private void deleteFilter( int in ) {
        filters.remove(in);
    }

    private void addFilter( int idx ) {
        JPanel optionsPanel= new JPanel();

        optionsPanel.setLayout( new BoxLayout(optionsPanel,BoxLayout.Y_AXIS) );

        ButtonGroup group= new ButtonGroup();

        // CAUTION: ") " is used to delimit the annotation from the command.
        String[] opts= new String[] {
        "abs() return the absolute value of the data.",
        "accum() running sum of the rank 1 data. (opposite of diff).",
        "add(1.) add a scalar",
        "butterworth(2,500,550,True) Butterworth notch filter",
        "collapse0() average over the zeroth dimension to reduce the dimensionality. (See total)",
        "collapse1() average over the first dimension to reduce the dimensionality.",
        "cos() cos of the data in radians. (No units check)",
        "dbAboveBackgroundDim1(10) show data as decibels above the 10% level",
        "detrend(5) remove boxcar average from the rank 1 data. (See smooth)",
        "diff() finite differences between adjacent elements in the rank 1 data.",
        "divide(2.) divide by a scalar",
        "exp10() plot pow(10,ds)",
        "fftPower(128) plot power spectrum by breaking waveform data in windows of length size.",
        "fftPower(128,2,'Hanning') power spectrum with sliding window (1=no overlap,2=50% 4=75%).",
        "flatten() flatten a rank 2 dataset. The result is a n,3 dataset of [x,y,z]. (opposite of grid)",
        "grid() grid the rank2 buckshot but gridded data into a rank 2 table.",
        "hanning(128) run a hanning window before taking fft.",
        "histogram() perform an \"auto\" histogram of the data that automatically sets bins. ",
        "logHistogram() perform the auto histogram in the log space.",
        "log10() take the base-10 log of the data." ,
        "magnitude() calculate the magnitude of the vectors ",
        "multiply(2) multiply by a scalar ",
        "negate() flip the sign on the data.",
        "setUnits('nT') reset the units to the new units",
        "sin() sin of the data in radians. (No units check)",
        "slice0(0) slice the data on the zeroth dimension (often time) at the given index.",
        "slice1(0) slice the data on the first dimension at the given index.",
        "slices(':',2,3) slice the data on the first and second dimensions, leaving the zeroth alone.",
        "smooth(5) boxcar average over the rank 1 data.  (See detrend)",
        "reducex('1 hr') reduce data to 1 hr intervals",
        "toDegrees() convert the data to degrees. (No units check)",
        "toRadians() convert the data to radians. (No units check) ",
        "total1() total over the first dimension to reduce the dimensionality. (See collapse0)",
        "transpose() transpose the rank 2 dataset.",
        "unbundle('Bx') unbundle a component ",
        "valid() replace data with 1 where valid, 0 where invalid",
        };

        for ( int i=0; i<opts.length; i++ ) {
            JRadioButton cb= new JRadioButton(opts[i]);
            group.add(cb);
            optionsPanel.add(cb);
        }

        JScrollPane p= new JScrollPane(optionsPanel);
        Dimension d= java.awt.Toolkit.getDefaultToolkit().getScreenSize();

        Dimension v= new Dimension( 700, Math.min( 700, d.height-100 ) );
        p.setMaximumSize(v);
        p.setPreferredSize(v);
        
        p.setVerticalScrollBarPolicy( JScrollPane.VERTICAL_SCROLLBAR_ALWAYS );
        p.getVerticalScrollBar().setUnitIncrement(optionsPanel.getFont().getSize());
       int r= AutoplotUtil.showConfirmDialog( this, p, "Add Filter", JOptionPane.OK_CANCEL_OPTION );
       if ( r==JOptionPane.OK_OPTION ) {
           String ss=null;
           Enumeration<AbstractButton> ee= group.getElements();
           while ( ee.hasMoreElements() ) {
               AbstractButton b= ee.nextElement();
               if ( b.isSelected() ) {
                   String s= b.getText();
                   int ii= s.indexOf(") ");
                   ss= s.substring(0,ii+1);
               }
           }
           if ( ss!=null ) {
               filters.add( idx, ss );

               JPanel one= onePanel(idx);

               Component[] ccs= content.getComponents();
               content.removeAll();

               for ( int i=0; i<idx; i++ ) {
                   content.add(ccs[i]);
               }
               content.add( one );
               for ( int i=idx; i<ccs.length-1; i++ ) {
                   content.add(ccs[i]);
               }
               content.add( add );
               content.add(Box.createVerticalGlue());
               
               content.revalidate();
           }
       }

    }

    private JPanel onePanel( int fi ) {
        final JPanel sub= new JPanel( new BorderLayout() );

        JButton subAdd= new JButton("");
        subAdd.setIcon( new ImageIcon( FilterChainPanel.class.getResource("/org/virbo/datasource/add.png") ) );

        if ( fi>=0 ) {
            subAdd.addActionListener( new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    org.das2.util.LoggerManager.logGuiEvent(e);
                    int ifi= getIndex( sub );
                    addFilter(ifi);
                }
            } );
        } else {
           subAdd.addActionListener( new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    org.das2.util.LoggerManager.logGuiEvent(e);
                    addFilter(filters.size());
                }
            } );
        }

        sub.add( subAdd, BorderLayout.WEST );

        if ( fi>=0 ) {
            JButton subDelete= new JButton("");
            subDelete.setIcon( new ImageIcon( FilterChainPanel.class.getResource("/org/virbo/datasource/subtract.png") ) );
            subDelete.addActionListener( new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    org.das2.util.LoggerManager.logGuiEvent(e);                    
                    int ifi= getIndex( sub );
                    deleteFilter(ifi);
                    Container parent= sub.getParent();
                    parent.remove(sub);
                    parent.validate();
                }
            } );
            sub.add( subDelete, BorderLayout.EAST );
        }

        if ( fi>=0 ) {
            final JTextField tf= new JTextField();
            tf.setText(filters.get(fi));
            tf.addActionListener( new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    int ifi= getIndex( sub );
                    filters.set( ifi, tf.getText() );
                }
            });
            tf.addFocusListener( new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    int ifi= getIndex( sub );
                    filters.set( ifi, tf.getText() );
                }
            });
            sub.add( tf, BorderLayout.CENTER );

        } else {
            final JLabel tf= new JLabel();
            tf.setText("<html><em>(click to add)</em></html>");
            sub.add( tf, BorderLayout.CENTER );

        }

        Dimension maximumSize = sub.getPreferredSize();
        maximumSize.width = Integer.MAX_VALUE;
        sub.setMaximumSize(maximumSize);

        return sub;
    }

    void init() {
        content= new JPanel();
        this.setPreferredSize( new Dimension( 300, 300 ) );

        BoxLayout lo= new BoxLayout( content, BoxLayout.Y_AXIS );
        content.setLayout( lo );

        JScrollPane pane= new JScrollPane( content );
        pane.setVerticalScrollBarPolicy( JScrollPane.VERTICAL_SCROLLBAR_ALWAYS );
        for ( int i=0; i<filters.size(); i++ ) {
            content.add( onePanel(i) );
        }

        add= onePanel(-1);
        content.add( add );

        content.add(Box.createVerticalGlue());
        
        this.setLayout( new BorderLayout() );
        this.add( pane );

        String msg= "<html>Add filters for operations like<br>smoothing and slicing data.<br>";
        if ( this.component.length()>0 ) {
            msg+= "component="+component+"<br>";
        }
        this.add( new JLabel(msg), BorderLayout.NORTH );

    }

    public JPanel getPanel() {
        return this;
    }

    public static void main( String[] args ) {
        FilterChainPanel fpc= new FilterChainPanel();
        fpc.setFilters("");
        fpc.init();
        JOptionPane.showMessageDialog( null, fpc.getPanel() );
    }
}
