///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.7
//DEPS com.fasterxml.jackson.core:jackson-databind:2.20.1
//DEPS org.apache.commons:commons-csv:1.14.1

import java.io.BufferedWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Callable;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "grafana_log", mixinStandardHelpOptions = true, version = "2026-01-08",
        description = "Extracts JSON 'body' field from the CSV 'Line' column into a .log file.")
class grafana_log implements Callable<Integer> {

    @Parameters(index = "0", description = "Input CSV file (Grafana export).")
    Path inputCsv;

    @Option(names = {"-o", "--output"}, description = "Output .log file path (default: <input>.log).")
    Path outputLog;

    @Option(names = {"--skip-bad-rows"}, description = "Skip rows with invalid JSON/body/timestamp instead of printing errors.")
    boolean skipBadRows = false;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final DateTimeFormatter OUT_TS = DateTimeFormatter.ISO_INSTANT;

    public static void main(String... args) {
        int exitCode = new CommandLine(new grafana_log()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        if (!Files.exists(inputCsv) || !Files.isRegularFile(inputCsv)) {
            System.err.println("Input file not found or not a file: " + inputCsv);
            return 2;
        }

        if (outputLog == null) {
            outputLog = defaultOutputPath(inputCsv);
        }

        long rows = 0, written = 0, skipped = 0;

        try (Reader reader = Files.newBufferedReader(inputCsv, StandardCharsets.UTF_8);
             BufferedWriter writer = Files.newBufferedWriter(
                     outputLog,
                     StandardCharsets.UTF_8,
                     StandardOpenOption.CREATE,
                     StandardOpenOption.TRUNCATE_EXISTING
             )) {

            CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreSurroundingSpaces(true)
                    .get();

            try (CSVParser parser = CSVParser.parse(reader, format)) {
                if (!parser.getHeaderMap().containsKey("Date") || !parser.getHeaderMap().containsKey("Line")) {
                    System.err.println("CSV must contain columns: Date, Line. Found: " + parser.getHeaderMap().keySet());
                    return 3;
                }

                for (CSVRecord rec : parser) {
                    rows++;

                    String dateStr = rec.get("Date");
                    String severity = rec.get("level");
                    String lineJson = rec.get("Line");

                    String isoTs;
                    try {
                        Instant ts = Instant.parse(dateStr); // e.g. 2026-01-08T19:12:48.558Z
                        isoTs = OUT_TS.format(ts);
                    } catch (Exception e) {
                        if (skipBadRows) { skipped++; continue; }
                        System.err.println("Row " + rows + ": invalid Date timestamp: " + dateStr);
                        continue;
                    }

                    if (severity == null || severity.isBlank()) {
                        severity = "UNKNOWN";
                    }

                    String body;
                    try {
                        JsonNode root = MAPPER.readTree(lineJson);
                        JsonNode b = root.get("body");
                        body = (b == null || b.isNull()) ? null : b.asText();
                    } catch (Exception e) {
                        if (skipBadRows) { skipped++; continue; }
                        System.err.println("Row " + rows + ": invalid JSON in Line column");
                        continue;
                    }

                    if (body == null) {
                        if (skipBadRows) { skipped++; continue; }
                        System.err.println("Row " + rows + ": JSON has no 'body' field");
                        continue;
                    }

                    writer.write(isoTs);
                    writer.write(" ");
                    writer.write(severity.trim());
                    writer.write(" ");
                    writer.write(body);
                    writer.newLine();
                    written++;
                }
            }
        }

        System.out.println("Input  : " + inputCsv.toAbsolutePath());
        System.out.println("Output : " + outputLog.toAbsolutePath());
        System.out.println("Rows read   : " + rows);
        System.out.println("Lines written: " + written);
        if (skipBadRows) System.out.println("Skipped     : " + skipped);

        return 0;
    }

    private static Path defaultOutputPath(Path input) {
        String name = input.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = (dot > 0) ? name.substring(0, dot) : name;
        return input.toAbsolutePath().getParent().resolve(base + ".log");
    }
}
