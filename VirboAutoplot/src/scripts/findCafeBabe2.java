/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package scripts;

import org.apache.poi.hpsf.MutableProperty;
import org.virbo.autoplot.ScriptContext;
import org.virbo.dataset.AbstractDataSet;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dsops.Ops;
import org.virbo.jythonsupport.Util;

/**
 *
 * @author jbf
 */
public class findCafeBabe2 {

    static class FilterDataSet extends AbstractDataSet {

        QDataSet ds;

        FilterDataSet(QDataSet ds) {
            this.ds = ds;
        }

        public double value(int i) {
            return ( ds.value(i) == 0xCA ) 
                    && ( ds.value(i + 1) == 0xFE ) 
                    && ( ds.value(i + 2) == 0xBA ) 
                    && ( ds.value(i + 3) == 0xBE ) ? 1.0 : 0.0;
        }

        public int length() {
            return ds.length() - 4;
        }

        public int rank() {
            return 1;
        }
    }

    public static void main(String[] args) throws Exception {
       /* QDataSet ds = Util.getDataSet( "bin.file:///net/spot3/home/jbf/incoming/netbeans-6.5rc2-ml-linux.sh"  );

        QDataSet fds= new FilterDataSet(ds);

        System.err.println(  "starting where command..." );

        // this  is slow because of all the jython overhead.  Better to make some callable
        QDataSet r= Ops.where( fds );

        int index= (int)(r.value(0));

        System.err.printf( "Index is at %d.\n",  index );
        System.err.printf(  "All indeces: %s\n", r.toString() );
    */
        MutablePropertyDataSet dstail= (MutablePropertyDataSet) Util.getDataSet("bin.file:///net/spot3/home/jbf/incoming/netbeans-6.5rc2-ml-linux.sh?byteOffset=90770&byteLength=254080366" );
        dstail.putProperty( QDataSet.DEPEND_0, null );
        ScriptContext.formatDataSet(dstail, "bin.file:///home/jbf/temp/tailingStuff.bin?type=ubyte");
        
    }
}
