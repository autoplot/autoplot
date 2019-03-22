/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import java.io.File;
import java.io.UnsupportedEncodingException;

/**
 *
 * @author jbf
 */
public class Unicode {
    public static void main(String[] args) throws UnsupportedEncodingException {
        System.err.println("/Spaß/");
        for ( byte c : "/Spaß/".getBytes("UTF-8") ) {
            System.err.println( c );
        }
        String[] ff= new File("/home/jbf/public_html/i18n/").list();
        System.err.println("ls /home/jbf/public_html/i18n/");
        for ( String f: ff ) { 
            System.err.println(f);
        }
    }
}
