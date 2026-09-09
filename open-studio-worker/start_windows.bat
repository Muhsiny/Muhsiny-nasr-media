@echo off
setlocal
cd /d "%~dp0"
where python >nul 2>nul || (
  echo Python 3 is required.
  pause
  exit /b 1
)
echo Starting Documentary Studio V7 Open Worker...
python server.py --host 0.0.0.0 --port 8190
if errorlevel 1 pause
