///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.4


import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

@Command(name = "extract_html_headers", mixinStandardHelpOptions = true, version = "2023-08-11",
        description = "Extract header tags from html file as indented text to create table of contents")
class extract_html_headers implements Callable<Integer> {

    @Parameters(index = "0", description = "Input file", defaultValue="html/index.html")
    private String inputFile;

    final String outputFile = "html_headers.txt";

    @Override
    public Integer call() throws Exception {
        System.out.println(" Input file: " + inputFile);
        
        String text = Files.readString(Path.of(inputFile));
		createTextFile(text, outputFile);

        return 0;
    }

	void createTextFile(String text, String outputFile) throws FileNotFoundException {
        PrintWriter out = new PrintWriter(new FileOutputStream(outputFile));
        
		indentRegExp(text, out, "<h([1-6])(.*?)>(.*?)</h\\1>");

        out.close();
		System.out.println(" File created: " + outputFile);		
	}

	void indentRegExp(String text, PrintWriter out, String regex) {
		Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
		Matcher m = p.matcher(text);
		int k = 0;
		while (m.find()) {
			out.println("  ".repeat(Integer.parseInt(m.group(1))) + m.group(3));
			k++;
		}
		System.out.println(k + " findings");
	}

    public static void main(String... args) {
        int exitCode = new CommandLine(new extract_html_headers()).execute(args);
        System.exit(exitCode);
    }
}
