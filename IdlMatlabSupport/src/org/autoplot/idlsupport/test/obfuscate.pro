; this replaces all the data within a structure with dummy data, so that 
; private structures can be supported.  This might be used by:
; 1. load your structure into IDL memory.
; 2. compile obfuscate procedure.
; 3. run obfuscate, <your struct>
; 4. run save, file='test.idlsav', <your struct>

pro obfuscate, s
   t= tag_names(s)
   for j=0,n_elements(s)-1 do begin
       for i=0,n_elements(t)-1 do begin
           t= s[j].(i)
           if ( size(t,/type) eq 8 ) then begin
               obfuscate, t
           endif else if ( size(t,/n_dim) gt 0 ) then begin
               t[*]= 9.9
               s[j].(i)= t
           endif else begin
               s[j].(i)= 9
           endelse
       endfor
   endfor
end

       

