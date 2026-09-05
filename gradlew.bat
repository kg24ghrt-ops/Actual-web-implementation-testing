@if "%DEBUG%" == "" @echo off
@rem ##############################################################################
@rem ##
@rem ##  Gradle start up script for WINDOWS
@rem ##
@rem ##############################################################################

@setlocal

@echo off

@rem Set variable scope as wide as possible.
@set APP_HOME=%~dp0

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
@for %%i in ("%APP_HOME%") do @set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options here.
@set DEFAULT_JVM_OPTS="-Xmx64m"

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
    @which java.exe >nul 2>&1
    @if %ERRORLEVEL% neq 0 (
        @echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
        @echo Please set the JAVA_HOME variable in your environment to match the
        @echo location of your Java installation.
        @goto error
    )
)

@rem Increase the maximum file descriptors if we can.
@if "%cygwin%" == "false" (
    @if "%darwin%" == "false" (
        @set MAX_FD_LIMIT=
        @for /f "tokens=2 delims=:." %%a in ('reg query "HKLM\System\CurrentControlSet\Control\Session Manager\SubSystems" /v SharedSection 2^>nul') do (
            @set MAX_FD_LIMIT=%%a
        )
        @if "%MAX_FD%" == "maximum" (
            @set MAX_FD=%MAX_FD_LIMIT%
        )
        @if NOT "%MAX_FD%" == "" (
            @set /a MAX_FD=%MAX_FD%
            @if %ERRORLEVEL% neq 0 (
                @echo Could not set maximum file descriptor limit: %MAX_FD%
            )
        )
    )
)

@rem Collect all arguments for the java command, stacking in reverse order:
@rem   * args from the command line
@rem   * the main class
@rem   * -classpath
@rem   * the -Doptions...
@rem   * MAIN_CLASS_NAME

@set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

@rem Execute Gradle
"%JAVACMD%" %DEFAULT_JVM_OPTS% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

@goto end

:error
@exit /b 1

:end
@endlocal
@rem ##############################################################################
@rem ##
@rem ##  Gradle start up script for WINDOWS
@rem ##
@rem ##############################################################################
@exit /b %ERRORLEVEL%
