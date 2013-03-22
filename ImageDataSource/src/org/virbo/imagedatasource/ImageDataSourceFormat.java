/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.imagedatasource;

import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.dataset.DataSetIterator;
import org.virbo.dataset.DataSetOps;
import org.virbo.dataset.QDataSet;
import org.virbo.dataset.QubeDataSetIterator;
import org.virbo.datasource.URISplit;
import org.virbo.datasource.DataSourceFormat;

/**
 * formatter assumes data is (m,n,3) or (3,m,n) RGB.
 * or [m,n,4] where ds[:,:,3] is the alpha channel.
 * @author jbf
 */
public class ImageDataSourceFormat implements DataSourceFormat {

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
            r= DataSetOps.slice2( data, 0 );
            g= DataSetOps.slice2( data, 1 );
            b= DataSetOps.slice2( data, 2 );
            alpha= DataSetOps.slice2( data, 3 );
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
                    im.setRGB( i, h-j-1, (int)( r.value(i,j)*256*256 + g.value(i,j)*256 + b.value(i,j) ) );
                }
            }
        } else {
            for ( int i=0; i<w; i++ ) {
                for ( int j=0; j<h; j++ ) {
                    im.setRGB( i, h-j-1, (int)( alpha.value(i,j)*256*256*256 + r.value(i,j)*256*256 + g.value(i,j)*256 + b.value(i,j) ) );
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

    public boolean canFormat(QDataSet ds) {
        return ds.rank()==3 && ( ds.length()<5 || ds.length(0,0)<5 );
    }

    public String getDescription() {
        return "Image Format";
    }

}
