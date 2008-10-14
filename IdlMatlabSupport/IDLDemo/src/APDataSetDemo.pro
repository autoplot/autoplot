;+
; __NAME__
; created on __DATE__
;-

pro APDataSetDemo

    qds= OBJ_NEW('IDLjavaObject$GetDataSet', 'org.virbo.idlsupport.APDataSet') 
    cdaweburl= 'http://cdaweb.gsfc.nasa.gov/cgi-bin/opendap/nph-dods/istp_public/data/'
    url= 'genesis/3dl2_gim/2003/genesis_3dl2_gim_20030501_v01.cdf.dds?Proton_Density'
    url2= 'tsds.http://timeseries.org/get.cgi?StartDate=19890101&EndDate=19890101&ext=bin&out=tsml&ppd=1440&param1=SourceAcronym_Subset3-1-v0'
    url3= 'file:///media/mini/data.backup/examples/qstream/bigRank1.qds'
    qds->setDataSetUrl, url2

    async=1

    catch, err
    if ( err eq 0 ) then begin
        if ( async ) then begin
            mon= qds->getProgressMonitor()
            qds->doGetDataSet, mon

            while ( not mon->isFinished() ) do begin
              print, mon->toString()
              wait, 0.2
            endwhile
        endif else begin
            qds->doGetDataSet

        endelse
    endif else begin
       catch, /cancel
       oJSession = OBJ_NEW('IDLJavaObject$IDLJAVABRIDGESESSION')  
       oJExc = oJSession->GetException()  
       oJExc->PrintStackTrace  
       return 

    endelse

    data= qds->values(  )
    print, qds->depend(0)
    time= qds->values( 'ds_1' )
    plot, time, data
end
