/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test.endtoend;

import org.das2.datum.Datum;
import org.das2.qds.DDataSet;
import org.das2.qds.DataSetUtil;
import org.das2.qds.IndexListDataSetIterator;
import org.das2.qds.QDataSet;
import org.das2.qds.QubeDataSetIterator;
import org.das2.qds.WritableDataSet;
import org.das2.qds.ops.Ops;

/**
 * tests of qdataset operations performance, correctness.
 * @author jbf
 */
public class Test020 {
    
    public static void main( String[] args ) {
        
        for ( int i=0; i<5; i++ ) {
            testValid();
        }
        
        for ( int i=0; i<5; i++ ) {
            testValidSlow();
        }

        for ( int i=0; i<5; i++ ) {
            testClosestIndex();
        }

        testArrayIndexing();

        for ( int i=0; i<10; i++ ) {
            testRank1DDatasetAccess(i>5);
        }
        
        for ( int i=0; i<10; i++ ) {
            testRank2DDatasetAccess(i>5);
        }
        
    }

    private static void testValid() {
        final int SIZE=10000000;
        WritableDataSet ds= Ops.copy( Ops.findgen(SIZE) );
        long t0= System.currentTimeMillis();
        QDataSet wds= Ops.valid(ds);
        System.err.println("  valid returns "+wds.getClass() );
        QDataSet r= Ops.where( wds );
        System.err.println("testValid (millis): "+ ( System.currentTimeMillis()-t0 ) );
    }
    
    private static void testValidSlow() {
        final int SIZE=10000000;
        WritableDataSet ds= Ops.copy( Ops.findgen(SIZE) );
        ds.putProperty( QDataSet.VALID_MIN, 0 );     // these severely affect performance, because we must check for fill.
        ds.putProperty( QDataSet.VALID_MAX, SIZE ); 
        long t0= System.currentTimeMillis();
        QDataSet wds= Ops.valid(ds);
        System.err.println("  valid returns "+wds.getClass() );
        QDataSet r= Ops.where( wds );
        System.err.println("testValidSlow (millis): "+ ( System.currentTimeMillis()-t0 ) );
    }

    private static void testClosestIndex() {
        final int SIZE=10000000;
        WritableDataSet ds= Ops.copy( Ops.findgen(SIZE) );
        ds.putProperty( QDataSet.VALID_MIN, 0 );     // these severely affect performance, because we must check for fill.
        ds.putProperty( QDataSet.VALID_MAX, SIZE ); 
        long t0= System.currentTimeMillis();
        int i0= DataSetUtil.closestIndex( ds, Datum.create(SIZE*2/3) );
        System.err.println("closest index time (millis): "+  ( System.currentTimeMillis()-t0 )  );
    }
    
    private static void testRank1DDatasetAccess(boolean print) {
        final int SIZE=1000000;

        double[] dd= new double[SIZE];

        long t0,t1,t02,t2;
        double tot=0;

        t0= System.currentTimeMillis();

        for ( int i=0; i<SIZE; i++ ) {
            tot+= dd[i];
        }
        t1= System.currentTimeMillis();
        if ( print ) System.err.println("total from array access="+tot);

        DDataSet rank1= DDataSet.wrap(dd);
        tot= 0;

        t02= System.currentTimeMillis();

        for ( int i=0; i<SIZE; i++ ) {
            tot+= rank1.value(i);
        }
        t2= System.currentTimeMillis();
        if ( print ) System.err.println("total from DDataSet access="+tot);

        if ( print ) System.err.println( "array, rank1 DDataSet access (millis): "+( t1-t0 )+", "+(t2-t02) );
    }

    private static void testRank2DDatasetAccess( boolean print ) {
        final int SIZE=1000000;
        final int JSIZE = 10;

        double[][] dd= new double[SIZE][JSIZE];

        long t0,t1,t02,t2;
        double tot=0;

        t0= System.currentTimeMillis();

        for ( int i=0; i<SIZE; i++ ) {
            for ( int j=0; j<10; j++ ) {
                tot+= dd[i][j];
            }
        }
        t1= System.currentTimeMillis();
        if ( print ) System.err.println("total from array access="+tot);

        double [] dd1= new double[SIZE*JSIZE];
        DDataSet rank2= DDataSet.wrap(dd1,SIZE,JSIZE);
        tot= 0;

        t02= System.currentTimeMillis();

        for ( int i=0; i<SIZE; i++ ) {
            for ( int j=0; j<10; j++ ) {
                tot+= rank2.value(i,j);
            }
        }
        t2= System.currentTimeMillis();
        if ( print ) System.err.println("total from DDataSet access="+tot);

        if ( print ) System.err.println( "array, rank2 DDataSet access (millis): " + (t1-t0) + ", " + ( t2-t02 ) );
    }

    private static void testArrayIndexing( ) {
        QDataSet rank3= Ops.findgen( 400,50,60 );
        QDataSet rank1= Ops.shuffle( Ops.findgen( 63 ) ); // danger--contents of findgen ignored.
        
        long t0, t1, t2;
        QubeDataSetIterator it;
        double tot;

        t2= System.currentTimeMillis();

        t0= System.currentTimeMillis();
        tot= 0;
        it= new QubeDataSetIterator(rank3);
        //System.err.println( it.createEmptyDs() );
        while ( it.hasNext() ) {
            it.next();
            tot+= it.getValue(rank3);
        }
        t1= System.currentTimeMillis();
        System.err.printf( "total[:,:,:]=%.0f   in %d millis\n",tot, (t1-t0) );


        t0= System.currentTimeMillis();
        tot= 0;
        it= new QubeDataSetIterator(rank3);
        it.setIndexIteratorFactory( 0, new QubeDataSetIterator.SingletonIteratorFactory(30) );
        //System.err.println( it.createEmptyDs() );
        while ( it.hasNext() ) {
            it.next();
            tot+= it.getValue(rank3);
        }
        t1= System.currentTimeMillis();
        System.err.printf( "total[30,:,:]=%.0f   in %d millis\n",tot, (t1-t0) );


        t0= System.currentTimeMillis();
        tot= 0;
        it= new QubeDataSetIterator(rank3);
        it.setIndexIteratorFactory( 2, new QubeDataSetIterator.SingletonIteratorFactory(30) );
        //System.err.println( it.createEmptyDs() );
        while ( it.hasNext() ) {
            it.next();
            tot+= it.getValue(rank3);
        }
        t1= System.currentTimeMillis();
        System.err.printf( "total[:,:,30]=%.0f   in %d millis\n",tot, (t1-t0) );


        t0= System.currentTimeMillis();
        tot= 0;
        it= new QubeDataSetIterator(rank3);
        it.setIndexIteratorFactory( 0, new QubeDataSetIterator.StartStopStepIteratorFactory( 100, 300, 1 ) );
        //System.err.println( it.createEmptyDs() );
        while ( it.hasNext() ) {
            it.next();
            tot+= it.getValue(rank3);
        }
        t1= System.currentTimeMillis();
        System.err.printf( "total[100:300,:,:]=%.0f   in %d millis\n",tot, (t1-t0) );

        t0= System.currentTimeMillis();
        tot= 0;
        it= new QubeDataSetIterator(rank3);
        QDataSet list3000=  Ops.add( Ops.replicate( 5, 3000 ), Ops.mod( Ops.findgen(3000), Ops.replicate( 30, 3000 ) ) );
        it.setIndexIteratorFactory( 0, new QubeDataSetIterator.IndexListIteratorFactory(list3000) );
        it.setIndexIteratorFactory( 1, new QubeDataSetIterator.IndexListIteratorFactory(list3000) );
        it.setIndexIteratorFactory( 2, new QubeDataSetIterator.IndexListIteratorFactory(list3000) );
        //System.err.println( it.createEmptyDs() );
        while ( it.hasNext() ) {
            it.next();
            tot+= it.getValue(rank3);
        }
        t1= System.currentTimeMillis();
        System.err.printf( "total[list3000,list3000,list3000]=%.0f   in %d millis\n",tot, (t1-t0) );


        t0= System.currentTimeMillis();
        tot= 0;
        it= new QubeDataSetIterator(rank3);
        QDataSet list30=  Ops.linspace(5,34,30);
        QDataSet list20=  Ops.linspace(5,24,20);
        it.setIndexIteratorFactory( 0, new QubeDataSetIterator.SingletonIteratorFactory(30 ) );
        it.setIndexIteratorFactory( 1, new QubeDataSetIterator.IndexListIteratorFactory(list30) );
        it.setIndexIteratorFactory( 2, new QubeDataSetIterator.IndexListIteratorFactory(list20) );
        //System.err.println( it.createEmptyDs() );
        while ( it.hasNext() ) {
            it.next();
            tot+= it.getValue(rank3);
        }
        t1= System.currentTimeMillis();
        System.err.printf( "total[30,list30,list20]=%.0f   in %d millis\n",tot, (t1-t0) );


        t0= System.currentTimeMillis();
        tot= 0;

        QDataSet s= Ops.sort(rank1);

        IndexListDataSetIterator it2 = new IndexListDataSetIterator(s);

        //System.err.println( it2.createEmptyDs() );
        while ( it2.hasNext() ) {
            it2.next();
            tot+= it2.getValue(rank1);
        }
        t1= System.currentTimeMillis();
        System.err.printf( "total[rank1[s]]=%.0f   in %d millis\n",tot, (t1-t0) );


        System.err.printf( "total test time %d millis\n", (t1-t2) );
    }
}
