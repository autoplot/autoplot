dom= getDocumentModel()

canvas= dom.controller.canvas
w= canvas.width
h= canvas.height

if ( canvas.fitted==False ): print "canvas must be fitted"

dasPlot= dom.controller.plot.controller.dasPlot

rendAt= zeros( w, h )

for i in range(w):
   print i, w
   for j in range(h):
      rendAt[i,h-j-1]= dasPlot.findRendererAt(i,j)

formatDataSet( rendAt, 'file:///tmp/rendAt.qds?binary=1' )

