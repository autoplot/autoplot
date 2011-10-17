#!/bin/sh

# release script for the Autoplot website
export rpwd=`pwd`
cd dasCore
ant -quiet do-tag | grep -v  "Trying to override old definition of task " #TODO: why must we still do this?

cd ../VirboAutoplot
ant -quiet do-tag | grep -v  "Trying to override old definition of task "

export AP_VERSION=20110923

ant jar -Ddefault_release_tag=$AP_VERSION
ant jnlp-release -Ddefault_release_tag=$AP_VERSION

#SINGLE JAR RELEASE
#sh ./jumbojar.sh
mv dist/autoplot.jar dist/jnlp/
rsync -e ssh -av dist/jnlp/ root@aurora.gmu.edu:/var/www/autoplot/jnlp/$AP_VERSION/

#STANDARD TWO-JAR (volatile and stable) RELEASE
sh ./compile-application.sh

# these are left over from the do-tag stuff.
cd ..
rm -r VirboAutoplot/dist/lib
rm -r VirboAutoplot/dist/VirboAutoplot.jar
rm -r VirboAutoplot/dist/README.TXT

# These we get from a production location so clients only load once.
rm -r VirboAutoplot/dist/AutoplotStable.jar
rm -r VirboAutoplot/dist/AutoplotStable.jar.pack.gz

# pause the processing until we get back from getting more coffee.
echo "enter y to rsync to root@aurora.gmu.edu:/var/www/autoplot/jnlp/$AP_VERSION/"
read aline

if [ "$line"="y" ]; then 
  echo "rsync to aurora"
  rsync -e ssh -av VirboAutoplot/dist/ root@aurora.gmu.edu:/var/www/autoplot/jnlp/$AP_VERSION/
fi

echo "If this is to be a new production version, then update the latest link in /var/www/autoplot/jnlp/"

echo "If updating the production version http://autoplot.org/autoplot.jnlp, download this and post it to sourceforge as well."

