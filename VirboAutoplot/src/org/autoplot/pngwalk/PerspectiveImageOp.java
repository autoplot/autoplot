/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.autoplot.pngwalk;

import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ColorModel;

/**
 *
 * @author jbf
 */
class PerspectiveImageOp implements BufferedImageOp {

    final int w;
    final int h;
    final double sx;
    final double sy; //scale y
    final double p;
        int nw;
        int nh;
    public PerspectiveImageOp(int w, int h, double sx, double sy, double p ) {
        this.w = w;
        this.h = h;
        this.sx= sx;
        this.sy= sy;
        this.p = p;
        this.nw= (int) ( w*sx );
        this.nh= (int) ( w*sy );
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

        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int color = src.getRGB(i, j);

                int i1 = (int)( i / sx );
                int pp = (int) Math.round( ( i-w/2 ) * p );
                int j1 = (int)( ((j * (1000 - pp * 2)) + pp * h) / 1000 * sy );
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
            }
        }

        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
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
        double i1 = srcPt.getX() / 20;
        double pp = ( srcPt.getX()-w/2 ) * p;
        double j1 = ((srcPt.getY() * (1000 - pp * 2)) + pp * h) / 1000;
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
