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
def aliases = []

for (a: cat.aliases) {
	aliases << a.value
}

aliases = aliases.reverse()

f.println("|===");
for (c: aliases) {
    f.println("| ${pad(c['script-ref'], 20)} |  ${c.description}");
}
f.println("|===");

f.close();

String pad(String s, int length) {
	return ("`" + s + "`").padRight(length + 2)
}