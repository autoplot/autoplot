ds= getDataSet('vap:file:/media/mini/eg/content/avi/out.wav')
plot( 0, ds )
plot( 1, fftWindow( ds, 256 ) )
dom.plots[1].zaxis.log=True

def boxSelected(event):
    dep0= ds.property( QDataSet.DEPEND_0 )
    u= dep0.property( QDataSet.UNITS )
    tmin= event.getXRange().min().doubleValue( u )
    tmax= event.getXRange().max().doubleValue( u )

    from org.virbo.dataset import DataSetUtil, DataSetOps, VectorDataSetAdapter

    imin= DataSetUtil.closest( dep0, tmin, -1 )
    imax= DataSetUtil.closest( dep0, tmax, imin )

    play= DataSetOps.trim( ds, imin, imax-imin )

    a= org.das2.graph.Auralizor( VectorDataSetAdapter.create(play) )

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
