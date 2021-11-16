///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS org.apache.commons:commons-csv:1.9.0
//DEPS info.picocli:picocli:4.6.2
//DEPS org.postgresql:postgresql:42.2.4

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import static java.lang.System.*;

import java.io.FileWriter;
import java.sql.*;
import java.util.concurrent.Callable;

import org.apache.commons.csv.*;

/**
 * Export CSV file from database table.
 */
@Command(name = "csv_from_db", mixinStandardHelpOptions = true, version = "1.0", description = "Export CSV file from database table.")
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

    @Override
    public Integer call() throws Exception {
        try (Connection con = DriverManager.getConnection("jdbc:postgresql:" + dbName, username, password)) {
            Statement stmt = con.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName);

            try (CSVPrinter printer = new CSVPrinter(new FileWriter(outFile), CSVFormat.DEFAULT)) {
                printer.printRecords(rs);
            }
        }
        return 0;
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new csv_from_db()).execute(args);
        System.exit(exitCode);
    }
}
