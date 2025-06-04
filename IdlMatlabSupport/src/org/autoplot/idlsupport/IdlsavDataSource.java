
package org.autoplot.idlsupport;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Array;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.text.ParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.autoplot.datasource.AbstractDataSource;
import org.das2.datum.Datum;
import org.das2.datum.EnumerationUnits;
import org.das2.datum.TimeUtil;
import org.das2.datum.Units;
import org.das2.qds.ArrayDataSet;
import org.das2.qds.DDataSet;
import org.das2.qds.DataSetOps;
import org.das2.qds.DataSetUtil;
import org.das2.qds.IDataSet;
import org.das2.qds.LDataSet;
import org.das2.qds.QDataSet;
import org.das2.qds.SDataSet;
import org.das2.qds.examples.Schemes;
import org.das2.qds.ops.Ops;
import org.das2.qds.util.DataSetBuilder;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.ProgressMonitor;

/**
 * Read a variable from an IDLSav file.
 * @author jbf
 */
public class IdlsavDataSource extends AbstractDataSource {

    private static Logger logger= LoggerManager.getLogger("apdss.idlsav");
    
    public IdlsavDataSource(URI uri) {
        super(uri);
    }

    public static Object getFromStructure( Map v, String t ) {
        int i= t.indexOf('.');
        if ( i==-1 ) {
            return ((Map)v).get(t);
        } else {
            Map vc= (Map)(((Map)v).get(t.substring(0,i)));  // child of v
            return getFromStructure( vc, t.substring(i+1) );
        }
    }
    
    private static Map<String,Object> getUserProperties( ReadIDLSav.ArrayData arr ) {
        Map up= new LinkedHashMap();
        up.put( "fileOffset", arr._fileOffset );
        up.put( "lengthBytes", arr._lengthBytes );
        return up;
    }
    
    public static QDataSet getArray( ReadIDLSav reader, ByteBuffer buffer, String arg ) throws IOException {
        Object v;
        
        int i= arg.indexOf('.');
        String t=arg;
        if ( i>-1 ) { // structure
            String h= t.substring(0,i);
            t= t.substring(i+1);
            v= reader.readVar( buffer, h );
            if ( !( v instanceof Map ) ) {
                throw new IllegalArgumentException("expected map for '"+h+"'");
            } else {
                v= getFromStructure( ((Map)v), t );
                if ( v==null ) throw new IllegalArgumentException("unable to find variable: "+arg);
            }
        } else {
            v= reader.readVar( buffer, arg );
        }
        
        if ( v==null ) {
            throw new IllegalArgumentException("unable to find variable or not supported: "+arg);
        }
        
        if ( v instanceof ReadIDLSav.ArrayData ) {
            ReadIDLSav.ArrayData arrayData= (ReadIDLSav.ArrayData)v;
            Class c= arrayData.array.getClass();
            if ( c.isArray() && c.getComponentType()==String.class ) {
                if ( arrayData.dims.length>1 ) {
                    throw new IllegalArgumentException("not supported");
                }
                EnumerationUnits u= Units.nominal();
                ArrayDataSet result= IDataSet.create(arrayData.dims);
                result.putProperty( QDataSet.UNITS, u );
                if ( arrayData.dims.length!=1 ) throw new IllegalArgumentException("multi dimensional not supported");
                for ( int j=0; j<Array.getLength(arrayData.array); j++ ) {
                    Datum d= u.createDatum( Array.get( arrayData.array, j ) );
                    result.putValue( j, d.doubleValue(u) );
                }
                result.putProperty( QDataSet.USER_PROPERTIES, getUserProperties( arrayData ) );
                return result;
            } else {
                ArrayDataSet result;
                if ( arrayData.typeCode==ReadIDLSav.TYPECODE_COMPLEX_FLOAT || arrayData.typeCode==ReadIDLSav.TYPECODE_COMPLEX_DOUBLE ) {
                    result= ArrayDataSet.wrap( arrayData.array, DataSetOps.addElement(arrayData.dims, 2), false );
                    result.putProperty( QDataSet.DEPEND_1, Schemes.complexCoordinateSystemDepend() );
                } else {
                    result= ArrayDataSet.wrap( arrayData.array, arrayData.dims, false );
                }
                if ( result instanceof SDataSet || result instanceof IDataSet || result instanceof LDataSet ) {
                    result.putProperty( QDataSet.FORMAT, "%d" );
                }
                result.putProperty( QDataSet.USER_PROPERTIES, getUserProperties( arrayData ) );
                return result;
            }
        } else if ( v instanceof Map ) { 
            throw new IllegalArgumentException("Map is not supported, select one of its tags");
        } else if ( v instanceof String ) {
            return Ops.dataset( Units.nominal().createDatum(v) );
        } else if ( v instanceof double[] && Array.getLength(v)==2 ) { //
            ArrayDataSet result= ArrayDataSet.wrap( v, new int [] { 2 }, false );
            result.putProperty( QDataSet.DEPEND_0, Schemes.complexCoordinateSystemDepend() );
            return result;
        } else if ( v instanceof float[] && Array.getLength(v)==2 ) { //
            ArrayDataSet result= ArrayDataSet.wrap( v, new int [] { 2 }, false );
            result.putProperty( QDataSet.DEPEND_0, Schemes.complexCoordinateSystemDepend() );
            return result;
        } else {
            return Ops.dataset(v);
        }
    }

    public static QDataSet getArray( ReadIDLSav reader, FileChannel inch, String arg ) throws IOException {
        Object v;
        
        int i= arg.indexOf('.');
        String t=arg;
        if ( i>-1 ) { // structure
            String h= t.substring(0,i);
            t= t.substring(i+1);
            v= reader.readVar( inch, h );
            if ( !( v instanceof Map ) ) {
                throw new IllegalArgumentException("expected map for '"+h+"'");
            } else {
                v= getFromStructure( ((Map)v), t );
                if ( v==null ) throw new IllegalArgumentException("unable to find variable: "+arg);
            }
        } else {
            v= reader.readVar( inch, arg );
        }
        
        if ( v==null ) {
            throw new IllegalArgumentException("unable to find variable or not supported: "+arg);
        }
        
        if ( v instanceof ReadIDLSav.ArrayData ) {
            ReadIDLSav.ArrayData arrayData= (ReadIDLSav.ArrayData)v;
            Class c= arrayData.array.getClass();
            if ( c.isArray() && c.getComponentType()==String.class ) {
                if ( arrayData.dims.length>1 ) {
                    throw new IllegalArgumentException("not supported");
                }
                EnumerationUnits u= Units.nominal();
                ArrayDataSet result= IDataSet.create(arrayData.dims);
                result.putProperty( QDataSet.UNITS, u );
                if ( arrayData.dims.length!=1 ) throw new IllegalArgumentException("multi dimensional not supported");
                for ( int j=0; j<Array.getLength(arrayData.array); j++ ) {
                    Datum d= u.createDatum( Array.get( arrayData.array, j ) );
                    result.putValue( j, d.doubleValue(u) );
                }
                result.putProperty( QDataSet.USER_PROPERTIES, getUserProperties( arrayData ) );
                return result;
            } else {
                ArrayDataSet result;
                if ( arrayData.typeCode==ReadIDLSav.TYPECODE_COMPLEX_FLOAT || arrayData.typeCode==ReadIDLSav.TYPECODE_COMPLEX_DOUBLE ) {
                    result= ArrayDataSet.wrap( arrayData.array, DataSetOps.addElement(arrayData.dims, 2), false );
                    result.putProperty( QDataSet.DEPEND_1, Schemes.complexCoordinateSystemDepend() );
                } else {
                    result= ArrayDataSet.wrap( arrayData.array, arrayData.dims, false );
                }
                if ( result instanceof SDataSet || result instanceof IDataSet || result instanceof LDataSet ) {
                    result.putProperty( QDataSet.FORMAT, "%d" );
                }
                result.putProperty( QDataSet.USER_PROPERTIES, getUserProperties( arrayData ) );
                return result;
            }
        } else if ( v instanceof Map ) { 
            throw new IllegalArgumentException("Map is not supported, select one of its tags");
        } else if ( v instanceof String ) {
            return Ops.dataset( Units.nominal().createDatum(v) );
        } else if ( v instanceof double[] && Array.getLength(v)==2 ) { //
            ArrayDataSet result= ArrayDataSet.wrap( v, new int [] { 2 }, false );
            result.putProperty( QDataSet.DEPEND_0, Schemes.complexCoordinateSystemDepend() );
            return result;
        } else if ( v instanceof float[] && Array.getLength(v)==2 ) { //
            ArrayDataSet result= ArrayDataSet.wrap( v, new int [] { 2 }, false );
            result.putProperty( QDataSet.DEPEND_0, Schemes.complexCoordinateSystemDepend() );
            return result;
        } else {
            return Ops.dataset(v);
        }
    }
    
    private QDataSet handleDs( QDataSet array, String name ) {
        int[] qube= DataSetUtil.qubeDims(array);

        QDataSet result;
        switch (qube.length) {
            case 2:
                int t= qube[0];
                qube[0]= qube[1];
                qube[1]= t;

                result= array;
                if ( result.length(0)==6 && result.length()>0 ) {
                    double yr= result.value(0,0);
                    if ( Math.floor(yr)==yr && yr>1900 && yr<2200 ) {
                        result= Ops.toTimeDataSet( Ops.slice1(result,0),
                                Ops.slice1(result,1), 
                                Ops.slice1(result,2),
                                Ops.slice1(result,3),
                                Ops.slice1(result,4),
                                Ops.slice1(result,5), null );
                    }
                }
                break;
            case 1:
                Units u= (Units)array.property(QDataSet.UNITS);
                if ( u instanceof EnumerationUnits ) { // are the strings found actually ISO8601 times?
                    if ( array.length()>8 ) {
                        String firstRec= array.slice(0).svalue();
                        if ( firstRec.length()>10 ) {
                            String yr4= firstRec.substring(0,4);
                            int year= Integer.parseUnsignedInt(yr4);
                            if ( year>1600 && year<2900 ) {
                                if ( TimeUtil.isValidTime(firstRec) ) {
                                    boolean useTimes= true;
                                    DDataSet newTime= DDataSet.createRank1( array.length() );
                                    Units timeUnits= year>2010 ? Units.us2020 : Units.us2000;
                                    newTime.putProperty(QDataSet.UNITS, timeUnits);
                                    newTime.putProperty(name, array.property(QDataSet.NAME) );
                                    for ( int i=0; i<array.length(); i++ ) {
                                        try {
                                            String value= array.slice(i).svalue();
                                            newTime.putValue( i, timeUnits.parse(value).doubleValue(timeUnits) );
                                        } catch ( ParseException ex ) {
                                            useTimes= false;
                                            break;
                                        }
                                    }
                                    if ( useTimes ) {
                                        newTime.putProperty( QDataSet.USER_PROPERTIES, array.property(QDataSet.USER_PROPERTIES) );
                                        result= newTime;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            default:
                result= array;
                break;
        }
        result= Ops.putProperty( result, QDataSet.NAME, name.replaceAll("\\.","_") );
        result= Ops.putProperty( result, QDataSet.LABEL, name );
        return result;
    }
    
    /**
     * return a list of start,stop,name positions.
     * @param f
     * @return
     * @throws IOException 
     */
    public QDataSet getTagDescriptions( File f ) throws IOException {
        ReadIDLSav reader= new ReadIDLSav();
        RandomAccessFile aFile = new RandomAccessFile(f,"r");
        FileChannel inChannel = aFile.getChannel();
        ByteBuffer fileBuffer= inChannel.map( FileChannel.MapMode.READ_ONLY,0, f.length() );
        String[] names= reader.readVarNames(fileBuffer);
        DataSetBuilder dsb= new DataSetBuilder(2,100,3);
        for ( String n: names ) {
            ReadIDLSav.TagDesc t= reader.readTagDesc(fileBuffer, n);
            if ( t instanceof ReadIDLSav.ArrayDesc ) {
                ReadIDLSav.ArrayDesc ad= (ReadIDLSav.ArrayDesc)t;
                ReadIDLSav.ArrayData arrayData= (ReadIDLSav.ArrayData)reader.readVar(fileBuffer, n);
                dsb.nextRecord( arrayData._fileOffset, arrayData._fileOffset+arrayData._lengthBytes, n );
            } else {
                dsb.nextRecord( t.fileOffset, t.fileOffset+t._lengthBytes, n );
            }
        }
        dsb.putProperty(QDataSet.RENDER_TYPE, "eventsBar>ganttMode=T" );
        return dsb.getDataSet();
    }
    
    @Override
    public QDataSet getDataSet(ProgressMonitor mon) throws Exception {
        File f= getFile( uri, mon );

        if ( getParam("locations","").equals("true") ) {
            return getTagDescriptions(f);
        }

        ReadIDLSav reader= new ReadIDLSav();        

        RandomAccessFile aFile = new RandomAccessFile(f,"r");
        FileChannel inChannel = aFile.getChannel();
        long fileSize = inChannel.size();
        ByteBuffer buffer = ByteBuffer.allocate((int) fileSize);
        int bytesRead= 0;
        while ( bytesRead<fileSize ) {
            bytesRead+= inChannel.read(buffer);
        }
        buffer.flip();
        buffer.order(ByteOrder.BIG_ENDIAN);
        String x= getParam("X","");
        String y= getParam("Y","");
        String z= getParam("Z","");
        
        String name = getParam( "arg_0", "" );
        if ( name.length()==0 ) {
            if ( x.length()==0 && y.length()==0 && z.length()==0 ) {
                throw new IllegalArgumentException("name or X must be set");
            }
        }
        
        
        QDataSet[] datas=null;

        String[] names;
        
        int ndata;
        
        if ( name.length()==0 ) {
            if ( z.length()>0 ) {
                datas = new QDataSet[3];
                //QDataSet array= getArray( reader, buffer, z );
                QDataSet array= getArray( reader, inChannel, z );
                array= handleDs( array, z );
                datas[2]= array;
            }
            if ( y.length()>0 ) {
                if ( datas==null ) datas= new QDataSet[2];
                QDataSet array= getArray( reader, buffer, y );
                array= handleDs( array, y );
                datas[1]= array;
            }
            if ( x.length()>0 ) {
                if ( datas==null ) datas= new QDataSet[1];
                QDataSet array= getArray( reader, buffer, x );
                array= handleDs( array, x );
                datas[0]= array;
            }
            ndata= datas.length;
            
        } else {
            names = name.split(",");
            datas = new QDataSet[names.length+2];
            if ( names.length>4 ) {
                throw new IllegalArgumentException("first argument can only"
                        + " contain four comma-separated names." );
            }
            
            int i=0;
            if ( x.length()>0 ) {
                if ( datas==null ) datas= new QDataSet[1];
                QDataSet array= getArray( reader, buffer, x );
                array= handleDs( array, x );
                datas[i++]= array;
            }
            
            if ( y.length()>0 ) {
                if ( datas==null ) datas= new QDataSet[2];
                QDataSet array= getArray( reader, buffer, y );
                array= handleDs( array, y );
                datas[i++]= array;
            }

            for ( int j=0; j<names.length; j++ ) {
                QDataSet array= getArray( reader, buffer, names[j]);
                array= handleDs( array, names[j] );
                datas[i++]= array;

            }
            ndata= i;
        }
        
        String sxunits= getParam("xunits","");
        if ( sxunits.length()>0 ) {
            Units xunits= Units.lookupUnits(sxunits);
            if ( datas.length>0 ) {
                datas[0]= Ops.putProperty( datas[0], QDataSet.UNITS, xunits );
            }
        }

        String syunits= getParam("yunits", "" );
        if ( syunits.length()>0 ) {
            Units yunits= Units.lookupUnits(syunits);
            int ids= 1;
            datas[ids]= Ops.putProperty( datas[ids], QDataSet.UNITS, yunits );
        }
        
        String sunits= getParam("units", "" );
        if ( sunits.length()>0 ) {
            Units units= Units.lookupUnits(sunits);
            int ids= ndata-1;
            datas[ids]= Ops.putProperty( datas[ids], QDataSet.UNITS, units );
        }
                
        switch( ndata ) {
            case 1: {
                return datas[0];
            }
            case 2: {
                return Ops.link(datas[0],datas[1]);
            }
            case 3: {
                if ( datas[2].length()!=datas[0].length() ) { // automatically transpose, since this happens with IDL often.
                    if ( datas[2].rank()==2 ) {
                        if ( datas[2].length(0)==datas[0].length() ) {
                            datas[2]= Ops.transpose(datas[2]);
                        }
                    }
                }
                return Ops.link(datas[0],datas[1],datas[2]);
            }
            case 4: {
                return Ops.link(datas[0],datas[1],datas[2],datas[3]);
            }
            default: throw new IllegalArgumentException("not supported");
        }
    }
    
}
