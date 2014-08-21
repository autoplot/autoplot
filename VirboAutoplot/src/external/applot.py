# Write from python to d2s.  This will not work on Windows because of linefeeds.  (Ask Chris
# how to write out 0x10 and doubles.)

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
      rank= 1
      if ( rank==1 ):
         packetDescriptor.append( '   <y type="'+datatype+'" name="'+name+'" idlname="'+tags[i]+'" />' )

         if ( i<nt-1 ): format.append('%16.4e')
         else: format.append( '%15.3e' )
         totalItems= totalItems + 1
      else:
         if ytags==None: ytags= range(s[2])
         sytags= ','.join( ytags.strip() )
         nitems= s[2]
         packetDescriptor= packetDescriptor + '   <yscan type="' +datatype+'" name="'+name +'" nitems="'+str(nitems)  +'" yTags="'+sytags+'"'             +' />' 
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

   unit= open( filename, 'w' )

   for i in xrange(len(streamHeader)):
     unit.write( streamHeader[i] )
     unit.write( '\n' )

   for i in xrange(len(packetDescriptor)):
     unit.write( packetDescriptor[i] )
     unit.write( '\n' )    #'\0a' )

   nr= len( dataStruct['x'] )
   
   keys= dataStruct.keys()
   
   for i in xrange(nr):
      unit.write( ':01:' )
      for j in xrange(nt):
         tag= tags[j]
         if ( ascii ):
            #s= string( data[:,i], format=format )
            s= format[j] %  dataStruct[tag][i]
            unit.write( s )
         else:
            unit.write( float(dataStruct[tag][i]) )
      unit.write( '\n' )
    
   unit.close() 


def test_dump():
   x= range(3000)
   y= range(2,3002)
   data= { 'x':x, 'y':y, 'tags':['x','y'] }
   das2stream( data, '/tmp/my.d2s', ascii=1 )


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
   import socket
   s = socket.socket()
   s.connect(('localhost',port))
   s.close()

def sendCommand( s, cmd ):
   s.send( cmd )
   

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
#    APPLOT, X, Y
#    APPLOT, X, Y, Z   for a spectrogram
#
# KEYWORDS:
#   tmpfile=   explicitly set the file used to move data into Autoplot.  This can also be used with /noplot
#   /noplot    just make the tmpfile, don't actually try to plot.
#   xunits=    units as a string, especially like "seconds since 2010-01-01T00:00"
#   delta_plus=  array of positive lengths showing the upper limit of the 1-sigma confidence interval.
#   delta_minus= array of positive lengths showing the lower limit of the 1-sigma confidence interval.
#-
def applot( x=None, y=None, z=None, z4=None, xunits='', tmpfile=None, noplot=0, respawn=0, delta_plus=None, delta_minus=None ):
   
   #sep= !version.os_family eq 'Windows' ? ';' : ':'
   sep= ':'

   port= 12345
   
   ext='d2s'
   if ( delta_plus!=None ):
       ext='qds'
   
   if tmpfile==None:
     import datetime
     dt= datetime.datetime.today()
     tag= dt.strftime("%Y%m%dT%H%M%S")
     tmpfile= '/tmp/' + 'autoplot.' + tag + '.001.'+ext
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
   
   if ( delta_plus!=None and delta_minus!=None ):
     if ( ext != 'qds' ):
         raise Exception('internal error, ext does should be qds')
     
     if np==3:
       data= { 'x':xx, 'z':zz, 'tags':['x','z'] }
       qstream( data, tmpfile, ytags=yy, xunits=xunits, ascii=1  )
     elif np==2:
       data= { 'x':xx, 'y':yy, 'delta_plus':delta_plus, 'delta_minus':delta_minus, 'tags':['x','z','delta_plus','delta_minus'] }
       qstream( data, tmpfile, ascii=1, xunits=xunits, delta_plus='delta_plus', delta_minus='delta_minus'  )
     else:
       s= [1]  # TODO: support rank 2
       if s[0]==2:
         data= { 'x':range(s[1]), 'z':xx, 'tags':['x','z'] }
         qstream( data, tmpfile, ytags=findgen(s[2]), ascii=1, xunits='' )
       else:
         data= { 'x':range(s[1]), 'y':xx, 'delta_plus':delta_plus, 'delta_minus':delta_minus, 'tags':['x','z','delta_plus','delta_minus']  }
         qstream( data, tmpfile, ascii=0, xunits='', delta_plus='delta_plus', delta_minus='delta_minus' )
   else:
     if np==3:
        data= { 'x':xx, 'z':zz, 'tags':['x','z']  }
        das2stream( data, tmpfile, ytags=yy, xunits=xunits, ascii=1 )
     elif np==2:
        data= { 'x':xx, 'y':yy, 'tags':['x','y'] }
        das2stream( data, tmpfile, ascii=1, xunits=xunits )
     else:
        rank=1
        if ( rank==2 ):
          data= { 'x':range(len(xx)), 'z':xx, 'tags':['x','z']  }
          das2stream( data, tmpfile, ytags=xrange(s[2]), ascii=1, xunits='' )
        else:
          data= { 'x':range(len(xx)), 'y':xx, 'tags':['x','y']  }
          das2stream( data, tmpfile, ascii=1, xunits='' )
    
   if noplot==1:
      return

   err= 0
   if ( err==0 ):
       import socket
       s = socket.socket()
       s.connect(('localhost',port))

       if ( pos>-1 ):
           cmd= 'plotx( '+pos.trim()+', "file:'+tmpfile+'" );'  # semicolon means no echo

       else:
           cmd= 'plotx( "file:'+tmpfile+'" );'  # semicolon means no echo

       foo= sendCommand( s, cmd )

   else:
      raise Exception( 'error encountered!' )
      

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
#applot( [1,2,3,4,5] )
