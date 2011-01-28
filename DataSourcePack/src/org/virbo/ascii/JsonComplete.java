/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.ascii;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;
import org.virbo.dataset.AbstractDataSet;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.SemanticOps;

/**
 *
 * @author jbf
 */
public class JsonComplete {

    public static void test_01( ) throws JSONException {
         String ss = "#  \n"
                + "# TIME:{ LABEL: \"Time_UTC\" }\n"
                + "# DENSITY:{ LABEL: \"Density\", \n"
                + "#   SCALE_MIN:1E-2, SCALE_MAX:1e2, \n"
                + "#   SCALE_TYPE:\"LOG\" } \n"
                + "# \n"
                + "TIME DENSITY \n"
                + "2011-01-01T00:00 0.12\n"
                + "2011-01-01T00:01 0.14\n";

        JSONObject jo;
        jo = new JSONObject(JsonIntro.prep(ss));

        BundleDescriptor bs= toMetadata(jo);

        printBundle(bs);
    }

    public static void test_02( ) throws JSONException {
         String ss = "#  \n"
                + "# LABEL: [ \"Time_UTC\", \"Density\" ] \n"
                + "# SCALE_MIN: [ 0, 1E-2 ] \n"
                + "# SCALE_MAX: [1e31, 1e2 ] \n"
                + "# SCALE_TYPE: [ \"LINEAR\", \"LOG\" ] \n"
                + "# \n"
                + "2011-01-01T00:00 0.12\n"
                + "2011-01-01T00:01 0.14\n";

        JSONObject jo;
        jo = new JSONObject(JsonIntro.prep(ss));


    }

    private static void printBundle( QDataSet ds ) {
        for ( int i=0; i<ds.length(); i++ ) {
            String name= (String) ds.property( QDataSet.NAME, i );
            System.err.println("=="+name+"==");
            String[] props= DataSetUtil.dimensionProperties();
            for ( int j=0; j<props.length; j++ ) {
                Object v= ds.property( props[j], i );
                if ( v!=null ) {
                    System.err.println( "  "+props[j]+"="+v );
                }
            }
        }
    }

    /**
     * JSON can be specified in two ways:
     * 1. listing each dimension property and the values for each column (transpose)
     * 2. listing for each column, the dimension properties.
     * @param jo
     * @return
     */
    private static boolean isTranspose( JSONObject jo ) {
        for ( String s: DataSetUtil.dimensionProperties() ) {
            if ( jo.has(s) ) {
                return true;
            }
        }
        for ( String s: new String[] { "DEPEND_0" } ) {
            if ( jo.has(s) ) {
                return true;
            }
        }
        return false;
    }

    private static Object coerceToType( String propName, Object propValue ) {
        if ( propName.equals( QDataSet.UNITS ) ) {
            return SemanticOps.lookupUnits((String)propName);
        } else if ( propName.equals( QDataSet.VALID_MIN ) ) {
            return Double.parseDouble(String.valueOf(propValue) );
        } else if ( propName.equals( QDataSet.VALID_MAX ) ) {
            return Double.parseDouble(String.valueOf(propValue) );
        } else if ( propName.equals( QDataSet.TYPICAL_MIN ) ) {
            return Double.parseDouble(String.valueOf(propValue) );
        } else if ( propName.equals( QDataSet.TYPICAL_MAX ) ) {
            return Double.parseDouble(String.valueOf(propValue) );
        } else if ( propValue.equals( QDataSet.FILL_VALUE ) ) {
            return Double.parseDouble(String.valueOf(propValue) );
        } else if ( propValue.equals( QDataSet.MONOTONIC ) ) {
            return Boolean.valueOf(String.valueOf(propValue) );
        } else {
            return String.valueOf(propValue);
        }
    }

    public static class BundleDescriptor extends AbstractDataSet {

        int len;
        Map<String,Integer> datasets;
        Map<Integer,String> datasets2;
        Map<String,int[]> qubes;

        BundleDescriptor(  ) {
            properties= new LinkedHashMap();
            datasets= new LinkedHashMap();
            datasets2= new LinkedHashMap();
            qubes= new LinkedHashMap();
        }

        public void putProperty( String name, int i, Object value ) {
            properties.put( name+"__"+i, value );
        }

        /**
         * add the named dataset with the dimensions.  Note qube
         * doesn't include the first dimension, and this may be null for
         * rank 1 datasets.
         *
         * @param name name of the dataset
         * @param i  position in the qube.
         * @param qube the dimensions or null for rank 1 data, e.g. vector= [3]
         */
        protected void addDataSet( String name, int i, int[] qube ) {
            datasets.put( name, i );
            datasets2.put( i, name );
            qubes.put( name, qube );
        }

        public int rank() {
            return 2;
        }

        @Override
        public int length() {
            return datasets.size();
        }

        @Override
        public int length(int i) {
            String name= datasets2.get(i);
            int[] qube= qubes.get(name);
            if ( qube==null ) {
                return 0;
            } else {
                return qube[0];
            }
        }

        @Override
        public Object property(String name, int i) {
            Object v= properties.get( name+"__"+i );
            return v;
        }

        @Override
        public double value(int i0, int i1) {
            // support bundling just rank N-1 datasets.  to support higher rank
            // datasets, this should return the qube dims.
            throw new IndexOutOfBoundsException("length=0");
        }

    }
    /**
     * return a map of metadata for each column
     * @param jo
     * @return
     */
    public static BundleDescriptor toMetadata( JSONObject jo ) throws JSONException {

        BundleDescriptor bd= new BundleDescriptor();

        String[] names= JSONObject.getNames(jo);
        if ( isTranspose(jo) ) {
            throw new IllegalArgumentException("not implemented");
        } else {
            Iterator it= jo.keys();
            for ( ; it.hasNext(); ) {
                 String key= (String) it.next();
                 Object o= jo.get(key);
                 if ( !( o instanceof JSONObject ) ) {
                     System.err.println("expected JSONObject for value: "+key );
                     continue;
                 } else {
                     int ids= bd.length(); //DANGER:Rank2
                     bd.addDataSet( key, ids, null );
                     JSONObject propsj= ((JSONObject)o);
                     Iterator props= propsj.keys();
                     bd.putProperty( QDataSet.NAME, ids, key );
                     for ( ; props.hasNext(); ) {
                         String prop= (String) props.next();
                         Object v= coerceToType( prop, propsj.get(prop) );
                         bd.putProperty( prop, ids, v );
                     }
                 }
            }
        }
        return bd;
    }

    public static void main( String[] args ) throws Exception {
        test_01();
        test_02();
    }
}
