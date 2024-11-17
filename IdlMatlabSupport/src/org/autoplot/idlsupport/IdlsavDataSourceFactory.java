
package org.autoplot.idlsupport;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;
import org.autoplot.datasource.AbstractDataSourceFactory;
import org.autoplot.datasource.CompletionContext;
import org.autoplot.datasource.DataSetURI;
import org.autoplot.datasource.DataSource;
import org.autoplot.datasource.URISplit;
import org.autoplot.idlsupport.ReadIDLSav.ArrayDesc;
import org.autoplot.idlsupport.ReadIDLSav.StructDesc;
import org.autoplot.idlsupport.ReadIDLSav.TagDesc;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.ProgressMonitor;

/**
 * Factory for reading IDLSave files.
 * @author jbf
 */
public class IdlsavDataSourceFactory extends AbstractDataSourceFactory {

    private static Logger logger= LoggerManager.getLogger("apdss.idlsav");
    
    File file=null;
    ByteBuffer buf= null;
    String[] names;
    ReadIDLSav reader;

    @Override
    public DataSource getDataSource(URI uri) throws Exception {
        return new IdlsavDataSource(uri);
    }

    @Override
    public boolean reject(String surl, List<String> problems, ProgressMonitor mon) {
        try {
            URISplit split= URISplit.parse(surl);
            Map<String,String> params= URISplit.parseParams(split.params);
            String var= params.get(URISplit.PARAM_ARG_0);
             
            if ( "true".equals(params.get("locations")) ) {
                return false;
            }
             
            String x= params.get("X");
            String y= params.get("Y");
            String z= params.get("Z");
            
            if ( var==null && x==null && y==null && z==null ) {
                problems.add("need variable name to read");
                return true;
            }
            
            File file= DataSetURI.getFile( split.resourceUri, mon );
            
            FileChannel fc= ReadIDLSav.readFileIntoChannel(file);
            String[] names= new ReadIDLSav().readVarNames(fc);
            
            String[] vars=null;
            if ( var!=null ) {
                vars = var.split(",",-2);
            } else {
                if ( params.get("Z")!=null ) {
                    vars= new String[3];
                    vars[2]= params.get("Z");
                }
                if ( params.get("Y")!=null ) {
                    if ( vars==null ) vars= new String[2];
                    vars[1]= params.get("Y");
                } 
                if ( params.get("X")!=null ) {
                    if ( vars==null ) vars= new String[1];
                    vars[0]= params.get("X");
                }
            }
            
            for (String var1 : vars) {
                var = var1;
                if ( var==null ) continue;
                boolean found= false;
                for (String name : names) {
                    if (var.startsWith(name)) {
                        found= true;
                    }
                }
                if ( !found ) {
                    problems.add("no plottable parameters start with "+var);
                }
                ReadIDLSav reader= new ReadIDLSav();
                TagDesc t= reader.readTagDesc( fc, var );
                if ( t==null ) {
                    problems.add("no tag desc found for "+var);
                } else if ( t instanceof StructDesc ) {
                    problems.add("tag is a structure: "+var);                    
                }
            }
            fc.close();
            
            return problems.size()>0 ;
            
        } catch (IOException ex) {
            problems.add( ex.toString() );
            return true;
        }
    }
    
    private void addCompletions( ReadIDLSav reader, String root, String key, ByteBuffer buf, List<CompletionContext> ccresult ) throws IOException {
        String keyn= root==null ? key : root + "." + key;
        
        if ( root!=null ) {
            Object o= reader.readVar( buf, root );
            Map<String,Object> m= (Map<String,Object>)o;
            
            for ( Entry<String,Object> e: m.entrySet() ) {
                CompletionContext cc1= new CompletionContext( 
                    CompletionContext.CONTEXT_PARAMETER_NAME,
                    keyn, this, "arg_0", root + "." + key, "", true );
                ccresult.add(cc1);
            }            
        }
        if ( reader.isStructure( buf, key ) ) {
            StructDesc desc= (StructDesc)reader.readTagDesc( buf, key );
            
            for ( String t : desc.tagnames ) {
                CompletionContext cc1= new CompletionContext( 
                    CompletionContext.CONTEXT_PARAMETER_NAME,
                    keyn+"." +t, this, "arg_0", keyn+"." +t, "", true );
                ccresult.add(cc1);
                
            }
            
        } else if ( reader.isArray( buf, key ) ) {
            TagDesc tagDesc= reader.readTagDesc( buf, key );
            tagDesc.toString();
            if ( tagDesc instanceof ArrayDesc ) {
                ArrayDesc desc= (ArrayDesc)tagDesc;
                String stype = ReadIDLSav.decodeTypeCode(desc.typecode);
                StringBuilder sqube= new StringBuilder(stype).append("[").append(String.valueOf(desc.dims[0]));
                for ( int i=1; i<desc.ndims; i++ ) {
                    sqube.append(",").append(String.valueOf(desc.dims[i]));
                }
                sqube.append("]");
                CompletionContext cc1= new CompletionContext( 
                        CompletionContext.CONTEXT_PARAMETER_NAME,
                        keyn, this, "arg_0", keyn+" " +sqube, "", true );
                ccresult.add(cc1);
            } else { // complex numbers
                String stype = ReadIDLSav.decodeTypeCode(tagDesc.typecode);
                CompletionContext cc1= new CompletionContext( 
                        CompletionContext.CONTEXT_PARAMETER_NAME,
                        keyn, this, "arg_0", keyn+" "+stype, "", true );
                ccresult.add(cc1);
            }
        } else {
            String so= "";
            try {
                Object o = reader.readVar( buf, keyn );
                so= String.valueOf(o);
            } catch ( IOException ex ) {
                
            }
            CompletionContext cc1= new CompletionContext( 
                    CompletionContext.CONTEXT_PARAMETER_NAME,
                    keyn, this, "arg_0", keyn+" scalar (=" + so + ")", "", true );
            ccresult.add(cc1);
        }

    }
    
    private String[] getVariableNames( File file, String completable ) throws IOException {
        ArrayList<String> result= new ArrayList<>();
        ByteBuffer buf= ReadIDLSav.readFileIntoByteBuffer(file);
        String[] names= new ReadIDLSav().readVarNames(buf);
        ReadIDLSav reader= new ReadIDLSav();
        if ( completable.contains(".") ) {
            int i= completable.lastIndexOf('.');
            String root= completable.substring(0,i);
            int i2= root.lastIndexOf(",");
            if ( i2>-1 ) {
                root= root.substring(i2+1);
            }
            Object o= reader.readVar( buf, root );
            if ( o==null ) {
                return new String[0];
            }
            Map<String,Object> m= (Map<String,Object>)o;
            for ( Entry<String,Object> e: m.entrySet() ) {
                if ( e.getValue() instanceof Map ) {
                    for ( Entry<String,Object> e2: ((Map<String,Object>)e.getValue()).entrySet() ) {
                        result.add( root + "." + e.getKey() + "."+ e2.getKey() );
                    }
                } else {
                    result.add( root + "." + e.getKey() + "."+ e.getKey() );
                }
            }
            return result.toArray( new String[result.size()] );
        } else {
            return names;
        }
        
    }
    
    @Override
    public List<CompletionContext> getCompletions(CompletionContext cc, ProgressMonitor mon) throws Exception {
        
        File thefile= DataSetURI.getFile( cc.resourceURI, mon );
        //if ( file==null || !thefile.equals(file) ) {
            file= thefile;
            logger.info("reading variables from idlsav");
            buf= ReadIDLSav.readFileIntoByteBuffer(file);
            logger.info("done reading variables from idlsav");
            reader= new ReadIDLSav();
            names= reader.readVarNames(buf);
            
        //}

        if ( cc.context.equals(CompletionContext.CONTEXT_PARAMETER_NAME) ) {
            List<CompletionContext> ccresult= new ArrayList<>();
            getCompletionsWithStructs(names, reader, buf, ccresult, null );
            ccresult.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "X=", "variable for the x values"));
            ccresult.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "Y=", "variable for the y values"));
            ccresult.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "Z=", "variable for the z values"));
            ccresult.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "xunits=", "units for the x values"));
            ccresult.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "yunits=", "units for the y values"));
            ccresult.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_NAME, "units=", "units for the values"));
            return ccresult;
        } else if ( cc.context.equals(CompletionContext.CONTEXT_PARAMETER_VALUE ) ) {
            String paramName = CompletionContext.get(CompletionContext.CONTEXT_PARAMETER_NAME, cc);
            switch (paramName) {
                case "X":
                case "Y":
                case "Z":
                    List<CompletionContext> ccresult= new ArrayList<>();
                    getCompletionsWithStructs(names, reader, buf, ccresult, paramName);
                    return ccresult;
                case "xunits":
                    List<CompletionContext> result = new ArrayList<>();
                    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "t1970", "seconds since 1970-01-01T00:00" ) );
                    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "cdfTT2000", "cdf times" ) );
                    result.add(new CompletionContext(CompletionContext.CONTEXT_PARAMETER_VALUE, "hours+since+2015-01-01T00:00", "arbitrary time base" ) );
                    return result;
                default:
                    return super.getCompletions(cc, mon);
            }
        } else {
            return super.getCompletions(cc, mon);
        }
    }

    private void getCompletionsWithStructs(String[] names, ReadIDLSav reader, ByteBuffer buf, 
            List<CompletionContext> ccresult, String paramName) throws IOException {
        for (String name : names) {
            String root= name;
            if ( reader.isStructure(buf, name ) ) {
                Object o= reader.readVar( buf, name );
                if ( o instanceof Map ) {
                    Map<String,Object> m= (Map<String,Object>)o;
                    for ( Entry<String,Object> e: m.entrySet() ) {
                        if ( e.getValue() instanceof Map ) {
                            for ( Entry<String,Object> e2: ((Map<String,Object>)e.getValue()).entrySet() ) {
                                if ( paramName==null ) {
                                    CompletionContext cc1= new CompletionContext(
                                            CompletionContext.CONTEXT_PARAMETER_NAME,
                                            root + "." + e.getKey() + "."+ e2.getKey(), this, "arg_0", root + "." + e.getKey()+ "."+ e2.getKey(), "Dependent Parameter", true );
                                    ccresult.add(cc1);
                                } else {
                                    CompletionContext cc1= new CompletionContext(
                                            CompletionContext.CONTEXT_PARAMETER_VALUE,
                                            root + "." + e.getKey() + "."+ e2.getKey(), this, paramName, root + "." + e.getKey()+ "."+ e2.getKey(), "", true );
                                    ccresult.add(cc1);
                                }
                            }
                        } else {
                            if ( paramName==null ) {
                                CompletionContext cc1= new CompletionContext(
                                        CompletionContext.CONTEXT_PARAMETER_NAME,
                                        root + "." + e.getKey(), this, "arg_0", root + "." + e.getKey(), "Dependent Parameter", true );
                                ccresult.add(cc1);
                            } else {
                                CompletionContext cc1= new CompletionContext(
                                        CompletionContext.CONTEXT_PARAMETER_VALUE,
                                        root + "." + e.getKey(), this,  paramName, root + "." + e.getKey(), "", true );
                                ccresult.add(cc1);
                                
                            }
                        }
                    }
                }
            } else {
                addCompletions(reader, null, name, buf, ccresult);
            }
        }
    }

    @Override
    public String getDescription() {
        return "IDL Savesets";
    }
    
    
}
