@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    https://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM ----------------------------------------------------------------------------
@REM Maven Wrapper startup batch script
@REM ----------------------------------------------------------------------------

@REM Begin all REM lines with '@' in case MAVEN_BATCH_ECHO is 'on'
@echo off
@REM set title of command window
title %0
@REM enable echoing by setting MAVEN_BATCH_ECHO to 'on'
@if "%MAVEN_BATCH_ECHO%" == "on"  echo %MAVEN_BATCH_ECHO%

@REM Find the project base dir, i.e. the directory containing ".mvn"
set "MAVEN_PROJECTBASEDIR=%~dp0"

@REM Locate JAVA_HOME
set "JAVA_HOME=%JAVA_HOME:"=%"
if not defined JAVA_HOME goto findJavaFromCommand

:checkJdk
if not exist "%JAVA_HOME%\bin\java.exe" (
  echo ERROR: JAVA_HOME is set to an invalid directory: "%JAVA_HOME%"
  echo Please set the JAVA_HOME variable in your environment to match the
  echo location of your Java installation.
  exit /b 1
)

:execute
"%JAVA_HOME%\bin\java.exe" %MAVEN_OPTS% -Dmaven.multiModuleProjectDirectory="%MAVEN_PROJECTBASEDIR%" -classpath "%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar" org.apache.maven.wrapper.MavenWrapperMain %MAVEN_CMD_LINE_ARGS% %*

:end
@REM End of script

:findJavaFromCommand
set "JAVA_EXE=java.exe"
for %%i in ("%JAVA_EXE%") do set "JAVA_HOME=%%~dp$PATH:i"
goto checkJdk
