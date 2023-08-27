///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.4


import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Command(name = "replace_br", mixinStandardHelpOptions = true, version = "2023-08-27",
        description = "Replace <br> tags in input file with new lines")
class replace_br implements Callable<Integer> {

    @Parameters(index = "0", description = "Input file")
    private String inputFile;

    @Option(names = {"-o", "--overwrite"}, description = "Overwrite input file")
    boolean overwrite;


    final String regex = "<br>";

    @Override
    public Integer call() throws Exception {

        System.out.println("== Input file: " + inputFile);
        String text = Files.readString(Path.of(inputFile));
        
        if (!overwrite) {
            //processInput("123<br>456<br>789", System.out);
            processInput(text, System.out);
        } else {
            PrintStream out = new PrintStream(new FileOutputStream(inputFile));
            processInput(text, out);
            out.close();
            System.out.println("File overwritten: " + inputFile);
        }
        return 0;
    }

    private void processInput(String text, PrintStream out) {
        Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        int count = 0;
        int pos = 0;
        while (m.find()) {
            out.println(text.substring(pos, m.start()));
            pos = m.end();
            count++;
        }
        out.print(text.substring(pos));

        System.out.println("\n== " + count + " findings");        
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new replace_br()).execute(args);
        System.exit(exitCode);
    }
}
