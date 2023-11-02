///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.5
//DEPS com.vladsch.flexmark:flexmark-all:0.64.8

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import static java.lang.System.out;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.vladsch.flexmark.ast.BulletList;
import com.vladsch.flexmark.ast.Emphasis;
import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.ast.ListItem;
import com.vladsch.flexmark.ast.StrongEmphasis;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;

@Command(name = "obsidian_folder", mixinStandardHelpOptions = true, version = "2023-11-02", description = "Convert Obsidian folder to HTML")
class obsidian_folder implements Callable<Integer> {

    @Parameters(index = "0", defaultValue = ".", description = "Input folder.")
    Path inputFolder;

    @Parameters(index = "1", defaultValue = ".", description = "Output folder.")
    Path outputFolder;

    @Option(names = { "-a", "--asciidoc" }, description = "Output AsciiDoc")
    boolean outputAsciiDoc;

    // Regular expression pattern to match [[name]]
    final Pattern wikilinkPattern = Pattern.compile("\\[\\[([^\\]]+)\\]\\]");

    @Override
    public Integer call() throws Exception {
        out.println("------------------------------");
        out.println("  Input folder: " + inputFolder);
        out.println(" Output folder: " + outputFolder);

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(inputFolder)) {
            for (Path p : stream) {
                // out.println(fileToString(p));
                if (Files.isRegularFile(p) &&
                        p.getFileName().toString().endsWith(".md")) {
                    if (outputAsciiDoc) {
                        createAsciiDoc(p);
                    } else {
                        createHtml(p);
                    }

                }
            }
        } catch (IOException | DirectoryIteratorException ex) {
            System.err.println("Error occurred: " + ex.getMessage());
        }

        return 0;
    }

    private void createAsciiDoc(Path markdownFile) throws IOException {
        String markdownContent = Files.readString(markdownFile, StandardCharsets.UTF_8);

        // Replace wikilinks with proper Markdown links
        markdownContent = convertWikilinks(markdownContent);

        // Parse the Markdown to AST
        MutableDataSet options = new MutableDataSet();
        Parser parser = Parser.builder(options).build();
        Document document = parser.parse(markdownContent);

        // Convert the AST to AsciiDoc
        String asciidoc = convertToAsciiDoc(document);

        String name = removeExt(markdownFile.getFileName().toString());
        String fname = outputFolder.resolve(name + ".adoc").toString();
        out.println(fname);
        saveStr(fname, asciidoc);
    }

    private static String convertToAsciiDoc(Node node) {
        StringBuilder builder = new StringBuilder();
        Node child = node.getFirstChild();

        while (child != null) {
            // You need to implement conversion logic for each node type
            if (child instanceof Heading) {
                Heading heading = (Heading) child;
                String prefix = "==".repeat(heading.getLevel());
                builder.append(prefix).append(" ").append(heading.getText()).append("\n\n");
            } else if (child instanceof StrongEmphasis) {
                // For bold text in AsciiDoc
                builder.append("*").append(child.getFirstChild().getChars()).append("*");
            } else if (child instanceof Emphasis) {
                // For italic text in AsciiDoc
                builder.append("_").append(child.getFirstChild().getChars()).append("_");
            } else if (child instanceof BulletList) {
                // Convert bullet list items
                builder.append(convertBulletList((BulletList) child));
            } else {
                // Fallback for unhandled node types
                builder.append(child.getChars());
            }

            child = child.getNext();
        }

        return builder.toString();
    }

    private static String convertBulletList(BulletList bulletList) {
        StringBuilder builder = new StringBuilder();
        for (Node item : bulletList.getChildren()) {
            if (item instanceof ListItem) {
                builder.append("* ").append(item.getFirstChild().getChars().trim()).append("\n");
            }
        }
        builder.append("\n");
        return builder.toString();
    }

    private void createHtml(Path markdownFile) throws Exception {
        String markdownContent = Files.readString(markdownFile, StandardCharsets.UTF_8);

        // Replace wikilinks with proper Markdown links
        markdownContent = convertWikilinks(markdownContent);

        // Convert Markdown to HTML
        Parser parser = Parser.builder().build();
        HtmlRenderer renderer = HtmlRenderer.builder().build();
        String htmlContent = renderer.render(parser.parse(markdownContent));
        String name = removeExt(markdownFile.getFileName().toString());

        htmlContent = String.format("""
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>%s</title>
                </head>
                <body>
                <h3>%s</h3>
                %s
                </body>
                </html>
                """,
                name, name, htmlContent);

        String htmlName = outputFolder.resolve(name + ".html").toString();
        out.println(htmlName);
        saveStr(htmlName, htmlContent);
    }

    private String removeExt(String inputName) {
        int k = inputName.lastIndexOf('.');
        if (k == -1) {
            return inputName;
        }
        return inputName.substring(0, k);
    }

    String convertWikilinks(String markdownContent) {
        Matcher matcher = wikilinkPattern.matcher(markdownContent);

        // Replace wikilinks with [name](name)
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String wikilink = matcher.group(1);
            String replacement = "[" + wikilink + "](" + wikilink.replace(" ", "%20") + ".html)";
            matcher.appendReplacement(result, replacement);
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /** Save string to file. */
    public static void saveStr(String fname, String text)
            throws IOException {
        PrintWriter out = new PrintWriter(new FileOutputStream(fname));
        out.print(text);
        out.close();
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new obsidian_folder()).execute(args);
        System.exit(exitCode);
    }
}
