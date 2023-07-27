///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.4
//DEPS org.apache.commons:commons-csv:1.10.0

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import static java.lang.System.out;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Command(name = "p_stats", mixinStandardHelpOptions = true, version = "2023-07-27", 
         description = "Calculate project stats")
class p_stats implements Callable<Integer> {

    @Parameters(index = "0", description = "Input folder.", defaultValue = ".")
    Path inputFolder;
    
    // Output CSV file name for file type counts
    String fileTypeCountsCsvName = "fileTypeCounts.csv";
    
    // Output CSV file name for source type counts
    String sourceTypeCountsCsvName = "sourceTypeCounts.csv";

    int totalFileCount = 0;
    int totalLineCount = 0;
    int totalSourceCount = 0;
    int totalSourceErrors = 0;

    List<String> excludedFolders = Arrays.asList(".git", ".angular", ".gradle", "bin", "build", "node_modules");

    List<String> sourceFileTypes = Arrays.asList(
        "js", "java", "html", "htm", "ts", 
        "xml", "xsl", "xslt", "wsdl",
        "css", "less", "scss", 
        "md", "txt",
        "m", "h", "sql", "jsp");

    Map<String, Integer> fileTypeCounts = new HashMap<>();

    @Override
    public Integer call() throws Exception {

        if (!Files.exists(inputFolder)) {
            out.println("[ERROR] Folder not found: " + inputFolder);
            return 1;
        }
        if (!Files.isDirectory(inputFolder)) {
            out.println("[ERROR] Folder expected: " + inputFolder);
            return 1;
        }

        Files.walkFileTree(inputFolder, new FileVisitor<Path>() {

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {

                if (excludedFolders.contains(dir.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                } else {
                    return FileVisitResult.CONTINUE;
                }
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                processFile(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                System.err.println(exc);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }
        });

        outputResults();
        return 0;
    }

    void processFile(Path file) throws IOException {
        totalFileCount++;

        String ext = getFileExtension(file.getFileName().toString());
        if (ext.length() > 0) {
            increaseCounter(fileTypeCounts, ext);
        }
        
        if (sourceFileTypes.contains(ext)) {
            totalSourceCount++;

            int n = countLines(file);
            totalLineCount += n;
        }
    }

    void increaseCounter(Map<String, Integer> countMap, String ext) {
        Integer k = countMap.get(ext);
        countMap.put(ext, k == null ? 1 : k + 1);
    }

    /**
     * @return  file extension of a given path
     */
    public static String getFileExtension(String fileName) {
        // Find the last index of the dot character
        int dotIndex = fileName.lastIndexOf('.');
        // Check if the dot is found and is not the first or last character
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            // Return the substring after the dot
            return fileName.substring(dotIndex + 1);
        }
        // Otherwise, return an empty string
        return "";
    }

    /** 
     * @return The number of lines in a text file
     * @throws IOException
     */ 
    int countLines(Path file) throws IOException {
        int count = 0;
        //out.println("-- " + file);
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            while (reader.readLine() != null) {
                count++;
            }
        } catch(MalformedInputException e) {
            totalSourceErrors++;
        }
        return count;
    }

    void outputResults() throws IOException {
        
        saveCountsToCsv(fileTypeCounts, fileTypeCountsCsvName);

        Map<String, Integer> sourceTypeCounts = fileTypeCounts.entrySet().stream() //
            .filter(entry -> sourceFileTypes.contains(entry.getKey())) //
            .collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue()));
        saveCountsToCsv(sourceTypeCounts, sourceTypeCountsCsvName);
        
        // Print totals
        out.println("Input folder: " + inputFolder);
        out.println("                         Total files: " + totalFileCount);
        out.println("                        Source files: " + totalSourceCount);
        out.println("  Source files with unknown encoding: " + totalSourceErrors);
        out.println("                         Total lines: " + totalLineCount);
    }

    /**
     * Save file type counts
     * @throws IOException
     */ 
    private void saveCountsToCsv(Map<String, Integer> countMap, String csvName) throws IOException {

        List<String> extList = new ArrayList<>(countMap.keySet());
        extList.sort(new Comparator<String>() {

            @Override
            public int compare(String s1, String s2) {
                Integer k1 = countMap.get(s1);
                Integer k2 = countMap.get(s2);
                if (k1 == null) {
                    k1 = 0;
                }
                if (k2 == null) {
                    k2 = 0;
                }
                return k2 - k1;
            }
            
        });

        BufferedWriter writer = Files.newBufferedWriter(Path.of(csvName));
        for (String ext: extList) {
            writer.write(String.format("%s, %d\n", ext, countMap.get(ext)));
        }
        writer.close();
        out.println("File created: " + csvName);        
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new p_stats()).execute(args);
        System.exit(exitCode);
    }
}
