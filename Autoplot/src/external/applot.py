# Do not use this.  See https://github.com/autoplot/python/.
#
#
#
#
# send data over to Autoplot via its server port.  This is a port of the applot for IDL that supports NumPy.
#
# Write from python to d2s.  This will not work on Windows because of linefeeds.  (Ask Chris
# how to write out 0x10 and doubles.)

# Review of how to send data to the Autoplot port:
#import socket
#s = socket.socket()
#s.connect(('localhost',12345))
#cmd='plot(dataset([1,2,3,2,1]))\n'
#s.send( cmd )  

### for rank 2, ytags must be specified
## ascii, boolean, use ascii transfer types

def das2stream( dataStruct, filename, ytags=None, ascii=1, xunits='' ):

#filename='/tmp/python.d2s'
#ascii=0
#dataStruct= { 'x':[1,2,3,4,5], 'y':[2,3,4,4,3], 'tags':['x','y'] }
#xunits= ''
#if (True):

   print 'writing das2stream to ' + filename
   import time

   streamHeader= [ '[00]xxxxxx<stream source="applot.pro" localDate="'+time.asctime()+'">', '</stream>' ]
   contentLength= -10 # don't include the packet tag and content length
   for i in xrange( len( streamHeader ) ):
      contentLength += len( streamHeader[i] ) + 1

   x= streamHeader[0]
   x= '[00]' + '%06d' % contentLength + x[10:]
   streamHeader[0]= x

   if ascii: xdatatype= 'ascii24'
   else: xdatatype= 'sun_real8'
   if ascii: datatype= 'ascii16'
   else: datatype='sun_real8'

   packetDescriptor= [ '[01]xxxxxx<packet>' ]
   tags= dataStruct['tags']
   nt= len(tags)
   packetDescriptor.append( '   <x type="'+xdatatype+'" name="'+tags[0]+'" units="'+xunits+'" />' )

   totalItems=1

   format=['%24.12f']
   reclen= 4 + 24 + (nt-1) * 20
   i=0
   for tag in tags:
      d= dataStruct[tag]
      if ( i==0 ):
          name=''
          i=i+1
          continue
      else:
          name= tags[i]    ### stream reader needs a default plane
      if ( isinstance( d, list ) ):
          rank= 1
      elif ( hasattr( d, "shape") ):  # check for numpy
          rank= len(d.shape)

      if ( rank==1 ):
         packetDescriptor.append( '   <y type="'+datatype+'" name="'+name+'" units="" idlname="'+tags[i]+'" />' )

         if ( i<nt-1 ): format.append('%16.4e')
         else: format.append( '%15.3e' )
         totalItems= totalItems + 1
      else:
         if ytags==None: ytags= range(s[2])
         sytags= ','.join( [ "%f"%n for n in ytags ] )
         nitems= len(ytags)
         packetDescriptor.append( '   <yscan type="' +datatype+'" name="' +name +'" units="" nitems="'+str(nitems) +'" yTags="'+sytags+'"' +' />' )
 
         for i in xrange(1,nitems): format.append('%16.4e')
         if ( i<nt-1 ):
             format.append('%16.4e')
         else:
             format.append('%15.4e')
         totalItems+= nitems
      i=i+1;

   packetDescriptor.append( '</packet>' )

   contentLength= -10 # don't include the packet tag and content length
   for i in xrange(0,len(packetDescriptor)):
       contentLength += len( packetDescriptor[i] ) + 1
  
   x= packetDescriptor[0]
   x= x[0:4]+'%06d' % contentLength + x[10:]
   packetDescriptor[0]= x

   unit= open( filename, 'wb' )

   for i in xrange(len(streamHeader)):
     unit.write( streamHeader[i] )
     unit.write( '\n' )

   for i in xrange(len(packetDescriptor)):
     unit.write( packetDescriptor[i] )
     unit.write( '\n' )   

   nr= len( dataStruct['x'] )
   
   keys= dataStruct.keys()
   
   newline= ascii
   for i in xrange(nr):
      unit.write( ':01:' )
      for j in xrange(nt):
         tag= tags[j]
         if ( ascii ):
            rec= dataStruct[tag][i]
            if hasattr(rec, "__len__"):
               l= len(rec)
               for k in xrange(l):
                  s= format[j] %  rec[k]
                  unit.write( s )
               if ( j==nt-1 ): newline=False
            else:
               s= format[j] % rec
               unit.write( s )
         else:
            import struct
            rec= dataStruct[tag][i]
            if hasattr(rec, "__len__"):
               l= len(rec)
               for j in xrange(l):
                  unit.write( struct.pack( '>d', rec[j] ) )
            else:
               unit.write( struct.pack( '>d', rec ) )

      if ( newline ): unit.write( '\n' )
    
   unit.close() 

def test_dump():
   x= range(3000)
   y= range(2,3002)
   data= { 'x':x, 'y':y, 'tags':['x','y'] }
   das2stream( data, '/tmp/my.d2s', ascii=1 )


# for rank 2, ytags must be specified
# ascii, boolean, use ascii transfer types
def qstream( dataStruct, filename, ytags=None, ascii=True, xunits='', delta_plus=None, delta_minus=None ):

   tags= dataStruct['tags']
   nt= len(tags)
   name= tags[-1]
   tname= tags[0]

   print( 'writing qstream to ' + filename )
   print( tags )
   import time

   streamHeader= [ '<stream dataset_id="'+name+'" source="applot.pro" localDate="'+time.asctime()+'">', '</stream>' ]
   contentLength= 0
   for i in xrange( len( streamHeader ) ):
      contentLength += len( streamHeader[i] ) + 1

   x= streamHeader[0]
   x= '[00]' + '%06d' % contentLength + x
   streamHeader[0]= x

   if ( ascii ): xdatatype= 'ascii24'
   else: xdatatype='double'
   if ( ascii ): datatype= 'ascii16'
   else: datatype='double'

   if ( ytags!=None ):
      ny= len(ytags)
      svals= str(ytags[0])
      for j in xrange(1,len(ytags)):
         svals= svals+','+str(ytags[j]).strip()

      dep1Descriptor= [ '<packet>' ]
      dep1Descriptor.append( '     <qdataset id="DEP1" rank="1" >' )
      dep1Descriptor.append( '       <properties>' )
      dep1Descriptor.append( '           <property name="NAME" type="String" value="DEP1" />')
      dep1Descriptor.append( '       </properties>' )
      dep1Descriptor.append( '       <values encoding="'+datatype+'" length="'+str(ny)+'" values="'+svals+'" />' )
      dep1Descriptor.append( '     </qdataset>' )
      dep1Descriptor.append( '     </packet>' )

      contentLength= 0 # don't include the packet tag and content length
      for i in xrange( len( dep1Descriptor ) ):
         contentLength += len( dep1Descriptor[i] ) + 1
      
      x= dep1Descriptor[0]
      x= '[02]' + '%06d' % contentLength + x
      dep1Descriptor[0]= x


   packetDescriptor= [ '[01]xxxxxx<packet>' ]

   nt= len(tags)
   packetDescriptor.append(  '     <qdataset id="'+tname+'" rank="1" >' )
   packetDescriptor.append(  '       <properties>' )
   packetDescriptor.append(  '           <property name="NAME" type="String" value="'+tname+'" />' )
   packetDescriptor.append(  '           <property name="UNITS" type="units" value="'+xunits+'" />' )
   packetDescriptor.append(  '       </properties>' )
   packetDescriptor.append(  '       <values encoding="'+xdatatype+'" length="" />' )
   packetDescriptor.append(  '     </qdataset>' )

   totalItems=1

   format=['%24.12f']
   formats= { 'x':format }
   
   reclen= 4 + 24 + (nt-1) * 20

   i=1
   for tag in tags[1:]:
      formats1= []
      d= dataStruct[tag]
      if ( isinstance( d, list ) ):
          rank= 1
      elif ( hasattr( d, "shape") ):  # check for numpy
          rank= len(d.shape)

      name= tag  ### stream reader needs a default plane
      if ( rank==1 ):
         packetDescriptor.append(  '     <qdataset id="'+name+'" rank="1" >' )
         packetDescriptor.append(  '       <properties>' )
         packetDescriptor.append(  '           <property name="NAME" type="String" value="'+name+'" />' )
         packetDescriptor.append(  '           <property name="DEPEND_0" type="qdataset" value="'+tname+'" />' )
         if ( i==1 ):
             if ( delta_plus!=None ):
                 packetDescriptor.append(  '           <property name="DELTA_PLUS" type="qdataset" value="'+delta_plus+'" />' )
             if ( delta_minus!=None ):
                 packetDescriptor.append(  '           <property name="DELTA_MINUS" type="qdataset" value="'+delta_minus+'" />' )
         packetDescriptor.append(  '       </properties>' )
         packetDescriptor.append(  '       <values encoding="'+datatype+'" length="" />' )
         packetDescriptor.append(  '     </qdataset>' )
         if ( i<nt-1 ): formats1.append('%16.4e')
         else: formats1.append('%15.4e')
         totalItems+=1
      else:
         nitems= d.shape[1]
         packetDescriptor.append(  '   <qdataset id="'+name+'" rank="2" >' )
         packetDescriptor.append(  '       <properties>' )
         packetDescriptor.append(  '           <property name="DEPEND_0" type="qdataset" value="'+tname+'" />' )
         packetDescriptor.append(  '           <property name="DEPEND_1" type="qdataset" value="DEP1" />' )
         packetDescriptor.append(  '           <property name="NAME" type="String" value="'+name+'" />' )
         packetDescriptor.append(  '       </properties>' )
         packetDescriptor.append(  '       <values encoding="'+datatype+'" length="'+str(nitems)+'" />' )
         packetDescriptor.append(  '   </qdataset>' )
         for i in xrange(0,nitems-1):
            formats1.append('%16.4e')
         if ( i<nt-1 ): formats1.append('%16.4e')
         else: formats1.append('%15.4e')
         totalItems+= nitems
      i=i+1
      formats[tag]= formats1
   packetDescriptor.append(  '</packet>' )

   contentLength= -10 # don't include the packet tag and content length
   for i in xrange(len(packetDescriptor) ):
       contentLength += len( packetDescriptor[i] ) + 1

   x= packetDescriptor[0]
   x= x[0:4] + '%06d' % contentLength + x[10:]
   packetDescriptor[0]= x

   unit= open( filename, 'wb' )

   for i in xrange(len(streamHeader)):
     unit.write( streamHeader[i] )
     unit.write( '\n' )

   for i in xrange(len(packetDescriptor)):
     unit.write( packetDescriptor[i] )
     unit.write( '\n' )

   nr= len( dataStruct['x'] )

   if ( ytags!=None ):
      for i in xrange(len(dep1Descriptor)):
         unit.write(  dep1Descriptor[i] )
         unit.write( '\n' )

   nr= len(dataStruct['x'])  # number of records to output

   keys= dataStruct.keys()

   newline= False
   for i in xrange(nr):
      unit.write( ':01:' )
      for j in xrange(nt):
         tag= tags[j]
         format= formats[tag]
         if ascii:
            rec= dataStruct[tag][i]
            if hasattr(rec,'__len__'):
               l= len(rec)
               for k in xrange(l):
                   print format[k] 
                   s= format[k] % rec[k]
                   unit.write(s)
            else:
               s= format[0] % rec
               unit.write(s)
            if ( j==nt-1 ): newline=True
         else:
            import struct
            rec= dataStruct[tag][i]
            if hasattr(rec,"__len__"):
                l= len(rec)
                for j in xrange(l):
                    unit.write( struct.pack('>d',rec[j]) )
            else:
                unit.write( struct.pack('>d',rec) )
      if ( newline ): 
         unit.write('\n')
   unit.close()



#
#pro test_dump_rank2
#   z= dist(15,20)
#   x= findgen(15)+3
#   y= findgen(20)
#   data= { x:x, z:z }
#
#   das2stream, data, 'my.d2s', ytags= y, /ascii
#end
#
#pro test_dump_qstream
#   x= findgen(3000)/3
#   y= sin( x )
#   data= { x:x, y:y }
#   qstream, data, 'my.qds', /ascii
#end
#
#pro test_dump_rank2_qstream
#   z= dist(15,20)
#   x= findgen(15)+3
#   y= findgen(20)*10
#   data= { x:x, z:z }
#   qstream, data, 'my.qds', depend_1= y, /ascii
#end
#
#pro test_dump_delta_plus_qstream
#   z= dist(15,20)
#   x= findgen(20)+3
#   y= findgen(20)*10 + randomn( s, 20 )
#   dy= replicate(1,20)
#   data= { x:x, y:y, delta:dy }
#   qstream, data, 'my.qds', /ascii, delta_plus='delta', delta_minus='delta'
#end

def tryPortConnect( host, port ):
   print 'tryPortConnect'
   import socket
   s = socket.socket()
   s.connect(('localhost',port))
   s.close()

def sendCommand( s, cmd ):
   print cmd 
   s.send( cmd )
   print 'done'
   

#function kwToString, keywords
#   kw=''
#   t= tag_names(keywords)
#   for i=0,n_elements(t)-1 do begin
#      kw1= strlowcase( t[i] )
#      val1= keywords.(i)
#      type= size( val1 )
#      if ( kw1 eq 'rendertype' ) then kw1='renderType'
#      if ( type[0] eq 0 ) then begin
#         sval= string(val1)
#         if ( type[1] eq 7 ) then sval="'" + sval + "'"
#      endif else if ( type[0] eq 1 ) then begin
#        sval= '['
#        for j=0, type[3]-1 do begin
#          sval1= strtrim(val1[j],2)
#          if ( type[2] eq 7 ) then sval1="'" + sval1 + "'"
#          sval= sval+sval1
#          if ( j lt type[3]-1 ) then sval= sval+', '
#        endfor
#        sval= sval+']'
#      endif else begin
#        message, '2D and up arrays not supported.'
#      endelse
#      kw= kw + ', ' + kw1 + '=' + sval
#   endfor
#   return, strmid( kw, 2 )
#end
#
#function getStructTag, struct, tag, def
#  t= tag_names(struct)
#  ry = where( strmatch( t, tag, /fold) )
#  if ( ry[0] eq -1 ) then return, def
#  return, struct.(ry[0])
#end
 
#+
# NAME:
#    APPLOT
# PURPOSE:
#    Plot to Autoplot instead of the direct graphics plotting, by creating a temporary file of the data and sending a plot
#    command to Autoplot with the server turned on.
# ARGUMENTS:
#    X,Y,Z as with plot.  If X is an integer, then it is the position in Autoplot, so that multiple plots can be sent to 
#      one autoplot canvas.
# CALLING SEQUENCE:
#    plot( X, Y )
#    plot( X, Y, Z )  for a spectrogram
#
# KEYWORDS:
#   tmpfile=   explicitly set the file used to move data into Autoplot.  This can also be used with /noplot
#   /noplot    just make the tmpfile, don't actually try to plot.
#   xunits=    units as a string, especially like "seconds since 2010-01-01T00:00"
#   ylabel=''  label is currently ignored.
#   delta_plus=  array of positive lengths showing the upper limit of the 1-sigma confidence interval.
#   delta_minus= array of positive lengths showing the lower limit of the 1-sigma confidence interval.
#-
def plot( x=None, y=None, z=None, z4=None, xunits='', ylabel='', tmpfile=None, noplot=0, respawn=0, delta_plus=None, delta_minus=None ):
   
   #sep= !version.os_family eq 'Windows' ? ';' : ':'
   sep= ':'

   port= 12345
   
   ext='qds'
   if ( delta_plus!=None ):
       ext='qds'
   
   if tmpfile==None:
     import datetime
     dt= datetime.datetime.today()
     tag= dt.strftime("%Y%m%dT%H%M%S")
     import glob
     ff= glob.glob( '/tmp/' + 'autoplot.' + tag + '.???.'+ext )
     seq= '.%03d.' % len(ff)
     tmpfile= '/tmp/' + 'autoplot.' + tag + seq +ext   # TODO: IDL version handles multiple plots in one second.
   else:
     if ( tmpfile.index('.'+ext) != len(tmpfile)-4 ):
       tmpfile= tmpfile + '.'+ext  # add the extension

   if (z4!=None ): np=4
   elif (z!=None ): np=3
   elif( y!=None ): np=2
   elif( x!=None ): np=1
   else:
       raise Exception("no x, bad")
       
   # serialize the data to a das2stream in a temporary file
   if ( isinstance( x, ( int, long ) ) ):
      pos= x
      xx= y
      if ( z!=None ): yy= z
      if ( z4!=None ): zz= z4
      np= np-1
   else:
      pos= -1
      xx= x
      if ( y!=None ): yy= y
      if ( z!=None ): zz= z
   
   ascii=1

   if ( True ):
     if ( ext != 'qds' ):
         raise Exception('internal error, extension should be qds')
     
     if np==3:
       data= { 'x':xx, 'z':zz, 'tags':['x','z'] }
       qstream( data, tmpfile, ytags=yy, xunits=xunits, ascii=ascii  )
     elif np==2:
       if ( delta_plus!=None ):
           data= { 'x':xx, 'y':yy, 'delta_plus':delta_plus, 'delta_minus':delta_minus, 'tags':['x','y','delta_plus','delta_minus'] }
           qstream( data, tmpfile, ascii=ascii, xunits=xunits, delta_plus='delta_plus', delta_minus='delta_minus'  )
       else:
           data= { 'x':xx, 'y':yy, 'tags':['x','y'] }
           qstream( data, tmpfile, ascii=ascii, xunits=xunits  )
     else:
       ndim= len( xx.shape )
       if ndim==2:
         data= { 'x':range(len(xx)), 'z':xx, 'tags':['x','z'] }
         qstream( data, tmpfile, ytags=range(xx.shape[1]), ascii=ascii, xunits='' )
       else:
         if ( delta_plus!=None and delta_minus!=None ):
            data= { 'x':range(len(xx)), 'y':xx, 'delta_plus':delta_plus, 'delta_minus':delta_minus, 'tags':['x','y','delta_plus','delta_minus']  }
            qstream( data, tmpfile, ascii=ascii, xunits='', delta_plus='delta_plus', delta_minus='delta_minus' )
         else:
            data= { 'x':range(len(xx)), 'y':xx, 'tags':['x','y']  }
            qstream( data, tmpfile, ascii=ascii, xunits='' )
             
   else:
     if np==3:
        data= { 'x':xx, 'z':zz, 'tags':['x','z']  }
        das2stream( data, tmpfile, ytags=yy, xunits=xunits, ascii=ascii )
     elif np==2:
        data= { 'x':xx, 'y':yy, 'tags':['x','y'] }
        das2stream( data, tmpfile, ascii=ascii, xunits=xunits )
     else:
        rank=1
        if ( rank==2 ):
          data= { 'x':range(len(xx)), 'z':xx, 'tags':['x','z']  }
          das2stream( data, tmpfile, ytags=xrange(s[2]), ascii=ascii, xunits='' )
        else:
          data= { 'x':range(len(xx)), 'y':xx, 'tags':['x','y']  }
          das2stream( data, tmpfile, ascii=ascii, xunits='' )
    
   if noplot==1:
      return

   err= 0
   if ( err==0 ):
       import socket
       s = socket.socket()
       s.connect(('localhost',port))

       if ( pos>-1 ):
           cmd= 'plot( '+str(pos)+', "file:'+tmpfile+'" );\n'  # semicolon means no echo

       else:
           cmd= 'plot( "file:'+tmpfile+'" );\n'  # semicolon means no echo

       foo= sendCommand( s, cmd )
       s.shutdown(1)
       s.close()

   else:
      raise Exception( 'error encountered!' )
      
#import pdb; pdb.set_trace()

#   # clean up old tmp files more than 10 minutes old.
#   caldat, systime(1, /julian) - 10/1440., Mon, D, Y, H, Min  # ten minutes ago
#   tag= string( Y, Mon, D, H, Min, format='(I04,I02,I02,"T",I02,I02)' )
#   tmpfile= getenv('IDL_TMPDIR') + 'autoplot.' + tag + '.000.d2s'
#   f= findfile( getenv('IDL_TMPDIR') + 'autoplot.' + '*' + '.???.d2s', count=c )
#   for i=0,c-1 do begin
#      if ( f[i] lt tmpfile ) then begin
#         #print, 'deleting ' + f[i]
#         file_delete, f[i]
#      endif
#   endfor

#test_dump()
#applot( [1,2,3,6,3,3] )
#applot( [1,2,3,4,5], [2,4,2,4,2], delta_plus=[.1,.2,.2,.2,.1], delta_minus= [.1,.2,.2,.2,.1],)
#applot( [1,2,3,4,6], [2,4,2,4,2] )
#applot( [[1,2,3,4,6], [2,4,2,4,2] )

#test numpy 2-D array
#import numpy as np
#x,y= np.mgrid[-3:3,-3:3]
#applot( y )
