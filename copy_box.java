///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.6

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.concurrent.Callable;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "copy_box", mixinStandardHelpOptions = true, version = "2025-03-20",
        description = "Copy binary file to clipboard or paste it back from clipboard to binary file.")
class copy_box implements Callable<Integer> {

    @Parameters(index = "0", description = "File name")
    private String fileName;

    @Option(names = {"-p", "--paste"}, description = "Paste file")
    private boolean pasteFile;

    @Override
    public Integer call() throws Exception { 
        if (pasteFile) {
            pasteFile(fileName);
        } else {
            copyFile(fileName);
        }
        return 0;
    }
    
    /// Retrieve clipboard content and write to file
    void pasteFile(String fileName) throws IOException, UnsupportedFlavorException {
        System.out.println("Paste file: " + fileName);

        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        Transferable transferable = clipboard.getContents(null);
        if (transferable != null && transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            String encoded = (String) transferable.getTransferData(DataFlavor.stringFlavor);
            byte[] data = decodeBase64(encoded);
            Files.write(Paths.get(fileName), data);
            System.out.println("Pasted " + data.length + " bytes of " + encoded.length() + " encoded clipboard content to file '" + fileName + "'.");
        } else {
            System.err.println("Clipboard does not contain any text data.");
            System.exit(1);
        }
    }

    void copyFile(String fileName) throws IOException {
        System.out.println("Copy file: " + fileName); 

        byte[] data = Files.readAllBytes(Paths.get(fileName));
        String encoded = encodeBase64(data); 
        StringSelection selection = new StringSelection(encoded);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(selection, null);
        System.out.println("Copied " + data.length + " bytes of file '" + fileName + "' to " + encoded.length() + " encoded clipboard.");     
    }

    String encodeBase64(byte[] data ) {
        String encoded = Base64.getEncoder().encodeToString(data);
        return encoded;
    }

    byte[] decodeBase64(String encoded ) {
        byte[] data = Base64.getDecoder().decode(encoded);
        return data;
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new copy_box()).execute(args);
        System.exit(exitCode);
    }    
}
