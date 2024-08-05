///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.6
//DEPS org.yaml:snakeyaml:1.33
//JAVA17+

import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Callable;

import org.yaml.snakeyaml.Yaml;

@Command(name = "tommy", mixinStandardHelpOptions = true, version = "2024-07-30",
        description = "Tomcat - Create Windows Deployment Scripts")
class deploy implements Callable<Integer> {

    String configFile = "tommy.yaml";

    Yaml yaml = new Yaml();
    Map<String, Object> config; 
    Map<Integer, Map<String, String>> projects;

    @SuppressWarnings("unchecked")
    @Override
    public Integer call() throws Exception {
        config = yaml.load(Files.newInputStream(Path.of(configFile)));
        projects = (Map<Integer, Map<String, String>>) config.get("projects");

        template = template.replace("%", "%%");
        template = template.replace("<?>", "%s");

        final ArrayList<Integer> keys = new ArrayList<>(projects.keySet());
        for (Integer key: keys) {
            String batName = String.format("d%d.bat", key);
            System.out.println("Script: " + batName);
            Map<String, String> project = projects.get(key);

            String CATALINA_HOME = (String) config.get("CATALINA_HOME");
            String PROJECT_HOME = project.get("PROJECT_HOME");
            String WAR_PATH = project.get("WAR_PATH");

            saveStr(batName, String.format(template,
                    CATALINA_HOME, PROJECT_HOME, WAR_PATH));
        }

        return 0;
    }

    /** Save string to file. */
    public static void saveStr(String fname, String text)
            throws IOException {
        PrintWriter out = new PrintWriter(new FileOutputStream(fname));
        out.print(text);
        out.close();
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new deploy()).execute(args);
        System.exit(exitCode);
    }

    String template = """
        @echo off
        setlocal

        REM Set the CATALINA_HOME environment variable
        set CATALINA_HOME=<?>
        set PROJECT_HOME=<?>
        set WAR_PATH=<?>

        REM Determine the starting step
        set START_STEP=1
        if not "%1"=="" (
            set START_STEP=%1
        )

        REM Function to handle errors and exit if necessary
        :handle_error
        if %ERRORLEVEL% NEQ 0 (
            echo === %1 failed. Exiting...
            exit /b %ERRORLEVEL%
        )

        REM Step 1: Build the project
        if %START_STEP% EQU 1 (
            echo === Building the project
            cd %PROJECT_HOME%
            call mvn package -Plocal
            if %ERRORLEVEL% NEQ 0 call :handle_error "Maven build"
            cd %~dp0
        )

        REM Step 2: Stop Tomcat
        if %START_STEP% EQU 2 (
            echo === Stopping Tomcat
            call %CATALINA_HOME%\\bin\\shutdown.bat
            if %ERRORLEVEL% NEQ 0 call :handle_error "Tomcat shutdown"
        )

        REM Step 3: Deploy the WAR file
        if %START_STEP% EQU 3 (
            echo     %CATALINA_HOME%
            echo === Deploying WAR file from %PROJECT_HOME%
            copy %PROJECT_HOME%\\%WAR_PATH% %CATALINA_HOME%\\webapps\\
            if %ERRORLEVEL% NEQ 0 call :handle_error "WAR file copy"
        )

        REM Step 4: Start Tomcat
        if %START_STEP% EQU 4 (
            echo === Starting Tomcat
            call %CATALINA_HOME%\\bin\\catalina.bat jpda start
            rem call %CATALINA_HOME%\\bin\\startup.bat
            if %ERRORLEVEL% NEQ 0 call :handle_error "Tomcat startup"
            echo === Deployment complete.
        )

        endlocal
        exit /b 0
        """;

}
