///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS com.fasterxml.jackson.core:jackson-databind:2.17.2

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.io.*;

/**
 * Generate `README.adoc` from `jbang-catalog.json` 
 */
public class README {

    final String inputFile = "jbang-catalog.json";
    final String outputFile = "README.adoc";

    @SuppressWarnings("unchecked")
    void main() throws Exception {
        PrintWriter f = new PrintWriter(new FileOutputStream(outputFile));
        f.println("= jbang-catalog\n");
        f.println(".Catalog of jbang scripts");

        ObjectMapper mapper = new ObjectMapper();

        Map<String, Object> cat = mapper.readValue(new File(inputFile), Map.class);
        Map<String, Object> aliases = (Map<String, Object>) cat.get("aliases");
        
        List<String> keys = new ArrayList<>(aliases.keySet());
        Collections.reverse(keys);

        f.println("|===");
        for (String key: keys) {
            Map<String, String> c = (Map<String, String>) aliases.get(key);
            String scriptFile = (String) c.get("script-ref");
            f.println("| " + checkDoc(scriptFile) + " |  " + c.get("description"));           
        }
        f.println("|===");

        f.close();
    }

    String checkDoc(String fileNameWithExtension) {
        // Extract the file name without extension
        String fileNameWithoutExtension = fileNameWithExtension.lastIndexOf('.') > 0 ? 
            fileNameWithExtension.substring(0, fileNameWithExtension.lastIndexOf('.')) : 
            fileNameWithExtension;

        // Define the path to the 'doc' directory
        String docFolderPath = "doc/";

        // Construct the path to the .adoc file
        String adocFilePath = docFolderPath + fileNameWithoutExtension + ".adoc";

        // Check if the .adoc file exists in the 'doc' folder
        if (Files.exists(Paths.get(adocFilePath))) {
            // If it exists, return the formatted link string
            return "link:doc/" + fileNameWithoutExtension + ".adoc[" + fileNameWithExtension + "]";
        } else {
            // If the file does not exist, return the original file name with its extension
            return pad(fileNameWithExtension, 20);
        }
    }

    String pad(String s, int length) {
        return String.format("%-" + (length + 2) + "s", "`" + s + "`");
    }

    public static void main(String... args) throws Exception {
        new README().main();
    }
}
