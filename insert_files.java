///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.6

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/// Reads an input file, searches for lines matching
/// ```
/// :insert: /path/to/file
/// ```
/// and replaces those lines with the contents of the referenced file.
@Command(name = "insert_files", mixinStandardHelpOptions = true, version = "2024-12-26",
        description = "Replace file names with the contents of the respective files.")
class insert_files implements Callable<Integer> {

    @Parameters(index = "0", description = "Input file name")
    private String inputFileName;

    @Parameters(index = "1", description = "Output file name", defaultValue = "")
    private String outputFileName;

    private static final Pattern INSERT_PATTERN = Pattern.compile("^:insert:\\s+(.+)$");

    public static void main(String... args) {
        int exitCode = new CommandLine(new insert_files()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception { 
        System.out.println("Input file: " + inputFileName);

        Path inputFile = Paths.get(inputFileName);
        if (!Files.exists(inputFile)) {
            System.err.println("[ERROR] File not found: " + inputFile);
            return 1;
        }

        StringBuilder sb = new StringBuilder();

        // Read the input file line by line
        for (String line : Files.readAllLines(inputFile)) {
            Matcher matcher = INSERT_PATTERN.matcher(line);
            if (matcher.matches()) {
                String filePath = matcher.group(1).trim();
                Path toInsert = Paths.get(filePath);

                // Check if the file to insert exists
                if (!Files.exists(toInsert)) {
                    System.err.println("[ERROR] File to insert not found: " + toInsert);
                    return 1;
                }

                sb.append("```\n")
                  .append(Files.readString(toInsert)).append('\n')
                  .append("```\n");
            } else {
                // Print the line as is
                sb.append(line).append('\n');
            }
        }

        if (outputFileName.length() > 0) {
            saveStr(outputFileName, sb.toString());
            System.out.println("File created: " + outputFileName);
        } else {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(new StringSelection(sb.toString()), null);
            System.out.println("Result copied to clipboard");
        }

        return 0;
    }

    /** Save string to file. */
    public static void saveStr(String fname, String text)
            throws IOException {
        PrintWriter out = new PrintWriter(new FileOutputStream(fname));
        out.print(text);
        out.close();
    }
}
