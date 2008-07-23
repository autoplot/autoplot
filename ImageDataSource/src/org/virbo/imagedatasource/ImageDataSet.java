/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.imagedatasource;

import java.awt.Color;
import java.awt.image.BufferedImage;
import org.virbo.dataset.AbstractDataSet;
import org.virbo.dataset.QDataSet;

/**
 *
 * @author jbf
 */
public class ImageDataSet extends AbstractDataSet {

    BufferedImage image;
    ColorOp op;
    int w,h;
    
    public interface ColorOp {
        double value( int rgb );
    }

    private class ChannelColorOp implements ColorOp {
        int mask;
        int rot;
        ChannelColorOp( int mask, int rot ) {
            this.mask= mask;
            this.rot= rot;
        }
        public double value( int rgb ) {
            return rgb & mask >> rot;
        }
    }
    
    private double log2( double d ) {
        return Math.log(d)/Math.log(2);
    }
    
    public ImageDataSet( BufferedImage image, Color mask, ColorOp op ) {
        this.image= image;
        this.h= image.getHeight();
        this.w= image.getWidth();
        if ( mask==null ) {
            this.op= op;
        } else {
            this.op= new ChannelColorOp( mask.getRGB() & 0xFFFFFF, (int)log2( Integer.lowestOneBit( mask.getRGB() ) ) );
        }
        putProperty( QDataSet.QUBE, Boolean.TRUE );
    }
    
    @Override
    public int rank() {
        return 2;
    }

    @Override
    public int length() {
        return w;
    }

    @Override
    public int length(int i) {
        return h;
    }

    @Override
    public double value(int i0, int i1) {
        return op.value( image.getRGB( i0, h-i1-1 ) );
    }
    
    
    

}
