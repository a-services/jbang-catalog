/**
 * Generate `README.adoc` from `jbang-catalog.json` 
 */

inputFile = "jbang-catalog.json"
outputFile = "README.adoc"

import groovy.json.JsonSlurper

PrintWriter f = new PrintWriter(new FileOutputStream(outputFile));
f.println("= jbang-catalog\n");
f.println(".Catalog of jbang scripts");

def cat = new JsonSlurper().parseText(new File(inputFile).text)

f.println("|===");
for (a: cat.aliases) {
	def c = a.value
    f.println("| `${c['script-ref']}` |  ${c.description}");
}
f.println("|===");

f.close();
