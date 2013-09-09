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
_paramMap= dict()
_paramSort= []

import operator.isNumberType

def getParam( name, deflt, doc='', enums='' ):
  print '-- paramMap --'
  print _paramMap
  _paramMap[ name ]= [ name, deflt, doc, enums ]
  _paramSort.append( name )
  if ( type(name).__name__=='int' ):
     name= 'arg_%d' % i
  if type(params) is dict:
     if params.has_key(name): 
         t= type(deflt)  # Ed demonstrated this allows some pretty crazy things, e.g. open file, so be careful...
         return t(params[name])
     else:
         return deflt
  else:
     print 'in jython script, variable params was overriden.'
     return deflt

# invokeLater command is a scientist-friendly way to define a function that 
# is called on a different thread.
import java.lang.Thread, java.lang.Runnable
class InvokeLaterRunnable( java.lang.Runnable ):
   def __init__( self, fun, args, kw ):
      self.fun= fun
      self.args= args
      self.kw= kw
   def run( self ):
      self.fun( *self.args, **self.kw )
def invokeLater( fun, *args, **kw ):
   r= InvokeLaterRunnable( fun, args, kw )
   java.lang.Thread(r,'invokeLater').start()
