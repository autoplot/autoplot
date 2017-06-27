#!/bin/bash

##############################################################################
QUERY[0]="http://autoplot.org/data/autoplot.cdf?BGSM"
QUERY[1]="open=http://autoplot.org/data/autoplot.cdf?BGSM"
QUERY[2]="version=latest&http://autoplot.org/data/autoplot.cdf?BGSM"
QUERY[3]="version=latest&open=http://autoplot.org/data/autoplot.cdf?BGSM"
QUERY[4]="nativeLAF=true"
QUERY[5]="nativeLAF=true&logConsole=true&uri=http://autoplot.org/data/autoplot.cdf?BGSM"
QUERY[6]="ftp://nssdcftp.gsfc.nasa.gov/spacecraft_data/omni/omni2_1963.dat?column=field17"
QUERY[7]="main-class=org.autoplot.AutoplotUI&nativeLAF=true&uri=http://autoplot.org/data/autoplot.cdf?BGSM"
QUERY[8]="main-class=org.autoplot.pngwalk.PngWalk1&qualityControl=true&uri=http://autoplot.org/data/autoplot.cdf?BGSM"
QUERY[9]="open=vap+tsds:http://timeseries.org/get.cgi?StartDate=20030301&EndDate=20030401&ext=bin&out=tsml&ppd=1440&param1=OMNI_OMNIHR-26-v0"
QUERY[10]="version=latest&nativeLAF=true&main-class=org.autoplot.AutoplotUI&open=http://autoplot.org/data/autoplot.cdf?BGSM"
QUERY[11]="version=latest&nativeLAF=true&main-class=org.autoplot.pngwalk.PngWalk1&template=http://autoplot.org/data/pngwalk/product_\$Y\$m\$d.png"
QUERY[12]="main-class=org.autoplot.pngwalk.PngWalk1&version=20110110&qualityControl=true&nativeLAF=true&template=http://sarahandjeremy.net/~jbf/pngwalk/product_\$Y\$m\$d.png"

for ((i=0; i< ${#QUERY[@]}; i++))
do
     echo "Request = http://autoplot.org/autoplot.jnlp?"${QUERY[$i]}
     wget -q -O - http://autoplot.org/autoplot.jnlp?${QUERY[$i]} | grep application-desc
    echo ""
done
##############################################################################

