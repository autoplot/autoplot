/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test.endtoend;

import org.autoplot.datasource.DataSourceUtil;

/**
 * Unit tests added as new codes are introduced.  This will serve mostly
 * as documentation, but also it doesn't hurt to test.
 * @author jbf
 */
public class Test049 {
    
    private static void test000() {
        if ( ! DataSourceUtil.unescapeParam( "energy.gt(1e+3)" ).equals( "energy.gt(1e+3)" ) ) throw new AssertionError("001");
        if ( ! DataSourceUtil.unescapeParam( "energy.within(1e+3+to+1e+5)" ).equals( "energy.within(1e+3 to+1e+5)" ) )  throw new AssertionError("002");
    }
    
    public static void main( String[] args ) {
        test000();
    }
}
