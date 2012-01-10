/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test.endtoend;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.das2.datum.DatumRange;
import org.das2.datum.Units;
import org.virbo.autoplot.AutoplotUtil;
import org.virbo.autoplot.RenderType;
import org.virbo.autoplot.ScriptContext;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.JoinDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.SemanticOps;

/**
 * Test for autoranging for various QDataSets
 * @author jbf
 */
public class Test022 {

    private static void setRange( DDataSet range, DatumRange drange, boolean log ) {
        range.putProperty( QDataSet.UNITS, drange.getUnits() );
        range.putValue( 0,drange.min().doubleValue( drange.getUnits() ) );
        range.putValue( 1,drange.max().doubleValue( drange.getUnits() ) );
        if ( log ) range.putProperty( QDataSet.SCALE_TYPE, "log" );
    }

    private static QDataSet bounds( QDataSet fillDs, RenderType spec ) throws Exception {

        DDataSet xrange= DDataSet.createRank1(2);
        DDataSet yrange= DDataSet.createRank1(2);
        DDataSet zrange= DDataSet.createRank1(2);

        JoinDataSet result= new JoinDataSet(2);
        result.join( xrange );
        result.join( yrange );
        result.join( zrange );

        Map props= new HashMap();

        if (spec == RenderType.spectrogram || spec==RenderType.nnSpectrogram ) {

            QDataSet xds = (QDataSet) fillDs.property(QDataSet.DEPEND_0);
            if (xds == null) {
                if ( fillDs.property(QDataSet.JOIN_0)!=null ) {
                    JoinDataSet ds= new JoinDataSet(2);
                    for ( int i=0; i<fillDs.length(); i++ ) {
                        ds.join((QDataSet)fillDs.property(QDataSet.DEPEND_0,i));
                    }
                    xds = ds;
                } else {
                    xds = DataSetUtil.indexGenDataSet(fillDs.length());
                }
            }

            QDataSet yds = (QDataSet) fillDs.property(QDataSet.DEPEND_1);
            Map<String,Object> yprops= (Map) props.get(QDataSet.DEPEND_1);
            if (yds == null) {
                if ( fillDs.property(QDataSet.JOIN_0)!=null ) {
                    JoinDataSet ds= new JoinDataSet(2);
                    for ( int i=0; i<fillDs.length(); i++ ) {
                        QDataSet qds= fillDs.slice(i);
                        String f= new File("foo.qds").getAbsolutePath().toString();
                        ScriptContext.formatDataSet( fillDs, f );
                        ds.join((QDataSet)fillDs.property(QDataSet.DEPEND_1,i));
                    }
                    yds = ds;
                } else if ( fillDs.rank()>1 ) {
                    yds = DataSetUtil.indexGenDataSet(fillDs.length(0)); //TODO: QUBE assumed
                } else {
                    yds = DataSetUtil.indexGenDataSet(10); // later the user will get a message "renderer cannot plot..."
                    yprops= null;
                }
            }

            AutoplotUtil.AutoRangeDescriptor xdesc = AutoplotUtil.autoRange(xds, (Map) props.get(QDataSet.DEPEND_0));

            AutoplotUtil.AutoRangeDescriptor ydesc = AutoplotUtil.autoRange(yds, yprops );

            //QDataSet hist= getDataSourceFilter().controller.getHistogram();
            AutoplotUtil.AutoRangeDescriptor desc;

            desc = AutoplotUtil.autoRange( fillDs, props );


            setRange( zrange, desc.range, desc.log );
            setRange( xrange, xdesc.range, xdesc.log );
            setRange( yrange, ydesc.range, ydesc.log );

        } else {

            QDataSet hist= null; //getDataSourceFilter().controller.getHistogram();
            AutoplotUtil.AutoRangeDescriptor ydesc;

            QDataSet depend0;

            if ( false && hist!=null ) {
                ydesc= AutoplotUtil.autoRange( hist, fillDs, props );
                depend0 = (QDataSet) fillDs.property(QDataSet.DEPEND_0);
            } else {
                if ( SemanticOps.isBundle(fillDs) ) {
                    ydesc= AutoplotUtil.autoRange( DataSetOps.unbundle(fillDs, 1 ), props );
                    depend0= DataSetOps.unbundle(fillDs,0);
                } else {
                    ydesc = AutoplotUtil.autoRange( fillDs, props );
                    depend0 = (QDataSet) fillDs.property(QDataSet.DEPEND_0);
                }
            }


            setRange( yrange, ydesc.range, ydesc.log );

            QDataSet xds= depend0;
            if (xds == null) {
                xds = DataSetUtil.indexGenDataSet(fillDs.length());
            }

            AutoplotUtil.AutoRangeDescriptor xdesc = AutoplotUtil.autoRange(xds, (Map) props.get(QDataSet.DEPEND_0));

            setRange( xrange, xdesc.range, xdesc.log );

            if (spec == RenderType.colorScatter) {
                AutoplotUtil.AutoRangeDescriptor zdesc;
                if ( fillDs.property(QDataSet.BUNDLE_1)!=null ) {
                    zdesc= AutoplotUtil.autoRange((QDataSet) DataSetOps.unbundle( fillDs, 2 ),null);
                } else {
                    QDataSet plane0= (QDataSet) fillDs.property(QDataSet.PLANE_0);
                    zdesc= AutoplotUtil.autoRange(plane0,
                        (Map) props.get(QDataSet.PLANE_0));
                }

                setRange( zrange, zdesc.range, zdesc.log );

            }

        }

        for ( int i=0; i<result.length(); i++  ) {
           Units u= (Units) result.property(QDataSet.UNITS,i);
           if ( u!=null ) {
               DatumRange dr= DatumRange.newDatumRange( result.value(i,0), result.value(i,1), u );
               System.err.println( ""+i+": "+ dr );
           } else {
               System.err.println( ""+i+": "+ result.value(i,0) + "," + result.value(i,1) );
           }
           
        }

        return result;
    }

    private static boolean doTest( QDataSet ds, QDataSet bounds ) throws Exception {
        QDataSet tbounds= bounds(ds,RenderType.spectrogram);
        System.err.println( "tbounds=" + tbounds );
        if ( bounds!=null ) System.err.println( "bounds=" + bounds );
        return true;
    }


    private static void dumpRank3Ds( QDataSet ds ) {
        for ( int i=0; i<ds.length(); i++ ) {
            QDataSet slice= ds.slice(i);
            System.err.println( "--- " + slice + " ---");
            for ( int j=0; j<slice.length(); j++ ) {
                for ( int k=0; k<slice.length(j); k++ ) {
                    System.err.print( " \t" + slice.value(j,k) );
                }
                System.err.println("");
            }
        }
    }

    /**
     * test code for identifying dataset schemes
     */
    private static void testSchemes( ) {
        QDataSet ds;
        System.err.println( "---" );
        ds= TestSupport.sampleDataRank1(99);
        System.err.println( ds );
        System.err.println( "x: "+SemanticOps.xtagsDataSet( ds ) );
        System.err.println( "y: "+SemanticOps.ytagsDataSet( ds ) );

        System.err.println( "---" );
        ds= TestSupport.sampleDataRank2(99,20);
        System.err.println( ds );
        System.err.println( "x: "+SemanticOps.xtagsDataSet( ds ) );
        System.err.println( "y: "+SemanticOps.ytagsDataSet( ds ) );

        System.err.println( "---" );
        ds= TestSupport.sampleQube1( 3.4, 4.5, 22, 32 );
        System.err.println( ds );
        System.err.println( "x: "+SemanticOps.xtagsDataSet( ds ) );
        System.err.println( "y: "+SemanticOps.ytagsDataSet( ds ) );

        System.err.println( "---" );
        ds= TestSupport.sampleRank3Join();
        System.err.println( ds );
        System.err.println( "x: "+SemanticOps.xtagsDataSet( ds ) );
        System.err.println( "y: "+SemanticOps.ytagsDataSet( ds ) );
        
    }

    public static void main(String[] args)  {
        try {
            doTest( TestSupport.sampleDataRank1(100), null );
            //dumpRank3Ds( TestSupport.sampleRank3Join() );
            doTest( TestSupport.sampleRank3Join(), null );
            testSchemes();

        } catch ( Exception ex ) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
