@if "%DEBUG%" == "" @echo off
@rem ##############################################################################
@rem ##
@rem ##  Gradle start up script for WINDOWS
@rem ##
@rem ##############################################################################

@setlocal

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
@for %%i in ("%~dp0") do @set APP_HOME=%%~fi

@rem Find java.exe
@if "%JAVA_HOME%" neq "" (
    @if exist "%JAVA_HOME%\jre\sh\java.exe" (
        @set JAVACMD=%JAVA_HOME%\jre\sh\java.exe
    ) else (
        @if exist "%JAVA_HOME%\bin\java.exe" (
            @set JAVACMD=%JAVA_HOME%\bin\java.exe
        ) else (
            @echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
            @echo Please set the JAVA_HOME variable in your environment to match the
            @echo location of your Java installation.
            @goto error
        )
    )
) else (
    @set JAVACMD=java
    @where java >nul 2>&1
    @if %ERRORLEVEL% neq 0 (
        @echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
        @echo Please set the JAVA_HOME variable in your environment to match the
        @echo location of your Java installation.
        @goto error
    )
)

@set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

@rem Execute Gradle
"%JAVACMD%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

@goto end

:error
@exit /b 1

:end
@endlocal
@exit /b %ERRORLEVEL%
