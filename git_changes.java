///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.6
//DEPS org.eclipse.jgit:org.eclipse.jgit:6.9.0.202403050737-r
//DEPS commons-io:commons-io:2.16.1
//DEPS org.slf4j:slf4j-api:2.0.13
//DEPS org.slf4j:slf4j-simple:2.0.13
//JAVA 17+

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.Callable;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "git_changes", mixinStandardHelpOptions = true, version = "2024-05-28", 
         description = "Copy added/changed files from git to backup folder")
class git_changes implements Callable<Integer> {

    @Parameters(index = "0", description = "Project name")
    private String projName;

    @Parameters(index = "1", description = "Destination folder")
    private String destDir;

    @Override
    public Integer call() throws Exception {
        System.out.println("        Project name: " + projName);
        System.out.println("  Destination folder: " + destDir);

        File projectDir = new File(projName);
        File destinationDir = new File(destDir, projName);

        try (Repository repository = new RepositoryBuilder().setWorkTree(projectDir).build()) {
            try (Git git = new Git(repository)) {
                // Get status of the repository
                Status status = git.status().call();

                // Get list of changed files
                System.out.println("----------------------- changed files");
                Set<String> changedFiles = status.getModified();
                for (String filePath : changedFiles) {
                    File sourceFile = new File(projectDir, filePath);
                    File destFile = new File(destinationDir, filePath);
                    System.out.println("Copying: " + filePath);
                    copyFile(sourceFile, destFile);
                }

                // Get list of added files
                System.out.println("----------------------- added files");
                Set<String> addedFiles = status.getUntracked();
                for (String filePath : addedFiles) {
                    File sourceFile = new File(projectDir, filePath);
                    File destFile = new File(destinationDir, filePath);
                    System.out.println("Copying: " + filePath);
                    copyFile(sourceFile, destFile);
                }
                
                System.out.println("-----------------------");
                System.out.println(changedFiles.size() + " changed files copied");
                System.out.println(addedFiles.size() + " added files copied");
            }
        }
        return 0;
    }

    private static void copyFile(File sourceFile, File destFile) throws IOException {
        //System.out.println("Copying: " + sourceFile + "\n" +
        //                   "     to: " + destFile + "\n");
        FileUtils.copyFile(sourceFile, destFile);
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new git_changes()).execute(args);
        System.exit(exitCode);
    }
}
