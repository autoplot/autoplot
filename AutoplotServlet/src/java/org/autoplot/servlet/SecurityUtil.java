/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.servlet;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;

/**
 *
 * @author jbf
 */
public class SecurityUtil {

    /**
     * Return true if the address is of a domain that is trusted by the server.
     * For example, if "X-Forwarded-For" is found in the headers, we will trust
     * it.
     * @param who the hostname or address.
     * @return 
     */
    public static boolean whoIsTrusted( String who ) {
        if ( who.equals("localhost") || who.startsWith("192.168") || who.startsWith("10.0") ) {
            return true;
        } else {
            return false;
        }
    }
    
    /**
     * return an identifier for the client, looking for 127.0.0.1 and X-Forwarded-For
     * @param request
     * @return 
     */
    public static String clientId( HttpServletRequest request ) {
        String who = request.getRemoteAddr();
        if (who.equals("0:0:0:0:0:0:0:1")) {
            who = "localhost";
        }
        if (who.equals("127.0.0.1")) {
            who = "localhost";
        }
        if ( whoIsTrusted( who ) ) {
            String remoteHost= request.getHeader("X-Forwarded-For");
            if ( remoteHost!=null ) {
                who= remoteHost;
            }
        }
        
        return who;
    }
    
    /**
     * check that the person has access to this resource
     *
     * @param request
     * @throws IOException, SecurityException
     */
    public static void checkAllowed(HttpServletRequest request) throws IOException {
        String who = clientId(request);
        
        String home = System.getProperty("AUTOPLOT_SERVLET_HOME");
        if (home == null) {
            home = "/tmp/autoplotservlet/";
        }

        home = new File(home).getCanonicalPath();

        home = home + File.separator;

        new File(home).mkdirs();

        File hostsallow = new File(home + "allowhosts");

        if (!hostsallow.exists()) {
            PrintWriter write = new PrintWriter(new FileWriter(hostsallow));
            write.println("# Initially, only clients from the localhost can connect.  List the allowed clients, one per line.");
            write.println("# Globs like 192.168.0.* may be used.");
            write.println("localhost");
            write.close();
        }

        if (hostsallow.exists()) {
            boolean reject = true;
            BufferedReader r = new BufferedReader(new FileReader(hostsallow));
            String h = r.readLine();
            while (h != null) {
                int i = h.indexOf("#");
                if (i > -1) {
                    h = h.substring(0, i);
                }
                if (h.trim().length() == 0) {
                    h = r.readLine();
                    continue;
                }
                h = h.replaceAll("\\*", "\\.\\*");

                if (Pattern.matches(h, who)) {
                    reject = false;
                }

                h = r.readLine();
            }

            if (reject) {
                throw new SecurityException(hostsallow + " does not permit host=\"" + who + "\"");
            }
        } else {
            throw new SecurityException(hostsallow + " file does not exist: " + hostsallow);
        }
    }

}
