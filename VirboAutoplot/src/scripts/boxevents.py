## create the data set of image timetags #########################
dir= "file:/media/mini/data.backup/batpics/"
files= list( dir+'*.jpg' )

timeparser= TimeParser.create("%Y%m%d%H%M%S");
timeparser.format( Units.us2000.createDatum(0), None )

from org.virbo.dsutil import DataSetBuilder

builder= DataSetBuilder( 1, 1000 )

for s in files:
   timestr= s[3:17]
   d= timeparser.parse(timestr).getTime( Units.us2000 )
   builder.putValue( -1, d )
   builder.nextRecord()

builder.putProperty( QDataSet.UNITS, Units.us2000 )

ds= builder.getDataSet() 
plot( builder.getDataSet() )

## image component ###################
from javax.swing import JFrame, JLabel, ImageIcon

imageFrame= JFrame( title='image displayer', defaultCloseOperation=JFrame.HIDE_ON_CLOSE )

from javax.imageio import ImageIO
from java.net import URL

icon= ImageIcon( URL( dir + files[0] ) )

imageLabel= JLabel( icon )
imageFrame.add( imageLabel )
imageFrame.pack()
imageFrame.visible= True

def showImage( file ):
   icon= ImageIcon( URL( file ) )
   imageLabel.icon= icon
   imageFrame.pack()
   imageFrame.visible= True

def showImages( showFiles ):
    from java.lang.Thread import sleep
    for s in showFiles:
       file= dir + s
       showImage( file )
       print file
       sleep(10)
   

## add the box selector #########################
plot= dom.plots[0].controller.dasPlot

from org.das2.event import BoxSelectorMouseModule

mm= BoxSelectorMouseModule.create( plot, 'show images' )

def boxSelected(event):
    tmin= event.getYRange().min().doubleValue( Units.us2000 )
    tmax= event.getYRange().max().doubleValue( Units.us2000 )
    file= None
    from java.util import ArrayList
    showFiles= ArrayList()

    for s in files:
      timestr= s[3:17]
      d= timeparser.parse(timestr).getTime( Units.us2000 )
      if ( d>tmin and d<tmax  ):
         file = s
         showFiles.add( s )
    peekAt(showImages)
    invokeSometime( showImages, showFiles )

    
mm.BoxSelected=boxSelected

plot.addMouseModule(mm)
