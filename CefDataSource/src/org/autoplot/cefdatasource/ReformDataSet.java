/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.cefdatasource;

import java.util.Arrays;
import java.util.Map;
import org.das2.qds.AbstractDataSet;
import org.das2.qds.DataSetUtil;
import org.das2.qds.RankNDataSet;
import org.das2.qds.QDataSet;
import org.das2.qds.DataSetOps;

/**
 *
 * @author jbf
 */
public class ReformDataSet extends AbstractDataSet implements RankNDataSet {

    int dsLen1;
    int n0, n1, n2, n3; // copy for performance sake
    int[] sizes;
    int offset;  // offset into source dataset.
    final int rank;
    final QDataSet ds;

    /**
     * change the dimensionality of the QUBE dataset, backed by the source dataset.
     * @param ds QUBE dataset rank 2 where the product of the sizes is Q.
     * @param sizes dimensions for the new rank M QUBE dataset, where the product of the sizes is Q.
     * @return QUBE dataset with the same number of elements, in M different dimensions.
     */
    public ReformDataSet(final QDataSet ds, final int[] sizes) {
        this(ds, 0, sizes);
    }

    public ReformDataSet(final QDataSet ds, int offset, final int[] sizes) {

        this.ds = ds;
        dsLen1 = ds.length(0);
        
        this.offset= offset;

        if (ds.rank() != 2) {
            throw new IllegalArgumentException("input rank must==2");
        }

        if ( ! Boolean.TRUE.equals( ds.property(QDataSet.QUBE) ) ) {
            throw new IllegalArgumentException("dataset must be marked as QUBE");
        }

        rank = sizes.length;
        this.sizes = Arrays.copyOf( sizes, sizes.length );

        if (rank > 4) {
            StringBuilder ssizes= new StringBuilder( 10 ).append(sizes[0]);
            for ( int i=1; i<rank; i++ ) ssizes.append(",").append(sizes[i]);
            throw new IllegalArgumentException( String.format( "sizes=[%s] imply dataset with rank %d, which is not currently supported.", ssizes.toString(), rank ) );
        }

        n0 = sizes[0];
        if (rank > 1) {
            n1 = sizes[1];
        }
        if (rank > 2) {
            n2 = sizes[2];
        }
        if (rank > 3) {
            n3 = sizes[3];
        }

        // if the first dimension isn't changed, then keep DEPEND_0.
        QDataSet dep0 = (QDataSet) ds.property(QDataSet.DEPEND_0);
        if (dep0 != null) {
            if (sizes[0] == ds.length()) {
                this.putProperty(QDataSet.DEPEND_0, dep0);
            }
        }

        DataSetUtil.putProperties( DataSetUtil.getProperties(ds), this );
        putProperty( QDataSet.DEPEND_1, null );
    }

    public int rank() {
        return rank;
    }

    @Override
    public double value(int i) {
        return ds.value(i % dsLen1, i / dsLen1);
    }

    @Override
    public double value(int i0, int i1) {
        int i = offset + i0 * n1 + i1;
        return ds.value(i / dsLen1, i % dsLen1);
    }

    @Override
    public double value(int i0, int i1, int i2) {
        int i = offset + i0 * n1 * n2 + i1 * n2 + i2;
        return ds.value(i / dsLen1, i % dsLen1);
    }

    @Override
    public double value(int i0, int i1, int i2, int i3) {
        int i = offset + i0 * n1 * n2 * n3 + i1 * n2 * n3 + i2 * n3 + i3;
        return ds.value(i / dsLen1, i % dsLen1);
    }

    @Override
    public Object property(String name) {
        if ( properties.containsKey(name) ) {
            return properties.get(name);
        } else {
            return ds.property(name);
        }
    }

    @Override
    public int length() {
        return n0;
    }

    @Override
    public int length(int i) {
        return n1;
    }

    @Override
    public int length(int i, int j) {
        return n2;
    }

    @Override
    public int length(int i, int j, int k) {
        return n3;
    }

    @Override
    public QDataSet slice(int dim) {
        int[] newSizes = new int[rank - 1];
        offset = dim;
        for (int i = 0; i < rank - 1; i++) {
            newSizes[i] = this.sizes[i + 1];
            offset *= this.sizes[i + 1];
        }

        ReformDataSet result= new ReformDataSet(ds, offset, newSizes);
        Map<String,Object> props= DataSetOps.sliceProperties0(dim,DataSetUtil.getProperties(this));
        props= DataSetUtil.sliceProperties( this, dim, props );
        DataSetUtil.putProperties( props, result );
        return result;
    }
    
}
