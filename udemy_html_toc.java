///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.6
//DEPS org.jsoup:jsoup:1.16.1

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.File;
import java.io.IOException;

@Command(name = "udemy_html_toc", mixinStandardHelpOptions = true, version = "2024-06-12",
        description = "Parse html file with Udemy course TOC")
class udemy_html_toc implements Callable<Integer> {

    @Parameters(index = "0", description = "Input file", defaultValue = "toc.html")
    private String inputFile;

    @Option(names = {"-n", "--numbers"}, description = "Section numbers")
    private boolean sectionNumbers;
               
    // CSS classes of the elements to be selected.
    static final String TITLE_CLASS = "ud-accordion-panel-title";
    static final String ITEM_CLASS = "ud-block-list-item-content";
    static final String TITLE_SPAN_PREFIX = "section--section-title--";
    static final String TIME_SPAN_PREFIX = "section--item-content-summary--";

    /** 
     * Process an HTML file, extract certain elements using Jsoup[^1], 
     * and print these elements with optional section numbers.
     * 
     * [^1] [JSoup DOM Navigation](https://jsoup.org/cookbook/extracting-data/dom-navigation)
     */
    @Override
    public Integer call() throws Exception { 
        //System.out.println("Input file: " + inputFile);
      
        try {
            // Load the HTML file using Jsoup
            File input = new File(inputFile);
            Document document = Jsoup.parse(input, "UTF-8");

            // Counters for titles and items, respectively.
            int titleNum = 0;
            int itemNum = 0;

            // Selects all `span` elements with the class `TITLE_CLASS` and all `div` elements with the class `ITEM_CLASS`.
            Elements elements = document.select("span." + TITLE_CLASS + ", div." + ITEM_CLASS);

            for (Element element : elements) {
                if (element.className().equals(TITLE_CLASS)) {
                    titleNum++;
                    String n = sectionNumbers? titleNum + ". " : "";
                    Elements titleElement = element.select("span[class^=" + TITLE_SPAN_PREFIX + "]");
                    System.out.println(n + titleElement.text());
                }
                if (element.className().equals(ITEM_CLASS)) {
                    /* Selects the first `span` within the `div` elements with the class `ITEM_CLASS`, 
                       extracts its text, 
                       checks if this `div` includes a timestamp, 
                       optionally prefixes with the number, 
                       and prints the item name.
                     */  
                    Elements itemElement = element.select("span");
                    String itemName = itemElement.get(0).text();
                    boolean hasNumber = itemIncludesTimestamp(element);
                    if (hasNumber) {
                        itemNum++;
                    }
                    String n = sectionNumbers && hasNumber? itemNum + ". " : "";
                    System.out.println("  " + n + itemName);                    
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return 0;
    }

    boolean itemIncludesTimestamp(Element divElement) {
        // Compiles a regex pattern to match time strings in the format `HH:mm`.
        Pattern timePattern = Pattern.compile("^\\d{2}:\\d{2}$");

        // Select elements whose class names contain a specific prefix.
        Elements timeElements = divElement.select("span[class*=" + TIME_SPAN_PREFIX + "]");
    
        if (timeElements.isEmpty()) {
            // System.err.println("********** No '" + TIME_SPAN_PREFIX + "' in item:\n" + divElement);
            return false;
        }
        String timeStamp = timeElements.get(0).text();
        Matcher matcher = timePattern.matcher(timeStamp);
        return matcher.find();
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new udemy_html_toc()).execute(args);
        System.exit(exitCode);
    }
}
