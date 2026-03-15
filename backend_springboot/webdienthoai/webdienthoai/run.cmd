@echo off
REM Dung JDK 21 cho Maven (Temurin). Neu cai o thu muc khac, sua duong dan ben duoi.
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot"
if not exist "%JAVA_HOME%\bin\java.exe" (
  echo [ERROR] Khong tim thay JDK 21 tai: %JAVA_HOME%
  echo Sua duong dan JAVA_HOME trong run.cmd hoac set bien moi truong JAVA_HOME.
  exit /b 1
)
call "%~dp0mvnw.cmd" %*
