@echo off
rem Detached dev-client launcher for RosettaRemoteDebugBridge probing.
rem
rem The agent must never block on a GUI client that takes minutes to load, so
rem this script is started detached and its output polled:
rem
rem     cmd /c start "" tools\start_client_detached.cmd "New World" [withgtceu]
rem
rem It quick-joins the given save (default "New World") and, unlike runServer,
rem includes GTCEu so the client matches the verified runtime. The Gradle console
rem goes to build\client-run.log and the game's own logs to run-client\logs\.
rem The client is closed later through the bridge (an exec of Minecraft.stop())
rem or, failing that, taskkill on the java tree.
rem
rem JAVA_HOME is inherited from the caller; the game needs JDK 17.
setlocal
cd /d "%~dp0.."
set "WORLD=%~1"
if "%WORLD%"=="" set "WORLD=New World"
set "GTCEU=%~2"
if "%GTCEU%"=="" set "GTCEU=true"
echo [%DATE% %TIME%] launching dev client, quick-joining "%WORLD%", withGtceu=%GTCEU%
gradlew.bat runClient "-PquickPlay=%WORLD%" "-PwithGtceu=%GTCEU%" --console=plain --no-daemon > "build\client-run.log" 2>&1
