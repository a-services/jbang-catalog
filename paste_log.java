///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.6

import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javax.swing.JOptionPane;
import java.util.Properties;

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