pro aboutAutoplot
    ;AboutUtil= OBJ_NEW('IDLJavaObject$Static$AboutUtil', 'org.das2.util.AboutUtil')
    ;print, "= Build Information ="
    ;print, "release tag: ", AboutUtil.getReleaseTag()
    ;print, "build url: ", AboutUtil.getJenkinsURL()
  
    print, "= Runtime Information ="
    System= OBJ_NEW('IDLJavaObject$Static$System', 'java.lang.System')
    javaVersion = System.getProperty("java.version"); // applet okay
    javaVersionWarning= '' ; The java about checks for 1.8.102
    arch = System.getProperty("os.arch"); // applet okay
    Runtime= OBJ_NEW('IDLJavaObject$Static$System', 'java.lang.Runtime')
    nf= OBJ_NEW( 'IDLJavaObject$System', 'java.text.DecimalFormat' )
    
    mem = nf.format( (Runtime.getRuntime()).maxMemory()   / 1000000 )
    tmem= nf.format( (Runtime.getRuntime()).totalMemory() / 1000000 )
    fmem= nf.format( (Runtime.getRuntime()).freeMemory()  / 1000000 )
    print, "Java version: " + javaVersion + " " + javaVersionWarning 
    print, "max memory (MB): " + mem + " (memory available to process)" 
    print, "total memory (MB): " + tmem + " (amount allocated to the process)" 
    print, "free memory (MB): " + fmem + " (amount available before more must be allocated)" 

end

;; for rank 2, ytags must be specified
; ascii, boolean, use ascii transfer types
pro das2stream, dataStruct, filename, ytags=ytags, ascii=ascii, xunits=xunits

   print, 'writing das2stream to ' + filename
   on_error, 2
   
   streamHeader= [ '[00]xxxxxx<stream source=''applot.pro'' localDate='''+systime(0)+'''>', '</stream>' ]
   contentLength= -10 ; don't include the packet tag and content length
   for i=0,n_elements( streamHeader )-1 do begin
      contentLength += strlen( streamHeader[i] ) + 1
   endfor
   x= streamHeader[0]
   strput, x, string( contentLength, format='(i6.6)' ), 4
   streamHeader[0]= x

   ascii= keyword_Set(ascii) ; 1=do ascii stream, 0=binary

   xdatatype= ascii ? 'ascii24' : 'sun_real8'
   datatype= ascii ? 'ascii16' : 'sun_real8'

   packetDescriptor= [ '[01]xxxxxx<packet>' ]
   t= tag_names( dataStruct )
   nt= n_elements(t)
   packetDescriptor= [ packetDescriptor, '   <x type="'+xdatatype+'" name="'+t[0]+'" units="'+xunits+'" />' ]

   totalItems=1

   format='(f24.12'
   reclen= 4 + 24 + (nt-1) * 20
   for i=1,nt-1 do begin
      s= size( dataStruct.(i) )
      name= i eq 1 ? '' : t[i]  ;;; stream reader needs a default plane
      if ( s[0] eq 1 ) then begin
         packetDescriptor= [ packetDescriptor, $
             '   <y type="'+datatype+'" name="'+name+'" idlname="'+t[i]+'" units=""/>' ]
         format= format + ( ( i lt n_elements(t)-1 ) ? ',e16.4' : ',e15.3)' )
         totalItems+=1
      endif else begin
         if n_elements( ytags ) eq 0 then ytags= findgen(  s[2] )
         sytags= strjoin( strtrim( ytags, 2 ), ',' )
         nitems= s[2]
         packetDescriptor= [ packetDescriptor, $
             '   <yscan type="'+datatype+'" name="'+name $
             +'" nitems="'+strtrim(nitems,2)  $
             +'" yTags="'+sytags+'" yUnits="" zUnits=""' $
             +' />' ]
         for i=1,nitems-1 do format= format + ',e16.4'
         format= format + ( ( i lt n_elements(t)-1 ) ? ','+',e16.4' : ','+'e15.4)' )
         totalItems+= nitems
      endelse
   endfor
   packetDescriptor= [ packetDescriptor, '</packet>' ]

  contentLength= -10 ; don't include the packet tag and content length
  for i=0,n_elements( packetDescriptor )-1 do begin
      contentLength += strlen( packetDescriptor[i] ) + 1
  endfor
  x= packetDescriptor[0]
  strput, x, string( contentLength, format='(i6.6)' ), 4
  packetDescriptor[0]= x

  openw, unit, filename, /get_lun

   for i=0,n_elements(streamHeader)-1 do begin
     writeu, unit, byte( streamHeader[i] )
     writeu, unit, byte(10)
   endfor

   for i=0,n_elements(packetDescriptor)-1 do begin
     writeu, unit, byte( packetDescriptor[i] )
     writeu, unit, byte(10)
   endfor

   nr= n_elements(dataStruct.(0))

   data= make_array( /double, totalItems, nr )
   dataCol= 0 ; column within rank2 array
   for j=0,nt-1 do begin
     dd= dataStruct.(j)
     s= size(dd)
     if ( s[0] eq 2 ) then begin
        data[dataCol:(dataCol+nitems-1),*]= transpose(dd)
        dataCol= dataCol+nitems
     endif else begin
        data[dataCol,*]= dd
        dataCol= dataCol+1
     endelse
   endfor

   if ( ascii eq 0 ) then begin
      r= where( finite( data ) eq 0 )
      swap_endian_inplace, data, /swap_if_little_endian
      if ( r[0] ne -1 ) then begin
         data[r]= !values.d_nan
      endif 
   endif

   for i=0L, nr-1 do begin
      writeu, unit, byte(':01:')
      if ( ascii ) then begin
         s= string( data[*,i], format=format )
         writeu, unit, s
         writeu, unit, byte(10)
      endif else begin
         writeu, unit, data[*,i]
      endelse
   endfor

   close, unit
   free_lun, unit

end

;; for rank 2, ytags must be specified
; ascii, boolean, use ascii transfer types
pro qstream, dataStruct, filename, depend_1=ytags, ascii=ascii, xunits=xunits, delta_plus=delta_plus, delta_minus=delta_minus

   print, 'writing qstream to ' + filename
   on_error, 2
   
   t= tag_names( dataStruct )
   name= t[1]
   tname= t[0]
   
   streamHeader= [ '[00]xxxxxx<stream dataset_id="'+name+'" source="applot.pro" localDate="'+systime(0)+'">', '</stream>' ]
   contentLength= -10 ; don't include the packet tag and content length
   for i=0,n_elements( streamHeader )-1 do begin
      contentLength += strlen( streamHeader[i] ) + 1
   endfor
   x= streamHeader[0]
   strput, x, string( contentLength, format='(i6.6)' ), 4
   streamHeader[0]= x

   ascii= keyword_Set(ascii) ; 1=do ascii stream, 0=binary
   if ( n_elements( xunits ) eq 0 ) then begin
      xunits=""  ; dimensionless
   end

   xdatatype= ascii ? 'ascii24' : 'double'
   datatype= ascii ? 'ascii16' : 'double'

   if ( n_elements( ytags ) gt 0 ) then begin
      ny= n_elements(ytags)
      svals= strtrim(ytags[0],2)
      for j=1,n_elements(ytags)-1 do begin
         svals= svals+','+strtrim(ytags[j],2)
      endfor
         dep1Descriptor= [ '[99]xxxxxx<packet>' ]
         dep1Descriptor= [ dep1Descriptor, '     <qdataset id="DEP1" rank="1" >' ]
         dep1Descriptor= [ dep1Descriptor, '       <properties>' ]
         dep1Descriptor= [ dep1Descriptor, '           <property name="NAME" type="String" value="DEP1" />']
         dep1Descriptor= [ dep1Descriptor, '       </properties>' ]
         dep1Descriptor= [ dep1Descriptor, '       <values encoding="'+datatype+'" length="'+strtrim(ny,2)+'" values="'+svals+'" />' ]         
         dep1Descriptor= [ dep1Descriptor, '     </qdataset>' ]  
         dep1Descriptor= [ dep1Descriptor, '     </packet>' ]
         
      contentLength= -10 ; don't include the packet tag and content length
      for i=0,n_elements( dep1Descriptor )-1 do begin
         contentLength += strlen( dep1Descriptor[i] ) + 1
      endfor
      x= dep1Descriptor[0]
      strput, x, string( contentLength, format='(i6.6)' ), 4
      dep1Descriptor[0]= x

   endif
   
   packetDescriptor= [ '[01]xxxxxx<packet>' ]
   
   nt= n_elements(t)
   packetDescriptor=       [ packetDescriptor, '     <qdataset id="'+tname+'" rank="1" >' ]
   packetDescriptor=       [ packetDescriptor, '       <properties>' ]
   packetDescriptor=       [ packetDescriptor, '           <property name="NAME" type="String" value="'+tname+'" />']
   packetDescriptor=       [ packetDescriptor, '           <property name="UNITS" type="units" value="'+xunits+'" />']
   packetDescriptor=       [ packetDescriptor, '       </properties>' ]
   packetDescriptor=       [ packetDescriptor, '       <values encoding="'+xdatatype+'" length="" />' ]
   packetDescriptor=       [ packetDescriptor, '     </qdataset>' ]

   totalItems=1

   format='(f24.12'
   reclen= 4 + 24 + (nt-1) * 20
   for i=1,nt-1 do begin
      s= size( dataStruct.(i) )
      name= t[i]  ;;; stream reader needs a default plane
      if ( s[0] eq 1 ) then begin      
         packetDescriptor= [ packetDescriptor, '     <qdataset id="'+name+'" rank="1" >' ]
         packetDescriptor= [ packetDescriptor, '       <properties>' ]
         packetDescriptor= [ packetDescriptor, '           <property name="NAME" type="String" value="'+name+'" />']
         packetDescriptor= [ packetDescriptor, '           <property name="DEPEND_0" type="qdataset" value="'+tname+'" />']
         if ( i eq 1 ) then begin
             if ( keyword_set(delta_plus) ) then begin
                 packetDescriptor= [ packetDescriptor, '           <property name="DELTA_PLUS" type="qdataset" value="'+strupcase(delta_plus)+'" />']
             endif
             if ( keyword_set(delta_minus) ) then begin
                 packetDescriptor= [ packetDescriptor, '           <property name="DELTA_MINUS" type="qdataset" value="'+strupcase(delta_minus)+'" />']
             endif
         endif
         packetDescriptor= [ packetDescriptor, '       </properties>' ]
         packetDescriptor= [ packetDescriptor, '       <values encoding="'+datatype+'" length="" />' ]         
         packetDescriptor= [ packetDescriptor, '     </qdataset>' ]
         format= format + ( ( i lt n_elements(t)-1 ) ? ',e16.4' : ',e15.3)' )
         totalItems+=1
      endif else begin
         nitems= s[2]
         packetDescriptor= [ packetDescriptor, '   <qdataset id="'+name+'" rank="2" >' ]
         packetDescriptor= [ packetDescriptor, '       <properties>' ]
         packetDescriptor= [ packetDescriptor, '           <property name="DEPEND_0" type="qdataset" value="'+tname+'" />']
         packetDescriptor= [ packetDescriptor, '           <property name="DEPEND_1" type="qdataset" value="DEP1" />']
         packetDescriptor= [ packetDescriptor, '           <property name="NAME" type="String" value="'+name+'" />']
         packetDescriptor= [ packetDescriptor, '       </properties>' ]
         packetDescriptor= [ packetDescriptor, '       <values encoding="'+datatype+'" length="'+strtrim(nitems,2)+'" />' ]         
         packetDescriptor= [ packetDescriptor, '   </qdataset>' ]
         for i=1,nitems-1 do format= format + ',e16.4'
         format= format + ( ( i lt n_elements(t)-1 ) ? ','+',e16.4' : ','+'e15.4)' )
         totalItems+= nitems
      endelse
   endfor
   packetDescriptor=       [ packetDescriptor, '</packet>' ]

  contentLength= -10 ; don't include the packet tag and content length
  for i=0,n_elements( packetDescriptor )-1 do begin
      contentLength += strlen( packetDescriptor[i] ) + 1
  endfor
  x= packetDescriptor[0]
  strput, x, string( contentLength, format='(i6.6)' ), 4
  packetDescriptor[0]= x

  openw, unit, filename, /get_lun

   for i=0,n_elements(streamHeader)-1 do begin
     writeu, unit, byte( streamHeader[i] )
     writeu, unit, byte(10)
   endfor

   if ( n_elements( ytags ) gt 0 ) then begin
     for i=0,n_elements(dep1Descriptor)-1 do begin
      writeu, unit, byte( dep1Descriptor[i] )
      writeu, unit, byte(10)
     endfor
   endif

   for i=0,n_elements(packetDescriptor)-1 do begin
     writeu, unit, byte( packetDescriptor[i] )
     writeu, unit, byte(10)
   endfor

   nr= n_elements(dataStruct.(0))

   data= make_array( /double, totalItems, nr )
   dataCol= 0 ; column within rank2 array
   for j=0,nt-1 do begin
     dd= dataStruct.(j)
     s= size(dd)
     if ( s[0] eq 2 ) then begin
        data[dataCol:(dataCol+nitems-1),*]= transpose(dd)
        dataCol= dataCol+nitems
     endif else begin
        data[dataCol,*]= dd
        dataCol= dataCol+1
     endelse
   endfor

   if ( ascii eq 0 ) then begin
      r= where( finite( data ) eq 0 )
      swap_endian_inplace, data, /swap_if_little_endian
      if ( r[0] ne -1 ) then begin
         data[r]= !values.d_nan
      endif 
   endif

   for i=0L, nr-1 do begin
      writeu, unit, byte(':01:')
      if ( ascii ) then begin
         s= string( data[*,i], format=format )
         writeu, unit, s
         writeu, unit, byte(10)
      endif else begin
         writeu, unit, data[*,i]
      endelse
   endfor

   close, unit
   free_lun, unit

end


pro test_dump
   x= findgen(3000)/3
   y= sin( x )
   data= { x:x, y:y }
   das2stream, data, 'my.d2s', /ascii
end

pro test_dump_rank2
   z= dist(15,20)
   x= findgen(15)+3
   y= findgen(20)
   data= { x:x, z:z }

   das2stream, data, 'my.d2s', ytags= y, /ascii
end

pro test_dump_qstream
   x= findgen(3000)/3
   y= sin( x )
   data= { x:x, y:y }
   qstream, data, 'my.qds', /ascii
end

pro test_dump_rank2_qstream
   z= dist(15,20)
   x= findgen(15)+3
   y= findgen(20)*10
   data= { x:x, z:z }
   qstream, data, 'my.qds', depend_1= y, /ascii
end

pro test_dump_delta_plus_qstream
   z= dist(15,20)
   x= findgen(20)+3
   y= findgen(20)*10 + randomn( s, 20 )
   dy= replicate(1,20)
   data= { x:x, y:y, delta:dy }
   qstream, data, 'my.qds', /ascii, delta_plus='delta', delta_minus='delta' 
end

function tryPortConnect, host, port, unit=unit
   socket, unit, 'localhost', port, error=error, /get_lun, write_timeout=1
   if ( error eq 0 ) then begin  
      close, unit
      free_lun, unit
   endif
   return, error
end

function sendCommand, unit, cmd
   printf, unit, cmd

   response=''

   return, 0
;   wait, 0.2
;
;   x= file_poll_input( unit, timeout=0, count=count )
;   while ( count gt 0 ) do begin
;      readf, unit, response
;      print, response
;      x= file_poll_input( unit, timeout=0, count=count )
;   endwhile

end

function kwToString, keywords
   kw=''
   t= tag_names(keywords)
   for i=0,n_elements(t)-1 do begin
      kw1= strlowcase( t[i] )
      val1= keywords.(i)
      type= size( val1 )
      if ( kw1 eq 'rendertype' ) then kw1='renderType'
      if ( type[0] eq 0 ) then begin
         sval= string(val1)
         if ( type[1] eq 7 ) then sval="'" + sval + "'"
      endif else if ( type[0] eq 1 ) then begin
        sval= '['
        for j=0, type[3]-1 do begin
          sval1= strtrim(val1[j],2)
          if ( type[2] eq 7 ) then sval1="'" + sval1 + "'"
          sval= sval+sval1
          if ( j lt type[3]-1 ) then sval= sval+', '
        endfor
        sval= sval+']'
      endif else begin
        message, '2D and up arrays not supported.'
      endelse
      kw= kw + ', ' + kw1 + '=' + sval
   endfor
   return, strmid( kw, 2 )
end

function getStructTag, struct, tag, def
  t= tag_names(struct)
  ry = where( strmatch( t, tag, /fold) )
  if ( ry[0] eq -1 ) then return, def
  return, struct.(ry[0])
end
 
;+
; NAME:
;    APPLOT
; PURPOSE:
;    Plot to Autoplot instead of the direct graphics plotting, by creating a temporary file of the data and sending a plot
;    command to Autoplot with the server turned on.
; ARGUMENTS:
;    X,Y,Z as with plot.  If X is an integer, then it is the position in Autoplot, so that multiple plots can be sent to 
;      one autoplot canvas.
; CALLING SEQUENCE:
;    APPLOT, X, Y
;    APPLOT, X, Y, Z   for a spectrogram
;
; KEYWORDS:
;   tmpfile=   explicitly set the file used to move data into Autoplot.  This can also be used with /noplot
;   /noplot    just make the tmpfile, don't actually try to plot.
;   xunits=    units as a string, especially like "seconds since 2010-01-01T00:00"
;   delta_plus=  array of positive lengths showing the upper limit of the 1-sigma confidence interval.
;   delta_minus= array of positive lengths showing the lower limit of the 1-sigma confidence interval.
;   index=       plot position [0,1,2,3..] 0 is default. (same as integer for first argument.)
;-
pro applot, x_in, y_in, z_in, z4_in, xunits=xunits, tmpfile=tmpfile, noplot=noplot, _extra=e, $ 
    respawn=respawn, delta_plus=delta_plus, delta_minus=delta_minus, $
    index=index

   x= x_in
   if ( n_elements(y_in) gt 0 ) then y= y_in
   if ( n_elements(z_in) gt 0 ) then z= z_in
   if ( n_elements(z4_in) gt 0 ) then z4= z4_in
   
   common applot_common, appid

  ; on_error, 2

   if n_elements( x ) eq 0 then begin
      message, 'x is undefined'
   endif

   sep= !version.os_family eq 'Windows' ? ';' : ':'

   port= 12345
   
   if keyword_set(respawn) then begin
      javahome= getenv( 'JAVA_HOME' )
      if javahome eq '' then javahome= 'c:/"program files"/java/jre1.6.0_03/'

      print, 'spawn autoplot java process'
      cmd= javahome+'/bin/java -cp '+classpath+ ' org.autoplot.AutoplotUI --port='+strtrim(port,2)
      print, cmd
      if !version.os_family eq 'Windows' then begin
        spawn, cmd, pid=appid, /nowait
      endif else begin
        spawn, cmd+' &', pid=appid
      endelse

      t0= systime(1)
      print, 'sleeping until AP wakes up...'
      while ( ( systime(1)-t0) lt 15 && tryPortConnect( 'localhost', port ) ne 0 ) do begin
          sleep, 1
          print, 'sleeping...'
      endwhile

      print, 'survived spawn'
   endif

   ext='d2s'
   if ( keyword_set( delta_plus ) ) then begin
       ext='qds'
   endif
    
   
   if n_elements( tmpfile ) eq 0 then begin
     caldat, systime(1, /julian), Mon, Day, Year, Hour, Min
     tag= string( Year, Mon, Day, Hour, Min, format='(I04,I02,I02,"T",I02,I02)' )
     tmpfile= getenv('IDL_TMPDIR') + 'autoplot.' + tag + '.???.'+ext
     f= findfile( tmpfile, count=c )
     tmpfile= getenv('IDL_TMPDIR') + 'autoplot.' + tag + '.' + string(c,format='(I3.3)') + '.'+ext
     tmpfile= strjoin( str_sep( tmpfile, '\' ), '/' )
   endif else begin
     if ( strpos( tmpfile, '.'+ext ) ne strlen(tmpfile)-4 ) then begin
       tmpfile= tmpfile + '.'+ext  ; add the extension
     endif
   endelse

   np= n_params();

   ; check for papco data structures
   if ( size( x, /type ) eq 8 ) then begin
       rank= size( x.data, /n_dimensions )
       t= tag_names( x )
       
       vmin= -1e38
       r= where( strmatch( t, 'validmin', /fold) )
       if ( r[0] ne -1 ) then begin
          vmin= x.(r)
       endif
       vmax= 1e38
       r= where( strmatch( t, 'validmax', /fold) )
       if ( r[0] ne -1 ) then begin
          vmax= x.(r)
       endif
       
       if ( rank eq 2 ) then begin
           z= double(x.data)
           ry = where( strmatch( t, 'depend_1', /fold) )
           if ( ry eq -1 ) then begin
              y= findgen( (size(x.data))[2] )
           endif else begin
              ry = where( strmatch( t, x.(ry[0]), /fold) )
              y= double( (x.(ry[0])).data )
           endelse 
           rx = where( strmatch( t, 'depend_0', /fold) )
           if ( rx eq -1 ) then begin
              x= findgen( (size(z))[1] )
           endif else begin
              rx = where( strmatch( t, x.(rx[0]), /fold) )
              xds= (x.(rx[0]))
              x= double( xds.data )
              xunits= getStructTag( xds, 'units', '' )
           endelse 
           np= 3
           r= where( z le vmin or z ge vmax ) ; papco_ds is exclusive of vmin and vmax (for now).
           if ( r[0] ne -1 ) then begin
               z[r]= !values.f_nan
           endif

       endif else if ( rank eq 1 ) then begin
           y= double(x.data)
           rx = where( strmatch( t, 'depend_0', /fold) )
           if ( rx eq -1 ) then begin
              x= findgen( (size(y))[1] )
           endif else begin
              rx = where( strmatch( t, x.(rx[0]), /fold) )
              xds= (x.(rx[0]))
              x= double( xds.data )
              xunits= getStructTag( xds, 'units', '' )
           endelse 
           np= 2
           
       endif else begin
           message, 'papco ds rank not supported'
       endelse
       if ( xunits eq 'mjd2000' ) then begin
          xunits= 'days since 2000-001T00:00'  ; verified by comparing conversion to cdf epoch in papco_ds_units.pro
       endif
   endif
   
   ; serialize the data to a das2stream in a temporary file
   if ( size(x,/n_elements) eq 1 and size( x, /type ) eq 2   or  size( x, /type ) eq 3  ) then begin
      pos= x
      xx= y
      if ( n_elements(z) gt 0 ) then yy= z
      if ( n_elements(z4) gt 0 ) then zz= z4 ; TODO: sloppy, move y to x should clear y
      np= np-1
   endif else begin
      pos= -1
      xx= x
      if ( n_elements(y) gt 0 ) then yy= y
      if ( n_elements(z) gt 0 ) then zz= z
   endelse

   if ( n_elements( index ) eq 1 ) then begin
      pos= index
   endif
   
   if ( size(xx,/type) eq 6 ) then begin
       message, 'complex numbers are not supported'
   endif
   if ( size(yy,/type) eq 6 ) then begin
     message, 'complex numbers are not supported'
   endif
   if ( size(zz,/type) eq 6 ) then begin
     message, 'complex numbers are not supported'
   endif
    
   if n_elements(xunits) eq 0 then xunits=''
   
   if ( keyword_set(delta_plus) and keyword_set(delta_minus) ) then begin
     if ( ext ne 'qds' ) then begin  
         message, 'internal error, ext does not match'
     endif
     if np eq 3 then begin
       data= { x:xx, z:zz }
       qstream, data, tmpfile, ytags=yy, xunits=xunits, ascii=0   ; TODO: redo with qstreams  ; TODO: redo with PAPCO's old version of QDataSet
     endif else if np eq 2 then begin
       data= { x:xx, y:yy, delta_plus:delta_plus, delta_minus:delta_minus }
       qstream, data, tmpfile, ascii=0, xunits=xunits, delta_plus='DELTA_PLUS', delta_minus='DELTA_MINUS'
     endif else begin
       s= size( xx )
       if s[0] eq 2 then begin
         data= { x:findgen(s[1]), z:xx }
         qstream, data, tmpfile, ytags=findgen(s[2]), ascii=0, xunits=''
       endif else begin
         data= { x:findgen(s[1]), y:xx, delta_plus:delta_plus, delta_minus:delta_minus }
         qstream, data, tmpfile, ascii=0, xunits='', delta_plus='DELTA_PLUS', delta_minus='DELTA_MINUS'
       endelse    
     endelse
   endif else begin
     if np eq 3 then begin
        data= { x:xx, z:zz }
        das2stream, data, tmpfile, ytags=yy, xunits=xunits, ascii=0   ; TODO: redo with qstreams  ; TODO: redo with PAPCO's old version of QDataSet
     endif else if np eq 2 then begin
        data= { x:xx, y:yy }
        das2stream, data, tmpfile, ascii=0, xunits=xunits
     endif else begin
        s= size( xx )
        if s[0] eq 2 then begin
          data= { x:findgen(s[1]), z:xx }
          das2stream, data, tmpfile, ytags=findgen(s[2]), ascii=0, xunits=''
        endif else begin
          data= { x:findgen(s[1]), y:xx }
          das2stream, data, tmpfile, ascii=0, xunits=''
        endelse
     endelse
   endelse
    
   if keyword_set( noplot ) then begin
      return
   endif

   ex= ''

   if ( n_elements( e ) gt 0 ) then begin
      ex= kwToString( e )
   endif


   catch, err
   if ( err eq 0 ) then begin
       socket, unit, 'localhost', port, /get_lun, write_timeout=1

       if ( !version.os_family eq 'Windows' ) then tmpfile= '/'+tmpfile

       if ( pos gt -1 ) then begin
           if n_elements( e ) gt 0 then begin
              cmd= 'plot( '+strtrim(pos,2)+', ''file:'+tmpfile+''', '+ex+ ');'  ; semicolon means no echo
           endif else begin
              cmd= 'plot( '+strtrim(pos,2)+', ''file:'+tmpfile+''' );'  ; semicolon means no echo
           endelse
       endif else begin
           if n_elements( e ) gt 0 then begin
              cmd= 'plot( ''file:'+tmpfile+''', '+ex+ ');'  ; semicolon means no echo
           endif else begin
              cmd= 'plot( ''file:'+tmpfile+''' );'  ; semicolon means no echo
           endelse
       endelse

       foo= sendCommand( unit, cmd )

       if n_elements( ytitle ) eq 1 then begin
          cmd= 'dom.controller.plot.yaxis.label='''+ytitle+'''
          foo= sendCommand( unit, cmd )
       endif

       close, unit
       free_lun, unit
   endif else begin
      catch, /cancel
      print, 'error encountered!'
      print, 'try with /respawn'
      print, !error_state.msg
      if n_elements(appid) ne 0 then xxxx= temporary( appid )
      retall
   endelse

   ; clean up old tmp files more than 10 minutes old.
   caldat, systime(1, /julian) - 10/1440., Mon, D, Y, H, Min  ; ten minutes ago
   tag= string( Y, Mon, D, H, Min, format='(I04,I02,I02,"T",I02,I02)' )
   tmpfile= getenv('IDL_TMPDIR') + 'autoplot.' + tag + '.000.d2s'
   f= findfile( getenv('IDL_TMPDIR') + 'autoplot.' + '*' + '.???.d2s', count=c )
   for i=0,c-1 do begin
      if ( f[i] lt tmpfile ) then begin
         ;print, 'deleting ' + f[i]
         file_delete, f[i]
      endif
   endfor
end
