@echo off
setlocal
rem One-shot packaging: build frontend -> copy to backend static -> fat jar -> jpackage app-image (bundled JRE)
rem Requires JDK 21 (jpackage) and Node.js on THIS machine; target machines need nothing.

cd /d "%~dp0frontend" || exit /b 1
call npm run build || exit /b 1
xcopy /e /i /y dist ..\backend\src\main\resources\static >nul || exit /b 1

cd /d "%~dp0backend" || exit /b 1
call mvnw.cmd -q package -DskipTests || exit /b 1
if not exist jpackage-input mkdir jpackage-input
copy /y target\springboot-0.0.1-SNAPSHOT.jar jpackage-input\ >nul || exit /b 1

if exist package\KomoReader rmdir /s /q package\KomoReader
jpackage --type app-image --name KomoReader --input jpackage-input --main-jar springboot-0.0.1-SNAPSHOT.jar --dest package --java-options "-Dfile.encoding=UTF-8" || exit /b 1

rem regenerate the launcher (rmdir above removed the previous copy)
(
  echo @echo off
  echo rem KomoReader launcher: start app, wait until ready, open browser
  echo start "" "%%~dp0KomoReader.exe"
  echo :wait
  echo timeout /t 2 /nobreak ^>nul
  echo curl -s -o nul --max-time 2 http://localhost:1236/api/manga
  echo if errorlevel 1 goto wait
  echo start http://localhost:1236
) > "package\KomoReader\start.bat"

echo.
echo Done: backend\package\KomoReader\KomoReader.exe ^(launcher: start.bat^)
