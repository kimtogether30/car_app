@echo off
rem VEMORING vehicle app installer for the Pleos Connect emulator.
rem Installs for every account (driver user 10 AND system user 0). Pleos SystemUI reads
rem pinned-bar / recent icons from user 0, so a driver-only install keeps the default icon.
rem Usage: install_vehicle.cmd [device-serial]   e.g. install_vehicle.cmd emulator-5554
setlocal
if not "%~1"=="" set "ANDROID_SERIAL=%~1"
set "APK=%~dp0VEMORING_VEMORING-Vehicle_release.apk"

where adb >nul 2>nul
if errorlevel 1 (
  if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
    set "PATH=%LOCALAPPDATA%\Android\Sdk\platform-tools;%PATH%"
  ) else (
    echo [ERROR] adb not found. Install Android SDK Platform-Tools or add adb to PATH.
    exit /b 1
  )
)

echo Installing VEMORING for all accounts...
adb install -r "%APK%"
if errorlevel 1 (
  echo [ERROR] Install failed. If several devices are connected, pass the emulator serial, e.g. install_vehicle.cmd emulator-5554
  exit /b 1
)

adb shell pm list packages --user 0 com.example.vemoring | findstr /x /c:"package:com.example.vemoring" >nul
if errorlevel 1 (
  echo [WARN] Not installed for system user 0. Pinned-bar icons will stay blue.
) else (
  echo [OK] Installed for system user 0: pinned-bar and recent icons can change with the mascot state.
)

rem Open once in the driver account; this also leaves exactly one app-list icon.
adb shell am start --user current -n com.example.vemoring/.MainActivity >nul
echo Done. Pin VEMORING: App Library ^> long-press VEMORING ^> drag to a pinned-bar slot.
endlocal
