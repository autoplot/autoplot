
package org.autoplot;

import java.awt.AWTPermission;
import java.io.FileDescriptor;
import java.net.InetAddress;
import java.net.URLPermission;
import java.security.Permission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.PropertyPermission;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.LoggingPermission;
import org.autoplot.datasource.AutoplotSettings;
import org.autoplot.hapi.HapiDataSource;

/**
 * Security Manager which allows Autoplot access to:<ul>
 * <li>read and write files under HOME/autoplot_data
 * <li>read and write files under HOME/.java/.userprefs
 * </ul>
 * TODO: check what happens with /home/jbf/autoplot_data/../
 * 
 * Presently this just logs access.  Level FINER implies that the property 
 * access would be okay, and FINE implied this needs to be studied more.
 * 
 * @author jbf
 */
public final class Sandbox {

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

        String hapiData= HapiDataSource.getHapiCache();
        readWriteList.add( hapiData );
        
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
        
        Set<String> okayProps= new HashSet<>();
        okayProps.add( "sun.awt.disablegrab" );
        okayProps.add( "java.awt.headless" );
        okayProps.add( "useHugeScatter" );
        okayProps.add( "rangeChecking" );
        okayProps.add( "sun.arch.data.model" );
        okayProps.add( "line.separator" );
        okayProps.add( "os.name" );
        okayProps.add( "os.arch" );
        okayProps.add( "user.home" );
        okayProps.add( "java.home" );
        okayProps.add( "proxyHost" );
        okayProps.add( "socksProxyHost" );
        okayProps.add( "https.proxyHost" );
        okayProps.add( "enableReferenceCache" );
        okayProps.add( "sun.awt.noerasebackground" );
        okayProps.add( "HAPI_DATA" );
        okayProps.add( "AUTOPLOT_DATA" );
        Set<String> f_okayProps= Collections.unmodifiableSet(okayProps);
        
        Set<String> okayPermissions= new HashSet<>();
        okayPermissions.add("accessClipboard");
        okayPermissions.add("AWTPermission");
        okayPermissions.add("suppressAccessChecks");
        Set<String> f_okayPermissions= Collections.unmodifiableSet(okayPermissions);
        
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
                logger.log(Level.FINE, "checkPackageDefinition({0})", pkg);
            }


            @Override
            public void checkPrintJobAccess() {
                logger.fine( "checkPrintJobAccess" );
            }

            @Override
            public void checkPropertyAccess(String key) {
                if ( f_okayProps.contains(key) ) {
                    logger.log(Level.FINER, "checkPropertyAccess({0}) OK", key);
                } else {
                    logger.log(Level.FINE, "checkPropertyAccess({0})", key);
                }
            }

            @Override
            public void checkPropertiesAccess() {
                logger.fine( "checkPropertiesAccess()");
            }

            @Override
            public void checkMulticast(InetAddress maddr) {
                logger.log(Level.FINE, "checkMulticast({0})", maddr);
            }

            @Override
            public void checkAccept(String host, int port) {
                logger.log(Level.FINE, "checkAccept({0}, {1})", new Object[]{host, port}); 
            }

            @Override
            public void checkListen(int port) {
                logger.log(Level.FINE, "checkListen({0})", port);
            }

            @Override
            public void checkConnect(String host, int port, Object context) {
                logger.log(Level.FINER, "checkConnect({0}, {1}, {2}) NET", new Object[]{host, port, context});
            }

            @Override
            public void checkConnect(String host, int port) {
                logger.log(Level.FINER, "checkConnect({0}, {1}) NET", new Object[]{host, port});
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
                    logger.log(Level.FINER, "checkDelete({0}) WHITELIST", file );
                } else {
                    logger.log(Level.FINE, "checkDelete({0})", file);
                }

            }

            @Override
            public void checkWrite(String file) {
                if ( whitelistFile(file) ) {
                    logger.log(Level.FINER, "checkWrite({0}) WHITELIST", file);
                } else {
                    logger.log(Level.FINE, "checkWrite({0})", file);
                }
            }

            @Override
            public void checkWrite(FileDescriptor fd) { // stdin, stdout, stderr
                logger.fine( "checkWrite(fd)");
            }

            @Override
            public void checkRead(String file, Object context) {
                if ( readOnlyWhitelistFile(file) ) {
                    logger.log( Level.FINER, "checkRead({0}, {1}) WHITELIST", new Object[]{file, context});
                } else {
                    super.checkRead(file);
                    logger.log(Level.FINE, "checkRead({0}, {1})", new Object[]{file, context});
                }                    
            }

            @Override
            public void checkRead(String file) {
                if ( readOnlyWhitelistFile(file) ) {
                    logger.log(Level.FINER, "checkRead({0}) WHITELIST", file);
                } else {
                    super.checkRead(file);
                    logger.log(Level.FINE, "checkRead({0})", file);
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
                logger.log(Level.FINER, "checkAccess({0}) OK", g);
            }

            @Override
            public void checkAccess(Thread t) {
                logger.log(Level.FINER, "checkAccess({0}) OK", t);
            }

            @Override
            public void checkCreateClassLoader() {
                logger.fine( "checkCreateClassLoader()");
            }

            @Override
            public void checkPermission(Permission perm, Object context) {
                logger.log(Level.FINE, "checkPermission( {0}, {1})", new Object[]{perm.getName(), context}); //super.checkPermission(perm); 
            }

            @Override
            public void checkPermission(Permission perm) {
                if ( perm instanceof PropertyPermission ) {
                    String name= perm.getName();
                    if ( f_okayProps.contains(name) ) {
                        logger.log(Level.FINER, "checkPropertyPermission( {0} ) OK", new Object[]{name});
                    } else {
                        logger.log(Level.FINE, "checkPropertyPermission( {0} )", new Object[]{name}); 
                    }
                } else if ( perm instanceof LoggingPermission ) {
                    logger.log(Level.FINER, "checkLoggingPermission( {0} ) OK", new Object[]{perm});
                } else if ( perm instanceof AWTPermission ) {
                    logger.log(Level.FINER, "checkAWTPermission( {0} ) OK", new Object[]{perm});
                } else if ( perm instanceof URLPermission ) {
                    logger.log(Level.FINER, "checkURLPermission( {0} ) OK", new Object[]{perm});
                } else {
                    String name= perm.getName();
                    if ( f_okayPermissions.contains(name) ) {
                        logger.log(Level.FINER, "checkPermission( {0} ) OK", new Object[]{name});
                    } else {
                        logger.log(Level.FINE, "checkPermission( {0} )", new Object[]{name}); 
                    }
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
