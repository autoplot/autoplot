#!/bin/bash

##############################################################################
# Uncomment print statements in cgi and use this to debug.
QUERY[0]="http://autoplot.org/data/autoplot.cdf?BGSM"
QUERY[1]="open=http://autoplot.org/data/autoplot.cdf?BGSM"
QUERY[2]="version=latest&http://autoplot.org/data/autoplot.cdf?BGSM"
QUERY[3]="version=latest&open=http://autoplot.org/data/autoplot.cdf?BGSM"
QUERY[4]="nativeLAF=true"
QUERY[5]="nativeLAF=true&logConsole=true&uri=http://autoplot.org/data/autoplot.cdf?BGSM"
QUERY[6]="ftp://nssdcftp.gsfc.nasa.gov/spacecraft_data/omni/omni2_1963.dat?column=field17"
QUERY[7]="main-class=org.autoplot.AutoplotUI&nativeLAF=true&uri=http://autoplot.org/data/autoplot.cdf?BGSM"
QUERY[7]="main-class=org.autoplot.pngwalk.PngWalk1&qualityControl=true&uri=http://autoplot.org/data/autoplot.cdf?BGSM"
QUERY[7]="main-class=org.autoplot.pngwalk.PngWalk1&qualityControl=true&open="
QUERY[8]="vap+cdaweb:ds=AC_H1_MFI&id=Magnitude&timerange=2010-05-27"
QUERY[9]="vap+tsds:http://timeseries.org/get.cgi?StartDate=20030301&EndDate=20030401&ext=bin&out=tsml&ppd=1440&param1=OMNI_OMNIHR-26-v0"
QUERY[10]="vap+tsds:http://timeseries.org/get.cgi?StartDate=20030301&EndDate=20030401&ext=bin&out=tsml&ppd=1440&param1=OMNI_OMNIHR-26-v0"
QUERY[11]="http://autoplot.org/autoplot.jnlp?version=latest&nativeLAF=true&main-class=org.autoplot.pngwalk.PngWalk1&open=http://autoplot.org/data/pngwalk/product_\$Y\$m\$d.png"
QUERY[12]="http://autoplot.org/autoplot.jnlp?version=latest&nativeLAF=true&main-class=org.autoplot.pngwalk.PngWalk1&template=http://autoplot.org/data/pngwalk/product_\$Y\$m\$d.png"

for ((i=0; i< ${#QUERY[@]}; i++))
do
    QUERY_STRING=${QUERY[$i]} perl jnlp2.cgi cgi-script | grep application-desc
    echo ""
done
##############################################################################
