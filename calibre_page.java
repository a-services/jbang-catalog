///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.5


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
@Command(name = "calibre_page", mixinStandardHelpOptions = true, version = "2024-03-16",
        description = "Extract page from HTML created by Calibre")
class calibre_page implements Callable<Integer> {

    @Parameters(index = "0", description = "Index number for calibre_link tag", defaultValue="-1")
    private Integer indexNum;

    @Option(names = {"-s", "--start-tag"}, description = "Start tag for the extracted text")
    private String startTag;

    @Option(names = {"-e", "--end-tag"}, description = "End tag for the extracted text")
    private String endTag;
    
    @Option(names = {"-f", "--file"}, description = "HTML file", required = true,
        defaultValue = "./html/index.html")
    private Path htmlFile;

    // Java Tutorial: List
    // https://docs.oracle.com/javase/tutorial/collections/interfaces/list.html

    private List<String> lines;

    
    @Override
    public Integer call() throws Exception { 

        if (indexNum >= 0) {
            if (startTag == null) {
                startTag = "calibre_link-" + indexNum;
            }
            if (endTag == null) {
                endTag = "calibre_link-" + (indexNum + 1);
            }
        }
        if (startTag == null) {
            out.println("[ERROR] Start index required");
            return 1;
        } 
        if (endTag == null) {
            out.println("[ERROR] End index required");
            return 1;
        }

        out.println("HTML File: " + htmlFile);
        out.println("Start tag: " + startTag);
        out.println("  End tag: " + endTag);

        lines = Files.readAllLines(htmlFile);
        int k1 = findLineIndex(startTag);
        if (k1 == -1) {
            out.println("[ERROR] Start tag not found: " + startTag);
            return 1;
        }
        int k2 = findLineIndex(endTag);
        if (k2 == -1) {
            out.println("[ERROR] End tag not found: " + endTag);
            return 1;
        }
        if (k1 >= k2) {
            out.println("[ERROR] Start tag is after the end tag");
            return 1;
        }

        saveStr(startTag + ".html", String.join("\n", lines.subList(k1, k2)));
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
