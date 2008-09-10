from org.das2.graph import ContoursRenderer, SpectrogramRenderer;
from javax.beans.binding import *;

createGui()

model = getApplicationModel()

model.autoOverview= False

contoursRenderer= ContoursRenderer()
contoursRenderer.contours="1,3,5,10,30,50"
contoursRenderer.drawLabels=True

model.plot.addRenderer( contoursRenderer )

specRend= model.plot.getRenderer(1)

specRend.rebinner= SpectrogramRenderer.RebinnerEnum.nearestNeighbor
model.isotropic= True

print specRend

bind( specRend, "dataSet", contoursRenderer, "dataSet" )
bind( specRend, "active", contoursRenderer, "active" )

model.plot.drawGrid= True
model.plot.drawMinorGrid= True
