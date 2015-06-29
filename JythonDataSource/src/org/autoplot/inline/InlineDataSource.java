
package org.autoplot.inline;

import java.io.InputStream;
import java.net.URI;
import java.text.ParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.das2.datum.EnumerationUnits;
import org.das2.datum.LoggerManager;
import org.das2.datum.Units;
import org.das2.util.monitor.ProgressMonitor;
import org.python.core.PyList;
import org.python.core.PyObject;
import org.python.core.PyTuple;
import org.python.util.PythonInterpreter;
import org.virbo.dataset.BundleDataSet;
import org.virbo.dataset.DDataSet;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.DataSetUtil;
import org.virbo.dataset.MutablePropertyDataSet;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.SemanticOps;
import org.virbo.datasource.AbstractDataSource;
import org.virbo.datasource.URISplit;
import org.virbo.dsops.Ops;
import org.virbo.dsutil.DataSetBuilder;
import org.virbo.jythonsupport.JythonOps;
import org.virbo.jythonsupport.JythonUtil;

/**
 * Data source used mostly for demonstrations and quick modifications
 * of data.
 * @author jbf
 */
public class InlineDataSource extends AbstractDataSource {

    private static final Logger logger= LoggerManager.getLogger("jython.inline");
    
    PythonInterpreter interp;
    public InlineDataSource(URI uri) {
        super(uri);
    }
    
    private MutablePropertyDataSet jyCommand( String c ) throws Exception {
        logger.finest(c);
        
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

    /**
     * s formatted ds with only commas delineating datums.  If all elements are parseable as a
     * double, the result is a dimensionless array.  If parseable as
     * times, the result is a time array.  Otherwise the result is a
     * result has enumeration units for the ordinal values.
     * @param s formatted ds with only commas delineating datums
     * @return
     */
    private MutablePropertyDataSet parseInlineDsSimple( String s ) {
        logger.log(Level.FINEST, "parseInlineDsSimple {0}", s);
        
        Units u= Units.dimensionless;
        Units tu= Units.us2000;
        EnumerationUnits eu= EnumerationUnits.create("default");
        String[] ss2= s.split(",");
        DDataSet result= DDataSet.createRank1(ss2.length);

        boolean isTime= false;
        boolean isEnum= false;
        for ( int j=0; j<ss2.length; j++ ) {
            try {
                if ( !isTime && !isEnum ) u.parse(ss2[j]);
            } catch ( ParseException e ) {
                isTime= true;
                if ( !isEnum ) {
                    try {
                        tu.parse(ss2[j]);
                    } catch (ParseException ex) {
                        isEnum= true;
                    }
                }
            }
        }

        try {
            for ( int j=0; j<ss2.length; j++ ) {
                String ss= ss2[j];
                if ( isEnum ) {
                    if ( ss.startsWith("'") && ss.endsWith("'") ) ss= ss.substring(1,ss.length()-1);
                    result.putValue( j, eu.createDatum(ss).doubleValue(eu) );
                    if ( j==0 ) result.putProperty( QDataSet.UNITS, eu );
                } else if ( isTime ) {
                    result.putValue( j, tu.parse(ss).doubleValue(tu) );
                    if ( j==0 ) result.putProperty( QDataSet.UNITS, tu );
                } else {
                    result.putValue(j, u.parse(ss).value() );
                }
            }
        } catch ( ParseException ex ) {
            throw new RuntimeException(ex);
        }

        return result;
    }

    private MutablePropertyDataSet parseInlineDs( String s ) throws Exception {
        logger.log(Level.FINEST, "parseInlineDs {0}", s);

        if ( s.equals("None") || s.equals("null") || s.equals("") ) return null;
            
        boolean isCommand= s.length()>0 && s.charAt(0)>='a' && s.charAt(0)<='z';

        String linkCommand= "link( "+s + ")";

        try {
            PyObject result= interp.eval( linkCommand ); //wha?
            QDataSet res = (QDataSet) result.__tojava__(QDataSet.class);
            return DataSetOps.makePropertiesMutable(res);
        } catch ( RuntimeException ex ) {
            // since we couldn't run the command, we know that wasn't it.
            // TODO: it would be nicer to try to parse the string a little instead.
            logger.log(Level.FINE, "failed to execute: {0}", linkCommand);
            logger.log( Level.FINE, ex.getMessage(), ex );
        }


        if ( isCommand ) {  // we'll use a jython interpretter in the future
            try {
                return jyCommand(s);
            } catch ( Exception ex ) {
                throw new IllegalArgumentException( "inline jython code raises exception", ex );
            }
        }

        String[] ss= s.split(";",-2);
        if ( ss.length>1 ) { // rank 2
            BundleDataSet bds= BundleDataSet.createRank1Bundle();
            String[] ss2= ss[0].split(",");
            int nds= ss2.length; // number per record

            // do each column of the rank two table.
            int nrec=ss.length;
            for ( int j=0; j<nds; j++ ) {
                DataSetBuilder b= new DataSetBuilder(1,ss.length);
                for ( int i=0; i<nrec; i++ ) {
                    ss2= ss[i].split(",");
                    if ( j==0 && ss2[j].trim().length()==0 && i<nrec ) {
                        nrec=i;
                        continue;
                    } // check for empty record after semi: "1.23,134;"
                    QDataSet result= parseInlineDsSimple(ss2[j]);
                    b.putValue(-1,result.value(0));
                    b.putProperty( QDataSet.UNITS, result.property(QDataSet.UNITS) );
                    b.nextRecord();
                }
                bds.bundle(b.getDataSet());
            }

            //bds.putProperty( QDataSet.BUNDLE_1, null );
            return bds;
        } else {
            MutablePropertyDataSet result= parseInlineDsSimple(ss[0]);
            return result;
        }
    }

    private String[] wowSplit( String s, char delim, char exclude ) {    
        if ( delim=='_') throw new IllegalArgumentException("_ not allowed for delim");
        StringBuilder scopyb= new StringBuilder(s.length());
        boolean inExclude= false;
        for ( int i=0; i<s.length(); i++ ) {
            char c= s.charAt(i);
            if ( c==exclude ) inExclude= !inExclude;
            if ( inExclude ) c='_';
            scopyb.append(c);            
        }
        String[] ss= scopyb.toString().split(""+delim);
        
        int i1= 0;
        for ( int i=0; i<ss.length; i++ ) {
            int i2= i1+ss[i].length();
            ss[i]= s.substring(i1,i2);
            i1= i2+1;
        }
        return ss;
    }
    
    // http://autoplot.org/developer.inlineData
    //vap+inline:3,4;3,6;5,6
    //vap+inline:2000-001T00:00,23.5;2000-002T00:00,23.5;2000-003T00:00,23.5
    //vap+inline:1,2,3&DEPEND_0=1,2,3&DEPEND_0.UNITS=hours since 2000-001T00:00
    //vap+inline:exp(findgen(20))&UNITS=eV&SCALE_TYPE=log&LABEL=Energy
    //vap+inline:ripples(1440)&DEPEND_0=timegen('2003-05-01','1 min',1440) 
    //vap+inline:t=linspace(0,2*PI,200)&cos(2*t),sin(3*t),t
    @Override
    public QDataSet getDataSet( ProgressMonitor mon ) throws Exception {

        logger.log(Level.FINE, "getDataSet {0}", getURI() );

        logger.log( Level.FINER, "create interpreter");
        interp= JythonUtil.createInterpreter(true);
        if ( ! org.virbo.jythonsupport.Util.isLegacyImports() ) { // we need to always bring this in to support legacy URIs.
            logger.log( Level.FINER, "import the stuff we don't import automatically anymore");
            InputStream in=  org.virbo.jythonsupport.Util.class.getResource("imports.py").openStream();
            interp.execfile( in, "imports.py");
            in.close();
        }
                
        String s= getURI();

        s= s.replaceAll("%20"," ");
        //s= s.replaceAll("\\+"," ");

        String noFile= null;
        if ( s.startsWith("vap+inline:file:///") ) {
            noFile= s.substring(19);
        } else if ( s.startsWith("vap+inline:file:/") ) { // this is an old bug where file was inserted.
            noFile= s.substring(17);
        } else if ( s.startsWith("vap+inline:") ) {
            noFile= s.substring(11);
        } else { // do what we did before    
            // this is because URISplit treats it like a file.
            URISplit split= URISplit.parse(s);
            noFile= split.params==null ? split.file : split.params; //kludge...
            
        }
        
        String[] ss= wowSplit( noFile, '&', '\'' );
        

        MutablePropertyDataSet ds= null;
        MutablePropertyDataSet bundle1= null;
        MutablePropertyDataSet[] depn= new MutablePropertyDataSet[4];
        Map<String,String>[] deppropn= new Map[4];

        Map<String,String> p= new LinkedHashMap<String,String>();

        Pattern depPat= Pattern.compile("DEPEND_(\\d+)(\\.([A-Z]+))?");
        Matcher m;
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
                        if ( DataSetUtil.isDimensionProperty(propName) || propName.equals(QDataSet.RENDER_TYPE) || propName.equals(QDataSet.DELTA_PLUS) || propName.equals(QDataSet.DELTA_MINUS) ) {
                            p.put(propName,propValue);
                            continue;
                        } else {
                            try {
                                interp.exec(arg);
                            } catch ( Exception ex ) {
                                throw ex; // https://sourceforge.net/p/autoplot/bugs/1376/
                            }
                        }
                    }
                } else {
                    throw new ParseException("expected = for non-number",0);
                }
            } else if ( isAssignment(arg) ) {
                logger.log( Level.FINER, "assignment {0}", arg);

                interp.exec(arg);
                
            } else { 
                ds= parseInlineDs(arg);
                
            }
        }

        if ( ds==null ) {
            throw new IllegalArgumentException("URI don't contain anything to plot");
        }
        
        for ( int idep=0; idep<4; idep++ ) {
            Map<String,String> depp= deppropn[idep];
            if ( depp==null ) continue;
            for ( Entry<String,String> ent: depp.entrySet() ) {
                String prop= ent.getKey();
                MutablePropertyDataSet dep0= depn[idep];
                if ( dep0==null ) {
                    throw new IllegalArgumentException( "DEPEND_"+idep+"."+prop+" specified, but no DEPEND_"+idep+" ds");
                }
                String propValue= ent.getValue();
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

        for ( Entry<String,String> e: p.entrySet() ) {
            String prop= e.getKey();
            String propValue= e.getValue();
            ds= Ops.putProperty( ds, prop, propValue );
        }

        if ( depn[0]==null ) {
            if ( bundle1==null && ds.rank()==2 && ds.length(1)==2 ) { //TODO: we should be able to use bundle dataset... kludge
                if ( Ops.isBundle(ds) ) {
                    MutablePropertyDataSet xx= (MutablePropertyDataSet)DataSetOps.unbundle(ds,0) ;
                    MutablePropertyDataSet zz= (MutablePropertyDataSet)DataSetOps.unbundle(ds,1);
                    zz.putProperty( QDataSet.DEPEND_0, xx );
                    ds= zz;
                } else if ( ds instanceof BundleDataSet ) { // use unbundle to support TimeLocation and EnumerationUnits types
                    BundleDataSet bds= (BundleDataSet)ds;
                    MutablePropertyDataSet xx= (MutablePropertyDataSet) bds.unbundle(0);
                    MutablePropertyDataSet zz= (MutablePropertyDataSet) bds.unbundle(ds.length(0)-1);
                    if ( ds.property(QDataSet.RENDER_TYPE)!=null ) zz.putProperty(QDataSet.RENDER_TYPE,ds.property(QDataSet.RENDER_TYPE)); // vap+inline:0,0,100,100,0,0; 0,0,0,100,100,0&RENDER_TYPE=scatter
                    zz.putProperty( QDataSet.DEPEND_0, xx );
                    ds= zz;
                } else {
                    MutablePropertyDataSet xx= DDataSet.copy(DataSetOps.slice1(ds,0));
                    MutablePropertyDataSet zz= DDataSet.copy(DataSetOps.slice1(ds,ds.length(0)-1));
                    DataSetUtil.copyDimensionProperties( ds, zz ); // we put these in the wrong place, fix this...
                    if ( ds.property(QDataSet.RENDER_TYPE)!=null ) zz.putProperty(QDataSet.RENDER_TYPE,ds.property(QDataSet.RENDER_TYPE)); // vap+inline:0,0,100,100,0,0; 0,0,0,100,100,0&RENDER_TYPE=scatter
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

    private boolean isAssignment(String arg) {
        int i= arg.indexOf("=");
        if ( i==-1 ) return false;
        Pattern p= Pattern.compile("[a-zA-Z_][a-zA-Z_0-9]*\\s*");
        return p.matcher(arg.substring(0,i)).matches();
    }

}
