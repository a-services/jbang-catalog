@Grab('info.picocli:picocli-groovy:4.6.3')
import picocli.CommandLine
import static picocli.CommandLine.*

import java.util.concurrent.Callable

@Command(name = 'ts_imports', mixinStandardHelpOptions = true, version = '2022-12-13',
  description = 'Find list of TypeScript imports in folder')
class ts_imports implements Callable<Integer> {

    @Parameters(index = '0', description = 'Input folder.', 
        defaultValue="/Users/eabramovich/Documents/2017/17-12/NorthwellM3/gitlab/nh-package-tracking-mobile/src/core/models/")
    File inputFolder


    Integer call() throws Exception {


        return 0
    }

    boolean isImportLine(String ln) {
        return ln.startsWith("import ")
    }

    boolean isExportLine(String ln) {
        return ln.startsWith("export ")
    }
    
    static void main(String[] args) {
        System.exit(new CommandLine(new ts_imports()).execute(args))
    }
}