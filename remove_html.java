///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.4
//DEPS org.jsoup:jsoup:1.16.1


import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;


@Command(name = "remove_html", mixinStandardHelpOptions = true, version = "2028-08-27",
        description = "Remove html tags from file")
class remove_html implements Callable<Integer> {


    @Parameters(index = "0", description = "Input file")
    private String inputFile;

    @Option(names = {"-o", "--overwrite"}, description = "Overwrite input file")
    boolean overwrite;

    @Override
    public Integer call() throws Exception {
        System.out.println("Input file: " + inputFile);

        File input = new File(inputFile);
        Document document = Jsoup.parse(input, "UTF-8");
        String result = document.text();
        if (!overwrite) {
            System.out.println(result);
        } else {
            PrintWriter out = new PrintWriter(new FileOutputStream(inputFile));
            out.print(result);
            out.close();
            System.out.println("File overwritten: " + inputFile);
        }

        return 0;
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new remove_html()).execute(args);
        System.exit(exitCode);
    }    
}
