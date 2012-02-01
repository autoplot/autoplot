/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.dom;

import java.beans.XMLEncoder;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

/**
 *
 * @author jbf
 */
public class DomTests {
    public static void main(String[] args) throws FileNotFoundException {
        //serializeTest();
        diffsTest();
    }

    private static void serializeTest() throws FileNotFoundException {
        Application app = new Application();
        new ApplicationController(null, app);
        app.getController().addPlotElement(null,null);

        XMLEncoder enc = new XMLEncoder(new FileOutputStream("/home/jbf/foo.xml"));
        enc.writeObject(enc);
        enc.close();
    }

    public static void diffsTest() {
        Plot p1= new Plot();
        Plot p2= (Plot) p1.copy();
        
        p1.getXaxis().range= p1.getXaxis().range.next();
        
        System.err.println( "diffsTest: " + p1.diffs(p2));
    }
}
