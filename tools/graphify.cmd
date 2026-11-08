@echo off
setlocal
set "PYTHONUTF8=1"
set "OMP_NUM_THREADS=1"
set "OPENBLAS_NUM_THREADS=1"
set "MKL_NUM_THREADS=1"
set "GRAPHIFY_EXE=%LOCALAPPDATA%\Graphify\venv\Scripts\graphify.exe"
if not exist "%GRAPHIFY_EXE%" (
    echo Graphify is not installed in the expected local tool environment.
    exit /b 1
)
pushd "%~dp0.."
if "%~1"=="" (
    "%GRAPHIFY_EXE%" extract . --code-only --max-workers 1
) else (
    "%GRAPHIFY_EXE%" %*
)
set "GRAPHIFY_RESULT=%ERRORLEVEL%"
popd
exit /b %GRAPHIFY_RESULT%
