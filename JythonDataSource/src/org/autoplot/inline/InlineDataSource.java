/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.inline;

import java.net.URI;
import java.text.ParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.das2.datum.Units;
import org.das2.util.monitor.ProgressMonitor;
import org.python.core.PyList;
import org.python.core.PyObject;
import org.python.core.PyTuple;
import org.python.util.PythonInterpreter;
import org.virbo.dataset.BundleDataSet;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.SemanticOps;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.URISplit;
import org.virbo.dsops.Ops;
import org.virbo.jythonsupport.JythonOps;
import org.virbo.jythonsupport.JythonUtil;

/**
/**
 *
 * @author jbf
 */
public class InlineDataSource extends AbstractDataSource {

    public InlineDataSource(URI uri) {
        super(uri);
    }

    private MutablePropertyDataSet jyCommand( String c ) throws Exception {
        PythonInterpreter interp = JythonUtil.createInterpreter(false);
        PyObject result= interp.eval(c);

        QDataSet res;

        if (result instanceof PyList) {
            res = JythonOps.coerceToDs((PyList) result);
        } else if ( result instanceof PyTuple && ((PyTuple)result).size()<3 ) {
            //JythonOps.coerce(result);  too bad coerce doesn't do this already.
            PyTuple tres= (PyTuple)result;
            if ( tres.size()==2 ) {
                res= Ops.link( (QDataSet)tres.get(0), (QDataSet)tres.get(1) );
            } else if ( tres.size()==3 ) {
                res= Ops.link( (QDataSet)tres.get(0), (QDataSet)tres.get(1), (QDataSet)tres.get(2) );
            } else if ( tres.size()==1 ) {
                res= (QDataSet)tres.get(0); // how did this happen?  Might as well support it
            } else {
                throw new ParseException( "unable to parse command: "+c, 0 );
            }
        } else {
            res = (QDataSet) result.__tojava__(QDataSet.class);
        }
        return DataSetOps.makePropertiesMutable(res);
    }


    private MutablePropertyDataSet parseInlineDs( String s ) throws Exception {
        if ( s.equals("None") || s.equals("null") || s.equals("") ) return null;
            
        boolean isCommand= s.length()>0 && s.charAt(0)>='a' && s.charAt(0)<='z';

        String linkCommand= "link( "+s + ")";

        PythonInterpreter interp = JythonUtil.createInterpreter(false);

        try {
            PyObject result= interp.eval( linkCommand ); //wha?
            QDataSet res = (QDataSet) result.__tojava__(QDataSet.class);
            return DataSetOps.makePropertiesMutable(res);
        } catch ( RuntimeException ex ) {
            // since we couldn't run the command, we know that wasn't it.
            // TODO: it would be nicer to try to parse the string a little instead.
        }


        if ( isCommand ) {  // we'll use a jython interpretter in the future
            return jyCommand(s);
        }

        String[] ss= s.split(";");
        Units u= Units.dimensionless;
        if ( ss.length>1 ) { // rank 2
            BundleDataSet bds= BundleDataSet.createRank1Bundle();
            for ( int i=0; i<ss.length; i++ ) {
                String[] ss2= ss[i].split(",");
                DDataSet result= DDataSet.createRank1(ss2.length);
                for ( int j=0; j<ss2.length; j++ ) {
                    result.putValue(j, u.parse(ss2[j]).doubleValue(u) );
                }
                bds.bundle(result);
            }
            bds.putProperty( QDataSet.BUNDLE_1, null );
            return bds;
        } else {
           String[] ss2= ss[0].split(",");
            DDataSet result= DDataSet.createRank1(ss2.length);
            for ( int j=0; j<ss2.length; j++ ) {
                result.putValue(j, u.parse(ss2[j]).doubleValue(u) );
            }
            return result;
        }
    }

    // http://autoplot.org/developer.inlineData
    //vap+inline:3,4;3,6;5,6
    //vap+inline:2000-001T00:00,23.5;2000-002T00:00,23.5;2000-003T00:00,23.5
    //vap+inline:1,2,3&DEPEND_0=1,2,3&DEPEND_0.UNITS=hours since 2000-001T00:00
    //vap+inline:exp(findgen(20))&UNITS=eV&SCALE_TYPE=log&LABEL=Energy
    //vap+inline:vap+inline:ripples(1440)&DEPEND_0=timegen('2003-05-01','1 min',1440)
    
    public QDataSet getDataSet( ProgressMonitor mon ) throws Exception {
        String s= getURI();

        s= s.replaceAll("%20"," ");
        //s= s.replaceAll("\\+"," ");

        // this is because URISplit treats it like a file.
        URISplit split= URISplit.parse(s);
        String noFile;
        if ( split.file!=null ) {
            if ( split.file.startsWith("file:///") ) {
                noFile= split.file.substring("file:///".length());
            } else {
                noFile= split.file;
            }
        } else {
            noFile= split.params;
        }
        String[] ss= noFile.split("&");

        MutablePropertyDataSet ds= null;
        MutablePropertyDataSet bundle1= null;
        MutablePropertyDataSet[] depn= new MutablePropertyDataSet[4];
        Map<String,String>[] deppropn= new Map[4];

        Map<String,String> p= new LinkedHashMap<String,String>();

        Pattern depPat= Pattern.compile("DEPEND_(\\d+)(\\.([A-Z]+))?");
        Matcher m=null;
        for ( int i=0; i<ss.length; i++ ) {
            String arg= ss[i];
            if ( arg.length()==0 ) continue;
            if ( arg.charAt(0)>='A' && arg.charAt(0)<='Z' ) { // it's a directive
                String[] sss= arg.split("=");
                if ( sss.length>1 ) {
                    String propName= sss[0];
                    String propValue= sss[1];
                    if ( (m=depPat.matcher(propName)).matches() ) {
                        int idep= Integer.parseInt( m.group(1) );
                        if ( m.group(3)!=null ) {
                            Map map= deppropn[idep];
                            if ( map==null ) {
                                map= new HashMap();
                                deppropn[idep]= map;
                            }
                            map.put( m.group(3), propValue );
                        } else {
                            depn[idep]= parseInlineDs(propValue);
                        }
                    } else if ( propName.startsWith("BUNDLE_1") ) {
                        if ( propValue.equals("") || propValue.equals("None") || propValue.equals("null") ) {
                            p.put(propName,propValue);
                        } else {
                            bundle1= parseInlineDs(propValue);
                        }
                    } else {
                        p.put(propName,propValue);
                    }
                } else {
                    throw new ParseException("expected = for non-number",0);
                }
            } else if ( arg.charAt(0)>='a' && arg.charAt(0)<='z') { // fun with jython?
                //TODO: didn't do jython because of dependency
                ds= parseInlineDs(arg);
            } else {
                ds= parseInlineDs(arg);
            }
        }

        for ( int idep=0; idep<4; idep++ ) {
            Map<String,String> depp= deppropn[idep];
            if ( depp==null ) continue;
            for ( String prop: depp.keySet() ) {
                MutablePropertyDataSet dep0= depn[idep];
                if ( dep0==null ) {
                    throw new IllegalArgumentException( "DEPEND_"+idep+"."+prop+" specified, but no DEPEND_"+idep+" ds");
                }
                String propValue= depp.get(prop);
                if ( prop.equals("UNITS") ) {
                    dep0.putProperty( prop,SemanticOps.lookupUnits(propValue));
                } else if ( prop.equals("FILL_VALUE" )  || prop.equals("VALID_MIN") || prop.equals("VALID_MAX") || prop.equals("TYPICAL_MIN") || prop.equals("TYPICAL_MAX") ) {
                    dep0.putProperty( prop,Double.parseDouble(propValue));
                } else if ( prop.equals("MONOTONIC") ) {
                    dep0.putProperty( prop, Boolean.parseBoolean(propValue) ); // True or TRUE
                } else {
                    // it would be nice if the same code handled DEPEND_0 and the dataset.
                    dep0.putProperty(prop,propValue);
                }
            }
        }

        for ( String prop: p.keySet() ) {
            String propValue= p.get(prop);
            if ( prop.equals("UNITS") ) {
                ds.putProperty( prop, SemanticOps.lookupUnits(propValue) );
            } else if ( prop.equals("FILL_VALUE" ) || prop.equals("VALID_MIN") || prop.equals("VALID_MAX") || prop.equals("TYPICAL_MIN") || prop.equals("TYPICAL_MAX")) {
                ds.putProperty( prop, Double.parseDouble(propValue) );
            } else if ( prop.equals("MONOTONIC") ) {
                ds.putProperty( prop, Boolean.parseBoolean(propValue) ); // True or TRUE
            } else if ( prop.startsWith("DEPEND_") ) {
                if ( propValue.equals("") || propValue.equals("None") || propValue.equals("null") ) {
                    ds.putProperty( prop, null);
                }
            } else {
                // it would be nice to use QStream's SerializeRegistery here to attempt decode for each type.
                ds.putProperty(prop,propValue);
            }
        }

        if ( depn[0]==null ) {
            if ( bundle1==null && ds.rank()==2 && ds.length()==2 ) { //TODO: we should be able to use bundle dataset... kludge
                MutablePropertyDataSet xx= DDataSet.copy(DataSetOps.slice0(ds,0));
                MutablePropertyDataSet zz= DDataSet.copy(DataSetOps.slice0(ds,ds.length()-1));
                zz.putProperty( QDataSet.DEPEND_0, xx );
                ds= zz;
            } else if ( bundle1==null && ds.rank()==2 && ds.length(1)==2 ) { //TODO: we should be able to use bundle dataset... kludge
                if ( Ops.isBundle(ds) ) {
                    MutablePropertyDataSet xx= (MutablePropertyDataSet)DataSetOps.unbundle(ds,0) ;
                    MutablePropertyDataSet zz= (MutablePropertyDataSet)DataSetOps.unbundle(ds,ds.length(0)-1);
                    zz.putProperty( QDataSet.DEPEND_0, xx );
                    ds= zz;   
                } else {
                    MutablePropertyDataSet xx= DDataSet.copy(DataSetOps.slice1(ds,0));
                    MutablePropertyDataSet zz= DDataSet.copy(DataSetOps.slice1(ds,ds.length(0)-1));
                    zz.putProperty( QDataSet.DEPEND_0, xx );
                    ds= zz;
                }
            }
        } else {
            for ( int idep=0; idep<4; idep++ ) {
                ds.putProperty( "DEPEND_"+idep, depn[idep] );
            }
        }

        if ( bundle1!=null ) {
            ds.putProperty( QDataSet.BUNDLE_1, bundle1 );
        }
        return ds;
    }

}
