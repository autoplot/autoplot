#!/bin/bash
#
# This file consists of a Java jar archive prefixed with a starter script.
# It should be executable on any POSIX.1 compliant OS. (Any UNIX, Linux
# or Max OS X)
#
# The version of Java used will be:
#
#  1. $JAVA_HOME/bin/java - if the JAVA_HOME environment variable is defined
#
#    -- or --
#
#  2. java - The first executable named "java" encountered in your path.
#
# This file is still a valid jar file and may be inspected by standard tools
# such as "unzip".
#
#  -h  --headless   run in headless mode
#  -Jxxx            pass argument xxx to JRE (e.g. -J-Xmx4G to get 4 Gig of RAM)
#  -s  --script     launch into script
#
#  Set APDEBUG=1 to print debug infomation
#
#  For example:
#    ./autoplot.jar -J-Xmx4G -h --script myscript.jy
#  

JAVA_ARGS=""
AP_ARGS=""

memIsImplicit=1

for i in "$@"; do
   if [ "$SSDEBUG"="1" ]; then    
       echo "arg: \"$i\""
   fi
   if [[ $i == -J-Xmx* ]]; then
      JAVA_ARGS="${JAVA_ARGS} ${i:2}";
      memIsImplicit=0
   elif [[ $i == -J* ]]; then
      JAVA_ARGS="${JAVA_ARGS} ${i:2}";
   elif [[ $i == '--headless' ]]; then
      JAVA_ARGS="${JAVA_ARGS} -Djava.awt.headless=true";
   elif [[ $i == '-h' ]]; then
      JAVA_ARGS="${JAVA_ARGS} -Djava.awt.headless=true";
   else
      AP_ARGS="${AP_ARGS} $i";
   fi
done

if [ "$SSDEBUG"="1" ]; then 
   echo "JAVA_ARGS=${JAVA_ARGS}"
   echo "AP_ARGS=${AP_ARGS}"
fi

if [ $memIsImplicit="1" ]; then 
   JAVA_ARGS="${JAVA_ARGS} -Xmx1000M ";
fi

# make debugging easier by checking if this is actually the starter script being tested.
if [ "$0"="./starterScript.sh" ]; then
   EXEC="echo";
else
   EXEC="exec";
fi

if [ "${JAVA_HOME}" -a \( -x "${JAVA_HOME}"/bin/java \) ]; then
      $EXEC "${JAVA_HOME}"/bin/java ${JAVA_ARGS} -jar $0 "${AP_ARGS}"
else
      $EXEC /usr/bin/env java ${JAVA_ARGS} -jar $0 "${AP_ARGS}"
fi

# JAR FILE DATA STARTS AFTER THIS TEXT #
