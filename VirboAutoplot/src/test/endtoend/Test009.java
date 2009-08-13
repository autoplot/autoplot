/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test.endtoend;

import java.io.IOException;
import org.das2.dataset.VectorDataSet;
import org.das2.dataset.VectorUtil;
import org.das2.datum.DatumRange;
import org.das2.datum.DatumRangeUtil;
import org.das2.datum.Units;
import org.virbo.autoplot.ScriptContext;
import org.virbo.autoplot.dom.Application;
import org.virbo.dataset.DataSetAdapter;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.VectorDataSetAdapter;
import org.virbo.dsops.Ops;

/**
 * das2 end-to-end tests of renderers and tick labelling.  Introduced as part
 * of migration to DomainDivider for tick labelling.
 * @author jbf
 */
public class Test009 {

    static long t0= System.currentTimeMillis();

    public static void writePng( String name ) throws InterruptedException, IOException {
        ScriptContext.writeToPng( name );
        System.out.println("wrote "+name+"  timer: "+(System.currentTimeMillis()-t0));
        resetTimer();
    }

    static void resetTimer() {
        t0= System.currentTimeMillis();
    }

    public static void main(String[] args) throws InterruptedException, IOException, Exception {
        try {
            Application dom= ScriptContext.getDocumentModel();
            ScriptContext.setCanvasSize( 800, 600 );
            dom.getOptions().setAutolayout(false);
            dom.getCanvases(0).getMarginColumn().setRight("100%-10em");

            ScriptContext.setCanvasSize(800,600);
            dom.getCanvases(0).setFont("sans-14");

            resetTimer();
            writePng( "test009_001.png" );
            dom.getPlots(0).getXaxis().setRange( DatumRangeUtil.parseTimeRangeValid("2009-08-10") );
            writePng( "test009_002.png" );
            dom.getPlots(0).getXaxis().setRange( DatumRangeUtil.parseTimeRangeValid("1990-01-01 03:15:01 to 03:15:02") );
            writePng( "test009_003.png" );
            dom.getPlots(0).getXaxis().setRange( DatumRangeUtil.parseTimeRangeValid("1990-01-01 03:15:01.100 to 03:15:01.110") );
            writePng( "test009_004.png" );
            dom.getPlots(0).getYaxis().setLog(true);
            writePng( "test009_005.png" );
            dom.getPlots(0).getYaxis().setRange( DatumRangeUtil.newDimensionless(1,100) );
            writePng( "test009_006.png" );
            dom.getPlots(0).getYaxis().setRange( DatumRangeUtil.newDimensionless(1,1.1) );
            writePng( "test009_007.png" );
            dom.getPlots(0).getYaxis().setLabel( "Y-Axis Label" );

            QDataSet rank1Rand= Ops.accum( Ops.randomn(-12345,10000000) );
            resetTimer();

            writePng( "test009_008.png" );
            ScriptContext.plot( DataSetOps.trim( rank1Rand, 0, 1000 ) );
            writePng( "test009_009.png" );
            ScriptContext.plot( DataSetOps.trim( rank1Rand, 0, 100000) );
            writePng( "test009_010.png" );
            ScriptContext.plot( DataSetOps.trim( rank1Rand, 0, 10000000) );
            writePng( "test009_011.png" );

            VectorDataSet vds= VectorDataSetAdapter.create(rank1Rand);
            vds= VectorUtil.reduce2D( vds, 0, vds.getXLength(), Units.dimensionless.createDatum(1e5), Units.dimensionless.createDatum(100) );
            ScriptContext.plot( DataSetAdapter.create(vds) );
            writePng( "test009_011a.png" );

            QDataSet rank2Rand= Ops.add( Ops.randomn(-12345,100000,100),
                    Ops.sin( Ops.add( Ops.outerProduct( Ops.linspace( 0, 1000., 100000 ), Ops.replicate(1,100)),
                                        Ops.outerProduct( Ops.replicate(1,100000), Ops.linspace(0,10,100) ) ) ) );


            resetTimer();

            ScriptContext.plot( DataSetOps.trim( rank2Rand, 0, 100 ) );
            writePng( "test009_012.png" );
            ScriptContext.plot( DataSetOps.trim( rank2Rand, 0, 10000) );
            writePng( "test009_013.png" );
            ScriptContext.plot( DataSetOps.trim( rank2Rand, 0, 100000) );
            writePng( "test009_014.png" );

            QDataSet x= Ops.randomn(-12345,1000);
            QDataSet y=  Ops.randomn(-12344,1000);
            ScriptContext.plot( x, y, Ops.sqrt( Ops.add( Ops.pow(x,2), Ops.pow(y,2) ) ) );
            ScriptContext.setRenderStyle("colorScatter");
            dom.getPanels(0).getStyle().setSymbolSize(10);

            writePng( "test009_015.png" );
            ScriptContext.writeToPdf("test009_015.pdf");

            ScriptContext.setCanvasSize(200,160);
            dom.getCanvases(0).getMarginColumn().setRight("100%-3em");
            dom.getCanvases(0).setFont("sans-8");
            
            dom.getPlots(0).getXaxis().setRange( DatumRange.newDatumRange( 999, 1021, Units.dimensionless ) );
            writePng( "test009_016.png" );


            //dom.getPlots(0).getXaxis().setLog( true );
            //writePng( "test009_017.png" );
            //dom.getPlots(0).getXaxis().setRange( DatumRangeUtil.parseTimeRangeValid("1990-01-01 03:15:01 to 03:15:02") );
            //writePng( "test009_018.png" );

            System.exit(0);  // TODO: something is firing up the event thread
        } catch ( RuntimeException ex ) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
