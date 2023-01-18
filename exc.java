///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.0

import static java.lang.System.out;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;


@Command(name = "exc", mixinStandardHelpOptions = true, version = "2023-01-17", 
         description = "Checking for exceptions in log file")
class exc implements Callable<Integer> {

    @Parameters(index = "0", description = "Log file")
    String logFile;

    @Option(names = { "-ts", "--timestamp" }, description = "Timestamp format.", 
            defaultValue = "yyyy-MM-dd HH:mm:ss,SSS")
    String timeStampFormat;

    @Option(names = { "-f", "--file" }, description = "Output YAML file.")
    boolean outputYaml;


    List<Exc> exceptions;

    ExcProcessor proc;


    @Override
    public Integer call() throws Exception {
        Path logPath = Path.of(logFile);
        if (!Files.exists(logPath)) {
            out.println("[ERROR] File not found: " + logFile);
            return 1;
        }

        String logText = Files.readString(logPath);
        extractExceptions(logText);
        //createOutputFile(logFile + ".html");
        if (outputYaml) {
            String outName = logFile + ".yml";
            try (
                PrintStream f = new PrintStream(new FileOutputStream(outName))
            ) {              
                printExceptions(f);
                out.println("File created: " + outName);
            }
        } else {
            printExceptions(out);
        }

        return 0;
    }

    // PrintStream:
    // https://docs.oracle.com/javase/8/docs/api/java/io/PrintStream.html

    void printExceptions(PrintStream out) {
        for (Exc exc : exceptions) {
            out.println("-");
            out.println(exc.toYaml("  "));
        }
    }

    void extractExceptions(String logText) throws IOException {
        proc = new ExcProcessor();
        TimestampExtractor tse = new SimpleTimestampExtractor();
        if (timeStampFormat != null) {
            tse.setDateFormat(timeStampFormat);
        }
        exceptions = proc.process(logText, tse);

        Collections.sort(exceptions, new Comparator<Exc>() {
            public int compare(Exc e1, Exc e2) {
                Date t1 = tse.parse(e1.time);
                Date t2 = tse.parse(e2.time);
                return -t1.compareTo(t2);
            }
        });
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new exc()).execute(args);
        System.exit(exitCode);
    }

    // ------ Classes 

    interface TimestampExtractor {

        String extractTimestamp(String line);

        Date parse(String tstamp);

        Date getTimestamp();

        String extractComment(String line);

        void setDateFormat(String dformat);

    }

    /**
     * Extract timestamp from the beginning of log line
     * in common `yyyy-MM-dd HH:mm:ss.SSS` format.
     *
     * It that fails, try `yyyy-MM-dd HH:mm:ss` format.
     *
     * It that fails, try `yyyy-MM-dd HH:mm` format.
     */
    class SimpleTimestampExtractor implements TimestampExtractor {

        /**
         * Expected length of timestamp
         */
        private static final int LEN = 24;

        Date date;
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS");

        @Override
        public void setDateFormat(String dformat) {
            if (dformat != null) {
                df = new SimpleDateFormat(dformat);
            }
        }

        @Override
        public String extractTimestamp(String line) {
            if (line == null || line.length() < LEN) {
                return null;
            }
            try {
                String tstamp = line.substring(0, LEN);
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

        public Date parse(String tstamp) {
            try {
                return df.parse(tstamp);
            } catch (ParseException e) {
                return new Date(0);
            }
        }

        /**
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

    class Exc {

        public int lno;
        public String time;
        public String sig;
        public String cmt;

        public String toYaml(String indent) {
            StringBuilder sb = new StringBuilder();
            sb.append(indent + "lno: " + lno + "\n");
            sb.append(indent + "time: " + time + "\n");
            sb.append(indent + "sig: " + sig + "\n");
            sb.append(indent + "cmt: " + cmt + "\n");
            return sb.toString();
        }

    }

    class ExcProcessor {

        int lno;

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
                        exceptions.add(createExc("Start time", tstamp, null));
                    }
                    lastTime = tstamp;
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
                exceptions.add(createExc("End time", lastTime, null));
            }
            in.close();
            return exceptions;
        }

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

        private String extractException(String line) {
            StringTokenizer st = new StringTokenizer(line);
            while (st.hasMoreTokens()) {
                String token = trimSpecial(st.nextToken());
                if (exceptionPattern.matcher(token).matches())
                    return token;
            }
            return null;
        }

        /** Trims not only spaces but any non-letters. */
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

}