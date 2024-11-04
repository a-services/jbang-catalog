///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.6

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

@Command(name = "maven_deps", mixinStandardHelpOptions = true, version = "2024-11-04",
        description = "Extract maven dependency tree for each project in current folder")
class maven_deps implements Callable<Integer> {
	
	@Option(names = { "-j", "--java" }, description = "Java version to use.")
    String jdkPath;
	
    @Option(names = { "-b", "--bash" }, description = "Use git-bash.")
    boolean useGitBash;
	
	@Option(names = { "-v", "--java-version" }, description = "Print java version.")
    boolean printJavaVersion;
	
	String newPath;
	
    public static void main(String... args) {
        int exitCode = new CommandLine(new maven_deps()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception { 
        File currentDir = new File(".");
        File depsDir = new File("..\\..\\deps");
        if (!depsDir.exists()) depsDir.mkdirs();

        // Setting up the JDK path at the beginning of the PATH environment variable
        if (jdkPath!=null) {			
			String originalPath = System.getenv("PATH");
		    newPath = jdkPath + ";" + originalPath;
			
			// Set the updated path
			System.setProperty("java.library.path", newPath);
		}
		

        Arrays.stream(currentDir.listFiles(File::isDirectory)).forEach(folder -> {
            String folderName = folder.getName();

            String cmd = "mvn dependency:tree -DoutputFile=..\\" + depsDir + "\\" + folderName + ".deps";
			
			if (printJavaVersion) {
			    cmd = "java -version";
            }
			
			System.out.println("##### " + cmd);
			
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "cmd", "/c", cmd
            );
			
			if (useGitBash) {
				processBuilder = new ProcessBuilder(
                    "C:\\Program Files\\Git\\git-bash.exe", "-c", cmd
                );
			}
				
            processBuilder.directory(folder);
            processBuilder.inheritIO();
			
			// Setting the environment variable in the process
			if (newPath != null) {
			    processBuilder.environment().put("PATH", newPath);
			}
					
            try {
                Process process = processBuilder.start();
                process.waitFor();
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        });
        return 0;
    }
}
