@REM ----------------------------------------------------------------------------
@REM Maven Wrapper startup batch script for Windows
@REM ----------------------------------------------------------------------------
@echo off
setlocal

set MAVEN_PROJECTBASEDIR=%~dp0
set WRAPPER_JAR="%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.jar"
set WRAPPER_URL="https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar"
set MAVEN_URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.7/apache-maven-3.9.7-bin.zip"

if exist %WRAPPER_JAR% goto runWrapper

echo Downloading Maven Wrapper...
powershell -Command "Invoke-WebRequest -Uri %WRAPPER_URL% -OutFile %WRAPPER_JAR%"
if errorlevel 1 goto downloadFailed

:runWrapper
java -jar %WRAPPER_JAR% %*
goto end

:downloadFailed
echo Failed to download Maven Wrapper. Attempting direct download of Maven...
if not exist "%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven" mkdir "%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven"

@REM Fallback: use mvn from PATH if available
where mvn >nul 2>&1
if %errorlevel%==0 (
    mvn %*
    goto end
)

echo.
echo ERROR: Maven Wrapper JAR not found and Maven is not installed.
echo Please install Maven or download the wrapper JAR manually.
echo.
exit /b 1

:end
endlocal

