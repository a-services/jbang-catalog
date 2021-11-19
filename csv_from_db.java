///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS org.apache.commons:commons-csv:1.9.0
//DEPS info.picocli:picocli:4.6.2
//DEPS org.postgresql:postgresql:42.2.4
//DEPS org.json:json:20180130

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import static java.lang.System.*;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.Arrays;
import java.util.concurrent.Callable;

import org.apache.commons.csv.*;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Export CSV file from database table.
 */
@Command(name = "csv_from_db", mixinStandardHelpOptions = true, version = "1.1",
         description = "Export CSV or JSON file from database table.")
public class csv_from_db implements Callable<Integer> {

    @Option(names = { "-db", "--database" }, required = true, description = "Database name")
    private String dbName;

    @Option(names = { "-t", "--table" }, required = true, description = "Table name")
    private String tableName;

    @Option(names = { "-u", "--user" }, required = true, description = "Username")
    private String username;

    @Option(names = { "-p", "--password" }, required = true, description = "Password", defaultValue="")
    private String password;

    @Option(names = { "-f", "--file" }, required = true, description = "Output file")
    private String outFile;

    String[] outFormats = { "csv", "json" };

    @Override
    public Integer call() throws Exception {
        out.println("Output file: " + outFile);
        int outFormat = determineOutputFormat(outFile);
        if (outFormat == -1) {
            out.println("[ERROR] Unknown output format.");
            return 1;
        }

        /* Export result set
         */
        try (Connection con = DriverManager.getConnection("jdbc:postgresql:" + dbName, username, password)) {
            Statement stmt = con.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName);

            switch (outFormat) {
                case 0:
                    exportCsv(rs);
                    break;
                case 1:
                    exportJson(rs);
                    break;
            }
        }
        return 0;
    }

    private void exportJson(ResultSet rs) throws SQLException, IOException {
        JSONArray json = new JSONArray();
        ResultSetMetaData rsmd = rs.getMetaData();
        while (rs.next()) {
            int numColumns = rsmd.getColumnCount();
            JSONObject obj = new JSONObject();
            for (int i = 1; i <= numColumns; i++) {
                String column_name = rsmd.getColumnName(i);
                obj.put(column_name, rs.getObject(column_name));
            }
            json.put(obj);
        }
        Files.writeString(Paths.get(outFile), json.toString());
    }

    private void exportCsv(ResultSet rs) throws SQLException, IOException {
        try (CSVPrinter printer = new CSVPrinter(new FileWriter(outFile), CSVFormat.DEFAULT)) {
            printer.printRecords(rs);
        }
    }

    /**
     * Determine output format by file extension
     */
    private int determineOutputFormat(String outFileName) {
        int k = outFileName.lastIndexOf(".");
        if (k == -1) {
            return -1;
        }
        String ext = outFileName.substring(k + 1);
        return Arrays.asList(outFormats).indexOf(ext);
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new csv_from_db()).execute(args);
        System.exit(exitCode);
    }
}
