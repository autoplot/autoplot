
package org.autoplot;

import java.awt.AWTPermission;
import java.io.FileDescriptor;
import java.net.InetAddress;
import java.security.Permission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.autoplot.datasource.AutoplotSettings;

/**
 * Security Manager which allows Autoplot access to:<ul>
 * <li>read and write files under HOME/autoplot_data
 * <li>read and write files under HOME/.java/.userprefs
 * </ul>
 * TODO: check what happens with /home/jbf/autoplot_data/../
 * @author jbf
 */
public class Sandbox {

    private static final Logger logger = org.das2.util.LoggerManager.getLogger("autoplot.security");        
    
    /**
     * return a security manager with reasonable settings.
     * @return 
     */
    public static SecurityManager getSandboxManager( ) {

        List<String> readWriteList= new ArrayList<>();
        readWriteList.add( AutoplotSettings.settings().resolveProperty( AutoplotSettings.PROP_AUTOPLOTDATA ) );
        readWriteList.add( System.getProperty("user.home")+"/.java/.userPrefs/" );
        readWriteList.add( "/tmp/imageio" );
        
        ArrayList<String> readOnlyList= new ArrayList<>(readWriteList);
        String path= System.getProperty("java.class.path");
        readOnlyList.addAll(Arrays.asList(path.split(":")));
        readOnlyList.add( System.getProperty("java.home") );
        readOnlyList.add( "/usr/share/fonts/" );
        readOnlyList.add( System.getProperty("user.home")+"/.das2rc" );
        
        return getSandboxManager( readWriteList, readOnlyList );
    }
    
    /**
     * return a security manager which allows read and write from a list
     * of areas, and read from another list.
     * @param okayHome
     * @param roOkayHome
     * @return 
     */
    public static SecurityManager getSandboxManager( List<String> okayHome, List<String> roOkayHome ) {
        
        final List<String> lokayHome= Collections.unmodifiableList(okayHome);
        final List<String> lroOkayHome= Collections.unmodifiableList(roOkayHome);
        
        SecurityManager limitedSecurityManager = new SecurityManager() {
            @Override
            public ThreadGroup getThreadGroup() {
                return super.getThreadGroup(); 
            }

            @Override
            public void checkSecurityAccess(String target) {
                if ( target.equals("putProviderProperty.SunRsaSign") ) {
                    logger.fine( "checkSecurityAccess(SynRsaSign)");
                } else if ( target.equals("putProviderProperty.SUN") ) {
                    logger.fine( "checkSecurityAccess(SUN)");
                } else if ( target.startsWith("putProviderProperty.Sun") ) {
                    logger.fine( "checkSecurityAccess(SUN)");
                } else {
                    logger.fine( "checkSecurityAccess(target)");
                }
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

            /**
             * return true if the file is within a sandboxed filesystem.
             * @param file
             * @return 
             */
            private boolean whitelistFile( String file ) {
                return lokayHome.stream().anyMatch((s) -> ( file.startsWith(s) ));
            }
            
            /**
             * return true if the file is within a sandboxed filesystem.
             * @param file
             * @return 
             */
            private boolean readOnlyWhitelistFile( String file ) {
                return lroOkayHome.stream().anyMatch((s) -> ( file.startsWith(s) ));
            }
            
            @Override
            public void checkDelete(String file) {
                if ( whitelistFile(file) ) {
                    logger.fine( "checkDelete(AP)");
                } else {
                    logger.fine( "checkDelete(file)");
                }

            }

            @Override
            public void checkWrite(String file) {
                if ( whitelistFile(file) ) {
                    logger.fine( "checkWrite(AP)");
                } else {
                    logger.fine( "checkWrite(file)");
                }
            }

            @Override
            public void checkWrite(FileDescriptor fd) { // stdin, stdout, stderr
                logger.fine( "checkWrite(fd)");
            }

            @Override
            public void checkRead(String file, Object context) {
                if ( readOnlyWhitelistFile(file) ) {
                    logger.fine( "checkRead(file, context)");
                } else {
                    super.checkRead(file);
                    logger.fine( "checkRead(file)");
                }                    
            }

            @Override
            public void checkRead(String file) {
                if ( readOnlyWhitelistFile(file) ) {
                    logger.fine( "checkRead(AP)");
                } else {
                    super.checkRead(file);
                    logger.fine( "checkRead(file)");
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
                throw new SecurityException("checkExec(cmd)");
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
