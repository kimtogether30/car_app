@echo off
rem Phone AVD only (Vemoring_Phone_API_34). Shows the on-screen Hangul keyboard (ReteKey).
rem
rem Why: x86 Android emulators always attach a PC "AT Translated Set 2 keyboard". Android then
rem reports a physical keyboard, and ReteKey hides its on-screen keys (Fcitx5 shows English
rem key labels instead). Real phones have no such device, so the app itself needs no change.
rem
rem This unbinds that virtual keyboard until the next emulator boot (nothing on disk changes).
rem Side effect: typing into the emulator with the PC keyboard stops; touch input works.
rem Run again after every phone emulator boot. Never run it against Pleos.
setlocal
set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
set "SERIAL=%~1"
if "%SERIAL%"=="" set "SERIAL=emulator-5554"

"%ADB%" -s %SERIAL% shell getprop ro.product.model | findstr /C:"Android SDK built for x86_64" >nul
if errorlevel 1 (
    echo %SERIAL% is not the phone emulator. Nothing changed.
    exit /b 1
)
"%ADB%" -s %SERIAL% root >nul
"%ADB%" -s %SERIAL% wait-for-device
"%ADB%" -s %SERIAL% shell "if [ -e /sys/bus/serio/drivers/atkbd/serio0 ]; then echo -n serio0 > /sys/bus/serio/drivers/atkbd/unbind; fi"
"%ADB%" -s %SERIAL% shell ime enable com.retekey/.ReteKeyImeService >nul
"%ADB%" -s %SERIAL% shell ime set com.retekey/.ReteKeyImeService >nul
"%ADB%" -s %SERIAL% unroot >nul
"%ADB%" -s %SERIAL% wait-for-device
echo Hangul on-screen keyboard ready on %SERIAL%.
endlocal
