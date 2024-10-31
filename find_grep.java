///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.6


import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import static java.lang.System.*;

import java.io.File;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Scanner;
import java.util.concurrent.Callable;

/**
 * Mimics the functionality of the `grep` command, but tailored to search for a specific substring within files in a given directory. 
 * The program is also equipped to output results to a file if desired.
 */
@Command(name = "find_grep", mixinStandardHelpOptions = true, version = "2024-04-24",
        description = "Find substring like grep")
class find_grep implements Callable<Integer> {

    @Parameters(index = "0", description = "String to search")
    private String pattern;

    @Parameters(index = "1", description = "Target folder")
    private String targetDir;

    @Option(names = {"-o", "--out"}, description = "Output file")
    private String outFile;

    @Option(names = {"-m", "--mask"}, description = "File mask")
    private String fileMask;
    
    @Option(names = {"-d", "--depth"}, description = "Max search depth", defaultValue = "2147483647") // Integer.MAX_VALUE
    private int maxDepth;
    
    @Option(names = {"-n", "--file-names"}, description = "Print file names to output file")
    private Boolean printFileNames;

    int fileCount;
    int count;
    int depth;
    PrintWriter pw;
    
    public static void main(String... args) {
        int exitCode = new CommandLine(new find_grep()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {

        pattern = pattern.toLowerCase();

        // Initializes a PrintWriter if an output file is specified.
        
        if (outFile != null) {
            pw = new PrintWriter(outFile);
        }

        fileCount = 0;
        count = 0;
        depth = -1; // 0 will not scan subfolders
        
        out.println("Searching \"" + pattern + "\" in " + targetDir);
        processFolder(new File(targetDir));
        out.println("=== " + fileCount + " files scanned, found " + count + " times");

        if (pw != null) {
            pw.close();
        }
        return 0;
    }
    
    // Lists and sorts the files in the target directory.
    // Iterates through each file, searching each line for the pattern.
    void processFolder(File targetDir) throws Exception {
        
        String[] files = targetDir.list();
        Arrays.sort(files);
        String printedFileName = null;
        
        depth++;
        if (depth > maxDepth) {
            return;
        }
        
        for (int i=0; i<files.length; i++) {
            File f = new File(targetDir, files[i]);
            //out.println("--- " + f.getName());
            
            if (f.isDirectory()) {
                processFolder(f);
                continue;
            }
            
            if (fileMask != null && !f.getName().equals(fileMask)) {
                continue;
            }
            
            try (Scanner scanner = new Scanner(f)) {
                int lineNumber = 0;
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    lineNumber++;
                    
                    // Converts the pattern to lowercase to make the search case-insensitive.
                    // If the pattern is found, it prints the line to the console and, if specified, to the output file.
                    if (line.toLowerCase().contains(pattern)) {
          
                        if (printFileNames != null && !(f.getName().equals(printedFileName))) {
                            out.println("--- Line " + lineNumber + ": " + f.getPath());
                            printedFileName = f.getPath();
                        }
                        
                        out.println(line);
                        count++;
                        
                        if (pw != null) {
                            pw.println("--- " + f.getPath());
                            pw.println(line);
                        }
                    }
                 }
            } catch (Exception e) {
                out.println(e.getClass() + ": " + e.getMessage());
            }
        }   
        fileCount += files.length;      
    }
}
