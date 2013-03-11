from org.virbo.dsops.Ops import *
from org.virbo.jythonsupport.JythonOps import *
from org.virbo.jythonsupport.Util import *
from org.virbo.dataset import QDataSet
from org.virbo.dsutil.BinAverage import *
from org.virbo.dsutil import DataSetBuilder

from org.das2.datum import DatumRange, Units, DatumRangeUtil, TimeUtil
from java.net import URL
from org.das2.datum import TimeParser

# security concerns
#from java.io import File
#from org.das2.util.filesystem import FileSystem
#from org.das2.fsm import FileStorageModel
from org.virbo.datasource.DataSetURI import getFile
from org.virbo.datasource.DataSetURI import downloadResourceAsTempFile
import java
import org
# end, security concerns.

# jython is tricky with single-jar releases, and using star imports to find classes doesn't work.
import org.das2
import org.das2.dataset
import org.das2.dataset.NoDataInIntervalException
import org.das2.graph

params= dict()

import operator.isNumberType

def getParam( x, default, title='', enums='' ):
  if ( type(x).__name__=='int' ):
     x= 'arg_%d' % i
  if ( x=='resourceUri' ):
     print 'resourceURI may be used, but resourceUri cannot.'
     x= 'resourceURI'
  if type(params) is dict:
     if params.has_key(x): 
         t= type(default)  # Ed demonstrated this allows some pretty crazy things, e.g. open file, so be careful...
         return t(params[x])
     else:
         return default
  else:
     print 'in jython script, variable params was overriden.'
     return default

# this will become internal to Autoplot
import java.lang.Thread, java.lang.Runnable

class InvokeLaterRunnable( java.lang.Runnable ):
   
   def __init__( self, fun, args ):
      self.fun= fun
      self.args= args
      
   def run( self ):
      f= self.fun      
      l= len( self.args )
      if ( l==0 ):
         f( )
      elif ( l==1 ):
         f( self.args[0] )
      elif ( l==2 ):
         f( self.args[0], self.args[1] )
      elif ( l==3 ):
         f( self.args[0], self.args[1], self.args[2] )      
      elif ( l==4 ):
         f( self.args[0], self.args[1], self.args[2], self.args[3] )      
      

      
def invokeLater( fun, *args ):
   if ( len(args)>4 ):
       raise Exception( 'invokeLater can only handle up to 4 arguments' )
   r= InvokeLaterRunnable( fun, args )
   java.lang.Thread(r,'invokeLater').start()
