/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package external;

import org.poly2tri.Poly2Tri;
import org.poly2tri.triangulation.delaunay.DelaunayTriangle;
import org.poly2tri.triangulation.sets.PointSet;
import org.poly2tri.triangulation.util.PointGenerator;
import org.virbo.autoplot.ScriptContext;
import org.virbo.dataset.QDataSet;
import org.virbo.dsops.Ops;
import org.virbo.dsutil.BundleBuilder;
import org.virbo.dsutil.DataSetBuilder;

/**
 *
 * @author jbf
 */
public class TestPoly2Tri {
    public static void main(final String[] args) 
        throws Exception
    {
        PointSet ps = new PointSet( PointGenerator.uniformDistribution( 20, 100 ) );
        
        for( int i=0; i<1; i++ ) {
            Poly2Tri.triangulate( ps );
        }

        DataSetBuilder b= new DataSetBuilder(2,100,2);
        for ( DelaunayTriangle t: ps.getTriangles() ) {
            b.nextRecord( t.points[0].getX(), t.points[0].getY() );
            b.nextRecord( t.points[1].getX(), t.points[1].getY() );
            b.nextRecord( t.points[2].getX(), t.points[2].getY() );
            b.nextRecord( t.points[0].getX(), t.points[0].getY() );
        }
        ScriptContext.createGui();
        QDataSet ds= b.getDataSet();
        BundleBuilder bds= new BundleBuilder(2);
        bds.putProperty( QDataSet.NAME, 0, "x" );
        bds.putProperty( QDataSet.NAME, 1, "y" );
        bds.putProperty( QDataSet.DEPENDNAME_0, 1, "x" );
        ds= Ops.putProperty( ds, QDataSet.BUNDLE_1, bds.getDataSet() );
        
        ScriptContext.plot( ds );
        
        Thread.sleep( 10000000 );        
    }
    
    public void startProfiling()
        throws Exception
    {
    }
}
