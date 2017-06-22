; *******************************************************************
; PROJECT:      ESA Cluster Active Archive
; COMPONENT:    Cluster Exchange Format
; MODULE:       IDL CEF Reader
; LANGUAGE:     IDL 6.3
; AUTHOR:       c.h.perry@rl.ac.uk
; DATE:         2007-08-16
;
; Description:
; This is a preliminary simple IDL based reader for CAA data downloaded
; in the Cluster Exchange Format (CEF-2). This is an ALPHA release and
; there are some known limitations which mean that it may fall over
; when reading some correctly formatted CEF files.
;
; To try and maximise the speed little syntax checking is performed,
; and if you supply an invalid CEF the most likely outcome is that
; the software will fall over! If you are only using CEF files
; obtained from the CAA then these should already have been syntax
; checked and you should not encounter problems with the majority
; of data sets.
;
; The software is 100% IDL so has the advantage that it should run
; on any platform that supports IDL. Of course the disadvantage of
; an IDL only solution is performance when reading the ASCII CEF
; format. Some testing has been performed on Linux and PC but not
; yet on Mac or other platforms. As a benchmark, on the test systems
; the software will read CEF files at about 1-2 MB/s so if you have
; a 20 MB file it will take about 10-20s to read.

; This is is still an Alpha release so you may encounter problems.
; If you do, PLEASE REPORT THEM so that we can try and resolve them
; for all users!
;
; Usage:
;   result = cef_read( [filename], [/NODATA], [/JULDAY], [/CDFEPOCH] )
;
; If filename is not supplied then a file selection widget will be
; displayed.
; The result is an array of pointers to parameter structure containing
; the data and associated global and variable metadata.
; If /NODATA is used then the global metadata as a top level structure.
; If the /NODATA flag is not used then the global metadata is copied
; into each of the indivdual parameter sub-structures along with a
; DATA element that contains the actual data values. The result
; array therefore only consists of a set of pointers to a parameter
; structures.
; By default time will be returned as the source ASCII time string
; but you can use the /JULDAY or /CDFEPOCH keywords in which case
; the DATA element will be returned as a DOUBLE and contain the
; converted time.
;
; Example:
;   result = cef_read('fgm.cef.gz')
;    (time to read 93MB compressed file on test system 3m09s)
;
;    then:
;   FOR i=0, N_ELEMENTS(result)-1 DO PRINT, i,': ',(*result(i)).VARNAME
;    gives:
;       0: time_tags__C1_CP_FGM_FULL
;       1: half_interval__C1_CP_FGM_FULL
;       2: B_vec_xyz_gse__C1_CP_FGM_FULL
;       3: B_mag__C1_CP_FGM_FULL
;       4: sc_pos_xyz_gse__C1_CP_FGM_FULL
;       5: range__C1_CP_FGM_FULL
;       6: tm__C1_CP_FGM_FULL
;
;       HELP, (*result(0)).DATA, (*result(2)).DATA
;       <Expression>    DOUBLE    = Array[1, 5122157]
;       <Expression>    FLOAT     = Array[3, 5122157]
;
; *******************************************************************
; $Id: caa_cef_read.pro,v 2.6 2007/08/22 08:54:54 cperry Exp cperry $
; *******************************************************************
; *******************************************************************
; $Log: caa_cef_read.pro,v $
; Revision 2.6  2007/08/22 08:54:54  cperry
; Fixed problem with dimensioning of multi-dimensional arrays.
; See TN-0016 for details on how multi-dimensional arrays
; should be accessed using this software.
;
; *******************************************************************

; *************************************************************************************
;+
; NAME:
;   CaaNotify
;
; PURPOSE:
;   Currently this is just a dummy error message handling routine.
;   It just prints out the supplied string and if the error flag
;   was raised then it STOPs allowing the developer to investigate
;   the problem.
;
; CATEGORY:
;   Private-Support.
;
; CALLING SEQUENCE:
;
;   CaaNotify, <Err String>, [ /ERROR ], [ /WARN ], [/DEBUG], [ /QUIET ]
;
; INPUTS:
;   <Err String>    The string to be printed on stdout
;
; KEYWORD PARAMETERS:
;   /ERROR   Halt the program
;   /WARN     (not currently used)
;   /DEBUG   Debugging message (need to set QUIET=-1 to view
;   /QUIET   /QUIET switches off all but ERR and WARN
;      use QUIET=0 to re-enable standard messages
;
; OUTPUTS:
;   None
;
; EXAMPLE:
;
;   CaaNotify, 'File not found', /ERROR
;
; MODIFICATION HISTORY:
;   Written by:  c.h.perry@rl.ac.uk.
;   Aug, 2007 CHP: Initial version
;-
; *************************************************************************************
PRO caanotify, message, ERROR=err, WARN=warn, DEBUG=dbug, QUIET=ql

    COMMON caanotify_cb, quiet_level

    IF ( N_ELEMENTS(quiet_level) EQ 0 ) THEN quiet_level = 0
    IF ( N_ELEMENTS(ql) GT 0 ) THEN quiet_level = ql

    IF ( N_ELEMENTS(message) GT 0 ) THEN BEGIN
        IF ( quiet_level LE 2 AND KEYWORD_SET(err) )      THEN PRINT, "ERROR: "+message $
        ELSE IF ( quiet_level LE 1 AND KEYWORD_SET(warn) ) THEN PRINT, "WARN:  "+message $
        ELSE IF ( quiet_level LE -1 AND KEYWORD_SET(dbug)) THEN PRINT, "DEBUG: "+systime()+" - "+message $
        ELSE IF ( quiet_level LE 0 AND NOT KEYWORD_SET(dbug))   THEN PRINT, "INFO:  "+message
    ENDIF

    IF (KEYWORD_SET(err)) THEN message, message
END


; *************************************************************************************
;+
; NAME:
;   Iso2CdfEpoch
;
; PURPOSE:
;   Function to convert a STRARR of standard ISO times into a
;   corresponding DOUBLE array containing CDFepoch times.
;
; CATEGORY:
;   Private-Support.
;
; CALLING SEQUENCE:
;
;   result = Iso2CdfEpoch( <input> )
;
; INPUTS:
;   <input>         A STRARR of ISO times (YYYY-MM-DDTHH:MM:SS.sssZ)
;
; KEYWORD PARAMETERS:
;   None
;
; OUTPUTS:
;   <result>       A DBLARR of CDFepoch times
;
; EXAMPLE:
;
;   epoch = Iso2CdfEpoch( [ '2001-01-01T00:00:00', '2001-05-14T23:45:01' ] )
;
; MODIFICATION HISTORY:
;   Written by:  c.h.perry@rl.ac.uk.
;   Aug, 2007 CHP: Initial version
;-
; *************************************************************************************
FUNCTION iso2cdfepoch, iso

    ; *** Get the number of elements in the input array ***
    n  = N_ELEMENTS(iso)

    ; *** Create a DBL array to hold the result CDF epoch values
    epoch = DBLARR(1,n)

    ; *** Convert each input string to the corresponding CDF epoch value
    FOR i=0L, n-1 DO BEGIN
       READS, iso(i), y, m, d, hr, mn, sc, $
         FORMAT="(I4.4,X,I2.2,X,I2.2,X,I2.2,X,I2.2,X,F)"
       ms = ((hr*60.0+mn)*60.0+sc)*1000.0
       CDF_EPOCH, ep, y, m, d, 0, 0, 0, ms, /COMPUTE
       epoch(0,i) = ep
    ENDFOR

    ; *** Return the result ***
    RETURN, epoch
END


; *************************************************************************************
;+
; NAME:
;   Iso2Julday
;
; PURPOSE:
;   Function to convert and STRARR of standard ISO times into a
;   corresponding DOUBLE array containing Julian Dates.
;
; CATEGORY:
;   Private-Support.
;
; CALLING SEQUENCE:
;
;   result = Iso2Julday( <input> )
;
; INPUTS:
;   <input>             A STRARR of ISO times (YYYY-MM-DDTHH:MM:SS.sssZ)
;
; KEYWORD PARAMETERS:
;   None
;
; OUTPUTS:
;   <result>            A DBLARR of Julian Days
;
; EXAMPLE:
;
;   epoch = Iso2Julday( [ '2001-01-01T00:00:00', '2001-05-14T23:45:01' ] )
;
; MODIFICATION HISTORY:
;   Written by:  c.h.perry@rl.ac.uk.
;   Aug, 2007 CHP: Initial version
;-
; *************************************************************************************
FUNCTION iso2julday, iso

        ; *** Get the number of elements in the input array ***
        n  = N_ELEMENTS(iso)

        ; *** We are assuming that we have a fully specified time! ***
        nf = N_ELEMENTS(STRSPLIT(iso(0),'-:TZ'))

        ; *** Join and then split array into required elements ***
        iso1 = REFORM(STRSPLIT(STRJOIN(iso,',',/SINGLE),'-:TZ,',/EXTRACT),nf,n)

        ; *** Convert to Julian day. NB: Month, Day, Year, Hour, Min, Sec
        RETURN, JULDAY(iso1(1,*),iso1(2,*),iso1(0,*),iso1(3,*),iso1(4,*),iso1(5,*))
END


; *************************************************************************************
;+
; NAME:
;   FilterNames
;
; PURPOSE:
;   Procedure to select which var names are to be extracted from the file.
;   By default the search is done on the var name but an alternative
;   metadata item can be specified (e.g. CATDESC) by using the SEARCHTAG
;   keyword. By default any variables referenced (e.g. DEPEND_0) are also
;   selected. This can be switched off with the /NODEPEND keyword.
;
; CATEGORY:
;   Private-Support.
;
; CALLING SEQUENCE:
;
;   FilterNames, cef, varnames, /NODEPEND, SEARCHTAG='CATDESC'
;
; INPUTS:
;   cef             The cef structure that contains the metadata
;   varnames        A STRARR that contains a set of search terms
;                   used to select variables. Standard wildcards
;                   can be specified.
;
; KEYWORD PARAMETERS:
;   /NODEPEND       Don't try to include dependent variables
;   SEARCHTAG       The metadata item to search instead of varname
;
; OUTPUTS:
;   None            The REC_TYPE parameter attribute is updated in the
;                   cef structure
;
; EXAMPLE:
;
;   FilterNames, cef, ['B*']
;
; MODIFICATION HISTORY:
;   Written by:  c.h.perry@rl.ac.uk.
;   Aug, 2007 CHP: Initial version
;-
; *************************************************************************************
PRO FilterNames, cef, varnames, NODEPEND=nr_flag, SEARCHTAG=stag

    IF (N_ELEMENTS(stag) NE 1) THEN stag='VARNAME'
    stag = STRUPCASE(STRTRIM(stag,2))

    tags = TAG_NAMES(cef)
    vidx = WHERE(STRMID(tags,0,6) EQ 'PARAM_', nvar)

    dep_tags = ['DEPEND_0', 'DEPEND_1', 'DEPEND_2', 'DEPEND_3', $
       'DELTA_PLUS', 'DELTA_MINUS' ]
    ndtags = N_ELEMENTS(dep_tags)

    ;*** If no variables detected then no point in continuing
    IF (nvar EQ 0 ) THEN RETURN

    ;*** Initialise the table var names
    vars  = STRARR(nvar)
    varsd = STRARR(nvar)
    flags = INTARR(nvar)

    FOR i=0,nvar-1 DO BEGIN
       vtags = TAG_NAMES(cef.(vidx(i)))
       sidx  = WHERE(vtags EQ stag, sc)
       IF (sc EQ 1) THEN vars(i) = cef.(vidx(i)).(sidx(0))
       varsd(i) = cef.(vidx(i)).VARNAME
    ENDFOR

    ;*** Search for vars that match requested input
    FOR i=0,N_ELEMENTS(varnames)-1 DO $
       flags = flags + STRMATCH(vars, varnames(i), /FOLD_CASE)

    ;*** Check if we need to look for dependencies
    IF ( NOT KEYWORD_SET(nr_flag) ) THEN BEGIN
       ;*** Take the vars that match our request
       nmatch1 = 0
       idx = WHERE(flags GT 0, nmatch)
       WHILE( nmatch1 LT nmatch ) DO BEGIN
          ;*** For each of these matches
          FOR j=0,nmatch-1 DO BEGIN

              ;*** Check the metadata items
              vtags = TAG_NAMES(cef.(vidx(idx(j))))

              ;*** For each of the tags that may contain a reference
              FOR k=0,ndtags-1 DO BEGIN

                  ;*** See if this tag is included for this var
                  didx = WHERE( vtags EQ dep_tags(k), dn )
                  IF ( dn GT 0 ) THEN BEGIN

                      ;*** Check that its a simple string attribute
                      ;*** If so then check it against our var list
                      IF (N_ELEMENTS(cef.(vidx(idx(j))).(didx[0])) EQ 1 $
                          AND SIZE(cef.(vidx(idx(j))).(didx[0]),/TYPE) EQ 7 ) THEN $
                          flags = flags + STRMATCH(varsd, cef.(vidx(idx(j))).(didx[0]), /FOLD_CASE)
                  ENDIF
              ENDFOR
          ENDFOR
          nmatch1 = nmatch
          idx = WHERE(flags GT 0, nmatch)
       ENDWHILE
    ENDIF

    ;*** Disable parameters that we don't want to keep
    FOR i=0,nvar-1 DO IF (flags(i) LE 0) THEN cef.(vidx(i)).REC_TYPE=-99

END

; *************************************************************************************
;+
; NAME:
;   FindInclude
;
; PURPOSE:
;   Function to try and resolve an include file name by checking
;   the ':' seperated list of include directories given by the
;   environment variable CEF_INCLUDE. If no match is found then
;   an error is generated.
;
; CATEGORY:
;   Private-Support.
;
; CALLING SEQUENCE:
;
;   result = FindInclude( <input> )
;
; INPUTS:
;   <input>             A string containing the include filename
;
; KEYWORD PARAMETERS:
;   None
;
; OUTPUTS:
;   <result>            The fully resolved directory/filename
;
; EXAMPLE:
;
;   epoch = FindInclude( 'test.ceh' )
;
; MODIFICATION HISTORY:
;   Written by:  c.h.perry@rl.ac.uk.
;   Aug, 2007 CHP: Initial version
;-
; *************************************************************************************

FUNCTION FindInclude, inc_name

    ; *** Get the list of include directories from the environment variable
    inc_dirs = [STRSPLIT(GETENV('CEF_INCLUDE'),':',/EXTRACT)]

    flag = 0     ; *** =0 if no match, 1 if match is found
    i    = 0     ; *** index into array of includ directories

    ; *** Look for includ file in the include directories

    WHILE( flag EQ 0 AND i LT N_ELEMENTS(inc_dirs) ) DO BEGIN
       fname = FILEPATH( inc_name, ROOT=inc_dirs(i) )
       IF ( inc_dirs(i) NE '' AND FILE_TEST( fname, /READ ) ) THEN $
             flag = 1 $
       ELSE  i = i + 1
    ENDWHILE

    ; *** Found a match!
    IF ( flag EQ 1 ) THEN RETURN, fname

    ; *** Didn't find a match
    CaaNotify, "Include file not found: "+inc_name, /ERR

    RETURN, inc_name
END

; *************************************************************************************
;+
; NAME:
;   CefSplitRec
;
; PURPOSE:
;   Just splits a CEF header record into its key and value parts
;   using the first '=' in the record as the delimiter. The key
;   part is converted to upper case to ease comparison. The value
;   is split into an array of elements separated by ','. If the
;   elements are quoted strings then the quotes are removed.
;
; CATEGORY:
;   Private-Support.
;
; CALLING SEQUENCE:
;
;   <status> = CefSplitRec( <record>, <key>, <value> )
;
; INPUTS:
;   <record>       The CEF header record being processed
;
; KEYWORD PARAMETERS:
;   None.
;
; OUTPUTS:
;   <status>       The function returns 1 if successful and 0 otherwise
;   <key>          The key part of the 'key = value'
;   <value>        The value part of the 'key = value'
;
; EXAMPLE:
;
;   IF ( CefSplitRec( 'VALUE_TYPE = FLOAT', key, value ) ) THEN PRINT, key, value
;
; MODIFICATION HISTORY:
;   Written by:  c.h.perry@rl.ac.uk.
;   Aug, 2007 CHP: Initial version
;-
; *************************************************************************************
FUNCTION CefSplitRec, record, key, val

    status = 0     ;*** Set default status

    ; *** look for comment
    pos = STRPOS( record, '!', /REVERSE_SEARCH )
    IF ( pos GT -1 ) THEN record = STRMID(record,0,pos-1)

    ; *** look for key/value delimiter ***
    pos = STRPOS( record, '=' )
    IF ( pos GT -1 ) THEN BEGIN
       status = 1

       ;*** Extract the key ***
       key = STRUPCASE(STRTRIM(STRMID(record,0,pos),2))

       ;*** Extract the value ***
       val = STRTRIM(STRMID(record,pos+1),2)

       ;*** Split value into separate array elements
       ;*** Handle quoted string elements

       IF (STRMID(val,0,1) EQ '"') THEN BEGIN
           val = STRSPLIT(STRMID(val,1,STRLEN(val)-2), $
               '"[ '+STRING(9B)+']*,[ '+STRING(9B)+']*"',/REGEX, /EXTRACT)
       ENDIF ELSE BEGIN
           val = STRTRIM(STRSPLIT(val,',',/EXTRACT),2)
       ENDELSE
    ENDIF

    RETURN, status
END


; *************************************************************************************
;+
; NAME:
;   CefReadHeadRec
;
; PURPOSE:
;   Reads the next CEF header line from the specified unit. If a continuation
;   marker is encountered then the next line is read and appended to the
;   the record. Comment lines are ignored.
;
; CATEGORY:
;   Private-Support.
;
; CALLING SEQUENCE:
;
;   <status> = CefReadHeadRec( <logical-unit>, <output-record> )
;
; INPUTS:
;   <logical-unit>        The logical unit which is connected to the input file
;
; KEYWORD PARAMETERS:
;   None.
;
; OUTPUTS:
;   <status>         1 if header record was extracted and 0 if EOF is reached.
;   <output-record>   A string that contains the full header record
;
; EXAMPLE:
;
;   IF ( CefReadHeadRec( lun, record ) ) THEN PRINT, record
;
; MODIFICATION HISTORY:
;   Written by:  c.h.perry@rl.ac.uk.
;   Aug, 2007 CHP: Initial version
;-
; *************************************************************************************
FUNCTION CefReadHeadRec, lun, record

    status = 0     ; *** Status flag, set to 1 if complete record found
    readFlag = 1     ; *** used to flag multi-line records
    record = ''       ; *** String used to hold input

    ;*** Keep reading unit until got complete entry or end of file ***
    WHILE ( readFlag AND (NOT EOF(lun)) ) DO BEGIN

       ;*** read next record ***
       tempRecord = ''
       READF, lun, tempRecord

       tempRecord = STRTRIM(tempRecord, 2)   ;*** Trim blanks from start/end

       ;*** skip comment lines ***
       IF (STRMID(tempRecord,0,1) EQ '!' ) THEN BEGIN
         ; PRINT, tempRecord
       ENDIF ELSE IF ( STRMID(tempRecord, 0, 1, /REVERSE) EQ '\' ) THEN BEGIN
           record = record + STRMID(tempRecord,0,STRLEN(tempRecord)-1)
       ENDIF ELSE BEGIN
           record = record + tempRecord
           ; *** If not blank then finish read  of this record ***
           IF ( STRTRIM(record, 2) NE '' ) THEN BEGIN
               readFlag = 0
               status = 1
           ENDIF ELSE record = ''
       ENDELSE

    ENDWHILE

    RETURN, status
END


; *************************************************************************************
;+
; NAME:
;   CefReadData
;
; PURPOSE:
;   Reads the data part of a CEF file. It is assumed that the logical unit points
;   to the start of the first record to be read from the file. To maximise performace
;   there is no error checking so if it hits a file that it does not like then it
;   will probably fall over. Note that if it does fall over then there maybe some
;   very large unreferenced heap variables left over. To garbage collect these do a
;   RETALL then HEAP_GC,/VER or exit from IDL and then restart. Use HELP,/MEM if
;   you are unsure as to how much memory IDL is currently using.
;
; CATEGORY:
;   Private-Support.
;
; CALLING SEQUENCE:
;
;   CefReadData, <lun>, <cef>, <param>
;
; INPUTS:
;   <lun>     Logical unit connected to the input file
;   <cef>     The structure containing the header information
;
; KEYWORD PARAMETERS:
;   /JULDAY     Returns times in Julian day rather than string
;   /CDFEPOCH     Returns times in CDF epoch rather than string
;   /NOCONV     No conversion. Leave all data fields as strings.
;
; OUTPUTS:
;   <param>     The output array containing pointers to the parameter data
;
; EXAMPLE:
;
;   CefReadData, lun, cef, param
;
; MODIFICATION HISTORY:
;   Written by:  c.h.perry@rl.ac.uk.
;   Aug, 2007 CHP: Initial version
;-
; *************************************************************************************
PRO CefReadData, lun, cef, param, TRANGE=trange, $
    JULDAY=jd_flag, CDFEPOCH=ep_flag, NOCONV=nc_flag

    CaaNotify, 'Reading data records, please wait...'

    ; *** Define the read buffer that we'll use to pull in chunks
    ; *** of the file. This is a trade off between memory footprint
    ; *** and speed. Note that the working buffer is twice this size.
    ; *** The buffer size should be much bigger than the typical
    ; *** record size or perfromance will be worse than just reading
    ; *** a records at a time.
    buffer_size = 2000000L
    read_buffer = BYTARR(buffer_size)
    work_buffer = BYTARR(2*buffer_size)
    work_size   = 0L

    ; *** Define the delimeters used in the CEF file
    comment=(BYTE('!'))[0]
    eor=(BYTE(cef.EOR))[0]
    comma=(BYTE(','))[0]
    eol=10B

    ; *** Build a new CEF structure so that we can modify the parameter
    ; *** sub-structures.
    ceftags = TAG_NAMES(cef)
    FOR i=0, N_ELEMENTS(ceftags) - 1 DO BEGIN
       IF ( i EQ 0 ) THEN cef1 = CREATE_STRUCT(ceftags(i), cef.(i)) $
       ELSE IF ( STRMID(ceftags(i),0,6) NE 'PARAM_' ) THEN $
           cef1 = CREATE_STRUCT( cef1, ceftags(i), cef.(i))
    ENDFOR

    cef1.nparam =1        ;*** set nparam to 1 when it is added to the parameter data

    ; *** Set the processing state flag (1=first record, 2=subsequent records, 0 = end of file )
    flag     = 1     ;*** set to 1 for first page, 2 for subsequent pages and 0 when read to end of file
    trflag   = 1     ;*** set to 0 if no more data required in requested time range
    n_rec    = 0     ;*** number of records read
    n_fields = 0     ;*** number of fields per record

    ; *** Keep reading until we reach the end of the file.
    WHILE( NOT EOF(lun) AND trflag GT 0 ) DO BEGIN

       ;*** read the next chunk of the file
       ;*** catch errors to avoid warning message if we read past end of file
       ON_IOERROR, skip
       READU, lun, read_buffer
skip:  ON_IOERROR, NULL

       ;*** calculate how much data we've read
       IF ( read_buffer(buffer_size - 1) NE 0B ) THEN read_size = buffer_size $
       ELSE read_size=(WHERE(read_buffer EQ 0B))[0]

       ;*** transfer this onto the end of the work buffer and update size of work buffer
       IF (read_size GT 0 ) THEN work_buffer[work_size] = $
           read_buffer[0:read_size-1]
       work_size = work_size + read_size

       ;*** look for delimeters, EOR, comments, EOL etc

       pos_eor = WHERE( work_buffer(0:work_size-1) EQ eor, n_eor )
       IF ( n_eor GT 0 ) THEN BEGIN
           pos1 = pos_eor(n_eor-1)+1
           pos2 = work_size-1
           work_size = pos1-1
       ENDIF ELSE BREAK

       pos_comment = WHERE( work_buffer(0:work_size-1) EQ comment, n_comment )

       pos_eol = WHERE( work_buffer(0:work_size-1) EQ eol, n_eol )

       ;*** search for blank lines and remove (only important if eol is also eor)
       ;*** DEBUG - this has not yet been tested and is currently disabled
       ;idx_el = WHERE( idx2(1:c-1)-idx2(0:c-2) LE 10, c1 )
       ;IF (c1 GT 0) THEN $
       ; FOR i=0L, c1-1 DO buf(idx2(idx2a(i)+1)) = 32B

       ;*** remove comment lines by setting to spaces (32B)
       IF (n_comment GT 0 ) THEN BEGIN
           FOR i=0L, n_comment-1 DO BEGIN
               j = pos_comment(i)
               WHILE( work_buffer(j) NE eol ) DO BEGIN
                   work_buffer(j) = 32B
                   j = j+1
               ENDWHILE
               work_buffer(j) = 32B
           ENDFOR
       ENDIF

       ;*** remove eol by setting to spaces (32B)
       IF (n_eol GT 0 ) THEN work_buffer(pos_eol) = 32B

       ;*** we replace all eor with comma so we can quickly split the buffer
       ;*** this assumes that the data is well formed with the same number
       ;*** of fields on every record.
       work_buffer(pos_eor) = comma

       ;*** work out number of fields per record by counting commas up to first eor
       IF ( n_fields EQ 0 ) THEN temp = WHERE( work_buffer(0:pos_eor(0)) EQ comma, n_fields )

       ;*** split the buffer into separate fields
       fields    = STRTRIM(STRSPLIT(STRING(work_buffer(0:work_size-1)), ',', /EXTRACT),2)

       nt_fields = N_ELEMENTS(fields)
       if ( fields[nt_fields-1] eq '' ) then begin   ; test_read_caa(2)
          fields= fields[0:nt_fields-2]
          nt_fields = N_ELEMENTS(fields)
       endif


       ;*** reform the to a num_fields x num_rec array
       IF ( (nt_fields MOD n_fields) NE 0 ) THEN CaaNotify, "Bad number of fields - truncated file?",/ERR
       fields = REFORM(fields, n_fields, N_ELEMENTS(fields)/n_fields, /OVERWRITE )
       n_recp = N_ELEMENTS(fields)/n_fields

       ;*** see if we want to subset the data range
       IF ( N_ELEMENTS(trange) GT 0 ) THEN BEGIN
            ridx   = LONARR(n_recp)
        trflag = 0
            FOR it=0,N_ELEMENTS(trange)-1 DO BEGIN
                ts = STRSPLIT(trange[it],'/',/EXTRACT)
                idx1 = WHERE(fields(0,*) GE ts(0) AND fields(0,*) LT ts(1), tcount)
                IF ( tcount GT 0 ) THEN ridx(idx1)=ridx(idx1)+1
       IF ( fields(0,n_recp-1) LT ts(1) ) THEN trflag = trflag + 1
            ENDFOR
            ridx = WHERE( ridx GT 0, tcount )
            IF ( tcount LE 0 ) THEN GOTO, skip1
       ENDIF ELSE ridx = LINDGEN(n_recp)

       n_rec = n_rec + N_ELEMENTS(ridx)
       CaaNotify, "Reading data, "+fields(0,0)+" to "+fields(0,n_recp-1)+" Stored rec #:"+STRING(n_rec), /DEBUG

       tmp_dpa = PTRARR(cef.nparam)

       FOR i = 0, cef.nparam-1 DO BEGIN
           name = STRING('PARAM_',i+1, FORMAT='(A,I3.3)')
           idx  = (WHERE(ceftags EQ name))[0]

           ;IF ( i GT 0 ) THEN cef.(idx).REC_TYPE = -2

           IF ( cef.(idx).REC_TYPE GT 0 ) THEN BEGIN
               IF ( KEYWORD_SET(nc_flag) ) THEN vt = 'CHAR' ELSE vt = cef.(idx).VALUE_TYPE

               s1 = cef.(idx).CEF_FIELD_POS(0)
               s2 = cef.(idx).CEF_FIELD_POS(1)

               CASE vt OF
                   'CHAR':         tmp_dpa(i) = PTR_NEW(fields[s1:s2,ridx])
                   'DOUBLE':       tmp_dpa(i) = PTR_NEW(DOUBLE(fields[s1:s2,ridx]),/NO_COPY)
                   'FLOAT':        tmp_dpa(i) = PTR_NEW(FLOAT(fields[s1:s2,ridx]),/NO_COPY)
                   'INT':          tmp_dpa(i) = PTR_NEW(LONG(fields[s1:s2,ridx]),/NO_COPY)
                   'ISO_TIME':     IF (KEYWORD_SET(jd_flag) ) THEN  $
                                       tmp_dpa(i) = PTR_NEW(iso2julday(fields[s1:s2,ridx]),/NO_COPY)  $
                                   ELSE IF (KEYWORD_SET(ep_flag) ) THEN $
                                       tmp_dpa(i) = PTR_NEW(iso2cdfepoch(fields[s1:s2,ridx]),/NO_COPY)  $
                                   ELSE tmp_dpa(i) = PTR_NEW(fields[s1:s2,ridx])
                   'ISO_TIME_RANGE': tmp_dpa(i) = PTR_NEW(fields[s1:s2,ridx])
                   ELSE:           tmp_dpa(i) = PTR_NEW(fields[s1:s2,ridx])
               ENDCASE
           ENDIF
       ENDFOR

       IF ( flag EQ 2 ) THEN dpa = [ [dpa], [TEMPORARY(tmp_dpa)] ] $
       ELSE BEGIN
           flag = 2
           dpa = REFORM(TEMPORARY(tmp_dpa),cef.nparam,1)
       ENDELSE

       ;*** we want to keep the part of the buffer not yet processed
skip1: IF (pos2-pos1 GE 0 ) THEN BEGIN
           work_buffer(0) = work_buffer(pos1:pos2)
           work_size = pos2-pos1+1
       ENDIF ELSE work_size = 0

       ;*** keep going until there is no more file to read
       IF ( read_size LT buffer_size ) THEN flag = 0

    ENDWHILE

    ;*** Finished with input file
    FREE_LUN, lun

    ;*** Release memory used by the work and read buffers
    work_buffer = 0
    read_buffer = 0

    ;*** Build the result array - we've read all the data so we know how many records there are!
    cef1.nrec = n_rec  ;*** Store the number of records in the metadata structure
    cef.nrec  = n_rec


    ;*** For each parameter build a metadata/data struture
    ;*** In theory we could filter out parameters that we don't want
    ;*** which would save processing time and memory. Ideally we would
    ;*** also do that above when we generate the dpa pointers.
    IF ( N_ELEMENTS(dpa) GT 0 ) THEN npage = (SIZE(dpa,/DIM))[1] ELSE npage =0
    flag = 0

    FOR i = 0, cef.nparam-1 DO BEGIN

        name = STRING('PARAM_',i+1, FORMAT='(A,I3.3)')
        idx  = (WHERE(ceftags EQ name))[0]
        pstru = CREATE_STRUCT(cef1, cef.(idx))

        ;*** Hack so we don't fall over if we get an empty file
        ;*** REC_TYPE: -2=no rec, -1=param disabled, 0=non-record vary, 1=normal
        IF ( n_rec EQ 0 AND pstru.REC_TYPE GT 0 ) THEN BEGIN
            pstru.REC_TYPE = -2
            pstru.nrec = 0
        ENDIF

        IF ( pstru.REC_TYPE GT 0 ) THEN BEGIN ;***For record varying parameters ***

            ;*** Check value type for conversion
            IF ( KEYWORD_SET(nc_flag) ) THEN vt = 'CHAR' ELSE vt = pstru.VALUE_TYPE

            CaaNotify, "Creating data array for "+pstru.VARNAME, /DEBUG
            ;*** Create the DATA array ***
            CASE vt OF

               'CHAR':    pstru = CREATE_STRUCT( pstru, 'DATA', STRARR([pstru.SIZES, n_rec]) )
               'DOUBLE':  pstru = CREATE_STRUCT( psrtu, 'DATA', DBLARR([pstru.SIZES, n_rec]) )
               'FLOAT':   pstru = CREATE_STRUCT( pstru, 'DATA', FLTARR([pstru.SIZES, n_rec]) )
               'INT':     pstru = CREATE_STRUCT( pstru, 'DATA', LONARR([pstru.SIZES, n_rec]) )
               'ISO_TIME':  IF ( KEYWORD_SET(jd_flag) OR KEYWORD_SET(ep_FLAG) ) THEN $
                                  pstru = CREATE_STRUCT( pstru, 'DATA', DBLARR([pstru.SIZES, n_rec]) ) $
                            ELSE  pstru = CREATE_STRUCT( pstru, 'DATA', STRARR([pstru.SIZES, n_rec]) )
               'ISO_TIME_RANGE':pstru = CREATE_STRUCT( pstru, 'DATA', STRARR([pstru.SIZES, n_rec]) )
               ELSE:     pstru = CREATE_STRUCT( pstru, 'DATA', STRARR([pstru.SIZES, n_rec]) )
            ENDCASE

            ;*** Fill the arrays ***
            dpos = 0L         ;*** Current record number
            s    = pstru.CEF_FIELD_POS(1) - pstru.CEF_FIELD_POS(0) + 1

            ;*** Check value type for conversion

            ;*** For each of data pages that we've read
            FOR j=0, npage-1 DO BEGIN
       siz = SIZE(*dpa(i,j),/DIM)
                IF (N_ELEMENTS(siz) EQ 2 ) THEN pn  = siz(1) ELSE pn = 1   ;*** Number of records in this block ***
                p1  = dpos*s        ;*** Start psoition
                p2  = p1 + pn*s - 1   ;*** End position
                pstru.DATA[p1:p2] = *dpa(i,j)
                PTR_FREE, dpa(i,j)

                dpos = dpos+pn       ;*** Update record position

            ENDFOR
        ENDIF

        ;*** Create the output array as a collection of the parameter strutures
        IF ( pstru.REC_TYPE GT -99 ) THEN BEGIN
            IF ( flag EQ 0 ) THEN param = [ PTR_NEW(pstru, /NO_COPY) ] $
            ELSE param = [ param, PTR_NEW(pstru, /NO_COPY) ]
            flag = flag + 1
        ENDIF
    ENDFOR

    ;*** Make sure we don't leave any unwanted storage allocated
    IF (N_ELEMENTS(dpa) GT 0) THEN HEAP_FREE, dpa

    CaaNotify, "Reading of data complete", /DEBUG
END

;==============================================================================================
; *** START OF PUBLIC ROUTINES
;==============================================================================================

; *************************************************************************************
;+
; NAME:
;   cef_get_attr
;
; PURPOSE:
;   Returns the requested attribute from the variable
;
; CATEGORY:
;   Public-Support
;
; CALLING SEQUENCE:
;
;   <result> = cef_get_attr( vararr, varid, attr, STATUS=status )
;
; INPUTS:
;   vararr  The variable pointer array returned from cef_read
;   varid   The variable index. This can be either the index number
;     for the variable array element in vararr or the name of
;     the variable.
;   attr    The attribute name to be searched for
;
; KEYWORD PARAMETERS:
;   STATUS=stat Returns the status of the search
;     -1 If the variable could not be identified
;     -2 If the attribute was not found
;     1 If a unique match was found
;     2 If multiple attributes match, only the first is returned
;
; OUTPUTS:
;   <results>   Either the attribute from the requested variable or an empty string.
;
; EXAMPLE:
;
;   result = cef_get_attr( vararr, 0, 'VARNAME')
;
; MODIFICATION HISTORY:
;   Written by:  c.h.perry@rl.ac.uk.
;   Aug, 2007 CHP: Initial version
;-
; *************************************************************************************

FUNCTION cef_get_attr, varptr, varid, attr, STATUS=status

    status = 0

    ; *** If varid given as STRING then need to work out varptr index
    IF ( SIZE(varid,/TYPE) EQ 7 ) THEN BEGIN
       varidx = -1
       FOR i = 0, N_ELEMENTS(varptr)-1 DO BEGIN
         IF ( STRMATCH(cef_get_attr(varptr, i, 'VARNAME'), varid, /FOLD_CASE) ) THEN BEGIN
          varidx = i
          BREAK
         ENDIF
       ENDFOR
    ENDIF ELSE BEGIN
       IF ( varid GE 0 AND varid LT N_ELEMENTS(varptr) ) THEN varidx = varid $
       ELSE varidx = -1
    ENDELSE

    ; *** Check if we found a variable
    IF ( varidx LT 0 ) THEN BEGIN
       CaaNotify, 'Request for attribute from unknown variable: '+varid, /DEBUG
       status = -1
       RETURN, ''
    ENDIF

    ; *** Now check for attribute
    tags = TAG_NAMES(*varptr(varidx))
    idx  = WHERE(STRMATCH(tags, attr, /FOLD_CASE), count)

    IF ( count EQ 0 ) THEN BEGIN     ;*** No matches, return empty string and set status
       status = -2
       RETURN, ''
    ENDIF ELSE IF ( count EQ 1 ) THEN BEGIN    ;*** One match
       status = 1
       RETURN, (*varptr(varidx)).(idx(0))
    ENDIF ELSE BEGIN      ;*** Multiple matches, just return first but set status
       status = 2
       RETURN, (*varptr(varidx)).(idx(0))
    ENDELSE
END

; *************************************************************************************
;+
; NAME:
;   cef_read
;
; PURPOSE:
;   Read a CEF file into an IDL struture
;
; CATEGORY:
;   Public-Support
;
; CALLING SEQUENCE:
;
;   <result> cef_read( [filename], [INCLUDE=cef], [/NODATA], [/JULDAY], [/CDFEPOCH] )
;
; INPUTS:
;   filename   Filename of CEF to be read. If not supplied a file selection
;           widget is displayed. CEF or Gzip'd CEF can be specified.
;
; KEYWORD PARAMETERS:
;   VAR=[names]     Array of regular expression of names to match. By default
;      all variables will be returned.
;   INCLUDE=cef     [PRIVATE] Used for recursive reading of include files.
;   /NODEPEND     Normally any depend variables will be included even if
;      not explicitly requested. Use this option to disable
;      the inclusion of any referenced variables.
;   /NODATA     Only read header information
;   /JULDAY     Returns times in Julian Days rather than the default iso string
;   /CDFEPOCH     Returns times in CDF EPOCH rather than the default iso string
;
; OUTPUTS:
;   <results>     An array (or structure, when /NODATA) that contains the result
;
; EXAMPLE:
;
;   result = cef_read( 'fgm.cef' )
;
; MODIFICATION HISTORY:
;   Written by:  c.h.perry@rl.ac.uk.
;   Aug, 2007 CHP: Initial version
;-
; *************************************************************************************
FUNCTION cef_read, readFile, INCLUDE=cef, NODATA=nd_flag, VAR=vnames, $
        NODEPEND=nr_flag, SEARCHTAG=stag, _EXTRA=extra

       HEAP_GC,/VER

       ;*** If the header structure is not yet defined then create it
       IF (N_ELEMENTS(cef) EQ 0) THEN cef = { ERROR: 0, NGLOBAL:0, NPARAM:0, NREC:0L, EOR:10B }

       ;*** Check flags
       IF ( NOT KEYWORD_SET(nr_flag) ) THEN nr_flag = 0
       nv_flag = N_ELEMENTS(vnames)

       ;*** If a file is not specified ask user to pick one
       IF ( NOT KEYWORD_SET(readFile)) THEN $
           readFile=DIALOG_PICKFILE(FILTER="*.cef;*.cef.gz", /READ, /MUST_EXIST)

       ;*** Make sure the input file is accessible
       IF (NOT FILE_TEST(readFile, /READ)) THEN BEGIN
            CaaNotify,"Can't open or read file "+readFile+" for import", /ERROR
            cef.ERROR = 1
            RETURN, cef
       ENDIF

       ;*** Set result to dummy value
       param = -1

       CaaNotify, "Reading "+readFile

       ;*** Look at file extension to determine if it is a compressed file
       IF ( STRUPCASE(STRMID(readFile,2,3,/REVERSE)) EQ ".GZ" ) THEN $
            compressFlag = 1 $
       ELSE compressFlag = 0

       ; *** Open the file ***
       OPENR, lun, readFile, /GET_LUN, COMPRESS=compressFlag, ERROR=err
       IF ( err NE 0 ) THEN BEGIN
            dummy = DIALOG_MESSAGE(!ERROR_STATE.MSG, /ERROR)
            cef.ERROR = 1
            RETURN, cef
       ENDIF

       ; *** Set the initial state of the header parser ***
       state  = "TOP"
       state1 = "END"

       ; *** data index
       pdata = 0

       ; *** Keep reading until end of header information or no more records ***
       WHILE( state NE "DATA_READ" AND state NE "END" ) DO BEGIN

            ;*** Try to read header record
            IF ( NOT CefReadHeadRec(lun, record) ) THEN BREAK

            ; *** Get the keyword/value(s) for this record
            IF ( CefSplitRec(record, key, value) ) THEN BEGIN

                ;*** Use the parser state to check what we are looking for
                CASE state OF

                "TOP": BEGIN

                    ;*** Use the keyword to determine the action
                    CASE key OF

                    "START_META": BEGIN     ;*** New global metadata item ***
                        state = "GLOBAL"
                        gName = value[0]
                        gStru = { VALUE_TYPE: "CHAR" }
                        eCount  = 0
                    END


                    "START_VARIABLE": BEGIN     ;*** New parameter ***
                        state = "PARAM"
                        pName = value[0]
                        pStru = { REC_TYPE:1, VARNAME:pName }
                    END

                    "INCLUDE": IF ( value[0] NE readFile ) THEN $
                        param = cef_read(findinclude(value[0]), INCLUDE=cef, _EXTRA=extra)

                    "DATA_UNTIL": BEGIN     ;*** Start of data ***
                        state = "DATA_READ"
                        cef = CREATE_STRUCT( cef, 'DATA_UNTIL', value[0])
                    END

                    ;*** Special CEF defined items at the top level ***

                    "FILE_NAME": cef = CREATE_STRUCT( cef, 'FILE_NAME', value[0] )

                    "FILE_FORMAT_VERSION": cef = CREATE_STRUCT( cef, 'FILE_FORMAT_VERSION', value[0] )

                    "END_OF_RECORD_MARKER": cef.EOR = (BYTE(value[0]))[0]

                    ELSE: CaaNotify, "Unsupported key "+key,/ERR

                    ENDCASE
                END


                "GLOBAL": BEGIN        ;*** Global metadata handling

                    IF (N_ELEMENTS(value) GT 1) THEN $
                        CaaNotify, "Global entry not allowed multiple values per entry : "+gName,/ERR

                    CASE key OF
                    "END_META":   BEGIN
                        state = "TOP"
                        IF (value[0] NE gName) THEN $
                            CaaNotify, "END_VARIABLE expected "+gName+"  got "+value[0],/ERROR
                        IF ( ecount EQ 1 ) THEN gStru = CREATE_STRUCT( gStru, 'ENTRY', element[0] ) $
                        ELSE gStru = CREATE_STRUCT( gStru, 'ENTRY', element )
                        cef.nglobal = cef.nglobal+1
                        IF ( gstru.VALUE_TYPE EQ 'CHAR' ) THEN cef = CREATE_STRUCT( cef, gname, gStru.ENTRY ) $
                        ELSE cef = CREATE_STRUCT( cef, gname, gStru )
                    END
                    ; *** WARNING: In theory CEF allows a different VALUE_TYPE for each entry
                    ; *** this is a 'feature' from CDF but I can't think of a situation where
                    ; *** it is useful. This feature is not currently supported by this
                    ; *** software and so we just assign a type based on the last specification
                    ; *** of the VALUE_TYPE.
                    "VALUE_TYPE": gStru.VALUE_TYPE = value[0]
                    "ENTRY": BEGIN
                         ; *** If this is the second entry then must be multi entry global ***
                         IF ( eCount EQ 0 ) THEN element = [ value[0] ] $
                         ELSE element = [ element, value[0] ]

                         eCount = eCount + 1
                    END
                    ELSE: CaaNotify, "Unsupported global key "+key  , /ERR
                    ENDCASE
                END

                "PARAM": BEGIN       ;*** Parameter description handling
                    IF ( key EQ "END_VARIABLE") THEN BEGIN
                        ;*** Set some defaults if not provided in the file
                        tags = TAG_NAMES(pStru)
                        id = WHERE( tags EQ 'SIZES', c )
                        IF ( c EQ 0 ) THEN pStru = CREATE_STRUCT( pStru, 'SIZES', 1L )

                        IF (pStru.REC_TYPE EQ 0) THEN data_idx = [-1L,-1L] $
                        ELSE BEGIN
                            n = LONG(pstru.SIZES[0])
                            FOR i=1, N_ELEMENTS(pstru.SIZES)-1 DO n = n * LONG(pstru.SIZES[i])
                            data_idx = [pdata,pdata+n-1]
                            pdata = pdata+n
                        ENDELSE
                        pStru = CREATE_STRUCT(pStru, 'CEF_FIELD_POS', data_idx)
                        ;*** Change parser state
                        state = "TOP"
                        ;*** Check this is the end of the correct parameter!
                        IF (value[0] NE pName) THEN $
                           CaaNotify, "END_VARIABLE expected "+pName+"  got "+value[0],/ERROR
                        ;*** Update the number of parameters
                        cef.nparam = cef.nparam + 1
                        ;*** Parameter names can be too long for structure element name
                        ;*** so just use a sequential number
                        pname = STRING('PARAM_',cef.nparam, FORMAT='(A,I3.3)')
                        cef = CREATE_STRUCT( cef, pname, pStru )

                    ENDIF ELSE BEGIN
                        IF ( key EQ 'DATA' ) THEN BEGIN
                            pStru.REC_TYPE = 0   ;*** Flag non-record varying data
                            ;********************************
                            ;At the moment we just add non-record varying data as string array
                            ;we should really check SIZES and VALUE_TYPE and reform and retype
                            ;data as we do for the real data fields. Something for the next release?
                            ;********************************
                        ENDIF ELSE IF ( key EQ 'SIZES' AND N_ELEMENTS(value) GT 1 ) THEN BEGIN
          value = REVERSE( value )
         ENDIF

                        IF (N_ELEMENTS(value) GT 1 ) THEN pStru = CREATE_STRUCT( pstru, key, value ) $
                        ELSE pStru = CREATE_STRUCT( pStru, key, value[0] )
                    ENDELSE
                END

                ENDCASE
            ENDIF ELSE CaaNotify, "Bad record?  "+record, /ERROR
       ENDWHILE

       ;*** Finished reading the header in the current file
       ;*** if state is END then we are at the end of the file
       ;*** for example this may be the end of an include file
       ;*** If the state is DATA_READ then we've reached the
       ;*** end of the header and are currently pointed at the
       ;*** start of the actual data!

       IF ( state EQ "DATA_READ" ) THEN BEGIN

            ;*** Check if not all vars are to be returned.
            IF (nv_flag GT 0) THEN filterNames, cef, vnames, NODEPEND=nr_flag, SEARCHTAG=stag

            IF ( KEYWORD_SET(nd_flag) ) THEN param = cef $
            ELSE BEGIN
                CefReadData, lun, cef, param, _EXTRA=extra
            ENDELSE
       ENDIF

       ;*** Done with this file so close it
       FREE_LUN, lun
       CaaNotify, "Finished reading "+readFile

       ;*** Return the result
       RETURN, param
END



;########################## EXAMPLES ############################

PRO example1

    ;*** When using pointers its a good idea garbage collect
    ;*** any allocated memory that is no longer referenced
    HEAP_GC

    ;*** Read the CEF file. The JULDAY flag is used to get the
    ;*** times returned in MJD so that we can use the IDL
    ;*** LABEL_DATE function for easy time axis plotting.
    ;*** The TR lets us specify a time range for the interval
    ;*** to be returned.
    result = cef_read( 'cis.cef.gz', /JULDAY )

    FOR i=0,N_ELEMENTS(result)-1 DO BEGIN
       PRINT, STRTRIM(i,2)+ ':  '+ (*result(i)).VARNAME+ $
         ' ('+STRJOIN(STRTRIM((*result(i)).SIZES,2),',')+')'
       PRINT, '    ', (*result(i)).CATDESC
       PRINT
    ENDFOR

    np=3

    dummy = LABEL_DATE(DATE_FORMAT="%H:%I:%S!C%Y-%N-%D")

    PLOT, (*result(0)).DATA, (*result(np)).DATA,                   $
       YTITLE=(*result(np)).LABLAXIS + '!C' + (*result(np)).UNITS, $
       XSTYLE=1, XTICKUNITS='TIME', XTICKFORMAT='LABEL_DATE', $
       YRANGE=[0.001,1], /YLOG, PSYM=3

    ;*** We've finished with the data so free up the memory
    HEAP_FREE, result
END



;################

    ;*** This is a slightly more advanced example that tries to plot
    ;*** time series of all the returned data parameters.
    ;*** This is just intended as an example! Don't expect it to
    ;*** to produce sensible results with all products!

PRO example2, filename

    IF ( N_ELEMENTS(filename) EQ 0 ) THEN filename = 'cis.cef.gz'

    ;*** Again we start by trying to free any unused space on the heap
    HEAP_GC

    ;*** Read the data file
    result = cef_read( filename, /JULDAY )

    ;*** Get the number of variables and create some arrays to hold
    ;*** the names and status of the parameters
    n_var      = N_ELEMENTS(result)
    var_name   = STRARR(n_var)
    d_var      = INTARR(n_var)

    ;*** First pass to extract var names and identify data variables
    ;*** Note that we use the PARAMETER_TYPE attribute to ignore
    ;*** support variables.
    FOR i = 0, n_var-1 DO BEGIN
       var_name(i) = cef_get_attr( result, i, 'VARNAME' )
       d_var(i)    = STRMATCH(cef_get_attr( result, i, 'PARAMETER_TYPE'),'DATA*',/FOLD_CASE)
       IF ( N_ELEMENTS(cef_get_attr( result, i, 'SIZES')) GT 1 ) THEN d_var(i) = 0
    ENDFOR


    ;*** Now we are ready to do a second pass where we will produce the plot
    n_plots = TOTAL(d_var)     ; *** Number of panels ***
    dy   = 0.90/n_plots       ; *** Height of panels ***
    yoff = 0.05         ; *** Vertical offset  ***
    dx   = 0.80         ; *** Width of panels  ***
    xoff = 0.10         ; *** Horizontal offset **

    ;*** Use the DATASET_TITLE to label the plot
    title = cef_get_attr(result,0,'DATASET_TITLE') + '!C'+ $
       cef_get_attr(result,0,'INSTRUMENT_NAME') + '  -  ' + $
       cef_get_attr(result,0,'DATASET_ID')

    ;*** Flag used with the NOERASE keyword on PLOT
    ne_flag = 0

    ;*** Loop through each of the variables
    FOR i = 0, n_var-1 DO BEGIN

       ;*** Check if this is a parameter to be plotted
       IF ( d_var(i) ) THEN BEGIN

         n_plots = n_plots - 1       ;*** Update the panel count ***

         ;*** If we are at the last panel we want a time axis otherwise we don't ***
         IF ( n_plots EQ 0 ) THEN dummy = LABEL_DATE(DATE_FORMAT="%H:%I:%S!C%Y-%N-%D") $
         ELSE dummy = LABEL_DATE(DATE_FORMAT=" ")

         ;*** Set the plot position for this panel ***
         !P.POSITION=[ xoff, yoff+n_plots*dy, xoff+dx, yoff+(n_plots+0.98)*dy ]

         ;*** Look for the DEPEND_0 - In the previous example we just
         ;*** assumed that it was the first variable which should be
         ;*** safe most of the time, but here we actually check!
         depend0 = WHERE( var_name EQ cef_get_attr(result,i,'DEPEND_0'), count )

         ;*** If not found then skip to the next parameter
         IF ( count EQ 0 ) THEN BREAK

         ;*** Look for fill values and replace with NaNs
         fillval = cef_get_attr(result,i,'FILLVAL', STATUS=status)
         IF ( status GT 0 ) THEN BEGIN
          idx = WHERE( (*result(i)).DATA EQ fillval, count )
          IF ( count GT 0 ) THEN (*result(i)).DATA(idx) = !VALUES.F_NAN
         ENDIF

         ;*** Use MIN/MAX to determine data range
         v2 = MAX( (*result(i)).DATA, MIN=v1, /NAN )
         IF (v2 EQ v1) THEN v2 = v1 + 1.0

         ;*** Based on data range decide if we want to do a log plot
         IF ( v1 GT 0 AND v2/v1 GT 50 AND (*result(i)).VALUE_TYPE EQ 'FLOAT' ) THEN ylog=1 ELSE ylog=0

         ;*** Plot the panel
         PLOT, (*result(depend0(0))).DATA, (*result(i)).DATA(0,*), $
          YTITLE=cef_get_attr(result,i,'LABLAXIS') + '!C' + cef_get_attr(result,i,'UNITS'), $
          XTICKUNITS='TIME', XTICKFORMAT='LABEL_DATE', $
          YRANGE=[v1,v2], YLOG=ylog, TITLE=title, $
          NOERASE=ne_flag

         ;*** If there are multiple components, overplot in the same panel
         IF ( FIX((*result(i)).SIZES) GT 1 ) THEN BEGIN
          FOR j=1,FIX((*result(i)).SIZES)-1 DO BEGIN
              OPLOT, (*result(depend0(0))).DATA, (*result(i)).DATA(j,*), LINESTYLE=j
          ENDFOR

          lab = cef_get_attr(result,i,'LABEL_1')
          IF ( N_ELEMENTS(lab) EQ 1 ) THEN lab = cef_get_attr(result,i,'REPRESENTATION_1')
          IF ( N_ELEMENTS(lab) EQ 1 ) THEN $
              lab = cef_get_attr(result,cef_get_attr(result,i,'DEPEND_1'),'DATA')
          FOR j=0, N_ELEMENTS(lab)-1 DO BEGIN
              x = xoff+dx+0.01
              y = yoff+(n_plots+1)*dy-(j+1)*0.015
              XYOUTS, x, y, lab(j), /NORM, CHARSIZE=0.75
              PLOTS, [x, x+0.05], [y-0.005,y-0.005], /NORM, LINESTYLE=j

          ENDFOR
         ENDIF

         title = ''     ;*** only want title on first panel      ***
         ne_flag = 1   ;*** don't want to erase previous panels ***
       ENDIF
    ENDFOR

    XYOUTS, 0.01, 0.005, 'File ID: '+cef_get_attr(result,0,'LOGICAL_FILE_ID'), CHARSIZE=0.5, /NORM
    XYOUTS, 0.99, 0.005, 'Plotted: '+SYSTIME(), /NORM,ALIGN=1.0,CHARSIZE=0.5

    ;*** Finished with the data so free the heap memory
    HEAP_FREE, result

    ;*** Put plot position back to the default
    !P.POSITION=0
END

