///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.1


import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.System.out;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

@Command(name = "fix_chapters", mixinStandardHelpOptions = true, version = "2023-04-01",
        description = "Fix numbers in markdown chapters")
class fix_chapters implements Callable<Integer> {

    @Parameters(index = "0", description = "Markdown file")
    private String fileName;

    @Parameters(index = "1", description = "Start number", defaultValue="1")
    private int startNumber;

    @Option(names = {"-o", "--out"}, description = "Send output to fix_chapters.out")
    private boolean outputToFile;

    @Option(names = {"-s", "--section"}, 
    	    description = "Section can start with any number of characters # = * probably followed by section number")
    private String sectionStarts;

    List<String> text;
    boolean canUpdate = false;

    @Override
    public Integer call() throws Exception { 
        text = Files.readAllLines(Path.of(fileName));

        fixChapters();

        if (canUpdate) {
            if (outputToFile) {
                saveList("fix_chapters.out", text);
            } else {
                for (String ln: text) {
                    out.println(ln);
                }
            }
        } else {
            out.println("[WARNING] Nothing to update");
        }
        return 0;
    }

    // java.util.regex.Pattern
    // https://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html
    void fixChapters() {
        Pattern regex = Pattern.compile("^" + addBackslashes(sectionStarts) + "\\s*(\\d+\\.?\\s+)?");
        int num = startNumber;
        for (int i=0; i<text.size(); i++) {
            String ln = text.get(i);
            Matcher m = regex.matcher(ln);
            if (m.lookingAt()) {
                ln = sectionStarts + " " + num + ". " + ln.substring(m.end());
                //out.println(ln + "\n");
                canUpdate = true;
                num++;
                text.set(i, ln);
            }
        }
    }

    String addBackslashes(String text) {
        String regex = "(.)";
        String replacement = "\\\\$1";
        return text.replaceAll(regex, replacement);
    }
    
    public static void saveList(String fname, List<String> text)
            throws IOException {
        PrintWriter f = new PrintWriter(new FileOutputStream(fname));
        for (String ln: text) {
            f.println(ln);
        }
        f.close();
        out.println("[SUCCESS] File created: " + fname);
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new fix_chapters()).execute(args);
        System.exit(exitCode);
    }    
}
