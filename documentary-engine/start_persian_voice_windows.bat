@echo off
setlocal
cd /d "%~dp0"
title DocStudio Persian Voice Clone

echo ==============================================
echo   DocStudio Persian Voice Clone - Local CPU
echo ==============================================

echo.
where py >nul 2>nul
if %errorlevel%==0 (
  set "PY=py -3.11"
) else (
  where python >nul 2>nul
  if not %errorlevel%==0 (
    echo Python 3.11+ was not found. Install Python and run this file again.
    pause
    exit /b 1
  )
  set "PY=python"
)

where ffmpeg >nul 2>nul
if not %errorlevel%==0 (
  echo FFmpeg was not found in PATH.
  echo Install FFmpeg first, then run this file again.
  pause
  exit /b 1
)

if not exist ".venv_voice\Scripts\python.exe" (
  echo Creating local Python environment...
  %PY% -m venv .venv_voice
  if errorlevel 1 goto :fail
)

call .venv_voice\Scripts\activate.bat
python -m pip install --upgrade pip
if errorlevel 1 goto :fail
python -m pip install pocket-tts scipy --extra-index-url https://download.pytorch.org/whl/cpu
if errorlevel 1 goto :fail

echo.
echo Starting Persian voice clone engine on port 8190...
echo Keep this window open while the Android app is using voice cloning.
echo The first synthesis downloads the Persian model; later runs use the local cache.
echo.
python voice_server.py
exit /b 0

:fail
echo.
echo Setup failed. Read the error above.
pause
exit /b 1
