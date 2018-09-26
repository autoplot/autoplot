/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.jythonsupport;

import java.lang.reflect.Array;
import org.das2.datum.Units;
import org.python.core.PyArray;
import org.python.core.PyList;
import org.python.core.PyObject;
import org.python.core.adapter.PyObjectAdapter;
import org.das2.qds.ArrayDataSet;
import org.das2.qds.DDataSet;
import org.das2.qds.JoinDataSet;
import org.das2.qds.QDataSet;
import org.das2.qds.SemanticOps;
import org.das2.qds.SparseDataSet;
import org.das2.qds.ops.Ops;
import org.python.core.PyTuple;

/**
 * Adapt QDataSet results to PyQDataSet, which provides __getitem__
 * and __setitem__.  (ds[0,0]=ds[0,0]+1)
 * @author jbf
 */
public class PyQDataSetAdapter implements PyObjectAdapter {

    @Override
    public boolean canAdapt(Object arg0) {
        if ( arg0 instanceof QDataSet ) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public PyObject adapt(Object arg0) {
        return new PyQDataSet((QDataSet) arg0);
    }

    // see usages elsewhere, this is sloppy.
    // TODO: consider if [ DSA, DSB ] should append( DSA, DSB ) where DSA DSB are datasets.
    /**
     * adapts list to QDataSet.  
     * If this contains datums or rank 0 datasets with different units, then a bundle is returned.
     * TODO: Consider: if element is a string, then enumeration units are used.
     * @param p
     * @return
     */
    public static QDataSet adaptList( PyList p ) {
        double[] j= new double[ p.size() ];
        QDataSet d1;
        JoinDataSet jds= null; // support list of lists.
        Units u= null;
        Units[] us= new Units[p.size()]; // in case it's a bundle
        boolean isBundle= false;
        
        for ( int i=0; i<p.size(); i++ ) {
            Object n= p.get(i);
            //if ( u!=null || n instanceof String ) {
            //    u= EnumerationUnits.getByName("default");
            //    j[i]= ((EnumerationUnits)u).createDatum( n ).doubleValue( u );
            //} else {
            if ( n instanceof PyObject ) {
                d1= JythonOps.dataset((PyObject)n);
            } else {
                d1= Ops.dataset(n);
            }
            
            if ( u==null ) u= SemanticOps.getUnits(d1);
          
            Units ud1= SemanticOps.getUnits(d1);
            
            us[i]= ud1;
            
            if ( ud1!=u ) {
                if ( u.isConvertibleTo(ud1)) {
                    d1= Ops.convertUnitsTo(d1, u);
                } else {
                    isBundle= true;
                }
            }
            
            if ( d1.rank()==0 ) {
                j[i]= d1.value();
            } else {
                if ( jds==null ) {
                    jds= new JoinDataSet(d1);
                } else {
                    jds.join(d1);
                }
            }
        }
        
        if ( jds==null ) {
            DDataSet q= DDataSet.wrap( j );
            if ( isBundle ) {
                SparseDataSet bds= SparseDataSet.createRankLen( 2,p.size() );
                for ( int i=0; i<p.size(); i++ ) {
                    bds.putProperty( QDataSet.UNITS, i, us[i] );
                    bds.putProperty( QDataSet.NAME, i, "ch"+i );
                }
                q.putProperty( QDataSet.BUNDLE_0, bds );
            } else {
                q.putProperty( QDataSet.UNITS, u );
            }
            return q;
        } else {
            jds.putProperty( QDataSet.UNITS, u );
            return jds;
        }
    }

    protected static QDataSet adaptArray(PyArray pyArray) {
        Object arr= pyArray.getArray();
        double[] j= new double[ pyArray.__len__() ];
        JoinDataSet jds=null;
        QDataSet d1;
        Units u= null;
        for ( int i=0; i<pyArray.__len__(); i++ ) {
            Object n= Array.get( arr, i );
            if ( n instanceof PyObject ) {
                d1= JythonOps.dataset((PyObject)n);
            } else if ( n.getClass().isArray() ) {
                d1= Ops.dataset(n);
                if ( jds==null ) {
                    jds = new JoinDataSet(d1);
                } else {
                    jds.join(d1);
                }
            } else {
                d1= Ops.dataset(n);
            }

            if ( u==null ) u= SemanticOps.getUnits(d1);
          
            if ( SemanticOps.getUnits(d1)!=u ) {
                d1= Ops.convertUnitsTo(d1, u);
            }

            if ( d1.rank()==0 ) {
                j[i]= d1.value(); 
            }
            
        }
        
        if ( jds!=null ) {
            jds.putProperty( QDataSet.UNITS, u );
            //TODO: QUBE property would be nice.
            jds.putProperty( QDataSet.JOIN_0, null );
            return ArrayDataSet.copy(jds);
        } else {
            DDataSet q= DDataSet.wrap( j );
            q.putProperty( QDataSet.UNITS, u );
            return q;
        }
    }
    
    public static QDataSet adaptTuple( PyTuple p ) {
        double[] j= new double[ p.size() ];
        QDataSet d1;
        JoinDataSet jds= null; // support list of lists.
        Units u= null;
        Units[] us= new Units[p.size()]; // in case it's a bundle
        boolean isBundle= false;
        
        for ( int i=0; i<p.size(); i++ ) {
            Object n= p.get(i);
            //if ( u!=null || n instanceof String ) {
            //    u= EnumerationUnits.getByName("default");
            //    j[i]= ((EnumerationUnits)u).createDatum( n ).doubleValue( u );
            //} else {
            if ( n instanceof PyObject ) {
                d1= JythonOps.dataset((PyObject)n);
            } else {
                d1= Ops.dataset(n);
            }
            
            if ( u==null ) u= SemanticOps.getUnits(d1);
          
            Units ud1= SemanticOps.getUnits(d1);
            
            us[i]= ud1;
            
            if ( ud1!=u ) {
                if ( u.isConvertibleTo(ud1)) {
                    d1= Ops.convertUnitsTo(d1, u);
                } else {
                    isBundle= true;
                }
            }
            
            if ( d1.rank()==0 ) {
                j[i]= d1.value();
            } else {
                if ( jds==null ) {
                    jds= new JoinDataSet(d1);
                } else {
                    jds.join(d1);
                }
            }
        }
        
        if ( jds==null ) {
            DDataSet q= DDataSet.wrap( j );
            if ( isBundle ) {
                SparseDataSet bds= SparseDataSet.createRankLen( 2,p.size() );
                for ( int i=0; i<p.size(); i++ ) {
                    bds.putProperty( QDataSet.UNITS, i, us[i] );
                    bds.putProperty( QDataSet.NAME, i, "ch"+i );
                }
                q.putProperty( QDataSet.BUNDLE_0, bds );
            } else {
                q.putProperty( QDataSet.UNITS, u );
            }
            return q;
        } else {
            jds.putProperty( QDataSet.UNITS, u );
            return jds;
        }
    }
    
    
    // see usages elsewhere, this is sloppy.
    // TODO: consider if [ DSA, DSB ] should append( DSA, DSB ) where DSA DSB are datasets.
    /**
     * adapts list to QDataSet.
     * @param p
     * @param u the units which are often known.
     * @return
     */
    public static QDataSet adaptList( PyList p, Units u ) {
        double[] j= new double[ p.size() ];
        QDataSet d1;
        JoinDataSet jds= null; // support list of lists.
        for ( int i=0; i<p.size(); i++ ) {
            Object n= p.get(i);
            //if ( u!=null || n instanceof String ) {
            //    u= EnumerationUnits.getByName("default");
            //    j[i]= ((EnumerationUnits)u).createDatum( n ).doubleValue( u );
            //} else {
            if ( n instanceof PyObject ) {
                d1= JythonOps.dataset((PyObject)n,u);
            } else {
                d1= Ops.dataset(n,u);
            }
          
            if ( d1.rank()==0 ) {
                j[i]= d1.value();
            } else {
                if ( jds==null ) {
                    jds= new JoinDataSet(d1);
                } else {
                    jds.join(d1);
                }
            }
        }
        
        if ( jds==null ) {
            DDataSet q= DDataSet.wrap( j );
            q.putProperty( QDataSet.UNITS, u );
            return q;
        } else {
            jds.putProperty( QDataSet.UNITS, u );
            return jds;
        }
    }

    /**
     * adapts the Python Tuple to a QDataSet, using the provided units.
     * @param p
     * @param u the units which are often known.
     * @return 
     */
    public static QDataSet adaptTuple( PyTuple p, Units u ) {
        double[] j= new double[ p.size() ];
        QDataSet d1;
        JoinDataSet jds= null; // support list of lists.
        for ( int i=0; i<p.size(); i++ ) {
            Object n= p.get(i);
            //if ( u!=null || n instanceof String ) {
            //    u= EnumerationUnits.getByName("default");
            //    j[i]= ((EnumerationUnits)u).createDatum( n ).doubleValue( u );
            //} else {
            if ( n instanceof PyObject ) {
                d1= JythonOps.dataset((PyObject)n,u);
            } else {
                d1= Ops.dataset(n,u);
            }
          
            if ( d1.rank()==0 ) {
                j[i]= d1.value();
            } else {
                if ( jds==null ) {
                    jds= new JoinDataSet(d1);
                } else {
                    jds.join(d1);
                }
            }
        }
        
        if ( jds==null ) {
            DDataSet q= DDataSet.wrap( j );
            q.putProperty( QDataSet.UNITS, u );
            return q;
        } else {
            jds.putProperty( QDataSet.UNITS, u );
            return jds;
        }
    }

    protected static QDataSet adaptArray(PyArray pyArray, Units u ) {
        Object arr= pyArray.getArray();
        double[] j= new double[ pyArray.__len__() ];
        JoinDataSet jds=null;
        QDataSet d1;
        for ( int i=0; i<pyArray.__len__(); i++ ) {
            Object n= Array.get( arr, i );
            if ( n instanceof PyObject ) {
                d1= JythonOps.dataset((PyObject)n,u);
            } else if ( n.getClass().isArray() ) {
                d1= Ops.dataset(n,u);
                if ( jds==null ) {
                    jds = new JoinDataSet(d1);
                } else {
                    jds.join(d1);
                }
            } else {
                d1= Ops.dataset(n,u);
            }
            if ( u==null )  u= SemanticOps.getUnits(d1);

            if ( d1.rank()==0 ) {
                j[i]= d1.value(); 
            }
            
        }
        
        if ( jds!=null ) {
            jds.putProperty( QDataSet.UNITS, u );
            //TODO: QUBE property would be nice.
            jds.putProperty( QDataSet.JOIN_0, null );
            return ArrayDataSet.copy(jds);
        } else {
            DDataSet q= DDataSet.wrap( j );
            q.putProperty( QDataSet.UNITS, u );
            return q;
        }
            
            
    }
    
}
