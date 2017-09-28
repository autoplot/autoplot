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
    eventsBar,
    stackedHistogram, // Voyager PWS uses these
    vectorPlot,
    bounds,     // region colored by upper and lower bounds.
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

        if ( rt==spectrogram || rt==nnSpectrogram ) {
            if ( SemanticOps.isTableDataSet(ds) ) {
                return true;
            }
            if ( Schemes.isXYZScatter(ds) ) return true;
            if ( Schemes.isLegacyXYZScatter(ds) ) return true;
            return false;
        }

        if ( rt==hugeScatter ) {
            if ( ds.rank()==2 ) {
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
            } else if ( ds.rank()==1 ) {
                Units u= SemanticOps.getUnits(ds);
                return UnitsUtil.isIntervalOrRatioMeasurement( u );
            } else {
                return false;
            }
        }

        if ( rt==series || rt==scatter || rt==stairSteps || rt==fillToZero ) {
            if ( ds.rank()==1 ) {
                Units u= SemanticOps.getUnits(ds);
                if ( UnitsUtil.isIntervalOrRatioMeasurement(u) ) {
                    return true;
                } else {
                    return false;
                }
            } else if ( ds.rank()==2 ) {
                if ( ds.length()==0 ) return true;
                if ( SemanticOps.isBundle(ds) ) {
                    return true;
                } else {
                    return true;
                }
            } else if ( ds.rank()==3 ) {
                return true; // we can always slice repeatedly
            } else {
                return false;
            }
        }

        if ( rt==colorScatter ) {
            if ( ds.rank()==2 ) {
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
            } else if ( ds.rank()==1 ) {
                if ( ds.property(QDataSet.PLANE_0)!=null ) {
                    return true;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }

        if ( rt==digital ) {
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
        }

        if ( rt==image ) {
            return RGBImageRenderer.acceptsData(ds);
        }

        if ( rt==pitchAngleDistribution ) {
            return org.das2.graph.PitchAngleDistributionRenderer.acceptsData(ds);
        }

        if ( rt==eventsBar ) {
            return ds.rank()==2 || ds.rank()==1;
        }

        if ( rt==vectorPlot ) {
            return org.das2.graph.VectorPlotRenderer.acceptsData(ds);
        }


        if ( rt==orbitPlot ) {
            return org.das2.graph.TickCurveRenderer.acceptsData(ds);
        }

        if ( rt==contour ) {
            if ( ds.rank()==2 ) {
                return true;
            } else {
                return false;
            }
        }

        return true;
    }
}
