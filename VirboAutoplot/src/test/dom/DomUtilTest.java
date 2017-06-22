/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package test.dom;

import java.util.List;
import org.autoplot.dom.Canvas;
import org.autoplot.dom.Column;
import org.autoplot.dom.Diff;
import org.autoplot.dom.DomUtil;

/**
 *
 * @author jbf
 */
public class DomUtilTest {
    public static void main( String[] args ) {
        Canvas c1= new Canvas();
        Canvas c2= new Canvas();
        c2.setFitted(true);
        c1.setFitted(false);
        c1.setColumns( new Column[] { new Column(), new Column() } );
        c2.setColumns( new Column[] { new Column() } );

        c1.getColumns(0).setId("col_1");
        c1.getColumns(1).setId("col_2");

        c1.getColumns(0).setId("col_1");
        c1.getColumns(0).setLeft("newval");

        c2.getColumns(0).setId("col_2");
        c2.getColumns(0).setLeft("newval");

        List<Diff> diffs=  DomUtil.getDiffs(c1, c2) ;
        System.err.println( diffs );
    }
}
