/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import java.util.List;

/**
 *
 * @author jbf
 */
public class ListLoopTestDan {
 
    public static void main(String[] args) {

        List a = new java.util.LinkedList();
        //List a = new java.util.ArrayList();

        a.add(1);

        long t0 = System.currentTimeMillis();

        for ( int i=0; i<100000; i++ ) {
            a.add(3.14);
        }
        
        for ( int i=0; i<300000; i++ ) {
            a.add(3.14);
            a.remove(0);
        }
        System.err.println(System.currentTimeMillis() - t0);
        System.err.println(a);
        
    }
}
