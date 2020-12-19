
package org.autoplot;

import java.awt.AWTPermission;
import java.io.FilePermission;
import java.net.InetAddress;
import java.net.NetPermission;
import java.net.URLPermission;
import java.nio.file.LinkPermission;
import java.security.AllPermission;
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
import javax.sound.sampled.AudioPermission;
import org.autoplot.datasource.AutoplotSettings;
import org.autoplot.hapi.HapiDataSource;

/**
 * Security Manager which allows Autoplot access to:<ul>
 * <li>read and write files under HOME/autoplot_data
 * <li>read and write files under HOME/.java/.userprefs
 * </ul>
 * 
 * Imagined attacks which are still possible:<ul>
 * <li> this does nothing to prevent a non-blacklisted file 
 * (for example /home/jbf/.profile) and then a post to send the data to
 * a remote site.
 * </ul>
 * 
 * Presently this just logs access.  Level FINER implies that the property 
 * access would be okay, and FINE implied this needs to be studied more.
 * 
 * @author jbf
 */
public final class Sandbox {

    private static final Logger logger = org.das2.util.LoggerManager.getLogger("autoplot.security");        
    
    /**
     * return a security manager which allows:<ul>
     * <li>read from anywhere besides a blacklist
     * <li>write to anywhere in whitelist
     * <li>any network activity
     * <li>any property read
     * </ul>
     * This is likely to change from the implementation as things develop, so
     * please review this code if you must know precisely, or perform 
     * experiments until you are satisfied with its operation.
     * @return 
     */
    public static SecurityManager getSandboxManager( ) {
        
        boolean linux= System.getProperty("os.name").equals("Linux");
        boolean notWindows= !System.getProperty("os.name").equals("Windows");
        
        List<String> readWriteList= new ArrayList<>();
        readWriteList.add( AutoplotSettings.settings().resolveProperty( AutoplotSettings.PROP_AUTOPLOTDATA ) );
        if ( linux ) {
            readWriteList.add( System.getProperty("user.home")+"/.java/.userPrefs" );
        }
        
        if ( notWindows ) {
            readWriteList.add( "/tmp" );
        }

        String hapiData= HapiDataSource.getHapiCache();
        readWriteList.add( hapiData );
        
        ArrayList<String> readOnlyList= new ArrayList<>(readWriteList);
        String path= System.getProperty("java.class.path");
        
        if ( linux ) {
            readOnlyList.addAll(Arrays.asList(path.split(":")));
        }
        
        readOnlyList.add( System.getProperty("java.home") );
        if ( linux ) {
            readOnlyList.add( "/usr/share/fonts/" );
            readOnlyList.add( System.getProperty("user.home")+"/.fonts/" );
        }
        
        readOnlyList.add( "__classpath__"); // files on Jython classpath show this.
        readOnlyList.add( System.getProperty("user.home")+"/.das2rc" );
                
        final List<String> f_rwOkayHome= Collections.unmodifiableList(readWriteList);
        final List<String> f_roOkayHome= Collections.unmodifiableList(readOnlyList);
        
        Set<String> roBlacklist= new HashSet<>();
        if ( notWindows ) {
            roBlacklist.add( "/etc" );
            roBlacklist.add( "/sys" );
            roBlacklist.add( "/boot" );
            roBlacklist.add( "/proc" );
            roBlacklist.add( "/dev" );
        }
        Set<String> f_roBlacklist= Collections.unmodifiableSet(roBlacklist);
        
        Set<String> okayProps= new HashSet<>();
        okayProps.add( "sun.awt.disablegrab" );
        okayProps.add( "java.awt.headless" );
        okayProps.add( "useHugeScatter" );
        okayProps.add( "rangeChecking" );
        okayProps.add( "sun.arch.data.model" );
        okayProps.add( "line.separator" );
        okayProps.add( "os.name" );
        okayProps.add( "os.arch" );
        okayProps.add( "user.dir" );
        okayProps.add( "user.home" );
        okayProps.add( "java.home" );
        okayProps.add( "java.version" );
        okayProps.add( "proxyHost" );
        okayProps.add( "socksProxyHost" );
        okayProps.add( "https.proxyHost" );
        okayProps.add( "http.agent" );
        okayProps.add( "enableReferenceCache" );
        okayProps.add( "sun.awt.noerasebackground" );
        okayProps.add( "HAPI_DATA" );
        okayProps.add( "AUTOPLOT_DATA" );
        okayProps.add( "cdawebHttps" );        
        okayProps.add( "python.cachedir" ); // this one is odd, because Autoplot writes to it, but we probably don't want others to write to it.
        Set<String> f_okayProps= Collections.unmodifiableSet(okayProps);
        
        Set<String> okayPermissions= new HashSet<>();
        okayPermissions.add("accessClipboard");
        okayPermissions.add("AWTPermission");
        okayPermissions.add("suppressAccessChecks");
        okayPermissions.add("accessDeclaredMembers");
        Set<String> f_okayPermissions= Collections.unmodifiableSet(okayPermissions);
        
        Set<String> okaySecurityAccessTargets= new HashSet<>();
        okaySecurityAccessTargets.add("putProviderProperty.SunRsaSign");
        okaySecurityAccessTargets.add("putProviderProperty.SUN");
        okaySecurityAccessTargets.add("putProviderProperty.Sun");
        okaySecurityAccessTargets.add("putProviderProperty.XMLD");
        Set<String> f_okaySecurityAccessTargets= Collections.unmodifiableSet(okaySecurityAccessTargets);
        
        SecurityManager limitedSecurityManager = new SecurityManager() {
            
            @Override
            public ThreadGroup getThreadGroup() {
                return super.getThreadGroup(); 
            }

            @Override
            public void checkSecurityAccess(String target) {
                for ( String s : f_okaySecurityAccessTargets ) {
                    if ( target.startsWith(s) ) {
                        logger.log(Level.FINER, "checkSecurityAccess({0})", s);
                        return;
                    }
                };
                logger.log(Level.FINE, "checkSecurityAccess({0})", target);
            }

            @Override
            public void checkPackageDefinition(String pkg) {
                logger.log(Level.FINE, "checkPackageDefinition({0})", pkg);
            }

            @Override
            public void checkPropertyAccess(String key) {
                if ( f_okayProps.contains(key) ) {
                    logger.log(Level.FINER, "checkPropertyAccess({0}) OK", key);
                } else {
                    logger.log(Level.FINER, "checkPropertyAccess({0}) (All properties are okay)", key); 
                }
            }

            @Override
            public void checkMulticast(InetAddress maddr) {
                logger.log(Level.FINE, "checkMulticast({0})", maddr);
            }

            @Override
            public void checkAccept(String host, int port) {
                logger.log(Level.FINER, "checkAccept({0}, {1})", new Object[]{host, port}); 
            }

            @Override
            public void checkListen(int port) {
                logger.log(Level.FINER, "checkListen({0})", port);
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
             * return true if the file is within a sandboxed filesystem where
             * Autoplot can write files as well as read them.
             * 
             * @param file
             * @return 
             */
            private boolean whitelistFile( String file ) {
                if ( file.contains("..") ) {
                    return false;
                }
                return f_rwOkayHome.stream().anyMatch((s) -> ( file.startsWith(s) ));
            }
            
            /**
             * return true if the file is within a sandboxed filesystem where
             * writing is allowed.
             * @param file
             * @return 
             */
            private boolean readOnlyWhitelistFile( String file ) {
                if ( file.contains("..") ) {
                    return false;
                }
                return f_roOkayHome.stream().anyMatch((s) -> ( file.startsWith(s) ));
            }
            
            /**
             * return true if the file has been blacklisted for reading, for example
             * /etc, /boot
             * @param file
             * @return 
             */
            private boolean readOnlyBlacklistFile( String file ) {
                if ( file.contains("..") ) {
                    return false;
                }
                return f_roBlacklist.stream().anyMatch((s) -> ( file.startsWith(s) ));
            }
                        
            @Override
            public void checkDelete(String file) {
                if ( whitelistFile(file) ) {
                    logger.log(Level.FINER, "checkDelete({0}) WHITELIST", file );
                } else {
                    throw new SecurityException("sandbox disallows delete of "+file);
                }

            }

            @Override
            public void checkWrite(String file) {
                if ( whitelistFile(file) ) {
                    logger.log(Level.FINER, "checkWrite({0}) WHITELIST", file);
                } else {
                    throw new SecurityException("sandbox disallows write of "+file);
                }
            }

            @Override
            public void checkRead(String file, Object context) {
                if ( readOnlyWhitelistFile(file) ) {
                    logger.log( Level.FINER, "checkRead({0}, {1}) WHITELIST", new Object[]{file, context});
                } else {
                    if ( readOnlyBlacklistFile(file) ) {
                        throw new SecurityException( String.format( "sandbox disallows read from "+ file) );
                    } else {
                        logger.log(Level.FINER, "checkRead({0}, {1})", new Object[]{file, context});
                    }
                    logger.log(Level.FINE, "checkRead({0}, {1})", new Object[]{file, context});
                }                    
            }

            @Override
            public void checkRead(String file) {
                if ( readOnlyWhitelistFile(file) ) {
                    logger.log(Level.FINER, "checkRead({0}) WHITELIST", file);
                } else {
                    if ( readOnlyBlacklistFile(file) ) {
                        throw new SecurityException( String.format( "checkRead( (%s) BLACKLIST", file) );
                    } else {
                        logger.log(Level.FINER, "checkRead({0})", file);
                    }
                }
            }

            @Override
            public void checkPermission(Permission perm, Object context) {
                logger.log(Level.FINE, "checkPermission( {0}, {1})", new Object[]{perm.getName(), context}); //super.checkPermission(perm); 
                checkPermission( perm );
            }

            @Override
            public void checkPermission(Permission perm) {
                if ( perm instanceof PropertyPermission ) {
                    String name= perm.getName();
                    String actions= perm.getActions();
                    if ( f_okayProps.contains(name) ) {
                        logger.log(Level.FINER, "checkPermission( PropertyPermission {0} ) OK", new Object[]{name});
                    } else {
                        if ( "*".equals(name) && "read,write".equals(actions) ) {
                            logger.log(Level.FINER, "checkPermission( PropertyPermission * read,write ) okay for Beans", new Object[]{name}); 
                        } else if ( "read".equals(actions) ) {
                            logger.log(Level.FINER, "checkPermission( PropertyPermission read ) OK" ); 
                        } else {
                            logger.log(Level.FINE, "checkPermission( PropertyPermission {0} )", new Object[]{name}); 
                        }
                    }
                } else if ( perm instanceof LoggingPermission ) {
                    logger.log(Level.FINER, "checkPermission( LoggingPermission {0} ) OK", new Object[]{perm});
                } else if ( perm instanceof AWTPermission ) {
                    logger.log(Level.FINER, "checkPermission( AWTPermission {0} ) OK", new Object[]{perm});
                } else if ( perm instanceof URLPermission ) {
                    logger.log(Level.FINER, "checkPermission( URLPermission {0} ) OK", new Object[]{perm});
                } else if ( perm instanceof FilePermission ) {
                    String name= perm.getName();
                    String action= perm.getActions();
                    if ( "read".equals(action) ) {
                        String file= name;
                        if ( readOnlyWhitelistFile(file) ) {
                            logger.log(Level.FINER, "checkPermission( FilePermission({0}) WHITELIST", file);
                        } else {
                            if ( readOnlyBlacklistFile(file) ) {
                                throw new SecurityException( String.format( "checkPermission( FilePermission(%s) BLACKLIST", file) );
                            } else {
                                logger.log(Level.FINER, "checkPermission( FilePermission({0})", file);
                            }
                        }
                    } else {
                        super.checkPermission(perm);
                    }
                } else if ( perm instanceof LinkPermission ) {
                    throw new SecurityException( String.format( "sandbox disallows making filesystem links." ) );
                } else if ( perm instanceof RuntimePermission ) {
                    String name= perm.getName();
                    logger.log(Level.FINER, "checkPermission( RuntimePermission {0} ) OK", new Object[]{name});
                } else if ( perm instanceof AllPermission ) {
                    // there's code in the FileSystem codes that detects applet using this.
                    logger.log(Level.FINER, "checkPermission( AllPermission ) FileSystem calls so OK" );
                } else if ( perm instanceof NetPermission ) {
                    logger.log(Level.FINER, "checkPermission( NetPermission ) OK, but this should be studied more." );
                } else if ( perm instanceof AudioPermission ) {
                    throw new SecurityException( String.format( "checkPermission( AudioPermission(%s) )", perm.getName() ) );
                } else {
                    String name= perm.getName();
                    if ( f_okayPermissions.contains(name) ) {
                        logger.log(Level.FINER, "checkPermission( {0} ) OK", new Object[]{name});
                    } else {
                        throw new SecurityException( String.format( "unrecognized permission: {0}", name ) ); 
                    }
                }
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
