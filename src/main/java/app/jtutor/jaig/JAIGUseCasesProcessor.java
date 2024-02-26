package app.jtutor.jaig;

import app.jtutor.jaig.config.GlobalConfig;
import org.apache.commons.io.FileUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeSet;

/**
 * This class implements 6 possible use cases when JAIG can be applied to:
 *
 * 1) FOLDER -> find all .txt files, generate .batch with .txt file on every line
 * 2) .yaml file -> read configuration and create requests from the template
 * 3) .batch file -> treat every line as a prompt and sequentially execute every prompt
 * 4) .patch file -> apply patch to the <prompt>-parsed folder
 * 5) .java file -> analyze files in src/main/java and generate patch based on difference with
 *                  <prompt>-parsed folder
 *                  (i.e. what user has changed in the code after generation)
 * 6) .txt file -> send prompt to GPT: see all processing steps in PromptProcessor class
 *
 */
public class JAIGUseCasesProcessor {

    private final LifecyclePhasesProcessor lifecyclePhasesProcessor;

    public JAIGUseCasesProcessor() {
        lifecyclePhasesProcessor = new LifecyclePhasesProcessor();
    }

    public void process(String inputFileOrFolder) {
        // OPTION 1: BATCH or INSERT FOLDER
        File inputFileFile = new File(inputFileOrFolder);
        if (inputFileFile.isDirectory()) {
            // check that inputFileFile starts with 2 digits and _, like 01_
            if (inputFileFile.getName().matches("\\d\\d_.*")) {
                // insert new folder
                InsertFolderProcessor.INSTANCE.insertFolder(inputFileOrFolder);
                return;
            } else {
                // show menu
                System.out.printf("""
                        What do you want to do with the folder %s?
                        1) Create batch file with all prompts (.txt files) in the folder
                        2) Cleanup the folder from all artifacts except prompts
                        """, inputFileOrFolder);
                String answer = new Scanner(System.in).nextLine();
                if (answer.equals("1")) {
                    if (processFolderCreateBatch(inputFileOrFolder)) return;
                } else if (answer.equals("2")) {
                    FolderCleanup.cleanFolder(inputFileOrFolder);
                    return;
                }
            }
        }

        // OPTION 2: YAML: processing template configured with .yaml
        if (processYaml(inputFileOrFolder)) return;

        // OPTION 3: .batch file: processing .batch with prompts
        if (processBatch(inputFileOrFolder)) return;

        // OPTION 4: patch: processing parsed files folder
        if (processPatch(inputFileOrFolder)) return;

        // OPTION 5: .rollback file
        if (processRollback(inputFileOrFolder, GlobalConfig.INSTANCE.getSrcFolder())) return;

        // OPTION 6: if we didn't branch to any of the previous "special cases" -
        // we process inputFileOrFolder as a prompt
        if (processPrompt(inputFileOrFolder)) return;

        System.out.println("JAIG doesn't know how to process "+inputFileOrFolder);
        System.out.println("\nJAIG can be applied to:\n"+
                        """
1) FOLDER -> find all .txt files, generate .batch with .txt file on every line
2) .yaml file with specified template -> read configuration and create requests from the template
3) .batch file -> treat every line as a prompt and sequentially execute every prompt
4) .patch file -> apply patch to the <prompt>-parsed folder
5) .rollback file -> apply rollback by removing artifacts and restore files in src folder
6) .txt file -> send prompt to GPT
7) .java file + selected fragment -> run JAIG in refactoring dialog mode
8) FOLDER like NN_smth (05_smth) -> enter the name of the folder and move all next folder down 
                                """
                );
    }

    private static boolean processFolderCreateBatch(String inputFileOrFolder) {
        File inputFileFile = new File(inputFileOrFolder);
        if (inputFileFile.isDirectory()) {
            System.out.println("Creating batch for directory: "+ inputFileOrFolder);
            try {
                Set<Path> txtFiles = new TreeSet<>();
                Files.walk(Paths.get(inputFileOrFolder))
                        .filter(f->f.toString().endsWith(".txt") &&
                                !f.toString().contains("-response") &&
                                !f.toString().contains("-request"))
                        .forEach(txtFiles::add);

                // If there are txt files, write them to the batch file
                if (!txtFiles.isEmpty()) {
                    String batchFileName = inputFileFile.getName() + ".batch";
                    Path batchFilePath = Paths.get(inputFileOrFolder, batchFileName);

                    try (BufferedWriter writer = Files.newBufferedWriter(batchFilePath)) {
                        for (Path txtFile : txtFiles) {
                            writer.write(txtFile.toString());
                            writer.newLine();
                        }
                    }
                    System.out.println("Created batch: "+batchFilePath);
                    System.out.println("Now you can open it and run JAIG to execute the batch");
                } else {
                    System.out.println("There were no .txt files found with potential prompts, batch was not created");
                }

                return true;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return false;
    }

    private static boolean processYaml(String inputFileOrFolder) {
        if (inputFileOrFolder.endsWith(".yaml") || inputFileOrFolder.endsWith(".yml")) {
            TemplateProcessor.process(inputFileOrFolder);
            return true;
        }
        return false;
    }

    private boolean processBatch(String inputFileOrFolder) {
        if (inputFileOrFolder.endsWith(".batch")) {
            try {
                Path batchFile = Paths.get(inputFileOrFolder);
                List<String> prompts = Files.lines(batchFile).toList();
                for (String prompt : prompts) {
                    if (prompt.trim().isEmpty()) continue;
                    if (prompt.startsWith("#")) continue;
                    System.out.println();
                    System.out.println("********** JAIG: Batch Processing **********");
                    System.out.println("** PROMPT: "+prompt);
                    System.out.println("********************************************");
                    System.out.println();
                    lifecyclePhasesProcessor.processPrompt(prompt);
                    System.out.println("********************************************");
                    System.out.println("** Finished processing prompt: "+prompt);
                    System.out.println("********************************************");
                    System.out.println();
                    System.out.println();
                }
                System.out.println();
                System.out.println("********** JAIG: Batch Processing **********");
                System.out.println("**************** finished ******************");
                System.out.println("*** NOW YOU CAN RUN THE PROGRAM AND TEST ***");
                System.out.println("********************************************");

                return true;
            } catch (IOException e) {
                System.out.println("Cannot process file");
            }

        }
        return false;
    }

    private boolean processPatch(String inputFileOrFolder) {
        if (inputFileOrFolder.endsWith("-parsed.patch")) {
            String patchFilePath = inputFileOrFolder;
            String parsedFolderPath = patchFilePath.replace(".patch","");
            String patchedFolderPath = parsedFolderPath.replace("-parsed","-patched");
            System.out.println("\n*********** JAIG Patch Applier ***********");
            System.out.println("Applying patch "+patchFilePath);
            System.out.println("to folder "+parsedFolderPath);
            try {
                FileUtils.copyDirectory(new File(parsedFolderPath), new File(patchedFolderPath));
            } catch (IOException e) {
                System.out.println("cannot copy directory "+ inputFileOrFolder +" to directory "+patchedFolderPath);
            }
            PatchApplier.INSTANCE.patchFolder(parsedFolderPath, patchedFolderPath, patchFilePath);
            if (GlobalConfig.INSTANCE.isWriteResponseToSrc()) {
                try {
                    FileUtils.copyDirectory(new File(patchedFolderPath), new File(GlobalConfig.INSTANCE.getSrcFolder()));
                } catch (IOException e) {
                    System.out.println("cannot copy directory "+ inputFileOrFolder +" to directory "+patchedFolderPath);
                }
            }
            return true;
        }
        return false;
    }

    private boolean processPrompt(String inputFileOrFolder) {
        if (inputFileOrFolder.endsWith(".txt")) {
            lifecyclePhasesProcessor.processPrompt(inputFileOrFolder);
            return true;
        }
        return false;
    }

    /**
     * Processes the rollback file and applies the rollback
     * by restoring or deleting files based on the instructions provided in the file
     *
     * @param rollbackFile the path of the rollback file or folder
     * @return true if the rollback is successfully applied, false otherwise
     */
    static boolean processRollback(String rollbackFile, String srcFolder) {
        if (rollbackFile.endsWith(".rollback")) {
            System.out.println("\n*********** JAIG Rollback Applier ***********");
            System.out.println("Rollback file "+rollbackFile+" is found");
            System.out.println("Applying rollback "+rollbackFile);
            try {
                List<String> lines = Files.readAllLines(Paths.get(rollbackFile), StandardCharsets.UTF_8);
                boolean deleteRollback = true;

                String toReplace = "-parsed.rollback";
                if (rollbackFile.contains("-full")) {
                    toReplace = "-full.rollback";
                }
                String parsedFolder = rollbackFile.replace(toReplace, "-parsed");
                String parsedOldFolder = rollbackFile.replace(toReplace, "-parsed-old");
                String parsedMergeFolder = rollbackFile.replace(toReplace, "-merge");
                String backupFolder = rollbackFile.replace(toReplace, "-backup");
                String patchFolder = rollbackFile.replace(toReplace, "-patched");

                /*
                This is the example of contents of rollback file:
                Restore src/main/java/ru/ibs/eduplatform/controllers/EnrollmentController.java
                from    generated_requests/enrollment2/04_controller/controller-parsed/ru/ibs/eduplatform/controllers/EnrollmentController.java
                Delete  ./rest/prepare-test-enrollments.http
                 */
                // analyze lines
                for (int i=0; i<lines.size(); i++) {
                    String line = lines.get(i);
                    if (line.startsWith("Restore")) {
                        // extract file name to restore
                        String fileNameToRestore = lines.get(i).replaceFirst("Restore\\s+", "");
                        i++; // go to next line
                        // extract file name to restore from
                        String fileNameToRestoreFrom = lines.get(i).replaceFirst("from\\s+", "");
                        if (!new File(fileNameToRestoreFrom).exists()) {
                            System.out.println("Cannot restore file "+fileNameToRestore+" from "+fileNameToRestoreFrom);
                            System.out.println("File "+fileNameToRestoreFrom+" doesn't exist");
                            deleteRollback = false;
                            break;
                        }
                        // copy file from fileNameToRestoreFrom to fileNameToRestore with replacement
                        FileUtils.copyFile(new File(fileNameToRestoreFrom), new File(fileNameToRestore));
                        System.out.println("Restored file "+fileNameToRestore+"\n"+
                                            " from "+fileNameToRestoreFrom);
                    } else if (line.startsWith("Delete")) {
                        // extract file name to delete
                        String fileNameToDelete = lines.get(i).replaceFirst("Delete\\s+", "");
                        // delete file
                        FileUtils.deleteQuietly(new File(fileNameToDelete));
                        System.out.println("Deleted file "+fileNameToDelete);
                        deleteEmptyUpperFolders(fileNameToDelete);
                        // delete file in parsed folder
                        String fileNameToDeleteInParsedFolder = fileNameToDelete.replace(srcFolder, parsedFolder);
                        FileUtils.deleteQuietly(new File(fileNameToDeleteInParsedFolder));
                        System.out.println("Deleted file "+fileNameToDeleteInParsedFolder);
                        deleteEmptyUpperFolders(fileNameToDeleteInParsedFolder);
                    } else {
                        System.out.println("Cannot parse rollback file: line "+(i+1)+" is not recognized:");
                        System.out.println(line);
                        deleteRollback = false;
                    }
                }

                if (deleteRollback) {
                    // delete rollback file, backup folder, parsed folder, patch folder
                    if (FileUtils.deleteQuietly(new File(rollbackFile))) {
                        System.out.println("Deleted file "+ rollbackFile +".");
                    } else {
                        System.out.println("Cannot delete file "+ rollbackFile +".");
                    }
                    deleteFolder(backupFolder);
                    deleteFolder(patchFolder);
                    deleteFolder(parsedOldFolder);
                    deleteFolder(parsedMergeFolder);
                    deleteFolder(parsedFolder);

                    // also delete -parsed.rollback
                    if (rollbackFile.contains("-full")) {
                        String parsedRollbackFile = rollbackFile.replace("-full","-parsed");
                        boolean rollbackDeleted = new File(parsedRollbackFile).delete();
                        if (rollbackDeleted) {
                            System.out.println("Rollback file "+parsedRollbackFile+ " was deleted");
                        } else {
                            System.out.println("Rollback file "+parsedRollbackFile+ " was not deleted");
                        }
                        boolean fullRollbackDeleted = new File(rollbackFile).delete();
                        if (fullRollbackDeleted) {
                            System.out.println("Full rollback file "+rollbackFile+ " was deleted");
                        } else {
                            System.out.println("Full rollback file "+rollbackFile+ " was not deleted");
                        }
                    }
                    System.out.println("Rollback is applied.");
                } else {
                    System.out.println("Rollback was not fully applied. Rollback file was not deleted");
                }
                System.out.println("** Finished applying rollback: "+rollbackFile);
                System.out.println("********************************************");
                System.out.println();
                return true;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return false;
    }

    private static void deleteFolder(String folder) {
        if (FileUtils.deleteQuietly(new File(folder))) {
            System.out.println("Deleted folder "+ folder +".");
        } else {
            System.out.println("Cannot delete folder "+ folder +".");
        }
    }

    private static void deleteEmptyUpperFolders(String fileNameToDelete) {
        // go to all upper folders and delete it if it is empty
        File folderToDelete = new File(fileNameToDelete).getParentFile();
        while (folderToDelete != null && folderToDelete.exists() && folderToDelete.list().length == 0) {
            FileUtils.deleteQuietly(folderToDelete);
            System.out.println("Deleted folder "+folderToDelete);
            folderToDelete = folderToDelete.getParentFile();
        }
    }


}
