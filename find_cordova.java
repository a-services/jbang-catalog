///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.6.3
//JAVA 15+

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.Callable;

import javax.swing.JOptionPane;

import static java.lang.System.out;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import static java.nio.file.FileVisitResult.*;

@Command(name = "find_cordova", mixinStandardHelpOptions = true, version = "1.0",
        description = "Search Cordova 8.x docs")
class find_cordova implements Callable<Integer> {

    @Parameters(index = "0", description = "Search pattern", defaultValue = "")
    private String pattern;

    String targetDir = "8.x";

    String outputName = "find_cordova.html";

    List<FileResult> fileResults = new ArrayList<>();
    
    @Override
    public Integer call() throws Exception { 

        // Если паттерн поиска не указан в командной строке, выбросим свинговое окошко
        if (pattern.length() == 0) {
            pattern = JOptionPane.showInputDialog("Pattern?");
            if (pattern == null) {
                return 1;
            }
        }

        pattern = pattern.toLowerCase();
        out.println("Pattern: " + pattern);

        walkFileTree();

        out.println("::::::::: Results found: " + fileResults.size());

        if (fileResults.size() > 0) {
            outputSearchResults();
        }

        return 0;
    }
    
    void outputSearchResults() {
        try (BufferedWriter w = Files.newBufferedWriter(Path.of(outputName))) {

            StringJoiner listItems = new StringJoiner("\n");
            for (FileResult fileResult : fileResults) {
                String p = fileResult.file.toString();
                if (p.endsWith(".md")) {
                    p = p.substring(0, p.length() - ".md".length()) + ".html";
                }
                listItems.add(String.format("<li><a target=\"_blank\" href=\"https://cordova.apache.org/docs/en/%s\">%s</a></li>", p, p));
            }

            String title = "Searching Cordova docs";
            w.write("""
                <!doctype html>
                <html lang="en">
                
                <head>
                   <title>%s</title>
                   <meta charset="utf-8">
                   <meta name="viewport" content="width=device-width, initial-scale=1">
                   <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.2.2/dist/css/bootstrap.min.css" rel="stylesheet">
                </head>
                
                <body class="p-4">
                    <h2>%s</h2>
                    <p class="lead">%s</p>
                    <ol>
                    %s
                    </ol>
                    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.2.2/dist/js/bootstrap.bundle.min.js"></script>
                </body>
                
                </html>
                """.formatted(title, title, pattern, listItems));
    
                out.println("File created: " + outputName);                        

        } catch (IOException ex) {
            out.println("IOException: " + ex);
        }
    }

    // https://docs.oracle.com/javase/tutorial/essential/io/walk.html
    void walkFileTree() throws IOException {

        Files.walkFileTree(Path.of(targetDir), new FileVisitor<Path>() {

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                return CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                findPattern(file);
                return CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                return CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                return CONTINUE;
            }
            
        });
    }

    /**
     * Проверить паттерн поиска в данном файле.
     */ 
    void findPattern(Path file) throws IOException {
        List<String> allLines = Files.readAllLines(file);
        int k = 0;
        List<LineResult> result = new ArrayList<>();
        for (String line : allLines) {
            k++;
            if (line.toLowerCase().contains(pattern)) {
                result.add(new LineResult(k, line));
            }
        }
        if (result.size()>0) {
            fileResults.add(new FileResult(file, result));
        }
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new find_cordova()).execute(args);
        System.exit(exitCode);
    }

}

class LineResult {
    int lno;
    String s;
    public LineResult(int lno, String s) {
        this.lno = lno;
        this.s = s;
    }
}

class FileResult {
    Path file;
    List lines = new ArrayList<LineResult>();
    public FileResult(Path file, List lines) {
        this.file = file;
        this.lines = lines;
    }
    @Override
    public String toString() {
        return file.toString();
    }
}
