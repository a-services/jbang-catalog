///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.5


import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import static java.lang.System.out;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Command(name = "cmp_tstamps", mixinStandardHelpOptions = true, version = "2023-09-24",
        description = "Compare timestamps in two folders")
class cmp_tstamps implements Callable<Integer> {

    @Parameters(index = "0", description = "First input folder")
    private String inputFolder_1;

    @Parameters(index = "1", description = "Second input folder")
    private String inputFolder_2;

    @Override
    public Integer call() throws Exception { 
        out.println("Folder 1: " + inputFolder_1);
        out.println("Folder 2: " + inputFolder_2);

        // Get lists of files
        List<File> files_1 = Arrays.asList(new File(inputFolder_1).listFiles());
        List<File> files_2 = Arrays.asList(new File(inputFolder_2).listFiles());

        // Exclude directories
        files_1 = files_1.stream()
            .filter(File::isFile) 
            .collect(Collectors.toList());
        files_2 = files_2.stream()
            .filter(File::isFile) 
            .collect(Collectors.toList());

        // Get lists of names
        List<String> names_1 = files_1.stream()
                .map(File::getName)  
                .collect(Collectors.toList()); 
        List<String> names_2 = files_2.stream()
                .map(File::getName)  
                .collect(Collectors.toList()); 

        // Get missing files        
        List<File> missingIn_1 = files_1.stream()
            .filter(file -> !names_2.contains(file.getName()))
            .collect(Collectors.toList());
        List<File> missingIn_2 = files_2.stream()
            .filter(file -> !names_1.contains(file.getName()))
            .collect(Collectors.toList());

        // Print missing files     
        out.println("Missing in 1: " + missingIn_1.size());
        for (File missing: missingIn_1) {
            out.println("  " + missing.getName());
        }
        out.println("Missing in 2: " + missingIn_2.size());
        for (File missing: missingIn_2) {
            out.println("  " + missing.getName());
        } 
        
        // Get common names        
        List<String> common = names_1.stream()
            .filter(name -> names_2.contains(name))
            .collect(Collectors.toList());
        out.println("Common: " + common.size());
        common.sort(null);
        for (String name: common) {
            long t1 = new File(inputFolder_1, name).lastModified();
            long t2 = new File(inputFolder_2, name).lastModified();
            out.printf("  %10d : %s\n", (t2-t1), name);
        }
        return 0;
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new cmp_tstamps()).execute(args);
        System.exit(exitCode);
    }    
}
