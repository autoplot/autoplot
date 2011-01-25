#!/bin/sh

# release script for the Autoplot website
export rpwd=`pwd`
cd dasCore
ant do-tag

cd ../VirboAutoplot
ant do-tag

export AP_VERSION=20110121

ant jar -Ddefault_release_tag=$AP_VERSION
#ant jnlp-release -Ddefault_release_tag=$AP_VERSION

sh ./jumbojar.sh  
#mv dist/autoplot.jar dist/jnlp/
#rsync -e ssh -av dist/jnlp/ root@aurora.gmu.edu:/var/www/autoplot/jnlp/$AP_VERSION/
