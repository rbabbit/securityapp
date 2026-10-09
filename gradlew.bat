@echo off
setlocal
set "APP_HOME=%~dp0"
set "WRAPPER_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar"
if not exist "%WRAPPER_JAR%" (
  echo Setting up pinned Gradle wrapper jar...
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%APP_HOME%scripts\bootstrap-wrapper.ps1"
  if errorlevel 1 exit /b 1
)
set "JAVA_EXE=java.exe"
if defined JAVA_HOME set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
"%JAVA_EXE%" -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
exit /b %ERRORLEVEL%
