/*
 * DataSetURLDemo.java
 *
 * Created on December 10, 2007, 6:40 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.virbo.datasource;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import org.das2.components.DasProgressPanel;
import java.util.List;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.datasource.DataSetURI.CompletionResult;

/**
 *
 * @author jbf
 */
public class DataSetURLDemo {

    public static void demoGetCompletions() throws Exception {
        String context;
        int carotPos;

        String spaces = "                                                         ";
        spaces = spaces + spaces + spaces + spaces;

        int test = 4;

        switch (test) {
            case 0:
                context = "file:/media/mini/data.backup/examples/asciitable/asciiTab.dat?skip=1&column=field1&fill=-9999";
                carotPos = 56;
                break;
            case 1:
                context = "file:/net/spot3/home/jbf/ct/lanl/gpsdata/omni/omni2_%Y.dat?time=field0&timerange=1960to2010&column=field1&fixedColumns=0-11,54-60&timeFormat=&fill=999.9";
                carotPos = context.indexOf("timeFormat=") + "timeFormat=".length();
                break;
            case 2:
                context = "file:/net/spot3/home/jbf/ct/lanl/gpsdata/omni/omni2_%Y.dat?time=field0&timerange=1960to2010&&timeFormat=&fill=999.9";
                carotPos = context.indexOf("1960to2010&") + "1960to2010&".length();
                break;
            case 3: // file system completion
                //context= "file:/net/spot3/home/jbf/ct/lanl/gpsdata/";
                context = "vap+bin:file:/net/spot3/home/jbf/ct/lanl/gpsda";
                carotPos = context.indexOf("lanl/gp") + "lanl/gp".length();
                break;
            case 4: // file system completion
                //context= "file:/net/spot3/home/jbf/ct/lanl/gpsdata/";
                context = "vap+bin:file:/n";
                carotPos = context.indexOf(":/n") + ":/n".length();
                break;

            case 5: // bad insertion -- co was interpreted as arg_0...
                context = "vap+dat:file:///media/mini/data.backup/examples/asciitable/2490lintest90005.raw?skip=34&co";
                carotPos = context.indexOf("34&co") + "34&co".length();
                break;
            case 6: // bad insertion -- doesn't work to go back
                context = "vap+dat:file:///media/mini/data.backup/examples/asciitable/2490lintest90005.raw?skip=34&racolumn=field2";
                carotPos = context.indexOf("34&ra") + "34&ra".length();
                break;
            default:
                throw new IllegalArgumentException("bad test number");
        }

        List<DataSetURI.CompletionResult> ccs = DataSetURI.getFactoryCompletions(context, carotPos, new DasProgressPanel("completions"));

        System.err.println(context);
        System.err.println(spaces.substring(0, carotPos) + "L");

        for (DataSetURI.CompletionResult cc : ccs) {
            System.err.println("" + cc.completion);
        }

    }

    private static List<CompletionResult> factoryCompletions(String surl, int carotpos) {
        List<CompletionContext> exts = DataSourceRegistry.getPlugins();

        List<CompletionResult> completions = new ArrayList();

        String prefix = surl.substring(0, carotpos);
        String suffix = "";
        if (surl.startsWith("vap:")) {
            suffix = surl.substring(4);
        }

        for (CompletionContext cc : exts) {
            if (cc.completable.startsWith(prefix)) {
                completions.add(new CompletionResult(cc.completable + suffix, cc.completable, null, cc.completable, false));
            }
        }
        return completions;
    }

    private static List<CompletionResult> typesCompletions(String surl, int carotpos) {
        List<CompletionContext> exts = DataSourceRegistry.getPlugins();

        List<CompletionResult> completions = new ArrayList();

        String prefix = surl.substring(0, carotpos);
        String suffix = "";
        if (surl.startsWith("vap:")) {
            suffix = surl.substring(4);
        }

        for (CompletionContext cc : exts) {
            if (cc.completable.startsWith(prefix)) {
                completions.add(new CompletionResult(cc.completable + suffix, cc.completable, null, cc.completable, false));
            }
        }

       return completions;

    }

    private static List<CompletionResult> hostCompletions(String surl, int carotpos) {
        ProgressMonitor mon = new NullProgressMonitor();

        List<CompletionResult> completions = null;

        URISplit split = URISplit.parse(surl);
        String surlDir = split.path;

        final String labelPrefix = surlDir;

        try {
            completions = DataSetURI.getHostCompletions(surl, carotpos, mon);
        } catch ( IOException ex) {
            System.err.println(ex);
        }

        return completions;

    }

    private static List<CompletionResult> fileSystemCompletions( String surl, int carotpos ) {
        ProgressMonitor mon = new NullProgressMonitor();

        List<CompletionResult> completions = null;

        try {
            completions = DataSetURI.getFileSystemCompletions(surl, carotpos, true, true, null, mon);
        } catch (UnknownHostException ex ) {
            ex.printStackTrace();
        } catch (IOException ex) {
            ex.printStackTrace();
        } catch (URISyntaxException ex) {
            ex.printStackTrace();
        }

        return completions;

    }

    private static void maybeClearVap( URISplit split ) {
        if ( split.vapScheme!=null && split.vapScheme.equals("vap") ) { //bug 3298675 okay
            split.vapScheme=null;
            split.formatCarotPos-=4;
        }
    }

    /**
     * See DataSetSelector.showCompletions(final String surl, final int carotpos)
     * @param surl
     * @param carotpos
     * @return
     */
    private static List<CompletionResult> doit(String surl, int carotpos) {
        
        List<CompletionResult> r;

        URISplit split = URISplit.parse(surl, carotpos, true);
        if (split.resourceUriCarotPos > split.file.length() && DataSourceRegistry.getInstance().hasSourceByExt(DataSetURI.getExt(surl))) {
            r = factoryCompletions(URISplit.format(split), split.formatCarotPos);

        } else if ( carotpos==0 || ( 
                !surl.substring(0,carotpos).contains(":")
                && ( carotpos<4 && surl.substring(0, carotpos).equals( "vap".substring(0,carotpos ) )
                || surl.substring(0, 3).equals( "vap" ) ) ) ) {
            r = typesCompletions(surl, carotpos);

        } else if ( carotpos<6 ) {
            String[] types= new String[] { "ftp://", "http://", "https://", "file:/" };
            List<CompletionResult> result= new ArrayList<CompletionResult>();
            for ( int i=0; i<types.length; i++ ) {
                if ( types[i].length()>= carotpos &&
                        surl.substring(0, carotpos).equals(types[i].substring(0,carotpos) ) ) {
                    result.add( new CompletionResult(types[i],"") );
                }
            }
            r= result;

        } else if ( surl.startsWith("vap") && surl.substring(0,carotpos).split("\\:",-2).length==2 ) {
            String[] types= new String[] { "ftp://", "http://", "https://", "file:/" };
            String[] sp= surl.substring(0,carotpos).split("\\:",-2);
            String test= sp[1];
            int testCarotpos= carotpos - ( sp[0].length() + 1 );
            List<CompletionResult> result= new ArrayList<CompletionResult>();
            for ( int i=0; i<types.length; i++ ) {
                if ( types[i].length()>= testCarotpos &&
                        test.substring(0, testCarotpos).equals(types[i].substring(0,testCarotpos) ) ) {
                    result.add( new CompletionResult(sp[0]+":"+types[i],"") );
                }
            }
            r= result;

        } else {
            if ( split.scheme!=null && split.scheme.equals("file") ) {
                if ( !surl.startsWith("vap") ) maybeClearVap(split);
                r= fileSystemCompletions(URISplit.format(split), split.formatCarotPos);
                return r;
            }
            if (true) {
                split.formatCarotPos = split.formatCarotPos - ( split.vapScheme==null ? 0 : split.vapScheme.length() - 1 );
                split.vapScheme = null;
            }
            int firstSlashAfterHost = split.authority == null ? 0 : split.authority.length();
            if (split.resourceUriCarotPos <= firstSlashAfterHost) {
                if ( !surl.startsWith("vap") ) maybeClearVap(split);
                String doHost= URISplit.format(split);
                r= hostCompletions(doHost, split.formatCarotPos);
                if ( doHost.endsWith(".gov") || doHost.endsWith(".edu")
                        || doHost.endsWith(".com") || doHost.endsWith(".net") ) {
                    r.add( new CompletionResult(doHost+"/", "explore this host"));
                }
            } else {
                if ( !surl.startsWith("vap") ) maybeClearVap(split);
                r= fileSystemCompletions(URISplit.format(split), split.formatCarotPos);
            }

        }
        
        return r;
    }

    public static void testAll() throws Exception {
        DataSourceRegistry registry = DataSourceRegistry.getInstance();
        registry.discoverFactories();
        registry.discoverRegistryEntries();

        String[] contexts= new String[] {
            "ftp://papco@mrfrench.lanl.gov",
            "file:///media/mini/data.backup/examples/multiDay.d2s",
            "file:/c:/Documents and Settings/jbf/2008-lion and tiger summary.xls",
            "vap+d2s:file:///media/mini/data.backup/examples/multiDay.d2s",
        };

        int carotPosOrig= 0;

        for ( int j=0; j<contexts.length; j++ ) {
            String context = contexts[j];
            System.err.println("== "+ context + " ==");
            for (int carotPos = carotPosOrig; carotPos < context.length(); carotPos++) {
                List<DataSetURI.CompletionResult> ccs =
                        doit( context, carotPos );
                if ( ccs.size()==1 ) {
                    System.err.println( carotPos + "\t" + ccs.size() + "\t" + ccs.get(0).completion );
                } else if ( ccs.size()>1 ) {
                    System.err.println( carotPos + "\t" + ccs.size() + "\t" + ccs.get(0).completion );
                } else {
                    System.err.println( carotPos + "\t" + ccs.size() );
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        testAll();
        URI uri = DataSetURI.toUri("file:///home/jbf/foo_%25Y.txt");
        System.err.println(uri.toURL());
        URL url = DataSetURI.toUri("file:///home/jbf/foo_%Y.txt").toURL(); // java doesn't complain about percents like this, but they are not allowed.
        System.err.println(url);
        //System.err.println(url.toURI());
        demoGetCompletions();
    }
}
