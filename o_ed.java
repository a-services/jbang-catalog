///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.6
//DEPS org.thymeleaf:thymeleaf:3.1.2.RELEASE
//DEPS com.sparkjava:spark-core:2.9.4
//DEPS org.slf4j:slf4j-api:2.0.16
//DEPS org.slf4j:slf4j-simple:2.0.16
//DEPS org.json:json:20240303

import static spark.Spark.get;
import static spark.Spark.port;
import static spark.Spark.post;
import static spark.Spark.put;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Help.Ansi;
import spark.Request;

/// Obsidian Web Editor.
/// 
/// Edit markdown source for Obsidian page in `textarea`.
///
@Command(name = "o_ed", mixinStandardHelpOptions = true, version = "2024-11-16", description = "Obsidian Web Editor")
class o_ed implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(o_ed.class);

    @Option(names = { "-p", "--port" }, defaultValue = "8080", description = "HTTP port.")
    int httpPort;

    TemplateEngine templateEngine = new TemplateEngine();

    // Paths of obsidian folders
    List<String> obsidianFolders;

    // Base names of obsidian folders to use in dropdown
    List<String> obsidianNames;

    String selectedObsidianName;

    // Subfolders of Obsidian folder.
    List<String> subfolders;

    String selectedSubfolder;

    List<String> newestFiles;

    String noteName;

    File noteFolder;

    String noteHome;

    String noteText;

    String status;

    @Override
    public Integer call() throws Exception {
        printBanner();

        StringTemplateResolver templateResolver = new StringTemplateResolver();
        templateResolver.setTemplateMode("HTML");
        templateResolver.setCacheable(false);
        templateEngine.setTemplateResolver(templateResolver);

        port(httpPort);

        get("/", (req, res) -> {
            homePage(req);
            return templateEngine.process(pageTemplate(), pageContext());
        });

        post("/post", (req, res) -> {
            String action = req.queryParams("action");
            switch (action) {
                case "save":
                    savePage(req);
                    break;

                case "restore":
                    restorePage(req);
                    initNewestFiles();
                    break;
            }
            return templateEngine.process(pageTemplate(), pageContext());
        });

        put("/update", (req, res) -> {
            updatePage(req);
            return "{}";
        });

        return 0;
    }

    /// Home page is refreshed after each update
    void homePage(Request req) {
        if (status == null) {
            status = "Obsidian Web Editor";    
        }  

        if (selectedObsidianName == null) {
            initObsidianNames();
        }       
    }

    void initObsidianNames() {
        try {
            // Get the home directory
            String homeFolder = System.getProperty("user.home");

            // Construct the path to the obsidian.json file
            String obsidianJsonPath = homeFolder + "/Library/Application Support/obsidian/obsidian.json";

            // Read the JSON file
            String jsonContent = Files.readString(Paths.get(obsidianJsonPath));
            JSONObject obsidianJson = new JSONObject(jsonContent);

            // Get the vaults
            JSONObject vaultsObject = obsidianJson.getJSONObject("vaults");

            // Extract values and sort them based on the 'ts' key in reverse order
            List<JSONObject> sortedVaults = vaultsObject.keySet().stream()
                    .map(vaultsObject::getJSONObject)
                    .sorted((vault1, vault2) -> Long.compare(vault2.getLong("ts"), vault1.getLong("ts")))
                    .collect(Collectors.toList());

            // Extract the 'path' from each sorted entry
            obsidianFolders = sortedVaults.stream()
                    .map(vault -> vault.getString("path"))
                    .collect(Collectors.toList());

            // Extract the base names of the folders
            obsidianNames = obsidianFolders.stream()
                    .map(Paths::get)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .collect(Collectors.toList());

            log.info("[initObsidianNames] " + obsidianNames.size()); 
            
            if (obsidianNames.size() > 0) {
                setSelectedObsidianName(obsidianNames.get(0));      
            }

        } catch (Exception e) {
            log.error("[initObsidianNames]" , e);
        }
    }
 
    void initFolders() {
        try {
            int k = obsidianNames.indexOf(selectedObsidianName);
            noteHome = obsidianFolders.get(k);
            File noteHomeDir = new File(noteHome);

            subfolders = new ArrayList<>();

            // Insert "." at the beginning of the list
            subfolders.add(0, ".");

            // List all items in the directory
            String[] allDirItems = noteHomeDir.list();
            if (allDirItems != null) {
                for (String item : allDirItems) {
                    File itemFile = new File(noteHome, item);
                    if (itemFile.isDirectory() && !item.equals(".obsidian")) {
                        subfolders.add(item);
                    }
                }   
            }

            log.info("[initFolders] " + subfolders.size()); 
            setSelectedSubfolder(subfolders.get(0));

        } catch (Exception e) {
            log.error("[initFolders]" , e);
        }
    }
    
    void initNewestFiles() {
        try {
            noteFolder = new File(noteHome, selectedSubfolder);
            newestFiles = getNewestFiles(noteFolder, 5);

            log.info("[initNewestFiles] " + newestFiles.size()); 
            setNoteName(newestFiles.size() > 0 ? newestFiles.get(0) : null);

        } catch (Exception e) {
            log.error("[initNewestFiles]" , e);
        }
    }

    void initTextArea() {
        try {
            if (noteName != null) {
                File filePath = new File(noteFolder, noteName);
                noteText = Files.readString(Paths.get(filePath.getPath()));
            } else {
                noteText = "";
            }
            log.info("[initTextArea] " + noteText.length()); 

        } catch (IOException e) {
            log.error("[initTextArea]" , e);
        }

    }

    List<String> getNewestFiles(File dir, int numFiles) throws Exception {

        // Get a list of files in the directory with their full paths and modification times
        List<File> filesWithPaths = new ArrayList<>();
        for (File file : dir.listFiles()) {
            if (file.isFile() && file.getName().endsWith(".md")) {
                filesWithPaths.add(file);
            }
        }

        // Sort files by modification time in descending order (newest first)
        filesWithPaths.sort((file1, file2) -> {
            try {
                BasicFileAttributes attr1 = Files.readAttributes(file1.toPath(), BasicFileAttributes.class);
                BasicFileAttributes attr2 = Files.readAttributes(file2.toPath(), BasicFileAttributes.class);
                return Long.compare(attr2.lastModifiedTime().toMillis(), attr1.lastModifiedTime().toMillis());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // Extract the numFiles newest file names
        List<String> newestFiles = new ArrayList<>();
        for (int i = 0; i < Math.min(numFiles, filesWithPaths.size()); i++) {
            newestFiles.add(filesWithPaths.get(i).getName());
        }

        return newestFiles;
    }

    void updatePage(Request req) throws Exception {
        JSONObject updateJson = new JSONObject(req.body());
        String widget = updateJson.getString("widget");
        String selectedValue = updateJson.getString("value");
        log.info(String.format("[updatePage] widget: %s, value: %s", widget, selectedValue));
        switch (widget) {
            case "obsidianDropdown":
                setSelectedObsidianName(selectedValue);
                break;
            case "folderDropdown":
                setSelectedSubfolder(selectedValue);
                break;     
            case "pageDropdown":
                setNoteName(selectedValue);
                break;                 
        }
    }

    /// Set Page
    void setNoteName(String selectedValue) throws Exception {
        noteName = selectedValue;

        status = noteName != null? "Page selected: " + noteName : "Empty folder";
        log.info(status);

        initTextArea();
    }

    /// Set Folder
    void setSelectedSubfolder(String selectedValue) throws Exception {
        selectedSubfolder = selectedValue;

        status = "Folder selected: " + selectedSubfolder;
        log.info(status);

        initNewestFiles();
    }

    /// Set Obsidian
    void setSelectedObsidianName(String selectedValue) throws Exception {
        if (selectedValue == selectedObsidianName) {
            return;
        }
        selectedObsidianName = selectedValue;

        status = "Obsidian selected: " + selectedObsidianName;
        log.info(status);

        initFolders();
    }

    void savePage(Request req) throws IOException {
        noteText = req.queryParams("noteText");
        File filePath = new File(noteFolder, noteName);
        saveStr(filePath.getPath(), noteText);
        status = "Saved: " + noteName; 
        log.info(status);
    }

    void restorePage(Request req) {
        initTextArea();
        status = "Restored: " + noteName; 
        log.info(status);
    }

    /// Save string to file. 
    void saveStr(String fname, String text)
            throws IOException {
        PrintWriter out = new PrintWriter(new FileOutputStream(fname));
        out.print(text);
        out.close();
    }

    String currentTime() {
        LocalTime currentTime = LocalTime.now();
        return currentTime.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    Context pageContext() throws IOException {
        Context context = new Context();
        context.setVariable("obsidianNames", obsidianNames);
        context.setVariable("selectedObsidianName", selectedObsidianName);
        context.setVariable("subfolders", subfolders);
        context.setVariable("selectedSubfolder", selectedSubfolder);
        context.setVariable("newestFiles", newestFiles);
        context.setVariable("noteName", noteName);
        context.setVariable("noteText", noteText);
        context.setVariable("status", "[" + currentTime() + "] " + status);
        return context;
    }

    String pageTemplate() throws IOException {
        //return Files.readString(Paths.get("thymeleaf/page.html"));
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                <title>Obsidian Web Editor</title>
                <link
                href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css"
                rel="stylesheet"
                />
                <style>
                body {
                    height: 100vh;
                    margin: 0;
                    display: flex;
                    flex-direction: column;
                }

                #topbar {
                    flex: 0 0 auto;
                    padding: 1rem;
                    background-color: #f8f9fa;
                    border-bottom: 1px solid #dee2e6;
                }

                #content {
                    flex: 1 1 auto;
                    padding: 0;
                }

                textarea {
                    width: 100%;
                    height: calc(100vh - 210px);
                    /* border: none; */
                    resize: none;
                    outline: none;
                    padding: 1rem;
                }
                </style>
            </head>
            <body>
                <form method="post" action="/post">
                <!-- Topbar -->
                <div id="topbar" class="container-fluid">
                    <div class="row align-items-center">
                    <div class="col-md-4 mb-2 mb-md-0">
                        <label for="obsidianDropdown" class="form-label">Obsidian</label>
                        <select
                        id="obsidianDropdown"
                        name="obsidianDropdown"
                        class="form-select"
                        th:attr="onchange='notifyServer(\\'obsidianDropdown\\', this.value)'"
                        >
                        <option
                            th:each="obsidianName : ${obsidianNames}"
                            th:text="${obsidianName}"
                            th:selected="${obsidianName == selectedObsidianName}"
                        >
                            Option
                        </option>
                        </select>
                    </div>
                    <div class="col-md-4 mb-2 mb-md-0">
                        <label for="folderDropdown" class="form-label">Folder</label>
                        <select
                        id="folderDropdown"
                        name="folderDropdown"
                        class="form-select"
                        th:attr="onchange='notifyServer(\\'folderDropdown\\', this.value)'"
                        >
                        <option
                            th:each="subfolder : ${subfolders}"
                            th:text="${subfolder}"
                            th:selected="${subfolder == selectedSubfolder}"
                        >
                            Option
                        </option>
                        </select>
                    </div>
                    <div class="col-md-4 mb-2 mb-md-0">
                        <label for="pageDropdown" class="form-label">Page</label>
                        <select
                        id="pageDropdown"
                        name="pageDropdown"
                        class="form-select"
                        th:attr="onchange='notifyServer(\\'pageDropdown\\', this.value)'"
                        >
                        <option
                            th:each="file : ${newestFiles}"
                            th:text="${file}"
                            th:selected="${file == noteName}"
                        >
                            Option
                        </option>
                        </select>
                    </div>
                    </div>
                    <div class="row align-items-center">
                    <div class="col-md-8 pt-4">
                        <div class="alert alert-primary" role="alert" th:text="${status}">
                        Obsidian Web Editor
                        </div>
                    </div>
                    <div class="col-md-4 mb-2 mb-md-0 text-end">
                        <button
                        type="submit"
                        name="action"
                        value="save"
                        class="btn btn-outline-primary"
                        >
                        Save
                        </button>
                        <button
                        type="submit"
                        name="action"
                        value="restore"
                        class="btn btn-outline-secondary"
                        >
                        Restore
                        </button>
                    </div>
                    </div>
                </div>

                <!-- Content Area -->
                <div id="content">
                    <textarea th:text="${noteText}" name="noteText"></textarea>
                </div>
                </form>

                <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
                <script>
                function notifyServer(widget, selectedValue) {
                    console.log(
                    "[notifyServer] widget: " + widget + ", value: " + selectedValue
                    );
                    fetch(`/update`, {
                    method: "PUT",
                    headers: {
                        "Content-Type": "application/json",
                    },
                    body: JSON.stringify({ widget: widget, value: selectedValue }),
                    })
                    .then((response) => {
                        if (response.ok) {
                        window.location.href = '/';
                        } else {
                        console.error("Server error:", response.statusText);
                        }
                    })
                    .catch((error) => console.error("Network error:", error));
                }
                </script>
            </body>
            </html>
           """;
    }

    String getLocalIpAddress() {
        String localIpAddress = null;
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();

                // Skip loopback and inactive interfaces
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }

                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress inetAddress = inetAddresses.nextElement();

                    // Skip loopback addresses and IPv6 addresses
                    if (!inetAddress.isLoopbackAddress() && inetAddress.isSiteLocalAddress()) {
                        localIpAddress = inetAddress.getHostAddress();
                        break;
                    }
                }
            }
        } catch (SocketException e) {
        }
        return localIpAddress;
    }

    void printBanner() {
        System.out.println("""
                                                               o
                                                              <|>
                                                              < \\
                      o__ __o               o__  __o     o__ __o/
                     /v     v\\   _\\__o__   /v      |>   /v     |
                    />       <\\       \\   />      //   />     / \\
                    \\         /           \\o    o/     \\      \\o/
                     o       o             v\\  /v __o   o      |
                     <\\__ __/>              <\\/> __/>   <\\__  / \\
                """);
        System.out.printf(Ansi.AUTO.string("\n  Server is running at @|bold http://localhost:%d|@"), httpPort);
        System.out.printf(Ansi.AUTO.string("\n           Local IP is @|bold http://%s:%d|@\n\n"), getLocalIpAddress(), httpPort);      
    }

    public static void main(String... args) {
        new CommandLine(new o_ed()).execute(args);
    }
}
