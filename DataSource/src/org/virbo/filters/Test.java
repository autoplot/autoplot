/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.filters;

import javax.swing.JOptionPane;

/**
 *
 * @author mmclouth
 */
public class Test {
    
    public static void testAdd() {
        FilterEditorPanel p= new AddFilterEditorPanel();
        p.setFilter("|add(50)");
        JOptionPane.showMessageDialog( null, p );
        System.err.println( p.getFilter() );
    }
    
    public static void testSlice() {
        FilterEditorPanel p= new SliceFilterEditorPanel();
        p.setFilter("|slice1(50)");
        JOptionPane.showMessageDialog( null, p );
        System.err.println( p.getFilter() );
    }
    
    public static void main( String[] args ) {
        //testAdd();
        testSlice();
    }
    
    
}
