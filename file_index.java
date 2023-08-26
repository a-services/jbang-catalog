///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.4


import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "file_index", mixinStandardHelpOptions = true, version = "2023-07-25",
        description = "Generate index of files for current folder")
class file_index implements Callable<Integer> {

    @Option(names = {"-n", "--name"}, description = "Folder name")
    String folderName;
    
    public static String outFile = "index.html";

    List<String> fileList = new ArrayList<>(); 

    @Override
    public Integer call() throws Exception { 

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Path.of("."))) {
            for (Path file: stream) {
                String fname = file.getFileName().toString();
                if (!fname.equals(outFile)) {
                    fileList.add(fname);
                }
            }
        } 
        fileList.sort(null);

        if (folderName == null) {
            folderName = Path.of(".").toRealPath().getFileName().toString();
        }

        PrintWriter f = new PrintWriter(new FileOutputStream(outFile));
        f.printf("""
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
            folderName, folderName, createContent());
        f.close();
        System.out.println("File created: " + outFile);

        return 0;
    }

    /**
     * Listing a Directory's Contents
     * https://docs.oracle.com/javase/tutorial/essential/io/dirs.html#listdir
     */
    String createContent() throws IOException, DirectoryIteratorException {
        StringBuilder sb = new StringBuilder();
        sb.append("<ol>");
        for (String fname: fileList) {
            sb.append(String.format("<li><a href=\"%s\">%s</a></li>\n", fname, fname));
        }
        sb.append("</ol>");
        return sb.toString();
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new file_index()).execute(args);
        System.exit(exitCode);
    }    
}
