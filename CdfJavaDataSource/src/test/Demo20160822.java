
package test;

import gov.nasa.gsfc.spdf.cdfj.CDFDataType;
import gov.nasa.gsfc.spdf.cdfj.CDFException;
import gov.nasa.gsfc.spdf.cdfj.CDFWriter;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;

/**
 * This was used to debug a case where a file lock prevented writing multiple 
 * variables to a CDF file on Windows.  
 * @author Jeremy Faden
 */
public class Demo20160822 {
    
    public static void main( String[] args ) throws CDFException.WriterError, IOException, CDFException.ReaderError {
        ByteBuffer v1= ByteBuffer.allocate(800);        
        v1.order(ByteOrder.LITTLE_ENDIAN);
        for ( int i=101; i<201; i++ ) v1.putDouble( i );
        v1.flip();
        ByteBuffer v2= ByteBuffer.allocate(800);        
        v2.order(ByteOrder.LITTLE_ENDIAN);
        for ( int i=201; i<301; i++ ) v2.putDouble( i );
        v2.flip();
        ByteBuffer v3= ByteBuffer.allocate(800);        
        v3.order(ByteOrder.LITTLE_ENDIAN);
        for ( int i=301; i<401; i++ ) v3.putDouble( i );
        v3.flip();
        ByteBuffer v4= ByteBuffer.allocate(800);        
        v4.order(ByteOrder.LITTLE_ENDIAN);
        for ( int i=401; i<501; i++ ) v4.putDouble( i );
        v4.flip();
        
        File tempFile= new File("c:/tmp/TEMP.xxx.cdf");
        File targetFile= new File( "c:/tmp/TEMP.cdf" );
                
        CDFWriter cdf= new CDFWriter( false );        
        cdf.defineVariable("v1", CDFDataType.DOUBLE, new int[] {} );
        cdf.addData( "v1", v1 );
        cdf.defineVariable("v2", CDFDataType.DOUBLE, new int[] {} );
        cdf.addData("v2",v2);
        cdf.write( tempFile.toString() );
        
        System.err.println( "tempFile.delete()=" + tempFile.delete() );
        
        // CDF should be closed at this point, but file still has lock.        
        if ( !( tempFile.renameTo( targetFile ) ) ) {
            if ( tempFile.exists() ) tempFile.delete();
            Files.copy( tempFile.toPath(), targetFile.toPath() );
            tempFile.deleteOnExit();
            System.err.println("file rename returns false, used copy instead");
        }
        
        cdf= new CDFWriter( targetFile.toString(), false );
        cdf.defineVariable("v3", CDFDataType.DOUBLE, new int[] {} );
        cdf.addData("v3",v3);
        cdf.write( targetFile.toString() );

        cdf= new CDFWriter( targetFile.toString(), false );
        cdf.defineVariable("v4", CDFDataType.DOUBLE, new int[] {} );
        cdf.addData("v4",v4);
        cdf.write( targetFile.toString() );
        
        
    }
}
