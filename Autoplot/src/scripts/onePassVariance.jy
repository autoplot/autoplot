data= fltarr(1000000)
data[::2]= 1  # array is now [1,0,1,0,...]

ah = autoHistogram(data)

print ah
from org.virbo.dsutil import AutoHistogram

moments= AutoHistogram.moments(ah)
print moments.property( "stddev" ) 
