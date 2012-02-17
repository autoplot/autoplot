from org.virbo.dsops.Ops import *
from org.virbo.jythonsupport.JythonOps import *
from org.virbo.jythonsupport.Util import *
from org.virbo.dataset import QDataSet
from org.virbo.dsutil.BinAverage import *
from org.virbo.dsutil import DataSetBuilder

from org.das2.datum import DatumRange, Units, DatumRangeUtil, TimeUtil
from java.net import URL
from org.das2.datum import TimeParser
import org.das2.graph

# security concerns
#from java.io import File
#from org.das2.util.filesystem import FileSystem
#from org.das2.fsm import FileStorageModel
from org.virbo.datasource.DataSetURI import getFile
from org.virbo.datasource.DataSetURI import downloadResourceAsTempFile
import java
import org
# end, security concerns.

params= dict()

import operator.isNumberType

def getParam( x, default, title='', enums='' ):
  if ( type(x).__name__=='int' ):
     x= 'arg_%d' % i
  if ( x=='resourceUri' ):
     print 'resourceURI may be used, but resourceUri cannot.'
     x= 'resourceURI'
  if params.has_key(x):
     if ( operator.isNumberType(default) ): #TODO: complex
         return float(params[x])
     else:
         return str(params[x])
  else:
     return default
