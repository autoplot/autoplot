function tryPortConnect, host, port, unit=unit
   socket, unit, 'localhost', port, error=error, /get_lun, write_timeout=1
   if ( error eq 0 ) then begin  
      close, unit
      free_lun, unit
   endif
   return, error
end

pro applot, x, y, z, _extra=e, respawn=respawn

   common applot_common, appid

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
       cmd= 'plot( ''file:'+tmpfile+''' )'

       print, cmd
       printf, unit, cmd
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
