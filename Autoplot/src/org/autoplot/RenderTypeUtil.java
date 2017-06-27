/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot;

/**
 *
 * @author jbf
 */
public class RenderTypeUtil {
    public static boolean needsColorbar( RenderType rt ) {
        return rt==RenderType.spectrogram || rt==RenderType.nnSpectrogram
            || rt==RenderType.colorScatter || rt==RenderType.pitchAngleDistribution
            || rt==RenderType.stackedHistogram ;
    }
}
