///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.0


import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import static java.lang.System.err;
import static java.lang.System.out;

import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Command(name = "ts_imports", mixinStandardHelpOptions = true, version = "2022-12-16",
        description = "Find list of TypeScript imports in folder")
class ts_imports implements Callable<Integer> {

    @Parameters(index = "0", description = "Input folder.")
    String inputFolder;

    Path inputPath;

    @Override
    public Integer call() throws Exception { 

        inputPath = Path.of(inputFolder);
        //out.println("Input path: " + inputPath);

        /* Собрать все импорты в `importSet`
         */
        Set<String> importSet = new HashSet<>();
        int nFiles = 0;
        int totalLines = 0;
        DirectoryStream<Path> dirStream = 
            Files.newDirectoryStream(Path.of(inputFolder), "*.{ts}");
        for (Path file : dirStream) {

            /* Пройти по строкам указанного файла в папке
             */
            String curFile = null;
            int curLine = 0; 
            try {            
                //out.println("File: " + entry.getFileName());
                nFiles++;
                curFile = file.toString();
                List<String> text = Files.readAllLines(file);
                totalLines += text.size();
                curLine = 0;
                for (String ln: text) {
                    curLine++;
                    if (isImportLine(ln)) {
                        String imp = extractImport(ln);
                        if (isExternalImport(imp)) {
                            importSet.add(imp);
                        }
                    }
                    if (isExportLine(ln)) {
                        break;
                    }
                }
            } catch (IllegalArgumentException ex) {
                err.println("[ERROR] File: " + curFile + ", line: " + curLine + ": " + ex.getMessage());                
            }
        }
        
        out.println("======= " + nFiles + " `*.ts` file(s) with " + totalLines + " line(s) in folder: `" + inputFolder + "`");

        /* Отсортировать и вывести `importSet`
         */
        List<String> importList = new ArrayList<>(importSet);
        importList.sort(null);
        for (String s: importList) {
            out.println("Import: " + s);
        }
        out.println("======= " + importList.size() + " dependencies found");
        
        return 0;
    }

    boolean isExternalImport(String imp) {

        /* Отсекаем системные импорты, которые не начинаются с точки
         */
        if (!imp.startsWith(".")) {
            return false;
        }
        
        Path p = Path.of(inputFolder, imp);
        //out.println("| Path: " + p + " | Parent: " + p.getParent() + " | Home: " + p.getParent().normalize() + "|");

        return p.getParent().normalize().toString().length() > 0;
    }

    //out.println(extractImport("import { LogEntry } from '../services/loglevel-message-persistor';"));
    String extractImport(String ln) {

        /* Найдем в строке ключевое слово `from` и оставим только часть строки после него.
         */
        Pattern pFrom = Pattern.compile("\\bfrom\\b");
        Matcher m = pFrom.matcher(ln);
        if (!m.find()) {
            throw new IllegalArgumentException("Invalid import: " + ln);
        }
        ln = ln.substring(m.end()).trim();

        /* Строка должна заканчиваться на `;`
         */
        if (ln.charAt(ln.length() - 1) != ';') {
            throw new IllegalArgumentException("Missing semicolon: " + ln);
        }
        ln = ln.substring(0, ln.length() - 1).trim();

        /* Имя файла должно быть в одинарных или двойных кавычках
         */
        char c1 = ln.charAt(0);
        char c2 = ln.charAt(ln.length() - 1);
        if (!((c1=='\'' && c2=='\'') || (c1=='\"' && c2=='\"'))) {
            throw new IllegalArgumentException("Invalid quotes: " + ln);
        }
        ln = ln.substring(1, ln.length() - 1);
        //out.println("Line: " + ln);
        return ln;
    }

    boolean isImportLine(String ln) {
        Pattern reImport = Pattern.compile("^\\b*import\\b");
        Pattern reFrom = Pattern.compile("\\bfrom\\b");
        return reImport.matcher(ln).find() && reFrom.matcher(ln).find();
    }

    boolean isExportLine(String ln) {
        return ln.startsWith("export ");
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new ts_imports()).execute(args);
        System.exit(exitCode);
    }

}
