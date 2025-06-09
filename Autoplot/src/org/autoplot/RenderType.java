package org.autoplot;

import org.das2.datum.Units;
import org.das2.datum.UnitsUtil;
import org.das2.graph.RGBImageRenderer;
import org.das2.qds.DataSetOps;
import org.das2.qds.QDataSet;
import org.das2.qds.SemanticOps;
import org.das2.qds.examples.Schemes;

public enum RenderType {
    spectrogram,
    nnSpectrogram,
    hugeScatter,
    series,
    scatter,
    colorScatter,
    stairSteps,
    fillToZero,
    digital,
    image,
    pitchAngleDistribution,
    polar,  // spectograms and line plots in polar coordinates
    eventsBar,
    stackedHistogram, // Voyager PWS uses these
    vectorPlot,
    bounds,     // region colored by upper and lower bounds.
    internal,   // user-defined or unrecognized Renderer
    orbitPlot,  // call-outs with time vs position
    contour;

    /**
     * return true if the render type can make a reasonable rendering of the data.
     * If the render type is not recognized, just return true.  This was introduced to
     * constrain the options of the user to valid entries.
     *
     * Note this is called on the event thread and must be implemented so that 
     * evaluation takes a trivial amount of time.
     * 
     * @param rt
     * @param ds
     * @return
     */
    public static boolean acceptsData( RenderType rt, QDataSet ds ) {

        if ( null == rt )  return true;
        
        switch (rt) {
            case spectrogram:
            case nnSpectrogram:
                if ( SemanticOps.isTableDataSet(ds) ) {
                    return true;
                }
                if ( Schemes.isXYZScatter(ds) ) return true;
                if ( Schemes.isLegacyXYZScatter(ds) ) return true;
                return false;
            case hugeScatter:
            switch (ds.rank()) {
                case 2:
                    if ( SemanticOps.isBundle(ds) ) {
                        for ( int i=0; i<ds.length(0); i++ ) {
                            QDataSet ds1= DataSetOps.unbundle(ds,i);
                            if ( !UnitsUtil.isIntervalOrRatioMeasurement( SemanticOps.getUnits(ds1) ) ) {
                                return false;
                            }
                        }
                        return true;
                    } else {
                        return  SemanticOps.isRank2Waveform(ds);
                    }
                case 1:
                    Units u= SemanticOps.getUnits(ds);
                    return UnitsUtil.isIntervalOrRatioMeasurement( u );
                default:
                return false;
            }

            case series:
            case scatter:
            case stairSteps:
            case fillToZero:
            switch (ds.rank()) {
                case 1:
                    Units u= SemanticOps.getUnits(ds);
                    return UnitsUtil.isIntervalOrRatioMeasurement(u);

                case 2:
                    if ( ds.length()==0 ) return true;
                    if ( SemanticOps.isBundle(ds) ) {
                        return true;
                    } else {
                        return SemanticOps.isRank2Waveform(ds);
                    }
                case 3:
                    return true; // we can always slice repeatedly
                default:
                return false;
            }

            case colorScatter:
            switch (ds.rank()) {
                case 2:
                    if ( SemanticOps.isBundle(ds) ) {
                        for ( int i=0; i<ds.length(0); i++ ) {
                            QDataSet ds1= DataSetOps.unbundle(ds,i);
                            if ( !UnitsUtil.isIntervalOrRatioMeasurement( SemanticOps.getUnits(ds1) ) ) {
                                return false;
                            }
                        }
                        return true;
                    } else {
                        return false;
                    }
                case 1:
                    return ( ds.property(QDataSet.PLANE_0)!=null );
                default:
                return false;
            }

            case digital:
                if ( SemanticOps.isBundle(ds) ) {
                    for ( int i=0; i<ds.length(0); i++ ) {
                        QDataSet ds1= DataSetOps.unbundle(ds,i);
                        if ( !UnitsUtil.isIntervalOrRatioMeasurement( SemanticOps.getUnits(ds1) ) ) {
                            return false;
                        }
                    }
                    return true;
                }
                return true;
            case image:
                return RGBImageRenderer.acceptsData(ds);
            case pitchAngleDistribution:
                return org.das2.graph.PitchAngleDistributionRenderer.acceptsData(ds);
            case polar:
                return org.das2.graph.PolarPlotRenderer.acceptsData(ds);
            case eventsBar:
                return ds.rank()==2 || ds.rank()==1;
            case vectorPlot:
                return org.das2.graph.VectorPlotRenderer.acceptsData(ds);
            case orbitPlot:
                return org.das2.graph.TickCurveRenderer.acceptsData(ds);
            case contour:
                return ds.rank()==2;
            case stackedHistogram:
                return ds.rank()==2;
            case bounds:
                return  Schemes.isBoundingBox(ds) 
                        || Schemes.isArrayOfBoundingBox(ds) 
                        || ( Schemes.isTrajectory(ds) || ( ds.rank()>1 && ds.length(0)==2 ) )
                        || Schemes.isRank2Bins(ds);
            default:
                return true;
        }
    }
}
