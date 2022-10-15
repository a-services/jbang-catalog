///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.6.2
//DEPS org.apache.commons:commons-csv:1.9.0

import static java.lang.System.*;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

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
    "Example of input CSV file:",
    "",  
    "  astudio, 95.1 M",
    "  m3, 298.3 K",
    "",  
    "Only M and K suffixes allowed in folder sizes",
    ""
    }, name = "folder_sizes", mixinStandardHelpOptions = true, version = "1.0",
    description = "Read and write CSV with folder sizes.")
public class folder_sizes implements Callable<Integer> {

    @Parameters(index = "0", description = "Input CSV file.")
    private File inputFile;

    @Option(names = {"-o"}, description = "Output CSV file.")
    private String outputFile;

    List<Folder> folderList = new ArrayList<>();

    @Override
    public Integer call() throws Exception {
        if (!inputFile.exists()) {
            out.println("[ERROR] File not found: " + inputFile.getName());
            return 1;
        }
        String outputName = (outputFile==null)? "~" + inputFile.getName() : outputFile;
        out.println("Output file: " + outputName);

        Reader in = new FileReader(inputFile);
        Iterable<CSVRecord> records = CSVFormat.EXCEL.parse(in);
        for (CSVRecord record : records) {
            String folderName = record.get(0);
            String sizeM = record.get(1);
            String[] sizes = sizeM.trim().split("\\s");
            assert sizes.length==2;
            String unit = sizes[1];
            assert "M".equals(unit) || "K".equals(unit);
            float size = Float.parseFloat(sizes[0]);
            long sizeB = "M".equals(unit)? Math.round(size*1024*1024) : Math.round(size*1024);
            folderList.add(new Folder(folderName, sizeB));
        }

        folderList.sort(null);

        for (Folder folder : folderList) {
            out.println(String.format("| Folder: %s \t| %d bytes", folder.getName(), folder.getSize()));
        }

        return 0;
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new folder_sizes()).execute(args);
        System.exit(exitCode);
    }
}

class Folder implements Comparable {
    String name;
    long size;
    public Folder(String name, long size) {
        this.name = name;
        this.size = size;
    }
    public String getName() {
        return name;
    }
    public long getSize() {
        return size;
    }
    @Override
    public int compareTo(Object o) {
        Folder f = (Folder)o;
        if (size==f.getSize()) {
            return f.getName().compareTo(name);
        } else {
            return Long.valueOf(f.getSize()).compareTo(size); 
        }

    }
}
