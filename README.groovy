/**
 * Generate `README.adoc` from `jbang-catalog.json` 
 */

inputFile = "jbang-catalog.json"
outputFile = "README.adoc"

import groovy.json.JsonSlurper

import java.nio.file.Files
import java.nio.file.Paths

String checkDoc(String fileNameWithExtension) {
    // Extract the file name without extension
    String fileNameWithoutExtension = fileNameWithExtension.lastIndexOf('.') > 0 ? 
        fileNameWithExtension.substring(0, fileNameWithExtension.lastIndexOf('.')) : 
        fileNameWithExtension

    // Define the path to the 'doc' directory
    String docFolderPath = "doc/"
    
    // Construct the path to the .adoc file
    String adocFilePath = docFolderPath + fileNameWithoutExtension + ".adoc"

    // Check if the .adoc file exists in the 'doc' folder
    if (Files.exists(Paths.get(adocFilePath))) {
        // If it exists, return the formatted link string
        return "link:doc/${fileNameWithoutExtension}.adoc[${fileNameWithExtension}]"
    } else {
        // If the file does not exist, return the original file name with its extension
        return pad(fileNameWithExtension, 20)
    }
}

// ------------------

PrintWriter f = new PrintWriter(new FileOutputStream(outputFile));
f.println("= jbang-catalog\n");
f.println(".Catalog of jbang scripts");

def cat = new JsonSlurper().parseText(new File(inputFile).text)
def aliases = []

for (a: cat.aliases) {
	aliases << a.value
}

aliases = aliases.reverse()

f.println("|===");
for (c: aliases) {
	scriptFile = c['script-ref']

    f.println("| ${checkDoc(scriptFile)} |  ${c.description}");
}
f.println("|===");

f.close();

String pad(String s, int length) {
	return ("`" + s + "`").padRight(length + 2)
}