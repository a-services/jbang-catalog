///usr/bin/env jbang "$0" "$@" ; exit $?
//:folding=explicit:collapseFolds=1:
//DEPS info.picocli:picocli:4.7.5
//DEPS org.apache.commons:commons-csv:1.10.0
//DEPS org.yaml:snakeyaml:1.33

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import static java.lang.System.out;
import static java.lang.System.err;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.MalformedInputException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.yaml.snakeyaml.Yaml;

/**
 * Calculate project stats.
 */
@Command(name = "p_stats", mixinStandardHelpOptions = true, version = "2023-11-01", 
         description = "Calculate project stats")
class p_stats implements Callable<Integer> {

    @Parameters(index = "0", defaultValue = ".",
                description = "Input folder.")
    Path inputFolder;
    
    @Option(names = { "-o", "--output" }, defaultValue = ".",
            description = "Output folder.")
    Path outputFolder;
    
    @Option(names = { "-r", "--resources" }, defaultValue = "resources",
            description = "Folder to download resources if not available.")
    Path resourcesFolder;

    // {{{ Output files
    // Output CSV file name for all file type counts
    String outputCsvName = "p_stats.csv";
    
    // Output CSV file name with project roots
    String projectsCsvName = "p_projects.csv";
    
    // Output CSV file with errors
    String errorsCsvName = "p_errors.csv";
    // }}}
    
    Yaml yaml = new Yaml();

    int totalFileCount = 0;
    int totalLineCount = 0;
    int totalSourceCount = 0;
    long totalSize = 0;

    List<String> excludedFolders = List.of(".git", ".angular", ".gradle", "bin", "build", "node_modules", "target");

    /**
     * Map where a key is the name of the language, and a value is `Map<String, Object>`,
     * where for `extensions` key the value is `List<String>` of file extensions that belong to this language.
     */
    Map<String, Map<String, Object>> languages;

    /**
     * Map where each file extension is mapped to the name of the language.
     */
    Map<String, String> sourceFileTypes;
    
    // List of file extensions to search for javascript tests.
    List<String> jsFileTypes = List.of(
        "js", "jsx", "ts", "tsx"
    );

    // {{{ Project counts
    List<String> projectFiles = List.of(
        "package.json", "pom.xml", "build.gradle"
    );
    
    // Root project files to path
    Map<String, List<String>> projectPaths = new HashMap<>();
    
    // Length of absolute path to input folder
    int baseFolderLen;
    // }}}
    
    List<ErrorRecord> errors = new ArrayList<>();

    /* Regular expressions to count tests */

    String javaTestRegex = "@Test";
    Pattern javaTestPattern = Pattern.compile(javaTestRegex);
    
    String jsTestRegex = "\\bit\\(|\\btest\\(";
    Pattern jsTestPattern = Pattern.compile(jsTestRegex);

    String csTestRegex = "\\[Test\\]|\\[TestMethod\\]|\\[Fact\\]";
    Pattern csTestPattern = Pattern.compile(csTestRegex);

    String pythonTestRegex = "^def test_";
    Pattern pythonTestPattern = Pattern.compile(pythonTestRegex, Pattern.MULTILINE);

    // {{{ Dictionaries
    /* Dictionary is a map with a `String` key.
       File extension is used as a key.
       We use `df` as 2D Dictionary (aka DataFrame) that contains the counts for each parameter.
       Each parameter is 1D Dictionary (aka Series).
     */
     
    Map<String, Map<String, Long>> df = new HashMap<>();

    Map<String, Long> fileCounts;
    Map<String, Long> lineCounts;
    Map<String, Long> sizeCounts;
    Map<String, Long> testCounts;
    // }}}
    
    final String HR = "---------------------";

    @Override
    public Integer call() throws Exception {
        out.println(HR);
        out.println("Input folder: " + inputFolder);

        if (!Files.exists(inputFolder)) {
            out.println("[ERROR] Folder not found: " + inputFolder);
            return 1;
        }
        if (!Files.isDirectory(inputFolder)) {
            out.println("[ERROR] Folder expected: " + inputFolder);
            return 1;
        }

        // Make sure that `outputFolder` exists
        if (!Files.exists(outputFolder)) {
            Files.createDirectories(outputFolder);
        }
        
        // Create Series for each parameter
        fileCounts = newSeries("files");
        lineCounts = newSeries("lines");
        sizeCounts = newSeries("size");
        testCounts = newSeries("tests");
        
        // Create lists in `projectPaths`
        for (String file: projectFiles) {
            projectPaths.put(file, new ArrayList<String>());
        }
        baseFolderLen = inputFolder.toAbsolutePath().toString().length();        

        // Get path to the resource file, download it if needed
        Path langPath = downloadYaml("languages.yml", 
                 "https://raw.githubusercontent.com/github-linguist/linguist/master/lib/linguist/languages.yml");

        languages = yaml.load(Files.newInputStream(langPath));
        
        sourceFileTypes = extractSourceFileTypes(languages);

        /* Process each file in the tree.
         */
        out.print("Scanning..");
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
        out.println('.');

        outputResults();
        return 0;
    }
    
    Map<String, String> extractSourceFileTypes(Map<String, Map<String, Object>> languages) {
        
        // Collect all language names and sort them accrording to `knownLanguages`
        List<String> languagePriority = new ArrayList<>(languages.keySet());
        languagePriority.sort(new LanguagePriorityComparator());

        // Transform the map and collect into a List of Map Entries
        List<Map.Entry<String, String>> entryList = languages.entrySet().stream()
                .flatMap(entry -> {
                    Object value = entry.getValue().get("extensions");
                    if (value instanceof List) {
                        
                        @SuppressWarnings("unchecked")
                        List<String> extensions = (List<String>) value;

                        return extensions.stream().map(ext -> Map.entry(ext.substring(1), entry.getKey()));
                    }
                    return Stream.<Map.Entry<String, String>>empty();
                })
                .sorted(Comparator.comparingInt((Map.Entry<String, String> e) -> languagePriority.indexOf(e.getValue()))
                        .thenComparing(Map.Entry::getKey))
                .distinct()
                .collect(Collectors.toList());

        // Convert the List of Map Entries into a Map
        return entryList.stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (v1, v2) -> v1));         
    }
    
    Map<String, Long> newSeries(String param) {
        HashMap<String, Long> series = new HashMap<>();
        df.put(param, series);
        return series;
    }
    
    /**
      Check if `fileName` exists in `resourcesFolder`,
      otherwise it should download it from `downloadUrl`.
     */
    Path downloadYaml(String fileName, String downloadUrl) throws IOException {
        Path file = resourcesFolder.resolve(fileName);
        if (!Files.exists(file)) {
            out.println(fileName + " does not exist. Downloading...");
            if (!Files.exists(resourcesFolder)) {
                Files.createDirectories(resourcesFolder);
            }

            try (InputStream in = new URL(downloadUrl).openStream()) {
                Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
                out.println(file + " downloaded successfully.");
            }
        }
        return file;
    }

    void processFile(Path file) throws IOException {
        totalFileCount++;
        if (totalFileCount % 1000 == 0) {
            out.print('.');
        }

        String ext = getFileExtension(file.getFileName().toString());        
        String sourceFileType = sourceFileTypes.get(ext);
        if (sourceFileType != null) {
            totalSourceCount++;

            try {          
                int nl = countLines(file);
                long nb = Files.size(file);
                if (nb == 0) {
                    errors.add(new ErrorRecord("EmptyFileException", file.toString()));
                    return;
                }
                
                // Add counters if there was no MalformedInputException
                
                addCounter(fileCounts, ext, 1);

                totalLineCount += nl;
                addCounter(lineCounts, ext, nl);
                
                totalSize += nb;
                addCounter(sizeCounts, ext, nb);

                String fileName = file.getFileName().toString();
                if (projectFiles.contains(fileName)) {
                    List<String> pathList = projectPaths.get(fileName);
                    pathList.add(file.toAbsolutePath().toString().substring(baseFolderLen));
                }
                
                String input = Files.readString(file);
                if (ext.equals("java")) {
                    int k = countJavaTests(input);
                    addCounter(testCounts, ext, k);
                } else 
                if (jsFileTypes.contains(ext)) {
                    int k = countJsTests(input);
                    addCounter(testCounts, ext, k);                
                } else
                if (ext.equals("cs")) {
                    int k = countCsTests(input);
                    addCounter(testCounts, ext, k); 
                } else
                if (ext.equals("py")) {
                    int k = countPythonTests(input);
                    addCounter(testCounts, ext, k); 
                }
            } catch (MalformedInputException e) {
                errors.add(new ErrorRecord("MalformedInputException", file.toString()));
            }
        }
    }

    /**
     * Add to counter with extension `ext`.
     */
    void addCounter(Map<String, Long> countMap, String ext, long n) {
        Long k = countMap.get(ext);
        countMap.put(ext, k == null ? n : k + n);
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
     * Count lines in a text file
     */ 
    int countLines(Path file) throws IOException, MalformedInputException {
        int count = 0;
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            while (reader.readLine() != null) {
                count++;
            }
        }
        return count;
    }

    int countJavaTests(String input) throws IOException {
        Matcher matcher = javaTestPattern.matcher(input);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    int countJsTests(String input) throws IOException {
        Matcher matcher = jsTestPattern.matcher(input);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    int countCsTests(String input) throws IOException {
        Matcher matcher = csTestPattern.matcher(input);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    int countPythonTests(String input) throws IOException {
        Matcher matcher = pythonTestPattern.matcher(input);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    void outputResults() throws IOException {
        
        saveCountsToCsv(df, outputCsvName);
        
        saveProjectsToCsv(projectPaths, projectsCsvName);

        saveErrorsToCsv(errors, errorsCsvName);

        // Print totals
        out.println(HR);
        out.println(" Total files: " + totalFileCount);
        out.println("Source files: " + totalSourceCount);
        out.println(" Total lines: " + totalLineCount);
        out.println("  Total size: " + totalSize);
        out.println("      Errors: " + errors.size());    
    }

    /**
     * Save dictionary to CSV
     */ 
    void saveCountsToCsv(Map<String, Map<String, Long>> df, String csvName) throws IOException {

        BufferedWriter writer = Files.newBufferedWriter(outputFolder.resolve(csvName));

        List<String> extList = new ArrayList<>(fileCounts.keySet());
        Collections.sort(extList);

        List<String> params = new ArrayList<>(df.keySet());
        Collections.sort(params);

        writer.write("language, ext, " + String.join(", ", params) + "\n");
        for (String ext: extList) {
            String lang = sourceFileTypes.get(ext);

            // Extract values for this extension type
            long files = 0L;
            long lines = 0L;
            long size = 0L;
            long tests = 0L;
            if (lang == null) {
                lang = "";
            } else {
                // out.println("ext: " + ext + ", lang: " + lang);
                files = fileCounts.get(ext);
                lines = nullAsZero(lineCounts.get(ext));
                size = nullAsZero(sizeCounts.get(ext));
                tests = nullAsZero(testCounts.get(ext));
            }
            writer.write(String.format("%s, %s, %d, %d, %d, %d\n", lang, ext, files, lines, size, tests));
        }
        writer.close();
        out.println("File created: " + csvName);        
    }
    
    long nullAsZero(Long obj) {
        return obj == null? 0 : obj;
    }
    
    void saveProjectsToCsv(Map<String, List<String>> projectPaths, String csvName) throws IOException {
        BufferedWriter writer = Files.newBufferedWriter(outputFolder.resolve(csvName));
        
        writer.write("file, project\n");
        for (Map.Entry<String, List<String>> entry : projectPaths.entrySet()) {
            String file = entry.getKey();
            List<String> paths = entry.getValue();
            for (String project: paths) {
                writer.write(String.format("%s, %s\n", file, project));
            }
        }
        writer.close();
        out.println("File created: " + csvName);           
    }
    
    void saveErrorsToCsv(List<ErrorRecord> errors, String csvName) throws IOException {
        BufferedWriter writer = Files.newBufferedWriter(outputFolder.resolve(csvName));
        writer.write("error, file\n");
        for (ErrorRecord rec: errors) {
            writer.write(String.format("%s, %s\n", rec.error(), rec.file()));
        }
        writer.close();
        out.println("File created: " + csvName);           
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new p_stats()).execute(args);
        System.exit(exitCode);
    }
}

class LanguagePriorityComparator implements Comparator<String> {

    List<String> knownLanguages = List.of("Java", "JavaScript", "TypeScript", "C#", "Python", "TSX", "JSON", "YAML",
            "HTML", "XML", "XSLT", "CSS", "Less", "Sass", "SCSS", "Markdown", "Text", "Objective-C", "SQL",
            "Java Server Pages");

    @Override
    public int compare(String s1, String s2) {
        int k1 = knownLanguages.indexOf(s1);
        int k2 = knownLanguages.indexOf(s2);
        if (k1 >= 0 && k2 >= 0) {
            return k1 - k2;
        } else if (k1 >= 0) {
            return -1;
        } else if (k2 >= 0) {
            return 1;
        } else {
            return s1.compareTo(s2);
        }
    }
}

record ErrorRecord(String error, String file) {}