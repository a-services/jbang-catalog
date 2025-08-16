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

    /**
     * Initializes Obsidian vault names by reading the obsidian.json configuration file.
     * 
     * This method locates the Obsidian configuration file based on the operating system,
     * reads the vault information, sorts vaults by timestamp (most recent first), and
     * extracts vault paths and names for use in the application.
     * 
     * <p>The method performs the following operations:
     * <ul>
     *   <li>Determines the OS-specific path to obsidian.json configuration file</li>
     *   <li>Parses the JSON configuration to extract vault information</li>
     *   <li>Sorts vaults by timestamp in descending order (most recently accessed first)</li>
     *   <li>Populates {@code obsidianFolders} with full vault paths</li>
     *   <li>Populates {@code obsidianNames} with vault folder names only</li>
     *   <li>Sets the first vault as the selected vault if any vaults exist</li>
     * </ul>
     * 
     * <p>Supported operating systems:
     * <ul>
     *   <li>Windows: %APPDATA%\obsidian\obsidian.json</li>
     *   <li>macOS: ~/Library/Application Support/obsidian/obsidian.json</li>
     * </ul>
     * 
     * @throws Exception if the obsidian.json file cannot be read or parsed,
     *                   or if there are issues accessing the file system
     */
    void initObsidianNames() {
        try {
            // Get the home directory
            String homeFolder = System.getProperty("user.home");

            // Construct the path to the obsidian.json file
            String os = System.getProperty("os.name").toLowerCase();
            String obsidianJsonPath = os.contains("win") ?
                homeFolder + "\\AppData\\Roaming\\obsidian\\obsidian.json" :
                homeFolder + "/Library/Application Support/obsidian/obsidian.json";

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
 
    /**
     * Initializes the subfolders list for the selected Obsidian vault.
     * 
     * This method retrieves the home directory path for the currently selected Obsidian vault,
     * scans the directory for subdirectories, and populates the subfolders list. The root
     * directory is represented by "." and is always added as the first item. The .obsidian
     * folder is excluded from the list as it contains Obsidian's internal configuration files.
     * 
     * @throws Exception if there's an error accessing the file system or if the selected
     *                   Obsidian vault index is invalid
     * 
     * @see #setSelectedSubfolder(String)
     * @see #obsidianNames
     * @see #obsidianFolders
     * @see #subfolders
     */
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
    
    /**
     * Initializes the newest files list by scanning the selected subfolder within the note home directory.
     * Retrieves the 5 most recently modified files from the note folder and sets the note name
     * to the newest file if any files are found.
     * 
     * This method performs the following operations:
     * - Creates a File object for the note folder using noteHome and selectedSubfolder
     * - Fetches the 5 newest files from the folder
     * - Logs the number of files found
     * - Sets the note name to the first (newest) file, or null if no files exist
     * 
     * @throws Exception if an error occurs during file operations or folder access
     */
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

    /**
     * Initializes the text area by loading note content from a file.
     * 
     * If a note name is specified, reads the content from the corresponding file
     * in the note folder. If no note name is provided, initializes with empty text.
     * Logs the length of the loaded text for debugging purposes.
     * 
     * @throws IOException if an error occurs while reading the file
     */
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

    /**
     * Retrieves the names of the newest files from a directory based on modification time.
     * Only considers files with a ".md" extension.
     *
     * @param dir the directory to search for files
     * @param numFiles the maximum number of newest files to return
     * @return a list of file names (without paths) sorted by modification time in descending order,
     *         limited to the specified number of files
     * @throws Exception if an error occurs while reading file attributes or accessing the directory
     */
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

    /**
     * Updates the page state based on widget selection changes from the client request.
     * 
     * Processes JSON payload containing widget type and selected value, then updates
     * the corresponding application state based on the widget that was modified.
     * 
     * @param req the HTTP request containing JSON body with "widget" and "value" fields
     * @throws Exception if JSON parsing fails or request body is malformed
     */
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
        if (selectedValue.length() > 0) {
            selectedObsidianName = selectedValue;
        }

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
                <div class="small text-muted mb-1">Click Refresh to check for new files</div>
                <button type="submit" name="action" value="save" class="btn btn-outline-primary">
                    Save
                </button>
                <button type="submit" name="action" value="restore" class="btn btn-outline-secondary">
                    Restore
                </button>
                <button class="btn btn-outline-secondary" type="button" onclick="notifyServer('obsidianDropdown', '')">
                    Refresh
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
