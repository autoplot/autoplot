#!/bin/bash
#
# This rejars all the third-party libraries and compiles mature libraries
# into one "stable" jar.pack.gz, to improve download time for clients.
# This list is separate from the ant build script,
# so the configuration needs to be kept in sync.
#
# Used by: autoplot-jar-stable on Hudson
#

if [ "" = "$JAVA_HOME" ]; then
    JAVA_HOME=/usr/local/jdk1.7.0_80/
fi

# TODO: now that this code makes sure there's a slash, the rest of the code should not add it.
if [[ $JAVA_HOME != */ ]]; then
    JAVA_HOME=${JAVA_HOME}/
fi

if [ "" = "$KEYPASS" ]; then
    echo "KEYPASS NEEDED!"
    KEYPASS=virbo1
fi

if [ "" = "$STOREPASS" ]; then
    echo "STOREPASS NEEDED!"
    STOREPASS=dolphin
fi

if [ "" = "$ALIAS" ]; then
    echo "ALIAS NEEDED!"
    ALIAS=virbo
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
   echo ${JAVA_HOME}/bin/jar xf $i
   ${JAVA_HOME}/bin/jar xf $i
done

# use beta version of cdf library that supports tt2000.
echo "using tt2000 support"
rm -rf gsfc/
${JAVA_HOME}/bin/jar xf ../../APLibs/lib/cdfjava.3.3.2.tt2000.jar

for i in ../../APLibs/lib/netCDF/*.jar; do
   echo ${JAVA_HOME}/bin/jar xf $i
   ${JAVA_HOME}/bin/jar xf $i
done

echo "including new 2021 stuff for PDS library"
for i in ../../APLibs/lib/pds/*.jar; do
   echo ${JAVA_HOME}/bin/jar xf $i
   ${JAVA_HOME}/bin/jar xf $i
done

for i in ../../APLibs/lib/commons/*.jar; do
   echo ${JAVA_HOME}/bin/jar xf $i
   ${JAVA_HOME}/bin/jar xf $i
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

# add permissions attribute
rm temp-classes/META-INF/MANIFEST.MF
printf "Permissions: all-permissions\n" > temp-classes/META-INF/MANIFEST.MF

# remove CDF APPLICATION.JNLP
rm -r temp-classes/JNLP-INF/

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
$JAVA_HOME/bin/jar cmf META-INF/MANIFEST.MF ../dist/AutoplotStable.jar *
cd ..
echo "done make jumbo jar file..."

echo "normalize jar file for signing..."
$JAVA_HOME/bin/pack200 --repack dist/AutoplotStable.jar
echo "sign the jar files..."
echo "  use set +x to hide private info"
set +x
if ! ${JAVA_HOME}/bin/jarsigner -keypass "$KEYPASS" -storepass "$STOREPASS" $JARSIGNER_OPTS dist/AutoplotStable.jar "$ALIAS"; then
   echo "Failed to sign resources! (first call)"
   exit 1
fi

echo "repeat normalize/sign (workaround for known bug with large files...)"
echo $JAVA_HOME/bin/pack200 --repack dist/AutoplotStable.jar
$JAVA_HOME/bin/pack200 --repack dist/AutoplotStable.jar

if ! ${JAVA_HOME}/bin/jarsigner -keypass "$KEYPASS" -storepass "$STOREPASS" $JARSIGNER_OPTS dist/AutoplotStable.jar "$ALIAS"; then
   echo "Failed to sign resources! (second call)"
   exit 1
fi
set -x

echo "pack the jar file..."
$JAVA_HOME/bin/pack200 dist/AutoplotStable.jar.pack.gz dist/AutoplotStable.jar
#echo "done packing."

echo "Done!"