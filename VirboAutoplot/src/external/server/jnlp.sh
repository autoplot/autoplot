#!/bin/bash

PARSECOM="jnlp.cgi"

# Won't work
QUERY="uri=tsds.http://timeseries.org/get.cgi?StartDate=19890101&EndDate=19890101&ext=bin&out=tsml&ppd=1440&param1=SourceAcronym_Subset3-1-v0"
echo "Calling $QUERY perl $PARSECOM cgi-script"
QUERY_STRING=$QUERY perl $PARSECOM cgi-script 

exit

QUERY="uri=http://autoplot.org/data/autoplot.cdf?BGSM"
QUERY="version=20100728&uri=vap+cdf:http://autoplot.org/data/autoplot.cdf?BGSM"
echo "Calling $QUERY perl $PARSECOM cgi-script"
QUERY_STRING=$QUERY perl $PARSECOM cgi-script 

exit

echo "Calling $QUERY perl $PARSECOM cgi-script"
QUERY_STRING=$QUERY perl $PARSECOM cgi-script 



QUERY="vap+tsds:http://timeseries.org/get.cgi?StartDate=20030301&EndDate=20030401&ext=bin&out=tsml&ppd=1440&param1=OMNI_OMNIHR-26-v0"
echo "Calling $QUERY perl $PARSECOM cgi-script"
QUERY_STRING=$QUERY perl $PARSECOM cgi-script 




QUERY="http://autoplot.org/data/autoplot.cdf?BGSM"
echo "Calling $QUERY perl $PARSECOM cgi-script"
QUERY_STRING=$QUERY perl $PARSECOM cgi-script 

