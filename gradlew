#!/usr/bin/env sh

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ -f "$WRAPPER_JAR" ]; then
    exec java -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
fi

if command -v gradle >/dev/null 2>&1; then
    exec gradle "$@"
fi

echo "Gradle wrapper JAR is not present. Open the project in Android Studio or install Gradle 8.9 once to run: gradle wrapper" >&2
exit 1
