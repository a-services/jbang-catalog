///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.3
//DEPS org.apache.commons:commons-csv:1.10.0

import static java.lang.System.*;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import javax.swing.JOptionPane;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

@Command(header = {
        "@|cyan  _____   ___   _      ___      ___  ____    _____ ____  _____    ___  _____|@",
        "@|cyan |     | /   \\ | |    |   \\    /  _]|    \\  / ___/|    ||     |  /  _]/ ___/|@",
        "@|cyan |   __||     || |    |    \\  /  [_ |  D  )(   \\_  |  | |__/  | /  [_(   \\_ |@",
        "@|cyan |  |_  |  O  || |___ |  D  ||    _]|    /  \\__  | |  | |   __||    _]\\__  ||@",
        "@|cyan |   _] |     ||     ||     ||   [_ |    \\  /  \\ | |  | |  /  ||   [_ /  \\ ||@",
        "@|cyan |  |   |     ||     ||     ||     ||  .  \\ \\    | |  | |     ||     |\\    ||@",
        "@|cyan |__|    \\___/ |_____||_____||_____||__|\\_|  \\___||____||_____||_____| \\___||@",
        "@|cyan                                                                            |@",
        ""
}, name = "folder_sizes", mixinStandardHelpOptions = true, version = "2023-05-01", 
   description = "Calculate folder sizes.")
public class folder_sizes implements Callable<Integer> {

    @Parameters(index = "0", description = "Input folder.", defaultValue = ".")
    Path inputFolder;

    @Option(names = {"-t", "--track-csv"}, description = "Track CSV files.")
    boolean trackCsvFiles;

    @Option(names = {"-ic", "--ignore-csv"}, description = "Ignore CSV files.")
    boolean ignoreCsvFiles;

    @Option(names = {"-ig", "--ignore-gaps"}, description = "Ignore gap folders.")
    boolean ignoreGapFolders;

    @Option(names = {"-m", "--more-gaps"}, description = "More gap folders.")
    boolean moreGapFolders;
    
    @Option(names = {"-d", "--delete-gaps"}, description = "Delete gap folders.")
    boolean deleteGapFolders;

    static final String csvName = "folder_sizes.csv";

    static final int maxDepth = 10;

    List<String> gapFolders = Arrays.asList(new String[] { "node_modules" });

    // Walking the File Tree
    // https://docs.oracle.com/javase/tutorial/essential/io/walk.html

    // java.util.Formatter
    // https://docs.oracle.com/javase/8/docs/api/java/util/Formatter.html

    //List<Folder> folderList = new ArrayList<>();

    Map<Path, Long> knownFolders = new HashMap<>();

    long total;
    long topSize;

    String terminator;
    boolean deleteAlways = false;

    @Override
    public Integer call() throws Exception {
        if (!Files.exists(inputFolder)) {
            out.println("[ERROR] Folder not found: " + inputFolder);
            return 1;
        }
        if (!Files.isDirectory(inputFolder)) {
            out.println("[ERROR] Folder expected: " + inputFolder);
            return 1;
        }
        if (moreGapFolders) {
            gapFolders = new ArrayList<>(gapFolders);
        	gapFolders.add(".git");
        	gapFolders.add(".angular");
        }

        int startNameCount = inputFolder.getNameCount();
        total = 0;
        StringBuilder sb = new StringBuilder();
        Files.walkFileTree(inputFolder, new FileVisitor<Path>() {

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
 
                if (dir.getNameCount() == startNameCount + 1) {
                    topSize = total;
                }

                // Если мы знаем размер этой папки, не нужно ее сканировать
                Long knownSize = knownFolders.get(dir);
                if (knownSize != null) {
                    total += knownSize;
                    if (dir.getNameCount() == startNameCount + 1) {
                        long b = knownSize;
                        sb.append(String.format("\"%s\", %d, %f, %f\n", 
                            dir.getFileName(), b, kb(b), mb(b)));
                    }
                    return FileVisitResult.SKIP_SUBTREE;
                }

                // Возможно, в этой папке есть информация о размерах подпапок
                Path csv = dir.resolve(csvName);
                if (Files.exists(csv) && !ignoreCsvFiles) {
                    if (trackCsvFiles) {
                        out.println("Found: " + csv);
                    }
                    try {
                        readCsv(csv);
                    } catch (FolderException e) {
                        err.println("[ERROR] " + e);
                    }
                }

                if (dir.getNameCount() - startNameCount > maxDepth) {
                    terminator = "Max depth exceeded: " + dir;
                    return FileVisitResult.TERMINATE;
                }

                if (!ignoreGapFolders && gapFolders.contains(dir.getFileName().toString())) {
                    terminator = "Gap folder: " + dir;
                    if (deleteGapFolders) {
                        int result = 0;
                        if (!deleteAlways) {
                            result = askToDelete(dir);
                        } 
                        switch (result) {
                            case 0: // delete this
                            case 1: // delete always
                                deleteAlways = (result == 1);
                                deleteFolder(dir);
                                return FileVisitResult.SKIP_SUBTREE;
                            default: // cancel
                                return FileVisitResult.TERMINATE;
                        }
                    } else {
                        return FileVisitResult.TERMINATE;
                    }
                    
                } else {
                    return FileVisitResult.CONTINUE;
                }
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                long b = attrs.size();
                //out.format("File: %s : %d B %n", file, b);
                total += b;
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                err.println(exc);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (dir.getNameCount() == startNameCount + 1) {
                    long b = total - topSize;
                    sb.append(String.format("\"%s\", %d, %f, %f\n", 
                        dir.getFileName(), b, kb(b), mb(b)));
                }
                return FileVisitResult.CONTINUE;
            }
        });

        if (terminator != null) {
            err.println("[ERROR] " + terminator);
            return 1;
        }

        BufferedWriter writer = Files.newBufferedWriter(inputFolder.resolve(csvName));
        writer.write(sb.toString());
        writer.close();

        out.println("Folder: " + inputFolder);
        out.format("  Size: %d B = %.1f KB = %.1f MB %n", total, kb(total), mb(total));
        return 0;
    }

    void deleteFolder(Path dir) throws IOException {
        out.println("Delete folder: " + dir);
        deletePath(dir);
        terminator = null;
    }

    void deletePath(Path dir) throws IOException {
        if (Files.isDirectory(dir)) {
            Path[] paths = Files.list(dir).toArray(Path[]::new);
            for (Path path : paths) {
                deletePath(path);
            }
        }
        Files.delete(dir);
    }

    int askToDelete(Path dir) {
        Object[] options = {
                "Delete",
                "Delete All",
                "Cancel" };
        return JOptionPane.showOptionDialog(null,
                "Gap folder: " + dir,
                "Confirm Delete",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]);
    }

    static float kb(long b) {
        return b / 1024f;
    }

    static float mb(long b) {
        return b / 1024f / 1024f;
    }

    void readCsv(Path csv) throws IOException, FolderException {
        Path dir = csv.getParent();
        Reader in = Files.newBufferedReader(csv);
        Iterable<CSVRecord> records = CSVFormat.EXCEL.parse(in);
        int lno = 0;

        // Пройдем по строкам CSV-файла
        for (CSVRecord record : records) {
            lno++;
            
            // Папка, указанная в 1й колонке должна существовать
            String folderName = record.get(0);
            Path folder = dir.resolve(folderName);
            if (!Files.exists(folder)) {
                throw new FolderException(csv, lno, "Folder not found: " + folder);
            }

            Long size = null;

            // 2я колонка может содержать размер в байтах
            String byteStr = record.get(1).trim();
            if (byteStr.length() > 0) {
                try {
                    size = Long.parseLong(byteStr);
                    knownFolders.put(folder, size);
                } catch (NumberFormatException e) {
                }
                continue;
            } 

            // 3я колонка может содержать размер в кбайтах
            String kbyteStr = record.get(2).trim();
            if (kbyteStr.length() > 0) {
                try {
                    size = Long.valueOf(Math.round(Float.parseFloat(kbyteStr) * 1024));
                    knownFolders.put(folder, size);
                } catch (NumberFormatException e) {
                }
                continue;
            } 

            // 4я колонка может содержать размер в мбайтах
            String mbyteStr = record.get(3).trim();
            if (mbyteStr.length() > 0) {
                try {
                    size = Long.valueOf(Math.round(Float.parseFloat(mbyteStr) * 1024 * 1024));
                    knownFolders.put(folder, size);
                } catch (NumberFormatException e) {
                }
                continue;
            } 

            throw new FolderException(csv, lno, "Missing size");
        }
    }

    public static void main(String... args) throws IOException {
        //int exitCode = new folder_sizes().askToDelete(Path.of("./05-sw-caching/sw-caching-01--updated-project/sw-caching-01--updated-project/.git"));
        //out.println(exitCode);
        int exitCode = new CommandLine(new folder_sizes()).execute(args);
        System.exit(exitCode);
    }
}

class FolderException extends Exception {

    Path csv;
    int line;
    String msg;

    FolderException(Path csv, int line, String msg) {
        super(csv + ": line " + line + ": " + msg);
        this.csv = csv;
        this.line = line;
        this.msg = msg;
    }

    @Override
    public String toString() {
        return csv + ": line " + line + ": " + msg;
    }
}

