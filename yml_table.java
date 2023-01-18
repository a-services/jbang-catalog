///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.0
//DEPS org.yaml:snakeyaml:1.33


import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;

import static java.lang.System.out;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;


@Command(name = "yml_table", mixinStandardHelpOptions = true, version = "2023-01-18",
        description = "Convert YAML list to HTML")
class yml_table implements Callable<Integer> {


    @Parameters(index = "0", description = "Input YAML file", 
                defaultValue = "server.log.yml")
    private String inputYaml;


    // SnakeYAML: 
    //   Docs:      https://bitbucket.org/snakeyaml/snakeyaml/wiki/Documentation
    //   GitHub:    https://github.com/snakeyaml/snakeyaml
    //   Tutorial:  https://www.baeldung.com/java-snake-yaml

    Yaml yaml = new Yaml();


    private List<Map<String, Object>> list;    
    private ArrayList<String> keys;


    @Override
    public Integer call() throws Exception {
        Path yamlPath = Path.of(inputYaml);
        list = yaml.load(Files.newInputStream(yamlPath));
        //System.out.println("List: " + list);
        assert list.size() < 0;
        createOutputHtml(inputYaml + ".html", list);
        return 0;
    }

    void createOutputHtml(String outFile, List<Map<String, Object>> list) throws FileNotFoundException {
        Map<String, Object> map = list.get(0);
        keys = new ArrayList<>(map.keySet());

        PrintWriter f = new PrintWriter(new FileOutputStream(outFile));
        String bootstrapCDN = "https://cdn.jsdelivr.net/npm/bootstrap@5.2.3";
        f.printf("""
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>YAML</title>
              <link href="%s/dist/css/bootstrap.min.css" rel="stylesheet">
            </head>
            <body>
              <div class="container">
              %s
              </div>
              <script src="%s/dist/js/bootstrap.bundle.min.js"></script>
            </body>
            </html>
            """, 
            bootstrapCDN, createContents(), bootstrapCDN);
        f.close();
        out.println("File created: " + outFile);
    }

    String createContents() {
        return String.format("""
            <table class="table">
            <thead>
              <tr>
                %s
              </tr>
            </thead>            
            <tbody>
              %s
            </tbody>
            </table>
            """,
            createTableHeader(),
            createTableBody());
    }

    String createTableHeader() {
        StringBuilder sb = new StringBuilder();
        for (String key: keys) {
            sb.append("<th scope=\"col\">" + key + "</th>\n");
        }
        return sb.toString();        
    }

    String createTableBody() {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> em: list) {
            sb.append("<tr>\n");
            for (String key: keys) {
                sb.append("<td>" + em.get(key) + "</td>\n");
            }
            sb.append("</tr>\n");
        }
        return sb.toString();      
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new yml_table()).execute(args);
        System.exit(exitCode);
    }
}
