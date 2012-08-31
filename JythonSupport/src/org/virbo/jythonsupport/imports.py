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

