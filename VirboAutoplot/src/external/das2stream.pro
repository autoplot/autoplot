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
