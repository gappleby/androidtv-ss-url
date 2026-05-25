#!/bin/sh
#
# Copyright © 2015-2021 the original authors.
# Licensed under the Apache License, Version 2.0
# https://www.apache.org/licenses/LICENSE-2.0
#

# Resolve APP_HOME, following symlinks.
app_path=$0
while [ -h "$app_path" ]; do
    ls=$( ls -ld "$app_path" )
    link=${ls#*' -> '}
    case $link in
        /*)  app_path=$link ;;
        *)   app_path=$( dirname "$app_path" )/$link ;;
    esac
done
APP_HOME=$( cd "$( dirname "$app_path" )" && pwd -P ) || exit

APP_NAME="Gradle"
APP_BASE_NAME=${0##*/}
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Determine the Java command.
if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
    [ -x "$JAVACMD" ] || {
        echo "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME" >&2
        exit 1
    }
else
    JAVACMD=java
    command -v java >/dev/null 2>&1 || {
        echo "ERROR: JAVA_HOME is not set and 'java' was not found in PATH." >&2
        exit 1
    }
fi

# Increase max file descriptors if possible.
MAX_FD=maximum
case $( uname ) in Darwin*) MAX_FD=unlimited ;; esac
case $MAX_FD in
    max*) MAX_FD=$( ulimit -H -n 2>/dev/null ) ;;
esac
case $MAX_FD in ''|soft) : ;; *)
    ulimit -n "$MAX_FD" 2>/dev/null
esac

eval set -- $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS \
    "\"-Dorg.gradle.appname=$APP_BASE_NAME\"" \
    -classpath "\"$CLASSPATH\"" \
    org.gradle.wrapper.GradleWrapperMain '"$@"'

exec "$JAVACMD" "$@"
