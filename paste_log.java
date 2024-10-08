///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.6

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.concurrent.Callable;

import javax.swing.JOptionPane;

import picocli.CommandLine;
import picocli.CommandLine.Command;

// https://github.com/a-services/jbang-catalog/blob/main/paste_log.java

@Command(name = "paste_log", mixinStandardHelpOptions = true, version = "2024-09-04", 
         description = "Paste log from clipboard to file")
class paste_log implements Callable<Integer> {

    final String propFileName = ".paste_log";
    
    Properties pp = new Properties();
    
    @Override
    public Integer call() throws Exception {
        String text = paste();
        if (text == null) {
            System.out.println("[WARNING] Empty clipboard");
            return 1;
        }
        
        String prefix = loadPrefix();
        prefix = JOptionPane.showInputDialog("Prefix?", prefix);
        if (prefix != null) {
            prefix = prefix.trim();
            savePrefix(prefix);
        }
        
        LocalDateTime today = LocalDateTime.now();
        String fname = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm")) + ".log";
        if (prefix != null) {
            fname = prefix + "-" + fname;
        }
        
        saveStr(fname, text);
        System.out.println("File created: " + fname);
        return 0;
    }

    public String paste() {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        Transferable contents = clipboard.getContents(null);

        if (contents != null) {
            try {
                // Try to get the data as a String using stringFlavor
                if (contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                    return (String) contents.getTransferData(DataFlavor.stringFlavor);
                } else {
                    // If stringFlavor is not supported, iterate over available flavors
                    DataFlavor[] flavors = contents.getTransferDataFlavors();
                    for (DataFlavor flavor : flavors) {
                        try {
                            Object data = contents.getTransferData(flavor);

                            if (data instanceof String) {
                                return (String) data;
                            } else if (data instanceof Reader) {
                                // Read data from a Reader
                                BufferedReader br = new BufferedReader((Reader) data);
                                StringBuilder sb = new StringBuilder();
                                String line;
                                while ((line = br.readLine()) != null) {
                                    sb.append(line);
                                }
                                return sb.toString();
                            } else if (data instanceof InputStream) {
                                // Read data from an InputStream
                                BufferedReader br = new BufferedReader(new InputStreamReader((InputStream) data, "UTF-8"));
                                StringBuilder sb = new StringBuilder();
                                String line;
                                while ((line = br.readLine()) != null) {
                                    sb.append(line);
                                }
                                return sb.toString();
                            }
                        } catch (Exception e) {
                            // Ignore and try the next flavor
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println(e);
            }
        }
        return null;
    }

    
    String loadPrefix() throws IOException {
        String prefix = null;
        
        File f = new File(propFileName);
        if (f.exists()) {
            FileInputStream fin = new FileInputStream(f.getPath());
            pp.load(fin);
            fin.close();
            
            prefix = pp.getProperty("prefix");
        }
        return prefix;
    }
    
    void savePrefix(String prefix) throws IOException {
        pp.setProperty("prefix", prefix);
        
        FileOutputStream out = new FileOutputStream(propFileName);
        pp.store(out, null);
        out.close();
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