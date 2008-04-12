from org.virbo.autoplot import *;
from edu.uiowa.physics.pw.das.graph import ContoursRenderer, SpectrogramRenderer;
from javax.beans.binding import *;

model = ApplicationModel()
app = AutoPlotMatisse(model)

model.autoOverview= False

contoursRenderer= ContoursRenderer()
contoursRenderer.contours="1,3,10,30,50"

model.plot.addRenderer( contoursRenderer )

specRend= model.plot.getRenderer(1)

specRend.rebinner= SpectrogramRenderer.RebinnerEnum.nearestNeighbor
model.isotropic= True

print specRend
bc= BindingContext()
bc.addBinding( specRend, "${dataSet}", contoursRenderer, "dataSet", [] )
bc.addBinding( specRend, "${active}", contoursRenderer, "active", [] )

bc.bind()

app.setVisible(True);

