@echo off
rem ---------------------------------------------------------------------------
rem 项目自带 Maven 启动器（Windows CMD / PowerShell）
rem 直接以 Windows 路径调用 Maven 的 java 内核，绕开官方 mvn.cmd 的路径缺陷。
rem ---------------------------------------------------------------------------
setlocal enabledelayedexpansion
set "SCRIPT_DIR=%~dp0"
set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"

if "%MAVEN_HOME%"=="" (
    set "MH_BIN="
    for /f "delims=" %%i in ('where mvn 2^>nul') do (
        if "!MH_BIN!"=="" set "MH_BIN=%%i"
    )
    if "!MH_BIN!"=="" (
        echo [mvnw] 未找到 mvn 命令，请先设置 MAVEN_HOME 环境变量指向 Maven 安装目录。
        exit /b 1
    )
    for %%i in ("!MH_BIN!\..\..") do set "MAVEN_HOME=%%~fi"
)

set "CLASSWORLDS="
for %%f in ("%MAVEN_HOME%\boot\plexus-classworlds-*.jar") do set "CLASSWORLDS=%%f"
if "%CLASSWORLDS%"=="" (
    echo [mvnw] 在 %MAVEN_HOME%\boot 下未找到 plexus-classworlds jar，MAVEN_HOME 可能不正确。
    exit /b 1
)

set "SETTINGS_ARG="
if exist "%SCRIPT_DIR%\.mvn\settings.xml" set "SETTINGS_ARG=-s "%SCRIPT_DIR%\.mvn\settings.xml""

java %MAVEN_OPTS% ^
    -classpath "%CLASSWORLDS%" ^
    "-Dclassworlds.conf=%MAVEN_HOME%\bin\m2.conf" ^
    "-Dmaven.home=%MAVEN_HOME%" ^
    "-Dmaven.multiModuleProjectDirectory=%SCRIPT_DIR%" ^
    -Dfile.encoding=UTF-8 ^
    org.codehaus.plexus.classworlds.launcher.Launcher ^
    %SETTINGS_ARG% %*

endlocal
