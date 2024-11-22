///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.6

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.util.Arrays;

import java.util.concurrent.Callable;

@Command(name = "tree", mixinStandardHelpOptions = true, version = "2024-11-22",
        description = "Directory tree in ASCII symbols up to a specified depth.")
public class tree implements Callable<Integer> {

    @Option(names = {"-p", "--path"}, defaultValue = ".", description = "Starting path.")
    private String startPath;

    @Option(names = {"-d", "--depth"}, description = "Max depth. Default is unlimited.")
    private Integer maxDepth;

    public static void main(String... args) {
        new CommandLine(new tree()).execute(args);
    }

    @Override
    public Integer call() {
        try {
            displayTree(new File(startPath), maxDepth, 0, "");
        } catch (AppException e) {
            System.err.println("[ERROR] " + e.getMessage());
            return 1;
        }
        return 0;
    }

    void displayTree(File root, Integer maxDepth, int currentDepth, String prefix) throws AppException {
        if (maxDepth != null && currentDepth > maxDepth) {
            return; // Stop if maximum depth is reached
        }

        if (currentDepth == 0) {
            System.out.println(root.getAbsolutePath());
        }

        File[] files = root.listFiles();
        if (files == null) {
            throw new AppException("Permission Denied or Error: " + root.getName());
        }

        Arrays.sort(files, (a, b) -> a.getName().toLowerCase().compareTo(b.getName().toLowerCase()));

        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            boolean isLast = (i == files.length - 1);

            String connector = isLast ? "└──" : "├──";
            String nextPrefix = prefix + (isLast ? "    " : "│   ");

            if (file.isDirectory()) {
                System.out.println(prefix + connector + " " + file.getName() + "/");
                displayTree(file, maxDepth, currentDepth + 1, nextPrefix);
            } else {
                System.out.println(prefix + connector + " " + file.getName());
            }
        }
    }
}


class AppException extends Exception {
    AppException(String msg) {
        super(msg);
        
    }
}
