/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test.endtoend;

import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.File;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Arrays;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import org.das2.client.DasServer;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.stream.StreamDescriptor;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.DataSetURI;
import org.virbo.dsops.Ops;

import static org.virbo.autoplot.ScriptContext.*;

/**
 * Plasma Wave Group at Iowa, test Das2Server entries with "exampleRange".
 * @author jbf
 */
public class Test501 {

    private static final int testid=501;

    static void flatten( TreeModel tm, String root, Object node, List<String>result ) {
        for ( int i=0; i<tm.getChildCount(node); i++ ) {
            Object child= tm.getChild( node, i );
            if ( tm.isLeaf(child) ) {
                String ss= (String)((DefaultMutableTreeNode)child).getUserObject();
                result.add( root + "/" + ss );
            } else {
                String us=  (String)((DefaultMutableTreeNode)child).getUserObject();
                flatten( tm, root + "/" + us, child, result );
            }
        }
    }

    /**
     *
     * @param uri the URI to load
     * @param iid the index of the test.
     * @param doTest if true, then expect a match, otherwise an ex prefix is used to indicate there should not be a match
     * @return the ID of a product to test against a reference.
     * @throws Exception
     */
    private static String do1( String uri, int iid, boolean doTest ) throws Exception {
        
        long t0= System.currentTimeMillis();
        QDataSet ds= org.virbo.jythonsupport.Util.getDataSet( uri );

        double t= (System.currentTimeMillis()-t0)/1000.;
        MutablePropertyDataSet hist= (MutablePropertyDataSet) Ops.autoHistogram(ds);
        hist.putProperty( QDataSet.TITLE, uri );

        String label= String.format( "test%03d_%03d", testid, iid );
        hist.putProperty( QDataSet.LABEL, label );
        formatDataSet( hist, label+".qds");

        QDataSet dep0= (QDataSet) ds.property( QDataSet.DEPEND_0 );
        if ( dep0!=null ) {
            MutablePropertyDataSet hist2= (MutablePropertyDataSet) Ops.autoHistogram(dep0);
            formatDataSet( hist2, label+".dep0.qds");
        } else {
            PrintWriter pw= new PrintWriter( label+".dep0.qds" );
            pw.println("no dep0");
            pw.close();
        }

        plot( ds );
        setCanvasSize( 750, 300 );
        int i= uri.lastIndexOf("/");

        getApplicationModel().waitUntilIdle(true);

        String fileUri= uri.substring(i+1);

        if ( !getDocumentModel().getPlotElements(0).getComponent().equals("") ) {
            String dsstr= String.valueOf( getDocumentModel().getDataSourceFilters(0).getController().getDataSet() );
            fileUri= fileUri + " " + dsstr +" " + getDocumentModel().getPlotElements(0).getComponent();
        }

        String result= null;

        setTitle(fileUri);
        String name;
        if ( doTest ) {
            String id= String.format( "%016d",Math.abs(uri.hashCode()));
            name= String.format( "test%03d_%s.png", testid, id );
            result= name;
        } else {
            name= String.format( "ex_test%03d_%03d.png", testid, iid );
            result= null;
        }
        writeToPng( name );
        System.err.printf( "wrote to file: "+name );

        System.err.printf( "Read in %9.3f seconds (%s): %s\n", t, label, uri );

        return result;
    }

    public static void main( String[] args ) throws Exception {

        // special for testing
        //String s= "/voyager1/pws/SpecAnalyzer-4s-Efield.dsdf";
        //ids.add(0,s);

        getDocumentModel().getOptions().setAutolayout(false);
        getDocumentModel().getCanvases(0).getMarginColumn().setRight("100%-10em");

        int iid= 0;

        DasServer[] dsss= new DasServer[] { DasServer.plasmaWaveGroup,
            DasServer.create(new URL("http://emfisis.physics.uiowa.edu/das/das2Server")) };
        
        Map<Integer,String> failures= new LinkedHashMap();

        for ( int idsss= 0; idsss<dsss.length; idsss++ ) {

            DasServer dss= dsss[idsss];

            System.err.println("## Testing server: "+dss );
            
            TreeModel tm= dss.getDataSetListWithDiscovery();

            List<String> ids= new ArrayList();

            flatten( tm, "", tm.getRoot(), ids );

            List<Integer> skip;
            if ( dss==DasServer.plasmaWaveGroup ) {
                skip= new ArrayList( Arrays.asList( 3, 4, 5, 6, 7, 18 ) );
            } else {
                skip= new ArrayList(  );
            }

            int count=0;
            for ( String id: ids ) {
                if ( id.contains("/testing/") ) {
                    System.err.println("skipping /testing/: "+id);
                    continue;
                }
                if ( id.contains("/test/") ) { // Dan's server
                    System.err.println("skipping /test/: "+id);
                    continue;
                }
                if ( id.contains("juno/waves") && id.contains("housekeeping.dsdf") && !id.contains("/juno/waves/flight/housekeeping.dsdf") ) skip.add(count);
                count++;
            }

            System.err.println( "Skipping the tests: " + skip );

            int iis= 0; // index of element from this server.
            // iid index within the test

            for ( String id: ids ) {

                System.err.println( String.format( "==== test %03d of %d (%03d) ========================================================", iis, count, iid ) );

                if ( id.contains("/testing/") ) {
                    System.err.println( "ids containing /testing/ are automatically skipped: " + id );
                    iis++;
                    continue;
                }
                if ( id.contains("/test/") ) {
                    System.err.println( "ids containing /test/ are automatically skipped: " + id );
                    iis++;
                    continue;
                }
                
                if ( skip.contains(iis) ) {
                    iis++;
                    System.err.println( "test marked for skipping in Test501.java: " + id );
                    continue;
                }

                String uri= "";
                try {

                    StreamDescriptor dsdf= dss.getStreamDescriptor( dss.getURL(id) );
                    String exampleRange= (String) dsdf.getProperty("exampleRange"); // discovery properties have this, just make sure something comes back.
                    int ic= exampleRange.indexOf("|");
                    if ( ic>-1 ) {
                        exampleRange= exampleRange.substring(0,ic);
                    }

                    DatumRange tr= DatumRangeUtil.parseTimeRangeValid(exampleRange);
                    if ( tr.width().gt( org.das2.datum.Units.days.createDatum(30) ) ) {
                        throw new IllegalArgumentException("exampleRange parameter is too large, limit is 30 days");
                    }

                    uri= "vap+das2server:"+dss.getURL() + "?dataset="+id + "&start_time="+tr.min() + "&end_time=" + tr.max();

                    System.err.println("id: "+id );
                    System.err.println("uri: "+uri);

                    do1( uri, iid, false );

                    String testRange= (String) dsdf.getProperty("testRange"); // this is a more thorough test, and should not change
                    if ( testRange!=null ) {

                        ic= testRange.indexOf("|");
                        if ( ic>-1 ) {
                            testRange= testRange.substring(0,ic);
                        }

                        tr= DatumRangeUtil.parseTimeRangeValid(testRange);
                        uri= "vap+das2server:"+dss.getURL() + "?dataset="+id + "&start_time="+tr.min() + "&end_time=" + tr.max();

                        System.err.println("id: "+id );
                        System.err.println("uri: "+uri);

                        String result= do1( uri, iid, true );

                        // write a file that shows the mapping from hashcode to id.
                        File f= new File( "map_"+result+"__"+iid );

                        FileOutputStream fi= new FileOutputStream(f);
                        fi.write( uri.getBytes() );
                        fi.close();

                    }
                } catch ( Exception ex ) {
                    ex.printStackTrace();
                    failures.put(iid,uri);
                }
                iis++;
                iid++;
            }
        }

        System.err.println("DONE...");

        System.err.println("======= Report Summary =======");
        if ( failures.size()>0 ) {
            System.err.println( String.format( "found %d failures:", failures.size() ) );
            for ( int i: failures.keySet() ) {
                System.err.println( String.format( "%03d: %s", i, failures.get(i) ) );
            }
            System.exit(1);
        } else {
            System.err.println( "no failures, happy day.");
            System.exit(0);
        }
        
    }
}
