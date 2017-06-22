/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.cefdatasource;

import org.das2.datum.Units;
import org.das2.qds.DDataSet;
import org.das2.qds.DataSetUtil;
import org.das2.qds.RankNDataSet;
import org.das2.qds.QDataSet;

/**
 *
 * @author jbf
 */
public class DataSetOps {

    /**
     * averages all the leaf-node data together to reduce rank by one.
     * @param ds rank 4 qube dataset.  (Future implementation will support lower ranks and non-qube datasets.)
     * @param total return the total, rather than the average data.
     * @return DDataSet of rank 4-1.
     */
    public static DDataSet collapse(QDataSet ds) {
        DDataSet result;
        int[] qube = org.das2.qds.DataSetUtil.qubeDims( ds );
        Units u = (Units) ds.property(QDataSet.UNITS);
        double fill = u == null ? Double.NaN : u.getFillDouble();

        if (ds.rank() == 4) {
            result = DDataSet.createRank3(qube[0], qube[1], qube[2]);
            for (int i = 0; i < ds.length(); i++) {
                QDataSet slice = ((RankNDataSet) ds).slice(i);
                for (int j = 0; j < slice.length(); j++) {
                    for (int k = 0; k < slice.length(i); k++) {
                        double ssum = 0;
                        double wsum = 0;
                        for (int l = 0; l < ds.length(i, j); l++) {
                            double d = ds.value(j, k, l);
                            double w = (u == null || u.isValid(d)) ? 1. : 0.;
                            ssum += d * w;
                            wsum += w;
                        }
                        ssum = wsum == 0 ? fill : ssum / wsum;
                        result.putValue(i, j, k, ssum);
                    }
                }
            }
        } else {
            throw new IllegalArgumentException("only rank 4 supported");
        }
        
        DataSetUtil.putProperties( DataSetUtil.getProperties(ds), result );
        
        return result;
    }

    /**
     * averages all the data from the third dimension together to reduce rank by one.
     * @param ds rank 3 dataset
     * @return DDataSet of rank 3-1.
     */
    public static DDataSet collapse3(QDataSet ds) {
        DDataSet result;
        int[] qube = DataSetUtil.qubeDims( ds );
        Units u = (Units) ds.property(QDataSet.UNITS);
        double fill = u == null ? Double.NaN : u.getFillDouble();

        if (ds.rank() == 3) {
            result = DDataSet.createRank2( qube[0], qube[2] );
            for (int i = 0; i < ds.length(); i++) {
                for (int k = 0; k < ds.length(i,0); k++) {
                    double ssum = 0;
                    double wsum = 0;
                    for (int j = 0; j < ds.length(0); j++) {
                        double d = ds.value(i, j, k);
                        double w = (u == null || u.isValid(d)) ? 1. : 0.;
                        ssum += d * w;
                        wsum += w;
                    }
                    ssum = wsum == 0 ? fill : ssum / wsum;
                    result.putValue(i, k, ssum);
                }
            }
            org.das2.qds.DataSetUtil.putProperties(org.das2.qds.DataSetUtil.getProperties(ds), result);
            result.putProperty(QDataSet.DEPEND_1, result.property("DEPEND_2"));

        } else {
            throw new IllegalArgumentException("only rank 3 supported");
        }
        return result;
    }

    /**
     * averages all the data from the third dimension together to reduce rank by one.
     * @param ds rank 4 dataset
     * @return DDataSet of rank 4-1.
     */
    public static DDataSet collapse2(QDataSet ds) {
        DDataSet result;
        int[] qube = DataSetUtil.qubeDims(ds);
        Units u = (Units) ds.property(QDataSet.UNITS);
        double fill = u == null ? Double.NaN : u.getFillDouble();

        if (ds.rank() == 4) {
            result = DDataSet.createRank3(qube[0], qube[1], qube[3]);
            for (int i = 0; i < ds.length(); i++) {
                QDataSet slice = ((RankNDataSet) ds).slice(i);
                for (int j = 0; j < slice.length(); j++) {
                    for (int l = 0; l < slice.length(i, j); l++) {
                        double ssum = 0;
                        double wsum = 0;
                        for (int k = 0; k < slice.length(i); k++) {
                            double d = slice.value(j, k, l);
                            double w = (u == null || u.isValid(d)) ? 1. : 0.;
                            ssum += d * w;
                            wsum += w;
                        }
                        ssum = wsum == 0 ? fill : ssum / wsum;
                        result.putValue(i, j, l, ssum);
                    }
                }
            }
            org.das2.qds.DataSetUtil.putProperties(org.das2.qds.DataSetUtil.getProperties(ds), result);
            result.putProperty(QDataSet.DEPEND_2, result.property("DEPEND_3"));

        } else {
            throw new IllegalArgumentException("only rank 4 supported");
        }
        return result;
    }
}
