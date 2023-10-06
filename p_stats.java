///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.5
//DEPS org.apache.commons:commons-csv:1.10.0
//DEPS org.yaml:snakeyaml:1.33

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import static java.lang.System.out;

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

@Command(name = "p_stats", mixinStandardHelpOptions = true, version = "2023-10-06", 
         description = "Calculate project stats")
class p_stats implements Callable<Integer> {

    @Parameters(index = "0", description = "Input folder.", defaultValue = ".")
    Path inputFolder;
    
    @Option(names = { "-r", "--resources" }, description = "Folder to download resources if not available.",
                    defaultValue = "resources")
    Path resourcesFolder;

    // Output CSV file name for all file type counts
    String fileTypeCountsCsvName = "file_type_counts.csv";
    
    // Output CSV file name for number of tests for file types
    String testTypeCountsCsvName = "test_type_counts.csv";

    String errorsCsvName = "errors.csv";

    Yaml yaml = new Yaml();

    int totalFileCount = 0;
    int totalLineCount = 0;
    int totalSourceCount = 0;

    List<String> excludedFolders = List.of(".git", ".angular", ".gradle", "bin", "build", "node_modules");

    /**
     * Map where a key is the name of the language, and a value is `Map<String, Object>`,
     * where for `extensions` key the value is `List<String>` of file extensions that belong to this language.
     */
    Map<String, Map<String, Object>> languages;

    /**
     * Map where each file extension is mapped to the name of the language.
     */
    Map<String, String> sourceFileTypes;

    List<String> jsFileTypes = List.of(
        "js", "jsx", "ts", "tsx"
    );

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

    /**
     * Counts for each file extension.
     */
    Map<String, Integer> fileTypeCounts = new HashMap<>();

    /**
     * Counts tests for each file extension.
     */
    Map<String, Integer> testTypeCounts = new HashMap<>();

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

        // Get path to the resource file, download it if needed
        Path langPath = downloadYaml("languages.yml", 
                 "https://raw.githubusercontent.com/github-linguist/linguist/master/lib/linguist/languages.yml");

        languages = yaml.load(Files.newInputStream(langPath));

        // Collect all language names and sort them accrording to `knownLanguages`
        List<String> languagePriority = new ArrayList<>(languages.keySet());
        languagePriority.sort(new LanguagePriorityComparator());

        // Transform the map and collect into a List of Map Entries
        List<Map.Entry<String, String>> entryList = languages.entrySet().stream()
                .flatMap(entry -> {
                    Object value = entry.getValue().get("extensions");
                    if (value instanceof List) {
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
        sourceFileTypes = entryList.stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (v1, v2) -> v1));       

        /* Process each file in the tree.
         */
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

        String ext = getFileExtension(file.getFileName().toString());
        if (ext.length() > 0) {
            increaseCounter(fileTypeCounts, ext);
        }
        
        String sourceFileType = sourceFileTypes.get(ext);
        if (sourceFileType != null) {
            totalSourceCount++;

            try {
                int n = countLines(file);
                totalLineCount += n;

                String input = Files.readString(file);
                if (ext.equals("java")) {
                    int k = countJavaTests(input);
                    addCounter(testTypeCounts, ext, k);
                } else 
                if (jsFileTypes.contains(ext)) {
                    int k = countJsTests(input);
                    addCounter(testTypeCounts, ext, k);                
                } else
                if (ext.equals("cs")) {
                    int k = countCsTests(input);
                    addCounter(testTypeCounts, ext, k); 
                } else
                if (ext.equals("py")) {
                    int k = countPythonTests(input);
                    addCounter(testTypeCounts, ext, k); 
                }
            } catch (MalformedInputException e) {
                errors.add(new ErrorRecord("MalformedInputException", file.toString()));
            }
        }
    }

    /**
     * Count files with extension `ext`.
     */
    void increaseCounter(Map<String, Integer> countMap, String ext) {
        Integer k = countMap.get(ext);
        countMap.put(ext, k == null ? 1 : k + 1);
    }

    /**
     * Add to counter with extension `ext`.
     */
    void addCounter(Map<String, Integer> countMap, String ext, int n) {
        Integer k = countMap.get(ext);
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
     * @return The number of lines in a text file
     * @throws IOException
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
        
        saveCountsToCsv(fileTypeCounts, fileTypeCountsCsvName);

        saveCountsToCsv(testTypeCounts, testTypeCountsCsvName);

        saveErrorsToCsv(errors, errorsCsvName);

        // Print totals
        out.println(HR);
        out.println(" Total files: " + totalFileCount);
        out.println("Source files: " + totalSourceCount);
        out.println(" Total lines: " + totalLineCount);
        out.println("      Errors: " + errors.size());    
    }

    /**
     * Save file type counts
     * @throws IOException
     */ 
    void saveCountsToCsv(Map<String, Integer> countMap, String csvName) throws IOException {

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
            String lang = sourceFileTypes.get(ext);
            if (lang == null) {
                lang = "";
            }
            writer.write(String.format("%s, %s, %d\n", lang, ext, countMap.get(ext)));
        }
        writer.close();
        out.println("File created: " + csvName);        
    }

    void saveErrorsToCsv(List<ErrorRecord> errors, String csvName) throws IOException {
        BufferedWriter writer = Files.newBufferedWriter(Path.of(csvName));
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