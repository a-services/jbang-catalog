///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.1


import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import static java.lang.System.out;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;


/**
 * Вырезать страничку из HTML созданным Calibre
 */
@Command(name = "calibre_page", mixinStandardHelpOptions = true, version = "2023-02-27",
        description = "Extract page from HTML created by Calibre")
class calibre_page implements Callable<Integer> {

    @Parameters(index = "0", description = "Page title")
    private String pageTitle;

    @Option(names = {"-t", "--title"}, description = "HTML file", required = true,
            defaultValue = "./html/index.html")
    private Path htmlFile;

    @Option(names = {"-i", "--index"}, description = "Index number")
    private Integer indexNum;

    @Option(names = {"-i1", "--start-index"}, description = "Start index")
    private String startIndex;

    @Option(names = {"-i2", "--end-index"}, description = "End index")
    private String endIndex;
    
    // Java Tutorial: List
    // https://docs.oracle.com/javase/tutorial/collections/interfaces/list.html

    private List<String> lines;

    
    @Override
    public Integer call() throws Exception { 

        if (indexNum != null) {
            startIndex = "calibre_link-" + indexNum;
            endIndex = "calibre_link-" + (indexNum + 1);
        }
        if (startIndex == null) {
            out.println("[ERROR] Start index required");
            return 1;
        }
        if (endIndex == null) {
            out.println("[ERROR] End index required");
            return 1;
        }

        out.println("  HTML File: " + htmlFile);
        out.println("Start index: " + startIndex);
        out.println("  End index: " + endIndex);

        lines = Files.readAllLines(htmlFile);
        int k1 = findLineIndex(startIndex);
        if (k1 == -1) {
            out.println("[ERROR] Start index not found: " + startIndex);
            return 1;
        }
        int k2 = findLineIndex(endIndex);
        if (k2 == -1) {
            out.println("[ERROR] End index not found: " + endIndex);
            return 1;
        }
        if (k1 >= k2) {
            out.println("[ERROR] Start index greater or equal than end index");
            return 1;
        }

        saveStr(pageTitle + ".html", String.join("\n", lines.subList(k1, k2)));
        return 0;
    }

    /**
     * Save string to file.
     */
    static void saveStr(String fname, String text) throws IOException {
        Files.writeString(Path.of(fname), text);
        out.println("File created: " + fname);
    }

    private int findLineIndex(String anchor) {
        for (int i=0; i<lines.size(); i++) {
            if (lines.get(i).contains(" id=\"" + anchor + "\"")) {
                return i;
            }
        }
        return -1;
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new calibre_page()).execute(args);
        System.exit(exitCode);
    }
}
