//DEPS com.sparkjava:spark-core:2.9.4
//DEPS com.vladsch.flexmark:flexmark-all:0.64.8
//DEPS info.picocli:picocli:4.7.6
//DEPS org.slf4j:slf4j-api:2.0.16
//DEPS org.slf4j:slf4j-simple:2.0.16
//DEPS org.yaml:snakeyaml:1.33
//DEPS io.github.stefanbratanov:jvm-openai:0.11.0

import static spark.Spark.get;
import static spark.Spark.port;
import static spark.Spark.post;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.yaml.snakeyaml.Yaml;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;

import io.github.stefanbratanov.jvm.openai.ChatClient;
import io.github.stefanbratanov.jvm.openai.ChatCompletion;
import io.github.stefanbratanov.jvm.openai.ChatMessage;
import io.github.stefanbratanov.jvm.openai.CreateChatCompletionRequest;
import io.github.stefanbratanov.jvm.openai.OpenAI;
import io.github.stefanbratanov.jvm.openai.OpenAIModel;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Running OpenAI prompts.
 */
@Command(name = "ai_note", mixinStandardHelpOptions = true, version = "2024-10-16", 
         description = "Note-AI")
public class ai_note implements Callable<Integer> {

    @Option(names = { "-p", "--port" }, defaultValue = "8080",
            description = "HTTP port.")
    int httpPort;

    @Option(names = { "-f", "--file" }, defaultValue = "ai_note.yml",
            description = "OpenAI prompt file.")
    String promptFile;

    List<Prompt> prompts;

    Yaml yaml = new Yaml();

    String apiKey;
    
    public static void main(String... args) {
        new CommandLine(new ai_note()).execute(args);
    }

    @Override
    public Integer call() throws Exception {   

        // Retrieve OpenAI API Key from environment variable
        apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("[ERROR] Please set the OPENAI_API_KEY environment variable.");
            return 1;
        }

        try {
            prompts = asPrompts(yaml.load(Files.newInputStream(Path.of(promptFile))));
        } catch (NoSuchFileException e) {
            System.err.println("[ERROR] File not found: " + promptFile);
            return 1;
        }

        // Configure the server
        port(httpPort);
        
        // Serve the initial HTML page with textarea and button
        get("/", (req, res) -> {
            return String.format("""
                <html>
                <head>
                  %s
                </head>
                <body class="container mt-5">
                    <form method="post" action="/render">

                        <div class="mb-3">
                            <label for="optionSelect" class="form-label">Choose an option:</label>
                            <select class="form-select" name="option" id="optionSelect">
                              %s
                            </select>
                        </div>

                        <div class="mb-3">
                            <textarea class="form-control" name="markdown" rows="6" placeholder="Enter your query here"></textarea>
                        </div>

                        <button type="submit" class="btn btn-primary">Call OpenAI</button>
                    </form>
                </body>
                </html>
            """, createHeader(), getPromptOptions());
        });
        
        // Handle the form submission and render the Markdown as HTML
        post("/render", (req, res) -> {
            String markdown = req.queryParams("markdown");
            String selectedOption = req.queryParams("option");
            System.out.println("selectedOption: " + selectedOption);

            markdown = callOpenAI(markdown, selectedOption);

            // Parse and render the markdown input as HTML
            Parser parser = Parser.builder().build();
            HtmlRenderer renderer = HtmlRenderer.builder().build();
            String htmlContent = renderer.render(parser.parse(markdown));

            // Return the rendered HTML
            return String.format("""
                <html>
                <head>
                  %s
                </head>                
                <body class="container mt-5">
                    <code>Result:</code>
                    <div>%s</div><br>
                    <a href="/">Go back</a>
                </body>
                </html>
            """, createHeader(), htmlContent);
        });
        
        printBanner();

        return 0;
    }

    String callOpenAI(String query, String promptName) throws IOException, InterruptedException {
        Prompt prompt = findPrompt(promptName);

        OpenAI openAI = OpenAI.newBuilder(System.getenv("OPENAI_API_KEY")).build();

        ChatClient chatClient = openAI.chatClient();
        CreateChatCompletionRequest createChatCompletionRequest = CreateChatCompletionRequest.newBuilder()
                .model(OpenAIModel.GPT_4o)
                .message(ChatMessage.systemMessage(prompt.note()))
                .message(ChatMessage.userMessage(query))
                .build();

        ChatCompletion chatCompletion = chatClient.createChatCompletion(createChatCompletionRequest);

        String text = chatCompletion.choices().get(0).message().content();

        return text;
    }

    Prompt findPrompt(String promptName) {
        Optional<Prompt> optionalPrompt = prompts.stream()
            .filter(prompt -> Objects.equals(prompt.name(), promptName))
            .findFirst();
        return optionalPrompt.get();    
    }

    List<Prompt> asPrompts(List<Map<String, String>> promptObjects) {
        return promptObjects.stream()
            .map(map -> new Prompt(map.get("name"), map.get("note")))
            .collect(Collectors.toList());
    }

    String getPromptOptions() {
        return prompts.stream()
                .map(p -> String.format("<option value=\"%s\">%s</option>", p.name(), p.name()))
                .collect(Collectors.joining("\n"));
    }

    String createHeader() {
        return """
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">     
            """;
    }

    void printBanner() {
        System.out.printf("\n  Server is running at http://localhost:%d\n\n", httpPort);
        System.out.println("""
               _   __      __             ___    ____   
              / | / /___  / /____        /   |  /  _/   
             /  |/ / __ \\/ __/ _ \\______/ /| |  / /   
            / /|  / /_/ / /_/  __/_____/ ___ |_/ /      
           /_/ |_/\\____/\\__/\\___/     /_/  |_/___/                                                          
        """);
    }
}

record Prompt(String name, String note) {
}
