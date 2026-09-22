@ECHO OFF
SETLOCAL
SET APP_HOME=%~dp0
SET WRAPPER_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar

IF EXIST "%WRAPPER_JAR%" (
  java -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
  EXIT /B %ERRORLEVEL%
)

WHERE gradle >NUL 2>NUL
IF %ERRORLEVEL% EQU 0 (
  gradle %*
  EXIT /B %ERRORLEVEL%
)

ECHO Gradle wrapper JAR is not present. Open the project in Android Studio or install Gradle 8.9 once to run: gradle wrapper 1>&2
EXIT /B 1
