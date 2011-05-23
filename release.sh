#!/bin/sh

# release script for the Autoplot website
export rpwd=`pwd`
cd dasCore
ant do-tag

cd ../VirboAutoplot
ant do-tag

export AP_VERSION=20110523 

ant jar -Ddefault_release_tag=$AP_VERSION
ant jnlp-release -Ddefault_release_tag=$AP_VERSION

sh ./jumbojar.sh  
#mv dist/autoplot.jar dist/jnlp/
#rsync -e ssh -av dist/jnlp/ root@aurora.gmu.edu:/var/www/autoplot/jnlp/$AP_VERSION/

echo "If this is to be a new production version, then update the latest link in /var/www/autoplot/jnlp/"

echo "If updating the production version http://autoplot.org/autoplot.jnlp, download this and post it to sourceforge as well."

