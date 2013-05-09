/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test.endtoend;

import java.io.IOException;
import java.util.Map;
import org.das2.graph.DasAxis;
import org.das2.graph.DasCanvas;
import org.virbo.autoplot.AutoplotUtil;
import org.virbo.autoplot.AutoplotUtil.AutoRangeDescriptor;
import org.virbo.dataset.DataSetUtil;
import static org.virbo.autoplot.ScriptContext.*;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.datasource.MetadataModel;
import org.virbo.dsops.Ops;
import org.virbo.jythonsupport.Util;
import org.virbo.metatree.MetadataUtil;


/**
 * challenging autorange
 * @author jbf
 */
public class Test016 {


    public static void doTest( int id, String uri, QDataSet ds, Map<String,Object> meta, String dim ) throws Exception {
     
        long t0= System.currentTimeMillis();
        String label= String.format( "test016_%03d", id );
        double t;

        if ( dim!=null ) {
            ds= (QDataSet) ds.property( dim );
            meta= (Map<String, Object>) meta.get(dim);
        }

        t= (System.currentTimeMillis()-t0)/1000.;
        System.err.printf( "Read data in %9.3f seconds (%s): %s\n", t, label, uri );

        AutoRangeDescriptor ard= AutoplotUtil.autoRange( ds, meta );

        final QDataSet autoRange= AutoplotUtil.toDataSet(ard);
        
        t= (System.currentTimeMillis()-t0)/1000.;
        System.err.printf( "Autorange in %9.3f seconds (%s): %s\n", t, label, uri );

        System.err.printf( "autorange= %s \n", DataSetUtil.format(autoRange) );

        MutablePropertyDataSet hist;
        if ( "log".equals( autoRange.property(QDataSet.SCALE_TYPE) ) ) {
            hist= (MutablePropertyDataSet) Ops.histogram(  Ops.log(ds),
                    Math.log10(autoRange.value(0)), Math.log10(autoRange.value(1) ), 
                    Math.log10(autoRange.value(1)/autoRange.value(0))/20 );
            MutablePropertyDataSet dep0= (MutablePropertyDataSet) Ops.exp10( (QDataSet)hist.property(QDataSet.DEPEND_0) );
            dep0.putProperty(QDataSet.SCALE_TYPE,"log");
            hist.putProperty(QDataSet.DEPEND_0,dep0);
            dep0.putProperty(QDataSet.UNITS, autoRange.property(QDataSet.UNITS) );
        } else {
            hist= (MutablePropertyDataSet) Ops.histogram( ds, autoRange.value(0), autoRange.value(1), ( autoRange.value(1)-autoRange.value(0) ) / 20 );
            MutablePropertyDataSet dep0= (MutablePropertyDataSet) (QDataSet)hist.property(QDataSet.DEPEND_0);
            dep0.putProperty(QDataSet.UNITS, autoRange.property(QDataSet.UNITS) );
        }

        plot( hist );
        setCanvasSize( 600, 600 );

        //final DasCanvas cc= getDocumentModel().getCanvases(0).getController().getDasCanvas();
        DasAxis xAxis= getDocumentModel().getPlots(0).getXaxis().getController().getDasAxis();

        xAxis.setLabel(DataSetUtil.format(autoRange));

        //int i= uri.lastIndexOf("/");

        writeToPng( String.format( "test016_%03d.png", id ) );
        
        formatDataSet( autoRange, label+".qds");


    }
    
    public static void main(String[] args)  {
        try {

            getDocumentModel().getOptions().setAutolayout(false);
            getDocumentModel().getCanvases(0).getMarginColumn().setRight("100%-10em");

            QDataSet ds;
            Map<String,Object> meta;
            MetadataModel mm;
            Map<String,Object> metaraw;

            //ds= Util.getDataSet( "vap:http://spacedata.bu.edu/data/polar/cammice/k1/1998/po_k1_cam_19980117_v00.cdf?HCounts" );
            ds= Util.getDataSet( "vap:file:///home/jbf/ct/hudson/data.backup/cdf/po_k1_cam_19980117_v00.cdf?HCounts" );
            mm= MetadataUtil.getMetadataModel( (String) ds.property(QDataSet.METADATA_MODEL));
            metaraw= (Map<String, Object>) ds.property(QDataSet.METADATA);
            meta= mm.properties(metaraw);

            doTest( 3, "po_k1_cam_19980117_v00.cdf?FEDU", ds, meta, null );
            doTest( 4, "po_k1_cam_19980117_v00.cdf?FEDU dep0", ds, meta, QDataSet.DEPEND_0 );
            doTest( 5, "po_k1_cam_19980117_v00.cdf?FEDU dep1", ds, meta, QDataSet.DEPEND_1 );


            ds= Util.getDataSet( "vap:file:///home/jbf/ct/lanl/hudson/LANL_LANL-97A_H3_SOPA_20060505_V01.cdf?FEDU" );
            mm= MetadataUtil.getMetadataModel( (String) ds.property(QDataSet.METADATA_MODEL));
            metaraw= (Map<String, Object>) ds.property(QDataSet.METADATA);
            meta= mm.properties(metaraw);

            doTest( 0, "LANL_LANL-97A_H3_SOPA_20060505_V01.cdf?FEDU", ds, meta, null );
            doTest( 1, "LANL_LANL-97A_H3_SOPA_20060505_V01.cdf?FEDU dep0", ds, meta, QDataSet.DEPEND_0 );
            doTest( 2, "LANL_LANL-97A_H3_SOPA_20060505_V01.cdf?FEDU dep1", ds, meta, QDataSet.DEPEND_1 );


            System.exit(0);  // TODO: something is firing up the event thread
        } catch ( Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
