///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.4
//DEPS org.json:json:20210307

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import static java.lang.System.out;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


@Command(name = "har2postman", mixinStandardHelpOptions = true, version = "2023-07-05", 
         description = "Convert HAR file with HTTP requests exported from Chrome to Postman collection.")
class har2postman implements Callable<Integer> {

    @Parameters(index = "0", description = "Input HAR file.")
    String harFile;

    @Option(names = { "-f", "--filter-url" }, description = "Filter URL")
    String filterUrl;
    
    
    @Override
    public Integer call() throws Exception {
        out.println("Input HAR file: " + harFile);
        String jsonStr = Files.readString(Paths.get(harFile));
        JSONObject json = new JSONObject(jsonStr);
        
        // пройти по списку запросов в har-файле
        JSONArray logEntries = json.getJSONObject("log").getJSONArray("entries");
        ArrayList<RequestLine> reqList = new ArrayList<>();
        out.println("--------------------");
        for (int i = 0; i < logEntries.length(); i++) {
            
            JSONObject req = logEntries.getJSONObject(i).getJSONObject("request");
            String url = req.getString("url");
            if (filterUrl != null && !url.startsWith(filterUrl)) {
                continue;
            }
            out.println(url);
            
            RequestLine r = new RequestLine();
            r.setUrl(url);
            r.setMethod(req.getString("method"));
            r.setParams(W.unwrapQuery(req.getJSONArray("queryString")));

            reqList.add(r);
        }
        out.println("--------------------");
        
        new PostmanService().saveRequests(reqList, harFile);
        return 0;
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new har2postman()).execute(args);
        System.exit(exitCode);
    }
}

class Param {
    
    private String name;
    private String value;

    public Param(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }
    
}

class RequestLine {
    String url;
    String method;
    List<Param> params;
    String body;
    
    public String getUrl() {
        return url;
    }
    public void setUrl(String url) {
        this.url = url;
    }
    public String getMethod() {
        return method;
    }
    public void setMethod(String method) {
        this.method = method;
    }
    public List<Param> getParams() {
        return params;
    }
    public void setParams(List<Param> params) {
        this.params = params;
    }
    public String getBody() {
        return body;
    }
    public void setBody(String body) {
        this.body = body;
    }
}

class PostmanService {

    public static final String postmanExt = ".postman_collection.json";

    final String aexHostVar = "aex_host";
    final String aexKeyVar = "aex_key";

    /** 
     * Сохранить список запросов в постмановскую коллекцию
     */
    public void saveRequests(List<RequestLine> aexRequests, String postmanCollectionName) 
    {
        String postmanCollectionFile = postmanCollectionName + postmanExt;
        Path outFile = Path.of(postmanCollectionFile);

        JSONObject json = new JSONObject();
        JSONObject info = new JSONObject();
        JSONArray items = new JSONArray();
        json.put("info", info);
        info.put("name", postmanCollectionName);
        info.put("schema", "https://schema.getpostman.com/json/collection/v2.1.0/collection.json");
        json.put("item", items);
        int count = 0;
        for (RequestLine req : aexRequests) {
            if (req.getUrl() == null) {
                continue;
            }

            JSONObject item = new JSONObject();
            items.put(item);
            count++;

            try {
                URI uri = new URI(req.getUrl());

                /* Set name
                */
                String reqName = String.format("%02d %s", count, uri.getPath());
                item.put("name", reqName);

                /* Set request
                */
                JSONObject request = new JSONObject();
                item.put("request", request);
                request.put("method", req.getMethod());

                /* Set header
                */
                JSONArray header = new JSONArray();
                header.put(W.wrapPair("Content-Type", "application/json"));
                request.put("header", header);

                /* Set url
                */
                JSONObject url = new JSONObject();
                request.put("url", url);
                String rawUrl = req.getUrl();
                url.put("raw", rawUrl);


                JSONArray host = new JSONArray();
                url.put("host", host);
                host.put(W.getUriHost(uri));

                url.put("path", W.wrapPath(uri.getPath()));
                url.put("query", W.wrapQuery(req.getParams()));

                if (req.getBody() != null) {
                        request.put("body", W.wrapBody(req.getBody().toString()));
                }

            } catch (URISyntaxException e) {
                out.println("[WARN] Invalid URI: " + req.getUrl());
                out.println("       Reason: " + e.getMessage());
            }
        }

        /* Save JSON
         */
        try {
            Files.write(outFile, json.toString().getBytes());
        } catch (IOException e) {
            out.println("[WARN] Cannot write Postman file: " + postmanCollectionFile);
            out.println("       Reason: " + e.getMessage());
        }
    }
}

class W {
    
    static String getUriHost(URI uri) {
        String result = uri.getScheme() + "://" + uri.getHost();
        if (uri.getPort() != -1) {
            result += ":" + uri.getPort();
        }
        return result;
    }
    
    static JSONObject wrapBody(String body) {
        JSONObject result = new JSONObject();
        result.put("mode", "raw");
        result.put("raw", body);
        JSONObject options = new JSONObject();
        result.put("options", options);
        JSONObject raw = new JSONObject();
        options.put("raw", raw);
        raw.put("language", "json");
        return result;
    }

    static JSONArray wrapPath(String urlPath) {
        JSONArray result = new JSONArray();
        if (urlPath.startsWith("/")) {
            urlPath = urlPath.substring(1, urlPath.length());
        }
        result.putAll(urlPath.split("/"));
        return result;
    }

    static JSONArray wrapQuery(List<Param> query) {
        JSONArray result = new JSONArray();
        for (Param pair : query) {
            String value = pair.getValue();

            try {
                value = URLEncoder.encode(pair.getValue(), "UTF-8");
            } catch (JSONException | UnsupportedEncodingException e) {
                out.println("[WARN] Cannot encode value: " + value);
                out.println("       Reason: " + e.getMessage());
            }

            result.put(wrapPair(pair.getName(), value));
        }
        return result;
    }
    
    static List<Param> unwrapQuery(JSONArray queryString) {
        ArrayList<Param> result = new ArrayList<>();
        for (int j = 0; j < queryString.length(); j++) {
            JSONObject p = queryString.getJSONObject(j);
            Param param = new Param(p.getString("name"), p.getString("value"));
            result.add(param);
        }
        return result;
    }

    static JSONObject wrapPair(String key, String value) {
        JSONObject result = new JSONObject();
        result.put("key", key);
        result.put("value", value);
        return result;
    }

}
