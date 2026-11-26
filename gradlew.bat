@echo off
setlocal
set "DIR=%~dp0"

if "%JAVA_HOME%"=="" (
  if exist "%DIR%.toolchain\jdk\jdk-17.0.2\bin\javac.exe" (
    set "JAVA_HOME=%DIR%.toolchain\jdk\jdk-17.0.2"
  ) else if exist "%DIR%..\demo\.toolchain\jdk\jdk-17.0.2\bin\javac.exe" (
    set "JAVA_HOME=%DIR%..\demo\.toolchain\jdk\jdk-17.0.2"
  )
)

if "%ANDROID_HOME%"=="" (
  if exist "%DIR%.toolchain\sdk\platforms\android-34" (
    set "ANDROID_HOME=%DIR%.toolchain\sdk"
  ) else if exist "%DIR%..\demo\.toolchain\sdk\platforms\android-34" (
    set "ANDROID_HOME=%DIR%..\demo\.toolchain\sdk"
  )
  set "ANDROID_SDK_ROOT=%ANDROID_HOME%"
)

if exist "%DIR%.toolchain\gradle\gradle-8.7\bin\gradle.bat" (
  call "%DIR%.toolchain\gradle\gradle-8.7\bin\gradle.bat" %*
) else if exist "%DIR%..\demo\.toolchain\gradle\gradle-8.7\bin\gradle.bat" (
  call "%DIR%..\demo\.toolchain\gradle\gradle-8.7\bin\gradle.bat" %*
) else (
  call gradle %*
)
endlocal