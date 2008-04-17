pro applot, x, y, z, _extra=e, respawn=respawn

   common applot_common, appid

   sep= !version.os_family eq 'Windows' ? ';' : ':'

   port= 12344
   javahome= getenv( 'JAVA_HOME' )
   if javahome eq '' then javahome= 'c:/"program files"/java/jre1.6.0_03/'

   aphome= 'N:/nbprojects/virbo/VirboAutoplot/dist/'

   jars= file_search( aphome+'lib/*.jar' )
   jars= [ aphome + 'VirboAutoplot.jar', jars ]
   classpath= strjoin( jars, sep )

   if n_elements(appid) eq 0 or keyword_set(respawn) then begin
      print, 'spawn autoplot java process'
      cmd= javahome+'/bin/java -cp '+classpath+ ' org.virbo.autoplot.AutoPlotUI --port='+strtrim(port,2)
      print, cmd
      if !version.os_family eq 'Windows' then begin
        spawn, cmd, pid=appid, /nowait
      endif else begin
        spawn, cmd+' &', pid=appid, /nowait
      endelse
      wait, 1.0
   endif

   tmpfile= getenv('IDL_TMPDIR') + 'autoplot.d2s'
   tmpfile= strjoin( str_sep( tmpfile, '\' ), '/' )

   print, 'data transfer file is '+tmpfile

   ; serialize the data to a das2stream in a temporary file
   print, 'serialize the data to a das2stream in a temporary file'
   if n_params() eq 3 then begin
      data= { x: x, z:z }
      das2stream, data, tmpfile, ytags=y, /ascii
   endif else if n_params() eq 2 then begin
      data= { x:x, y:y }
      das2stream, data, tmpfile, /ascii
   endif else begin
      s= size( x )
      if s[0] eq 2 then begin
        data= { x:findgen(s[1]), z:x }
        das2stream, data, tmpfile, ytags=findgen(s[2]), /ascii
      endif else begin
        data= { x:findgen(s[1]), y:x }
        das2stream, data, tmpfile, /ascii
      endelse
   endelse

   ;openw, unit, tmpfile, /get_lun
   ;for i=0,n_elements(x)-1 do begin
   ;    printf, unit, string( x[i]) ,' ', string(y[i])
   ;endfor
   ;close, unit
   ;free_lun, unit

   socket, unit, 'localhost', port, /get_lun, write_timeout=1
   ;cmd= 'plot( ''file:/'+tmpfile+'?depend0=field0&column=field1'' )'
   cmd= 'plot( ''file:/'+tmpfile+''' )'

   print, cmd
   printf, unit, cmd
   close, unit
   free_lun, unit


end
