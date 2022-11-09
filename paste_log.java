///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.0

import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Command(name = "paste_log", mixinStandardHelpOptions = true, version = "1.0", description = "Paste log from clipboard to file")
class paste_log implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        String text = paste();
        if (text == null) {
            System.out.println("[WARNING] Empty clipboard");
            return 1;
        }
        LocalDateTime today = LocalDateTime.now();
        String fname = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm")) + ".log";
        saveStr(fname, text);
        System.out.println("File created: " + fname);
        return 0;
    }

    String paste() {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        DataFlavor flavor = DataFlavor.stringFlavor;
        if (clipboard.isDataFlavorAvailable(flavor)) {
            try {
                String text = (String) clipboard.getData(flavor);
                return text;

            } catch (UnsupportedFlavorException e) {
                System.out.println(e);
            } catch (IOException e) {
                System.out.println(e);
            }
        }
        return null;
    }
    
    /** Save string to file. */
    public static void saveStr(String fname, String text)
            throws IOException {
        PrintWriter out = new PrintWriter(new FileOutputStream(fname));
        out.print(text);
        out.close();
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new paste_log()).execute(args);
        System.exit(exitCode);
    }

}