///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.6


import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import static java.lang.System.*;

import java.nio.file.Files;
import java.nio.file.Paths;

import java.io.*;
import java.util.*;

import java.util.concurrent.Callable;

/**
 * Mimics the functionality of the `grep` command, but tailored to search for a specific substring within files in a given directory. 
 * The program is also equipped to output results to a file if desired.
 */
@Command(name = "find_grep", mixinStandardHelpOptions = true, version = "2024-11-04",
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
    
    @Option(names = {"-x", "--exclude"}, description = "Exclude list")
    private String excludeListName;
    
    @Option(names = {"-d", "--depth"}, description = "Max search depth", defaultValue = "2147483647") // Integer.MAX_VALUE
    private int maxDepth;
    
    @Option(names = {"-n", "--file-names"}, description = "Print file names to output file", defaultValue = "true")
    private boolean printFileNames;
    
    @Option(names = {"-v", "--verbose"}, description = "Verbose mode")
    private boolean verbose;
    
    int excludeCount;
    int fileCount;
    int count;
    int depth;
    PrintWriter pw;
    
    Set<String> excludeList = new HashSet<>();
    
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

        if (excludeListName != null) {
            excludeList = loadLinesIntoSet(excludeListName);
        }
        
        excludeCount = 0;
        fileCount = 0;
        count = 0;
        depth = -1; // 0 will not scan subfolders
        
        out.println("Searching \"" + pattern + "\" in " + targetDir);
        processFolder(new File(targetDir));
        out.println("=== " + fileCount + " files scanned, " + excludeCount + " excluded. FOUND: " + count + " times");

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
        try {
            if (depth > maxDepth) {
                return;
            }
            
            for (int i=0; i<files.length; i++) {
                File f = new File(targetDir, files[i]);
                
                if (f.isDirectory()) {
                    processFolder(f);
                    continue;
                }
                
                if (fileMask != null && !f.getName().equals(fileMask)) {
                    continue;
                }
                
                if (excludeList.contains(f.getPath())) {
                    excludeCount++;
                    continue;
                }
                
                if (verbose) {
                    out.println("[file] " + f.getPath());
                }
                
                fileCount ++; 
                
                try (Scanner scanner = new Scanner(f)) {
                    int lineNumber = 0;
                    while (scanner.hasNextLine()) {
                        String line = scanner.nextLine();
                        lineNumber++;
                        
                        // Converts the pattern to lowercase to make the search case-insensitive.
                        // If the pattern is found, it prints the line to the console and, if specified, to the output file.
                        if (line.toLowerCase().contains(pattern)) {
              
                            if (printFileNames && !(f.getName().equals(printedFileName))) {
                                out.println("--- FILE: " + f.getPath() + " : LINE " + lineNumber);
                                printedFileName = f.getPath();
                            }
                            
                            out.println("    " + line.trim());
                            count++;
                            
                            if (pw != null) {
                                pw.println("--- " + f.getPath());
                                pw.println("    " + line.trim());
                            }
                        }
                     }
                } catch (Exception e) {
                    out.println(e.getClass() + ": " + e.getMessage());
                }
            }   
              
        } finally {
            depth--;
        }           
    }
    
    static Set<String> loadLinesIntoSet(String filename) throws IOException {
        Set<String> linesSet = new HashSet<>();
        Files.lines(Paths.get(filename))
             .map(String::trim) // Trim each line
             .forEach(linesSet::add); // Add to the set
        return linesSet;
    }
}
