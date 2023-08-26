///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.4
//DEPS org.jsoup:jsoup:1.16.1

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.File;
import java.io.IOException;

@Command(name = "udemy_html_toc", mixinStandardHelpOptions = true, version = "2023-08-26",
        description = "Parse html file with Udemy course TOC")
class udemy_html_toc implements Callable<Integer> {

    @Parameters(index = "0", description = "Input file", defaultValue = "toc.html")
    private String inputFile;

    // JSoup DOM Navigation
    // https://jsoup.org/cookbook/extracting-data/dom-navigation

    @Override
    public Integer call() throws Exception { 
        System.out.println("Input file: " + inputFile);

        // Load the HTML file using Jsoup
        try {
            File input = new File(inputFile);
            Document document = Jsoup.parse(input, "UTF-8");
            
            String titleClass = "ud-accordion-panel-title";
            String itemClass = "ud-block-list-item-content";

            Elements spanElements = document.select("span." + titleClass + ", div." + itemClass);
            for (Element spanElement : spanElements) {
                if (spanElement.className().equals(titleClass)) {
                    Elements titleElement = spanElement.select(".section--section-title--wcp90");
                    System.out.println(titleElement.text());
                }
                if (spanElement.className().equals(itemClass)) {
                    Elements itemElement = spanElement.select("span");
                    System.out.println("  " + itemElement.get(0).text());                    
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return 0;
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new udemy_html_toc()).execute(args);
        System.exit(exitCode);
    }
}
