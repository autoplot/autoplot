#!/bin/bash
#
# This rejars all the third-party libraries and compiles mature libraries
# into one "stable" jar.pack.gz, to improve download time for clients.
# This list is separate from the ant build script,
# so the configuration needs to be kept in sync.
#

# set JAVA5_HOME and JAVA6_HOME
if [ "" = "$JAVA_HOME" ]; then
    JAVA_HOME=/usr/local/jdk1.5.0_15__32/
fi
if [ "" = "$JAVA5_HOME" ]; then
    JAVA5_HOME=$JAVA_HOME
fi
if [ "" = "$JAVA6_HOME" ]; then
    JAVA6_HOME=/usr/local/jdk1.6.0_16__32/
fi

if [ "" = "$KEYPASS" ]; then
    echo "KEYPASS NEEDED!"
    KEYPASS=virbo1
fi

if [ "" = "$STOREPASS" ]; then
    echo "STOREPASS NEEDED!"
    STOREPASS=dolphin
fi

if [ "" = "$CODEBASE" ]; then
    CODEBASE=NEED_CODEBASE_TO_BE_DEFINED_IN_COMPILE_SCRIPT
fi

if [ "" = "$HUDSON_URL" ]; then
    HUDSON_URL="http://papco.org:8080/hudson"
fi

rm -r -f temp-src/
mkdir temp-src/
rm -r -f temp-classes/
mkdir temp-classes

echo "copy jar file classes..."
cd temp-classes
for i in ../../APLibs/lib/*.jar; do
   echo jar xf $i
   jar xf $i
done

# use beta version of cdf library that supports tt2000.
echo "using tt2000 support"
rm -rf gsfc/
jar xf ../../APLibs/lib/cdfjava.3.3.2.tt2000.jar

for i in ../../APLibs/lib/netCDF/*.jar; do
   echo jar xf $i
   jar xf $i
done

for i in ../../APLibs/lib/commons/*.jar; do
   echo jar xf $i
   jar xf $i
done

cd ..
echo "done copy jar file classes."

# No "stable" sources for now.
#echo "copy sources..."
#echo "done copy sources"

# special handling of the META-INF stuff.
echo "special handling of META-INF stuff..."

# remove signatures
rm temp-classes/META-INF/*.RSA
rm temp-classes/META-INF/*.SF

# end, special handling of the META-INF stuff.
echo "done special handling of META-INF stuff."

#echo "copy resources..."
#cd temp-src
#for i in $(find * -name '*.png' -o -name '*.gif' -o -name '*.html' -o -name '*.py' -o -name '*.jy' ); do
#   mkdir -p $(dirname ../temp-classes/$i)
#   cp $i ../temp-classes/$i
#done
#echo "done copy resources."

hasErrors=0

# compile key java classes.
# (No "stable" sources.)

if [ $hasErrors -eq 1 ]; then
  echo "Error somewhere in compile, see above"
  exit 1 
fi

echo "make jumbo jar file..."
cd temp-classes

mkdir -p ../dist/
$JAVA5_HOME/bin/jar cf ../dist/AutoplotStable.jar *
cd ..
echo "done make jumbo jar file..."

echo "normalize jar file for signing..."
$JAVA5_HOME/bin/pack200 --repack dist/AutoplotStable.jar
echo "sign the jar files..."
echo $JAVA5_HOME/bin/jarsigner -keypass "$KEYPASS" -storepass "$STOREPASS"  dist/AutoplotStable.jar "$ALIAS"
$JAVA5_HOME/bin/jarsigner -keypass "$KEYPASS" -storepass "$STOREPASS"  dist/AutoplotStable.jar "$ALIAS"
echo "repeat normalize/sign (workaround for known bug with large files...)"
echo $JAVA5_HOME/bin/pack200 --repack dist/AutoplotStable.jar
$JAVA5_HOME/bin/pack200 --repack dist/AutoplotStable.jar

echo $JAVA5_HOME/bin/jarsigner -keypass $KEYPASS -storepass "$STOREPASS"  dist/AutoplotStable.jar "$ALIAS"
if ! $JAVA5_HOME/bin/jarsigner -keypass $KEYPASS -storepass "$STOREPASS"  dist/AutoplotStable.jar "$ALIAS"; then
   echo "Failed to sign resources!"
   exit 1
fi

echo "pack the jar file..."
$JAVA5_HOME/bin/pack200 dist/AutoplotStable.jar.pack.gz dist/AutoplotStable.jar
#echo "done packing."
