
package org.autoplot.imagedatasource;

import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.das2.util.monitor.ProgressMonitor;
import org.das2.qds.DataSetIterator;
import org.das2.qds.DataSetOps;
import org.das2.qds.QDataSet;
import org.das2.qds.QubeDataSetIterator;
import org.autoplot.datasource.URISplit;
import org.autoplot.datasource.DataSourceFormat;

/**
 * Format data to RGB images, or ARGB images.
 * Formatter presumes data is:<ul>
 * <li>(m,n,4) for ARGB 
 * <li>(3,m,n) RGB.
 * <li>(m,n,3) RGB.
 * </ul>
 * When data is (m,n,4) then ds[:,:,0] should be the alpha channel,
 * [:,:,1] should be the red channel, and so on.
 * @author jbf
 */
public class ImageDataSourceFormat implements DataSourceFormat {

    @Override
    public void formatData(String uri, QDataSet data, ProgressMonitor mon) throws Exception {
        BufferedImage im;

        QDataSet r,g,b;
        QDataSet alpha=null;

        int h, w;
        if ( data.length()==3 ) {
            im= new BufferedImage( data.length(0), data.length(0,0), BufferedImage.TYPE_INT_RGB );
            r= data.slice(0);
            g= data.slice(1);
            b= data.slice(2);
            w= data.length(0);
            h= data.length(0,0);

        } else if ( data.length(0,0)==4 ) {
            im= new BufferedImage( data.length(), data.length(0), BufferedImage.TYPE_INT_ARGB);
            alpha= DataSetOps.slice2( data, 0 );
            r= DataSetOps.slice2( data, 1 );
            g= DataSetOps.slice2( data, 2 );
            b= DataSetOps.slice2( data, 3 );
            
            w= data.length();
            h= data.length(0);

        } else {
            im= new BufferedImage( data.length(), data.length(0), BufferedImage.TYPE_INT_RGB);
            r= DataSetOps.slice2( data, 0 );
            g= DataSetOps.slice2( data, 1 );
            b= DataSetOps.slice2( data, 2 );
            w= data.length();
            h= data.length(0);
        }

        URISplit split= URISplit.parse(uri);

        DataSetIterator it= new QubeDataSetIterator(data);
        boolean warn= true;
        while ( it.hasNext() ) {
            it.next();
            double v= it.getValue(data);
            if ( warn && ( v<0 || v>=256. ) ) {
                System.err.println("element out of range 0-255: "+v );
                warn= false;
            }
        }

        if ( alpha==null ) {
            for ( int i=0; i<w; i++ ) {
                for ( int j=0; j<h; j++ ) {
                    im.setRGB( i, h-j-1, 
                            (int)(r.value(i,j))*256*256 
                            + (int)(g.value(i,j))*256 
                            + (int)b.value(i,j) );
                }
            }
        } else {
            for ( int i=0; i<w; i++ ) {
                for ( int j=0; j<h; j++ ) {
                    im.setRGB( i, h-j-1, 
                            (int)(alpha.value(i,j)) *256*256*256 
                            + (int)(r.value(i,j))*256*256 
                            + (int)(g.value(i,j))*256
                            + (int)(b.value(i,j)) );
                }
            }

        }

        int i= split.file.lastIndexOf(".");
        String ext= split.file.substring(i+1);

        if ( ImageIO.write( (RenderedImage)im, ext, new File( split.resourceUri ) ) ) {

        } else {
            throw new IOException("unable to find writer for "+ext);
        }

    }

    @Override
    public boolean canFormat(QDataSet ds) {
        return ds.rank()==3 && ( ds.length()<5 || ds.length(0,0)<5 );
    }

    @Override
    public String getDescription() {
        return "Image Format";
    }

}
