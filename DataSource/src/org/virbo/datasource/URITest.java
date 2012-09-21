/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.datasource;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;

/**
 *
 * @author jbf
 */
public class URITest {

    public static void main(String[] args) throws Exception {
        URI uri = new URI("file:/c:/foo.xyz");
        System.err.println(uri); // logger okay
        uri = new URI("bin.file:/c:/foo.xyz");
        System.err.println(uri); // logger okay
        uri = new URI("bin.http://c:/foo.xyz");
        System.err.println(uri); // logger okay
        uri = new URI("bin.http://c:/foo.xyz?x[3:5]");
        System.err.println(uri); // logger okay
        uri = new File("C:\\Documents and Settings\\jbf\\My Documents\\foo.jy").toURI();
        System.err.println(uri); // logger okay
        uri = new File("/home/jbf/my%file.txt").toURI();
        System.err.println(uri); // logger okay
        System.err.println(uri.toURL()); // logger okay
        URL url = uri.toURL();
        InputStream in = url.openStream();
        int ch = in.read();
        while (ch!=-1) {
            System.err.print((char)ch); // logger okay
            ch = in.read();
        }

    }
}
