/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test.dom;

import java.beans.XMLDecoder;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import org.autoplot.dom.Application;

/**
 *
 * @author jbf
 */
public class Test2 {
    public static void main(String[] args ) throws IOException {
        Application init0= (Application) new XMLDecoder( new FileInputStream( "/home/jbf/init.vap")).readObject();
        Application twoPanel= (Application) new XMLDecoder( new FileInputStream( "/home/jbf/contextOverview2.vap")).readObject();
        init0.syncTo(twoPanel);
        System.err.println(init0);
    }
}
