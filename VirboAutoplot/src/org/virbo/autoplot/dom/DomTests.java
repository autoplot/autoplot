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
        Application app= new Application();
        new ApplicationController(null,app);
        app.getController().addPanel(null);

        XMLEncoder enc= new XMLEncoder( new FileOutputStream("/home/jbf/foo.xml") );
        enc.writeObject(enc);
        enc.close();
    }

}
