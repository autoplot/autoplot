# Set ds to a data set that is rank 1 signal that is a function of time.  The
# dataset will be plotted in the top plot, and the fft spectrum timeseries will
# be plotted below.  Draw a box to hear the waveform within the selected time.
#

import org.das2.graph

ds= getDataSet( 'vap+wav:file:///home/jbf/heartBeats.15weeks.wav' )
#ds= getDataSet( 'vap+jyds:file:///home/jbf/project/autoplot/jyds/auralizeHfwr.jyds' )
#ds= getDataSet('vap:file:/media/mini/eg/content/wav/20090818 204141.wav')

plot( 0, ds )
plot( 1, fftWindow( ds, 256 ) )
dom.plots[1].zaxis.log=True

def boxSelected(event):
    dep0= ds.property( QDataSet.DEPEND_0 )
    u= dep0.property( QDataSet.UNITS )
    tmin= event.getXRange().min().doubleValue( u )
    tmax= event.getXRange().max().doubleValue( u )

    from org.virbo.dataset import DataSetUtil, DataSetOps

    imin= DataSetUtil.closest( dep0, tmin, -1 )
    imax= DataSetUtil.closest( dep0, tmax, imin )

    play= DataSetOps.trim( ds, imin, imax-imin )

    a= org.das2.graph.Auralizor( play )

    a.playSound()
    formatDataSet( play, 'file:/tmp/auralizeTool.wav' )
    
plot= dom.plots[0].controller.dasPlot
from org.das2.event import BoxSelectorMouseModule
mm= BoxSelectorMouseModule.create( plot, 'Auralize' )
mm.BoxSelected=boxSelected
plot.mouseAdapter.primaryModule=mm

plot= dom.plots[1].controller.dasPlot
from org.das2.event import BoxSelectorMouseModule
mm= BoxSelectorMouseModule.create( plot, 'Auralize' )
mm.BoxSelected=boxSelected
plot.mouseAdapter.primaryModule=mm
