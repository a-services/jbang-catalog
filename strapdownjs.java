///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.6.3

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.*;
import java.util.Arrays;
import java.util.concurrent.Callable;

@Command(name = "strapdownjs", mixinStandardHelpOptions = true, version = "1.0", description = "Create HTML from markdown using strapdownjs")
class strapdownjs implements Callable<Integer> {

    @Parameters(index = "0", description = "Markdown file")
    private String markdownFile;

    @Option(names = { "-t", "--theme" }, description = "Theme name from bootswatch.com", defaultValue = "readable")
    private String theme;

    String[] themes = { "amelia", "cerulean", "cyborg", "journal", "readable", "simplex",
            "slate", "spacelab", "spruce", "superhero", "united" };

    public static void main(String... args) {
        int exitCode = new CommandLine(new strapdownjs()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        if (!markdownFile.endsWith(".md")) {
            System.err.println("[ERROR] .md extension expected");
            return 1;
        }
        File md = new File(markdownFile);
        if (!md.exists()) {
            System.err.println("[ERROR] File not found: " + markdownFile);
            return 1;
        }
        if (!Arrays.asList(themes).contains(theme)) {
            System.err.println("[ERROR] Theme not found: " + theme);
            System.err.println("        Available themes: " + String.join(", ", themes));
            return 1;
        }
        String text = loadStr(markdownFile);
        String htmlFile = markdownFile.substring(0, markdownFile.length() - 3) + ".html";
        String result = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<title>" + markdownFile + "</title>\n" +
                "\n" +
                "<xmp theme=\"" + theme + "\" style=\"display:none;\">\n" +
                text + "\n" +
                "</xmp>\n" +
                "\n" +
                "<script src=\"http://strapdownjs.com/v/0.2/strapdown.js\"></script>\n" +
                "</html>\n";
        saveStr(htmlFile, result);
        System.out.println("File saved: " + htmlFile);
        return 0;
    }

    /** Save string to file. */
    public static void saveStr(String fname, String text)
            throws IOException {
        PrintWriter out = new PrintWriter(new FileOutputStream(fname));
        out.print(text);
        out.close();
    }

    /** Load string from file. */
    public static String loadStr(String fname) throws FileNotFoundException,
            IOException {
        FileInputStream in = new FileInputStream(fname);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int count = 0;
        final int BUF_SIZE = 8 * 1024;
        byte[] buffer = new byte[BUF_SIZE];
        while ((count = in.read(buffer, 0, BUF_SIZE)) > 0)
            out.write(buffer, 0, count);
        out.flush();
        in.close();
        return out.toString();
    }

}
