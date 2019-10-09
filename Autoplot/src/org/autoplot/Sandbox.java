/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot;

import java.io.FileDescriptor;
import java.net.InetAddress;
import java.security.Permission;
import org.autoplot.datasource.AutoplotSettings;

/**
 *
 * @author jbf
 */
public class Sandbox {
    
    public static SecurityManager getSandboxManager() {
        SecurityManager limitedSecurityManager = new SecurityManager() {
            @Override
            public ThreadGroup getThreadGroup() {
                return super.getThreadGroup(); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void checkSecurityAccess(String target) {
                super.checkSecurityAccess(target); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void checkSetFactory() {
                super.checkSetFactory(); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void checkPackageDefinition(String pkg) {
                super.checkPackageDefinition(pkg); //To change body of generated methods, choose Tools | Templates.
            }


            @Override
            public void checkPrintJobAccess() {
                super.checkPrintJobAccess(); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void checkPropertyAccess(String key) {
                super.checkPropertyAccess(key); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void checkPropertiesAccess() {
                super.checkPropertiesAccess(); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void checkMulticast(InetAddress maddr) {
                super.checkMulticast(maddr); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void checkAccept(String host, int port) {
                super.checkAccept(host, port); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void checkListen(int port) {
                super.checkListen(port); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void checkConnect(String host, int port, Object context) {
                super.checkConnect(host, port, context); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void checkConnect(String host, int port) {
                super.checkConnect(host, port); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void checkDelete(String file) {
                super.checkDelete(file); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void checkWrite(String file) {
                super.checkWrite(file); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void checkWrite(FileDescriptor fd) {
                super.checkWrite(fd); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void checkRead(String file, Object context) {
                super.checkRead(file, context); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void checkRead(String file) {
                String autoplotData= AutoplotSettings.settings().resolveProperty( AutoplotSettings.PROP_AUTOPLOTDATA );
                if ( !file.startsWith( autoplotData ) ) {
                    super.checkRead(file);
                }
            }

            @Override
            public void checkRead(FileDescriptor fd) {
                super.checkRead(fd); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void checkLink(String lib) {
                super.checkLink(lib); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void checkExec(String cmd) {
                super.checkExec(cmd); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void checkExit(int status) {
                super.checkExit(status); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void checkAccess(ThreadGroup g) {
                super.checkAccess(g); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void checkAccess(Thread t) {
                super.checkAccess(t); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void checkCreateClassLoader() {
                super.checkCreateClassLoader(); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void checkPermission(Permission perm, Object context) {
                super.checkPermission(perm, context); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void checkPermission(Permission perm) {
                //super.checkPermission(perm); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public Object getSecurityContext() {
                return super.getSecurityContext(); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            protected Class[] getClassContext() {
                return super.getClassContext(); //To change body of generated methods, choose Tools | Templates.
            }
            
        };
        return limitedSecurityManager;
    }

    /**
     * lock down this thread and all child threads so that they cannot do damage
     * to the running system.
     */
    public static void enterSandbox(  ) {
        System.setSecurityManager( getSandboxManager() );
    }
    
}
