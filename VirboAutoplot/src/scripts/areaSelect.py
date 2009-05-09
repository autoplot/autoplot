from org.das2.components import DataPointRecorder
dpr= DataPointRecorder()

addTab( 'digitizer', dpr )

## add the box selector #########################
plot= getApplicationModel().plot

from org.das2.event import BoxSelectorMouseModule,CrossHairRenderer

mm= BoxSelectorMouseModule.create( plot, 'digitizer' )
mm.keyEvents= True
mm.releaseEvents= False

def boxSelected( event ):
   dpr.addDataPoint( event.getFinishX(), event.getFinishY(), None )

mm.BoxSelected=boxSelected

plot.addMouseModule(mm)
