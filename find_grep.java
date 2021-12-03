///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.6.2


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

@Command(name = "find_grep", mixinStandardHelpOptions = true, version = "find_grep 0.1",
        description = "Find substring like grep")
class find_grep implements Callable<Integer> {

    @Parameters(index = "0", description = "String to search")
    private String pattern;

    @Parameters(index = "1", description = "Target folder")
    private String targetDir;

    @Option(names = {"-o", "--out"}, description = "Output file")
    private String outFile;

    @Option(names = {"-n", "--file-names"}, description = "Print file names to output file")
    private Boolean printFileNames;

    public static void main(String... args) {
        int exitCode = new CommandLine(new find_grep()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {

        pattern = pattern.toLowerCase();

        PrintWriter pw = null;
        if (outFile != null) {
            pw = new PrintWriter(outFile);
        }

        String[] files = new File(targetDir).list();
        Arrays.sort(files);
        int count = 0;
        String printedFileName = null;
        for (int i=0; i<files.length; i++) {
            File f = new File(targetDir, files[i]);
            out.println("--- " + f.getName());
            try (Scanner scanner = new Scanner(f)) {
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    if (line.toLowerCase().contains(pattern)) {
                        out.println(line);
                        count++;

                        if (pw != null) {
                            if (printFileNames != null && !(f.getName().equals(printedFileName))) {
                                pw.println("--- " + f.getName());
                                printedFileName = f.getName();
                            }
                            pw.println(line);
                        }
                    }
                 }
            }
        }
        out.println("=== " + files.length + " files scanned, found " + count + " times");

        if (pw != null) {
            pw.close();
        }
        return 0;
    }
}
