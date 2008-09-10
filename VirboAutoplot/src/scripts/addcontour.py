from org.das2.graph import ContoursRenderer, SpectrogramRenderer;
from javax.beans.binding import *;

createGui()

model = getApplicationModel()

model.autoOverview= False

contoursRenderer= ContoursRenderer()
contoursRenderer.contours="0.5,1,3,5,10,30,50"
contoursRenderer.drawLabels=True
contoursRenderer.color= Color.WHITE

model.plot.addRenderer( contoursRenderer )

specRend= model.plot.getRenderer(1)

specRend.rebinner= SpectrogramRenderer.RebinnerEnum.nearestNeighbor
model.isotropic= True

print specRend

bind( specRend, "dataSet", contoursRenderer, "dataSet" )
bind( specRend, "active", contoursRenderer, "active" )

model.plot.drawGrid= False
model.plot.drawMinorGrid= False

model.colorBar.setFlipLabel(True)
model.colorBar.setFillColor(Color.BLACK)

from org.das2.event import AngleSelectionDragRenderer, BoxSelectorMouseModule
from org.das2.components import AngleSpectrogramSlicer

plot= model.plot

aSlicer= AngleSpectrogramSlicer.createSlicer(plot,  specRend);
arend=  AngleSelectionDragRenderer();
bsel=  BoxSelectorMouseModule( plot, plot.getXAxis(), plot.getYAxis(), specRend,
                arend,
                "Angle Slice" );
bsel.setDragEvents(True);        
bsel.addBoxSelectionListener( aSlicer );  

model.plot.addMouseModule( bsel )

print bsel

