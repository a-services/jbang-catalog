///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS com.vladsch.flexmark:flexmark-all:0.64.8

import static java.lang.System.*;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.html.HtmlRenderer;


// https://github.com/vsch/flexmark-java

/**
 * HTTP server that serves markdown files from folder which name ends with ` Book`.
 */
public class bookserver {

    final int port = 8080;
    
    // Regular expression pattern to match [[name]]
    final Pattern wikilinkPattern = Pattern.compile("\\[\\[([^\\]]+)\\]\\]");

    String bookName;

    class MyHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {

            String fileName = exchange.getRequestURI().getPath();
            if (fileName.length() == 1) {
                fileName += bookName;
            }

            //String fileName = path.substring(1); // Remove leading "/"
            String filePath = bookName + fileName + ".md";
            out.println(filePath);
            File markdownFile = new File(filePath);

            if (markdownFile.exists() && markdownFile.isFile()) {
                String markdownContent = Files.readString(markdownFile.toPath(), StandardCharsets.UTF_8);
                
                // Replace wikilinks with proper Markdown links
                markdownContent = convertWikilinks(markdownContent);
                
                // Convert Markdown to HTML
                Parser parser = Parser.builder().build();
                HtmlRenderer renderer = HtmlRenderer.builder().build();
                String htmlContent = renderer.render(parser.parse(markdownContent));

                htmlContent = """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <title>Document</title>
                    </head>
                    <body>
                    """ +
                    htmlContent +        
                    """    
                    </body>
                    </html>
                    """;
                // Set response headers
                exchange.getResponseHeaders().add("Content-Type", "text/html");
                exchange.sendResponseHeaders(200, htmlContent.getBytes().length);

                // Write HTML response
                try (OutputStream outputStream = exchange.getResponseBody()) {
                    outputStream.write(htmlContent.getBytes());
                }

            } else {
                // File not found
                exchange.sendResponseHeaders(404, 0);
                exchange.close();
            }
        }
    }
    
    String convertWikilinks(String markdownContent) {
        Matcher matcher = wikilinkPattern.matcher(markdownContent);

        // Replace wikilinks with [name](name)
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String wikilink = matcher.group(1);
            //String replacement = "[" + wikilink + "](" + wikilink + ")";
            String replacement = "<a href=\"" + wikilink + "\">" + wikilink + "</a>";
            matcher.appendReplacement(result, replacement);
        }
        matcher.appendTail(result);

        return result.toString();
    }

    void run() throws Exception {
        bookName = findBookName();
        if (bookName == null) {
            System.err.println("[ERROR] Cannot find folder name that ends with ` Book`");
            return;
        }
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/", new MyHandler());

        server.start();
        out.println("Server started at http://localhost:" + port);
    }

    String findBookName() {
        File currentDir = new File(".");
        File[] folders = currentDir.listFiles(File::isDirectory);

        for (File folder : folders) {
            String folderName = folder.getName();
            if (folderName.endsWith(" Book")) {
                return folderName;
            }
        }
        return null;
    }

    public static void main(String... args) throws Exception {
        new bookserver().run();
    }
}
