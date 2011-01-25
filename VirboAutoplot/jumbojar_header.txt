#!/bin/sh
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

if [ "${JAVA_HOME}" -a \( -x "${JAVA_HOME}"/bin/java \) ]; then
        exec "${JAVA_HOME}"/bin/java -Xmx1000M -jar $0 "$@"
else
        exec /usr/bin/env java -Xmx1000M -jar $0 "$@"
fi

# JAR FILE DATA STARTS AFTER THIS TEXT #
