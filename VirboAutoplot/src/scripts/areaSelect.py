from org.das2.components import DataPointRecorder
dpr= DataPointRecorder()

addTab( 'digitizer', dpr )

## add the box selector to the current plot #########################
plot= dom.controller.plot.controller.dasPlot

from org.das2.event import BoxSelectorMouseModule,CrossHairRenderer

mm= BoxSelectorMouseModule.create( plot, 'digitizer' )
mm.keyEvents= True  # keystoke will accept and document the gesture
mm.releaseEvents= False

def boxSelected( event ):
   x= java.util.HashMap()
   x['dx']= event.getXRange().width()
   x['dy']= event.getYRange().width()
   x['key']= event.getPlane('keyChar')
   dpr.addDataPoint( event.getXRange().min(), event.getYRange().min(), x )

mm.BoxSelected=boxSelected

plot.addMouseModule(mm)
