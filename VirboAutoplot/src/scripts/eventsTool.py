## the events recorder #####
from org.das2.components import DataPointRecorder
dpr= DataPointRecorder()

def propertyChange(evt):
  print evt.newValue

dpr.propertyChange= propertyChange

addTab( 'digitizer', dpr )

## add the box selector #########################
dom= getDocumentModel()
plot= dom.plots[0].controller.dasPlot

from org.das2.event import BoxSelectorMouseModule

mm= BoxSelectorMouseModule.create( plot, 'events tool' )

from java.lang import Runtime

def boxSelected(event):
    tmin= event.getXRange().min()
    tmax= event.getXRange().max()
    dpr.addDataPoint( tmin, tmax.subtract(tmin), None )
    
mm.BoxSelected=boxSelected

plot.dasMouseInputAdapter.primaryModule= mm
