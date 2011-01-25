from org.virbo.dsops.Ops import *
from org.virbo.jythonsupport.JythonOps import *
from org.virbo.jythonsupport.Util import *
from org.virbo.dataset import QDataSet
from org.virbo.dsutil.BinAverage import *

from org.das2.datum import DatumRange, Units, DatumRangeUtil, TimeUtil
from java.net import URL
from org.das2.datum import TimeParser

# security concerns
#from java.io import File
#from org.das2.util.filesystem import FileSystem
#from org.das2.fsm import FileStorageModel
from org.virbo.datasource.DataSetURI import getFile
import java
import org
# end, security concerns.

params= dict()

def getParam( x, default, title='' ):
  if ( type(x).__name__=='int' ):
     x= 'arg_%d' % i

  if params.has_key(x):
     return params[x]
  else:
     return default
