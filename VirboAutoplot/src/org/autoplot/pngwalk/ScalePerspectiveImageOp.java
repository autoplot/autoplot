/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.pngwalk;

import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ColorModel;

/**
 *
 * @author jbf
 */
class ScalePerspectiveImageOp implements BufferedImageOp {

    final int w;
    final int h;
    // subsample
    final int ssx;
    final int ssy;
    // output offset
    final int x1;
    // output offset
    final int y1;
    final double p;
    int nw;
    int nh;
    private int w1;
    private int h1;
    private int rh1; // reflection height
    private boolean reflect; // enable/disable reflections

    /**
     *
     * @param w original width
     * @param h original height
     * @param x1 upper left hand corner
     * @param y1 upper left hand corner ( when p=0 )
     * @param w1 new width
     * @param h1 new height at the middle
     * @param rh1 extra height for the reflection
     * @param p rockiness, similar to the tan of the angle.  0. means no perspective.
     */
    public ScalePerspectiveImageOp(int w, int h, int x1, int y1, int w1, int h1, int rh1, double p, boolean reflect) {
        this.w = w;
        this.h = h;
        this.w1 = w1;
        this.h1= h1;
        this.reflect= reflect;
        this.rh1= rh1 * h / h1;
        this.x1= x1;
        this.y1= y1;
        this.p = p;
        this.nw = (int) w1;
        this.nh = (int) h1 + rh1;
        this.ssx= Math.max( 1, w / w1 / 2 );
        if ( p!=0 ) {
            this.ssy= 1;
        } else {
            this.ssy= Math.max( 1, h / h1 );
        }
    }

    Shape getOutline( double x, double y ) {
        GeneralPath path= new GeneralPath();
        Point2D dst= new Point2D.Float();
        dst= this.getPoint2D( new Point2D.Float(0,0), dst);
        path.moveTo((float)dst.getX(), (float)dst.getY());
        dst= this.getPoint2D( new Point2D.Float(w,0), dst);
        path.lineTo((float)dst.getX(), (float)dst.getY());
        dst= this.getPoint2D( new Point2D.Float(w,h), dst);
        path.lineTo((float)dst.getX(), (float)dst.getY());
        dst= this.getPoint2D( new Point2D.Float(0,h), dst);
        path.lineTo((float)dst.getX(), (float)dst.getY());
        dst= this.getPoint2D( new Point2D.Float(0,0), dst);
        path.lineTo((float)dst.getX(), (float)dst.getY());
        path.transform( AffineTransform.getTranslateInstance(x,y) );
        return path;
    }

    private final int index(int i, int j) {
        return Math.max(0, Math.min(i + j * nw, nw * nh - 1));
    }

    public BufferedImage filter(BufferedImage src, BufferedImage dest) {

        dest = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);

        int[] rr = new int[nw * nh];
        int[] gg = new int[nw * nh];
        int[] bb = new int[nw * nh];
        int[] aa = new int[nw * nh];
        int[] nn = new int[nw * nh];

        final int AVG = 0;

        boolean hasBg = true;
        int bgColor = src.getRGB(0, 0);
        if (src.getRGB(0, 0) != bgColor) hasBg = false;
        if (src.getRGB(0, h - 1) != bgColor) hasBg = false;
        if (src.getRGB(w - 1, 0) != bgColor) hasBg = false;
        if (src.getRGB(w - 1, h - 1) != bgColor) hasBg = false;

        // make sure that all the borders are included in the averages.
        int[] jj= new int[h/ssy+1];
        for ( int k1=0; k1<h/ssy; k1++ ) jj[k1]= k1*ssy;
        jj[h/ssy]= h-1;

        int[] ii= new int[w/ssx+1];
        for ( int k2=0; k2<w/ssx; k2++ ) ii[k2]= k2*ssx;
        ii[w/ssx]= w-1;

        for ( int k1 = 0; k1 < jj.length; k1++ ) {
            int j= jj[k1];
            for ( int k2 = 0; k2<ii.length; k2++ ) {
                int i= ii[k2];
                
                int color = src.getRGB(i,j);

                int i1 = x1 + (int) (i * w1 / w );
                double pp = p>0 ? ( i * p)  : ( ( i-w ) *p );
                double hai= h1 - 2 * pp;
                int j1= y1 + (int) ( pp + j * ( hai / h ) );
                //int j1 = y1 + (int) (((j * (1000 - pp * 2)) + pp * h) * h1 / h / 1000 );
                int didx = index(i1, j1);
                int weight;
                if (hasBg) {
                    weight = color == bgColor ? 0 : 255;
                } else {
                    weight = (color >> 24 & 0xff);
                }

                rr[didx] += weight * ((color >> 16 & 0xff) - AVG);
                gg[didx] += weight * ((color >> 8 & 0xff) - AVG);
                bb[didx] += weight * ((color >> 0 & 0xff) - AVG);
                aa[didx] += weight;
                nn[didx] += 255;

                if ( this.reflect ) {

                    j1= (int) ( pp + hai + (h-j) * ( hai / h ) );
                    //j1 = ( y1 + (int) (((j * (1000 - pp * 2)) + pp * h) * h1 / h / 1000 ) ) + j/10;

                    didx = index(i1, j1);

                    weight = (color >> 24 & 0xff);

                    double	ww1 = Math.max( 1,weight * ( ( (0.4 ) / (rh1) ) *(j - (h-rh1) ) ) );


                    rr[didx] += 255*((color >> 16 & 0xff) - AVG);
                    gg[didx] += 255*((color >> 8 & 0xff) - AVG);
                    bb[didx] += 255*((color >> 0 & 0xff) - AVG);
                    aa[didx] += ww1;
                    nn[didx] += 255;

                }
            }
        }

        for (int i = 0; i < nw; i++) {
            for (int j = 0; j < nh; j++) {
                int didx = index(i, j);
                int n = nn[didx];
                if (n > 0) {
                    int weight = aa[didx] * 255 / nn[didx];
                    if (hasBg) {
                        int color;
                        if (weight == 0) {
                            color = bgColor;
                        } else {
                            color = (weight << 24) + ((rr[didx] / n + AVG) << 16) + ((gg[didx] / n + AVG) << 8) + (bb[didx] / n + AVG);
                        }
                        dest.setRGB(i, j, color);
                    } else {
                        int color = (weight << 24) + ((rr[didx] / n + AVG) << 16) + ((gg[didx] / n + AVG) << 8) + (bb[didx] / n + AVG);
                        dest.setRGB(i, j, color);
                    }
                }
            }
        }

        return dest;
    }

    public Rectangle2D getBounds2D(BufferedImage src) {
        return new Rectangle2D.Double(0, 0, src.getWidth(), src.getHeight());
    }

    public BufferedImage createCompatibleDestImage(BufferedImage src, ColorModel destCM) {
        return new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
    }

    public Point2D getPoint2D(Point2D srcPt, Point2D dstPt) {

        double i1 = x1 + (int) (srcPt.getX() * w1 / w );
        double pp = (int) Math.round( p>0 ? ( srcPt.getX() * p)  : ( ( srcPt.getX()-w ) *p ) );
        double j1 = y1 + (int) (((srcPt.getY() * (1000 - pp * 2)) + pp * h) * h1 / h / 1000 );

        if (dstPt == null) {
            dstPt = new Point2D.Double(i1, j1);
        } else {
            dstPt.setLocation(i1, j1);
        }
        return dstPt;
    }

    public RenderingHints getRenderingHints() {
        return new RenderingHints(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }
}
