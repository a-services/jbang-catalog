///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17+
//DEPS info.picocli:picocli:4.7.5
//DEPS org.yaml:snakeyaml:1.33

import static java.lang.System.out;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

import org.yaml.snakeyaml.Yaml;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Command-line application for checking exceptions in a log file.
 * <p>
 * This application processes a specified log file to extract exceptions and output them in various formats,
 * including YAML and HTML. The user can specify options for timestamp format, encoding, and other parameters.
 * </p>
 */
@Command(name = "exc", mixinStandardHelpOptions = true, version = "2024-04-05", 
         description = "Checking exceptions in a log file")
class exc implements Callable<Integer> {

    @Parameters(index = "0", description = "Log file")
    String logFile;

    @Option(names = { "--tformat" }, description = "Timestamp format.", 
            defaultValue = "yyyy-MM-dd HH:mm:ss,SSS")
    String timeStampFormat;

    @Option(names = { "--yaml" }, description = "Output YAML file.")
    boolean outputYaml;

    @Option(names = { "--html" }, description = "Output HTML file.")
    boolean outputHtml;

    @Option(names = { "-r", "--reverse" }, description = "Reverse order.")
    boolean reverseOrder;

    @Option(names = { "--encoding" }, description = "Log file encoding.")
    String encoding = "UTF-8";
    
    @Option(names = { "--skip" }, description = "Skip prefix length.")
    int skipPrefix = 0;

    @Option(names = { "--restart" }, description = "Restart signature.")
    String restartSignature;
    
    List<Exc> exceptions;

    ExcProcessor proc;

    /**
     * Main execution method for processing the log file.
     * 
     * @return An exit code; 0 indicates success, while 1 indicates failure (e.g., file not found).
     * @throws Exception If an error occurs during file processing.
     */
    @Override
    public Integer call() throws Exception {
        Path logPath = Path.of(logFile);
        if (!Files.exists(logPath)) {
            out.println("[ERROR] File not found: " + logFile);
            return 1;
        }

        Charset charset = Charset.forName(encoding);
        String logText = Files.readString(logPath, charset);
        
        extractExceptions(logText);

        if (outputHtml) {
            outputYaml = true;            
        }
        
        if (outputYaml) {
            // Create YAML file
            String outName = logFile + ".yml";
            try (
                PrintStream f = new PrintStream(new FileOutputStream(outName))
            ) {              
                printExceptions(f);
                out.println("File created: " + outName);
            }
            
        } 
        
        if (outputHtml) {
            // Create HTML from YAML file
            new HtmlTable(logFile).createOutputHtml();            
        } else {
            // Send YAML to console
            printExceptions(out);
        }
        
        return 0;
    }
    
    /**
     * Extracts exceptions from the given log text.
     * 
     * @param logText The text content of the log file.
     * @throws IOException If an I/O error occurs during processing.
     */
    void extractExceptions(String logText) throws IOException {
        proc = new ExcProcessor();
        if (restartSignature !=null) {
            out.println("Restart signature: `" + restartSignature + "`");
            proc.setRestartSignature(restartSignature);
        }
        
        TimestampExtractor tse = new SimpleTimestampExtractor();
        if (timeStampFormat != null) {
            out.println("Timestamp format: `" + timeStampFormat + "`");
            out.println("Skip prefix length: " + skipPrefix);
            tse.setDateFormat(timeStampFormat, skipPrefix);
        }
        exceptions = proc.process(logText, tse);

        Collections.sort(exceptions, new Comparator<Exc>() {
            public int compare(Exc e1, Exc e2) {
                //Date t1 = tse.parse(e1.time);
                //Date t2 = tse.parse(e2.time);
                //return -t1.compareTo(t2);
                return reverseOrder ? 
                       Integer.compare(e2.lno, e1.lno) :
                       Integer.compare(e1.lno, e2.lno);
            }
        });
    }
    
    /**
     * Outputs the extracted exceptions to the specified {@link java.io.PrintStream} in YAML format.
     * 
     * @param out The PrintStream to output exceptions.
     *
     * @see https://docs.oracle.com/javase/8/docs/api/java/io/PrintStream.html
     */
    void printExceptions(PrintStream out) {
        for (Exc exc : exceptions) {
            out.println("-");
            out.println(exc.toYaml("  "));
        }
    }
    
    /**
     * The main method to execute the command-line application.
     * 
     * @param args Command-line arguments.
     */
    public static void main(String... args) {
        int exitCode = new CommandLine(new exc()).execute(args);
        System.exit(exitCode);
    }

    // ------ Inner Classes 
    
    /**
     * Represents an exception entry extracted from the log file.
     */
    class Exc {

        public int lno;
        public String time;
        public String sig;
        public String cmt;
        
        /**
         * Converts this exception entry to a YAML-formatted string.
         * 
         * @param indent The indentation to use for formatting.
         * @return A YAML representation of the exception entry.
         */
        public String toYaml(String indent) {
            StringBuilder sb = new StringBuilder();
            sb.append(indent + "lno: " + lno + "\n");
            sb.append(indent + "time: " + time + "\n");
            sb.append(indent + "sig: " + sig + "\n");
            sb.append(indent + "cmt: " + (cmt == null ? "" : cmt) + "\n");
            return sb.toString();
        }

    }
    
    /**
     * Processes log text to extract exceptions using a specified timestamp extractor.
     */
    class ExcProcessor {

        int lno;

        String restartSignature = "  :: Spring Boot ::  ";
        
        /**
         * Processes the provided log text and extracts exceptions.
         * 
         * @param logText The log text to process.
         * @param tse The TimestampExtractor to use for extracting timestamps.
         * @return A list of extracted exceptions.
         * @throws IOException If an I/O error occurs during processing.
         */        
        public List<Exc> process(String logText, TimestampExtractor tse)
                throws IOException {
            LinkedList<Exc> exceptions = new LinkedList<>();
            lno = 0;
            BufferedReader in = new BufferedReader(new StringReader(logText));
            String lastTime = null;
            String lastComment = null;
            while (in.ready()) {
                String line = in.readLine();
                lno++;
                if (line == null)
                    break;

                String tstamp = tse.extractTimestamp(line);
                if (tstamp != null) {
                    if (lastTime == null) {
                        exceptions.add(createExc("START OF LOG", tstamp, null));
                    }
                    lastTime = tstamp;

                    if (line.contains(restartSignature)) {
                        exceptions.add(createExc("SPRING BOOT RESTART", tstamp, null));
                    }
                }

                String comment = tse.extractComment(line);
                if (comment != null) {
                    lastComment = comment;
                }

                // process exceptions
                String sig = extractException(line);
                if (sig != null) {
                    exceptions.add(createExc(sig, lastTime, lastComment));
                }
            }
            if (lastTime != null) {
                exceptions.add(createExc("END OF LOG", lastTime, null));
            }
            in.close();
            return exceptions;
        }
        
        /**
         * Sets the signature that indicates a restart event in the log.
         * 
         * @param restartSignature The signature string to identify restart events.
         */        
        void setRestartSignature(String restartSignature) {
            this.restartSignature = restartSignature;
        }
        
        /**
         * Creates a new exception entry with the specified details.
         * 
         * @param sig The signature of the exception.
         * @param time The timestamp when the exception occurred.
         * @param cmt An optional comment associated with the exception.
         * @return A new Exc object representing the exception.
         */
        Exc createExc(String sig, String time, String cmt) {
            Exc exc = new Exc();
            exc.lno = lno;
            exc.sig = sig;
            exc.time = time;
            exc.cmt = cmt;
            return exc;
        }

        private static final Pattern exceptionPattern = Pattern
                .compile("(([a-z])+\\.)+[A-Z][a-zA-Z]*(Exception|Error)");
                
        /**
         * Extracts the exception signature from a log line.
         * 
         * @param line The log line to process.
         * @return The extracted exception signature, or null if none found.
         */
        private String extractException(String line) {
            StringTokenizer st = new StringTokenizer(line);
            while (st.hasMoreTokens()) {
                String token = trimSpecial(st.nextToken());
                if (exceptionPattern.matcher(token).matches())
                    return token;
            }
            return null;
        }

        /**
         * Trims special characters from the given token, keeping only letters.
         * 
         * @param token The token to trim.
         * @return The trimmed token.
         */
        String trimSpecial(String token) {
            int k1 = 0;
            int k2 = token.length() - 1;
            for (; k1 <= k2; k1++) {
                if (Character.isLetter(token.charAt(k1)))
                    break;
            }
            for (; k1 <= k2; k2--) {
                if (Character.isLetter(token.charAt(k2)))
                    break;
            }
            return token.substring(k1, k2 + 1);
        }

    }
    
    /**
     * Generates an HTML table from a list of exceptions in YAML format.
     */
    class HtmlTable {
    
        // SnakeYAML: 
        //   Docs:      https://bitbucket.org/snakeyaml/snakeyaml/wiki/Documentation
        //   GitHub:    https://github.com/snakeyaml/snakeyaml
        //   Tutorial:  https://www.baeldung.com/java-snake-yaml
    
        Yaml yaml = new Yaml();
        
        List<Map<String, Object>> list;    
        ArrayList<String> keys;
        String logFile;
        
        /**
         * Initializes an HtmlTable instance with the specified log file.
         * 
         * @param logFile The log file to read YAML data from.
         * @throws Exception If an error occurs while reading the YAML file.
         */    
        HtmlTable(String logFile) throws Exception {
            this.logFile = logFile;
            Path yamlPath = Path.of(logFile + ".yml");
            list = yaml.load(Files.newInputStream(yamlPath));
            //System.out.println("List: " + list);
            //assert list.size() < 0;
        }
        
        /**
         * Creates an HTML output file from the YAML data.
         * 
         * @throws FileNotFoundException If the output file cannot be created.
         */        
        void createOutputHtml() throws FileNotFoundException {
            createOutputHtml(logFile + ".html", list);
        }
        
        /**
         * Creates an HTML output file from the specified list of maps.
         * 
         * @param outFile The name of the output HTML file.
         * @param list The list of data to include in the HTML table.
         * @throws FileNotFoundException If the output file cannot be created.
         */                
        void createOutputHtml(String outFile, List<Map<String, Object>> list) throws FileNotFoundException {
            Map<String, Object> map = list.get(0);
            keys = new ArrayList<>(map.keySet());
    
            PrintWriter f = new PrintWriter(new FileOutputStream(outFile));
            String bootstrapCDN = "https://cdn.jsdelivr.net/npm/bootstrap@5.2.3";
            f.printf("""
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <link href="%s/dist/css/bootstrap.min.css" rel="stylesheet">
                </head>
                <body>
                  <div class="container">
                  %s
                  </div>
                  <script src="%s/dist/js/bootstrap.bundle.min.js"></script>
                </body>
                </html>
                """, 
                bootstrapCDN, createContents(), bootstrapCDN);
            f.close();
            out.println("File created: " + outFile);
        }
        
        /**
         * Creates the contents of the HTML table from the extracted exception data.
         * 
         * @return A string containing the HTML table structure.
         */    
        String createContents() {
            return String.format("""
                <table class="table">
                <thead>
                  <tr>
                    %s
                  </tr>
                </thead>            
                <tbody>
                  %s
                </tbody>
                </table>
                """,
                createTableHeader(),
                createTableBody());
        }
        
        /**
         * Creates the header row of the HTML table based on the keys of the data.
         * 
         * @return A string containing the HTML for the table header.
         */    
        String createTableHeader() {
            StringBuilder sb = new StringBuilder();
            for (String key: keys) {
                sb.append("<th scope=\"col\">" + key + "</th>\n");
            }
            return sb.toString();        
        }
        
        /**
         * Creates the body rows of the HTML table from the list of exceptions.
         * 
         * @return A string containing the HTML for the table body.
         */    
        String createTableBody() {
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> em: list) {
                sb.append("<tr>\n");
                for (String key: keys) {
                    Object value = em.get(key);
                    sb.append("<td>" + (value == null? "": value) + "</td>\n");
                }
                sb.append("</tr>\n");
            }
            return sb.toString();      
        }
    
    }
    

}

// ------ Outer Classes 

/**
 * Interface for extracting timestamps and comments from log lines.
 */
interface TimestampExtractor {
    
    /**
     * Extracts the timestamp from a log line.
     * 
     * @param line The log line to extract the timestamp from.
     * @return The extracted timestamp as a string.
     */
    String extractTimestamp(String line);
    
    /**
     * Parses a timestamp string into a Date object.
     * 
     * @param tstamp The timestamp string to parse.
     * @return The parsed Date object.
     */
    Date parse(String tstamp);
    
    /**
     * Gets the last extracted timestamp as a Date object.
     * 
     * @return The last extracted timestamp.
     */
    Date getTimestamp();
    
    /**
     * Extracts a comment from a log line.
     * 
     * @param line The log line to extract the comment from.
     * @return The extracted comment as a string.
     */
    String extractComment(String line);
    
    /**
     * Sets the expected date format and the number of characters to skip before the timestamp.
     * 
     * @param dformat The date format string.
     * @param skipPrefixLength The number of characters to skip.
     */
    void setDateFormat(String dformat, int skipPrefixLength);

}

/**
 * Extracts timestamps from log lines using a predefined format.
 */
class SimpleTimestampExtractor implements TimestampExtractor {

    /**
     * Expected length of timestamp
     */
    private int timestampLength = 24;

    /**
     * Skip prefix before timestamp.
     */
    private int skipPrefixLength = 0;

    Date date;
    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS");

    @Override
    public void setDateFormat(String dformat, int skipPrefixLength) {
        if (dformat != null) {
            df = new SimpleDateFormat(dformat);
            timestampLength = dformat.length() + skipPrefixLength;
            this.skipPrefixLength = skipPrefixLength;
        }
    }

    @Override
    public String extractTimestamp(String line) {
        if (line == null || line.length() < timestampLength) {
            return null;
        }
        try {
            String tstamp = line.substring(skipPrefixLength, timestampLength);
            date = df.parse(tstamp);
            return tstamp;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Date getTimestamp() {
        return date;
    }
    
    /**
     * Parses the given timestamp string into a Date object.
     * 
     * @param tstamp The timestamp string to parse.
     * @return The parsed Date object, or a default date if parsing fails.
     */
    public Date parse(String tstamp) {
        try {
            return df.parse(tstamp);
        } catch (ParseException e) {
            return new Date(0);
        }
    }

    /**
     * Extracts a comment from a log line, if available.
     *
     * The following code can be included into servlet filter
     * of the app to track which URL causes which exception.
     *
     * ```java
        private void memoryInfo(HttpServletRequest request) {
            long heapSize = Runtime.getRuntime().totalMemory();
            long heapMaxSize = Runtime.getRuntime().maxMemory();
            long heapFreeSize = Runtime.getRuntime().freeMemory();
            String uri = request.getRequestURI();
            log.info("[memoryInfo] uri=" + uri + ", free=" + heapFreeSize + ", total=" + heapSize + ", max=" + heapMaxSize);
        }
     * ```
     */
    @Override
    public String extractComment(String line) {
        if (line == null) {
            return null;
        }
        final String FLAG = "[memoryInfo] uri=";
        int k = line.indexOf(FLAG);
        if (k == -1) {
            return null;
        }
        k += FLAG.length();
        int n = line.indexOf(",", k);
        if (n == -1) {
            return null;
        }
        return line.substring(k, n);
    }

}