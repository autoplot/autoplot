/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import org.autoplot.cefdatasource.ReformDataSet;
import org.das2.qds.DDataSet;
import org.das2.qds.QDataSet;

/**
 *
 * @author jbf
 */
public class TestReformDataSet {

    public static void main(String[] args) {
        DDataSet ds1 = DDataSet.createRank2(4,6);
        int val = 0;
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 6; j++) {
                ds1.putValue(i, j, val++);
            }
        }
        QDataSet ds2 = new ReformDataSet(ds1, new int[]{4,2,3});
        System.err.println(" ds1[2,0]=" + ds1.value(2,0) + "  ds2[2,0,0]=" + ds2.value(2,0,0));
    }
}
