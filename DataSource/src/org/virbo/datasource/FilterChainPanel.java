/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.datasource;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

/**
 *
 * @author jbf
 */
public class FilterChainPanel extends JPanel {

    List<String> filters;
    JPanel content= new JPanel();
    JPanel add;

    public String getFilters() {
        StringBuffer result= new StringBuffer();
        for ( int i=0; i<filters.size(); i++ ) {
            result.append( "|"+filters.get(i) );
        }
        return result.toString();
    }

    public void setFilters( String filterStr ) {
        if ( filterStr.trim().length()==0 ) {
            filters= new LinkedList();
        } else {
            if ( filterStr.charAt(0)=='|') {
                filterStr= filterStr.substring(1);
            }
            List<String> f= new ArrayList<String>();
            String[] ss= filterStr.split("\\|");

            LinkedList<String> ff= new LinkedList( Arrays.asList(ss) );

            filters= ff;
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

    private String addFilter( int idx ) {
        JPanel optionsPanel= new JPanel();
        optionsPanel.setLayout( new BoxLayout(optionsPanel,BoxLayout.Y_AXIS) );

        ButtonGroup group= new ButtonGroup();
        String[] opts= new String[] {
        "histogram() perform an \"auto\" histogram of the data that automatically sets bins. ",
        "logHistogram() perform the auto histogram in the log space.",
        "log10() take the base-10 log of the data." ,
        "exp10() plot pow(10,ds)",
        "slice0(0) slice the data on the zeroth dimension (often time) at the given index.",
        "slice1(0) slice the data on the first dimension at the given index.",
        "slices(':',2,3)) slice the data on the first and second dimensions, leaving the zeroth alone.",
        "collapse0() average over the zeroth dimension to reduce the dimensionality.",
        "collapse1() average over the first dimension to reduce the dimensionality.",
        "transpose() transpose the rank 2 dataset.",
        "fftPower(128) plot power spectrum by breaking waveform data in windows of length size (experimental, not for publication).",
        "smooth(5) boxcar average over the rank 1 data",
        "diff() finite differences between adjacent elements in the rank 1 data.",
        "accum() running sum of the rank 1 data. (opposite of diff).",
        "grid() grid the rank2 buckshot but gridded data into a rank 2 table.",
        "flatten() flatten a rank 2 dataset. The result is a n,3 dataset of [x,y,z]. (opposite of grid)",
        "negate() flip the sign on the data.",
        "cos() cos of the data in radians. (No units check)",
        "sin() sin of the data in radians. (No units check)",
        "toDegrees() convert the data to degrees. (No units check)",
        "toRadians() convert the data to radians. (No units check) ",
        "magnitude() calculate the magnitude of the vectors ",
        "unbundle('Bx') unbundle a component ",
        "dbAboveBackgroundDim1(10) show data as decibels above the 10% level", };

        for ( int i=0; i<opts.length; i++ ) {
            JRadioButton cb= new JRadioButton(opts[i]);
            group.add(cb);
            optionsPanel.add(cb);
        }

       int r= JOptionPane.showConfirmDialog(this,optionsPanel,"Add Filter",JOptionPane.OK_CANCEL_OPTION);
       if ( r==JOptionPane.OK_OPTION ) {
           String ss=null;
           Enumeration<AbstractButton> ee= group.getElements();
           while ( ee.hasMoreElements() ) {
               AbstractButton b= ee.nextElement();
               if ( b.isSelected() ) {
                   String s= b.getText();
                   int ii= s.indexOf(" ");
                   ss= s.substring(0,ii);
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

       return null;
    }

    private JPanel onePanel( int fi ) {
        final JPanel sub= new JPanel( new BorderLayout() );

        JButton subAdd= new JButton("");
        subAdd.setIcon( new ImageIcon( FilterChainPanel.class.getResource("/org/virbo/datasource/add.png") ) );

        if ( fi>=0 ) {
            subAdd.addActionListener( new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    int ifi= getIndex( sub );
                    String s= addFilter(ifi);
                }
            } );
        } else {
           subAdd.addActionListener( new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    String s= addFilter(filters.size());
                }
            } );
        }

        sub.add( subAdd, BorderLayout.WEST );

        if ( fi>=0 ) {
            JButton subDelete= new JButton("");
            subDelete.setIcon( new ImageIcon( FilterChainPanel.class.getResource("/org/virbo/datasource/subtract.png") ) );
            subDelete.addActionListener( new ActionListener() {
                public void actionPerformed(ActionEvent e) {
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

        this.add( new JLabel("<html>Add filters for operations like<br>smoothing and slicing data.<br>"), BorderLayout.NORTH );

    }

    public JPanel getPanel() {
        return this;
    }

}
