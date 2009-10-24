;; for rank 2, ytags must be specified
; ascii, boolean, use ascii transfer types
pro das2stream, dataStruct, filename, ytags=ytags, ascii=ascii
   streamHeader= [ '[00]xxxxxx<stream>', '</stream>' ]
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
   packetDescriptor= [ packetDescriptor, '   <x type="'+xdatatype+'" name="'+t[0]+'" />' ]

   totalItems=1

   format='(f24.12'
   reclen= 4 + 24 + (nt-1) * 20
   for i=1,nt-1 do begin
      s= size( dataStruct.(i) )
      name= i eq 1 ? '' : t[i]  ;;; stream reader needs a default plane
      if ( s[0] eq 1 ) then begin
         packetDescriptor= [ packetDescriptor, $
             '   <y type="'+datatype+'" name="'+name+'" idlname="'+t[i]+'" />' ]
         format= format + ( ( i lt n_elements(t)-1 ) ? ',e16.4' : ',e15.3)' )
         totalItems+=1
      endif else begin
         if n_elements( ytags ) eq 0 then ytags= findgen(  s[2] )
         sytags= strjoin( strtrim( ytags, 2 ), ',' )
         nitems= s[2]
         packetDescriptor= [ packetDescriptor, $
             '   <yscan type="'+datatype+'" name="'+name $
             +'" nitems="'+strtrim(nitems,2)  $
             +'" yTags="'+sytags+'"' $
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

   if ( ascii eq 0 ) then swap_endian_inplace, data, /swap_if_little_endian

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


function tryPortConnect, host, port, unit=unit
   socket, unit, 'localhost', port, error=error, /get_lun, write_timeout=1
   if ( error eq 0 ) then begin  
      close, unit
      free_lun, unit
   endif
   return, error
end

pro applot, x, y, z, _extra=e, respawn=respawn, panel=panel, $
   ytitle=ytitle, xtitle=xtitle, title=title

   common applot_common, appid

   on_error, 2

   if n_elements( x ) eq 0 then begin
      message, 'x is undefined'
   endif

   sep= !version.os_family eq 'Windows' ? ';' : ':'

   port= 12345
   javahome= getenv( 'JAVA_HOME' )
   if javahome eq '' then javahome= 'c:/"program files"/java/jre1.6.0_03/'

   ; AUTOPLOT_HOME is the location of the Autoplot jar files
   aphome= getenv( 'AUTOPLOT_HOME' )
   
   jars= file_search( aphome+'lib/*.jar' )
   jars= [ aphome + 'VirboAutoplot.jar', jars ]
   classpath= strjoin( jars, sep )

   if keyword_set(respawn) then begin
      print, 'spawn autoplot java process'
      cmd= javahome+'/bin/java -cp '+classpath+ ' org.virbo.autoplot.AutoPlotUI --port='+strtrim(port,2)
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

   tmpfile= getenv('IDL_TMPDIR') + 'autoplot.d2s'
   tmpfile= strjoin( str_sep( tmpfile, '\' ), '/' )

   print, 'data transfer file is '+tmpfile

   ; serialize the data to a das2stream in a temporary file
   print, 'serialize the data to a das2stream in a temporary file'
   if n_params() eq 3 then begin
      data= { x: x, z:z }
      das2stream, data, tmpfile, ytags=y, ascii=0
   endif else if n_params() eq 2 then begin
      data= { x:x, y:y }
      das2stream, data, tmpfile, ascii=0
   endif else begin
      s= size( x )
      if s[0] eq 2 then begin
        data= { x:findgen(s[1]), z:x }
        das2stream, data, tmpfile, ytags=findgen(s[2]), ascii=0
      endif else begin
        data= { x:findgen(s[1]), y:x }
        das2stream, data, tmpfile, ascii=0
      endelse
   endelse

   ;openw, unit, tmpfile, /get_lun
   ;for i=0,n_elements(x)-1 do begin
   ;    printf, unit, string( x[i]) ,' ', string(y[i])
   ;endfor
   ;close, unit
   ;free_lun, unit

   catch, err
   if ( err eq 0 ) then begin
       socket, unit, 'localhost', port, /get_lun, write_timeout=1
       ;cmd= 'plot( ''file:/'+tmpfile+'?depend0=field0&column=field1'' )'
       if ( !version.os_family eq 'Windows' ) then tmpfile= '/'+tmpfile

       if n_elements( panel ) eq 1 then begin
          cmd= 'plot( '+strtrim(panel,2)+', ''file:'+tmpfile+''' );'  ; semicolon means no echo
       endif else begin
          cmd= 'plot( ''file:'+tmpfile+''' );'  ; semicolon means no echo
       endelse

       print, cmd
       printf, unit, cmd

       if n_elements( ytitle ) eq 1 then begin
          cmd= 'print dom.controller.plot.yaxis'  ;.label='''+ytitle+'''
          print, cmd
          printf, unit, cmd
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

end
