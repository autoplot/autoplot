/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot;

import java.awt.AWTPermission;
import java.io.FileDescriptor;
import java.net.InetAddress;
import java.security.Permission;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.autoplot.datasource.AutoplotSettings;
import org.das2.util.LoggerManager;

/**
 *
 * @author jbf
 */
public class Sandbox {

    private static final Logger logger = org.das2.util.LoggerManager.getLogger("autoplot.security");        
    
    public static SecurityManager getSandboxManager() {
        
        SecurityManager limitedSecurityManager = new SecurityManager() {
            @Override
            public ThreadGroup getThreadGroup() {
                return super.getThreadGroup(); 
            }

            @Override
            public void checkSecurityAccess(String target) {
                logger.fine( "checkSecurityAccess(target)");
            }

            @Override
            public void checkSetFactory() {
                logger.fine( "checkSetFactory()");
            }

            @Override
            public void checkPackageDefinition(String pkg) {
                logger.fine( "checkPackageDefinition(pkg)");
            }


            @Override
            public void checkPrintJobAccess() {
                logger.fine( "checkPrintJobAccess" );
            }

            @Override
            public void checkPropertyAccess(String key) {
                logger.fine( "checkPropertyAccess" );
            }

            @Override
            public void checkPropertiesAccess() {
                logger.fine( "checkPropertiesAccess()");
            }

            @Override
            public void checkMulticast(InetAddress maddr) {
                logger.fine( "checkMulticast(maddr)");
            }

            @Override
            public void checkAccept(String host, int port) {
                logger.fine( "checkAccept(host, port)"); 
            }

            @Override
            public void checkListen(int port) {
                logger.fine( "checkListen(port)");
            }

            @Override
            public void checkConnect(String host, int port, Object context) {
                logger.fine( "checkConnect(host, port, context)");
            }

            @Override
            public void checkConnect(String host, int port) {
                logger.fine( "checkConnect(host, port)");
            }

            @Override
            public void checkDelete(String file) {
                logger.fine( "checkDelete(file)");
            }

            @Override
            public void checkWrite(String file) {
                logger.fine( "checkWrite(file)");
            }

            @Override
            public void checkWrite(FileDescriptor fd) {
                logger.fine( "checkWrite(fd)");
            }

            @Override
            public void checkRead(String file, Object context) {
                logger.fine( "checkRead(file, context)");
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
                logger.fine( "checkRead(fd)");
            }

            @Override
            public void checkLink(String lib) {
                logger.fine( "checkLink(lib)");
            }

            @Override
            public void checkExec(String cmd) {
                logger.fine( "checkExec(cmd)");
            }

            @Override
            public void checkExit(int status) {
                logger.fine( "checkExit(status)");
            }

            @Override
            public void checkAccess(ThreadGroup g) {
                logger.fine( "checkAccess(g)");
            }

            @Override
            public void checkAccess(Thread t) {
                logger.fine( "checkAccess(t)");
            }

            @Override
            public void checkCreateClassLoader() {
                logger.fine( "checkCreateClassLoader()");
            }

            @Override
            public void checkPermission(Permission perm, Object context) {
                logger.log(Level.FINE, "checkPermission( {0}, {1})", new Object[]{perm, context}); //super.checkPermission(perm); 
            }

            @Override
            public void checkPermission(Permission perm) {
                if ( perm instanceof AWTPermission ) {
                    logger.log(Level.FINER, "checkPermission( {0} )", new Object[]{perm});
                } else {
                    logger.log(Level.FINE, "checkPermission( {0} )", new Object[]{perm}); 
                }
            }

            @Override
            public Object getSecurityContext() {
                return super.getSecurityContext(); 
            }

            @Override
            protected Class[] getClassContext() {
                return super.getClassContext(); 
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
