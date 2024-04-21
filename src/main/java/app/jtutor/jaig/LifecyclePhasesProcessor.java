package app.jtutor.jaig;

import app.jtutor.jaig.config.GlobalConfig;
import app.jtutor.jaig.config.LocalConfig;
import org.apache.commons.io.FileUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static app.jtutor.WindowsUtil.windowsCompatiblePath;
import static app.jtutor.jaig.JAIGUseCasesProcessor.processRollback;

/**
 * Processing of the prompt:
 * PHASE 1) processing prompt (inputFile) inclusions and directives, generate the -request.txt, save it to outputFile
 * PHASE 2) send request to the AI to get the response: outputFile (request) => responseFile (response)
 * PHASE 3) create or update a patch, if response was parsed before and there are some code changes
 *             go through all files in parsed folder and create a patch for each
 * PHASE 4) parsing the results, if possible
 *             parsing is possible if number of "package ..." lines equal to number of classes and interfaces
 * PHASE 5) patch generated code if there's a .patch file found
 * PHASE 6) create backup and write parsed and patched code to SRC_FOLDER (usually src/main/java)
 * PHASE 7) create rollback file
 *
 */
public class LifecyclePhasesProcessor {

    private boolean pathMatches(String path, String pattern) {
        // Normalize and convert file separators to forward slashes for the glob pattern
        String normalizedPattern = pattern.replace(File.separator, "/");

        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + normalizedPattern);
        // glob: using glob syntax for matching
        // https://docs.oracle.com/javase/7/docs/api/java/nio/file/FileSystem.html#getPathMatcher(java.lang.String)
        return matcher.matches(Paths.get(path));
    }

    // PROCESSING prompt
    public void processPrompt(String inputFile) {
        // create new localConfig for this prompt
        LocalConfig localConfig = new LocalConfig();

        // flag that we're processing not a prompt, but the response from GPT
        boolean processingResponse = inputFile.endsWith("-response.txt");

        // setting output and response files
        String outputFile = inputFile.replace(".txt","-request.txt");
        String responseFile = inputFile.replace(".txt","-response.txt");
        boolean autoRunJavaCode = false;

        String SRC_FOLDER = GlobalConfig.INSTANCE.getSrcFolder();
        if (localConfig.getSrcFolder() != null) SRC_FOLDER = localConfig.getSrcFolder();
        String TEST_FOLDER = GlobalConfig.INSTANCE.getTestFolder();
        if (localConfig.getTestFolder() != null) TEST_FOLDER = localConfig.getTestFolder();

        String gptResponse = null;

        // PHASE 0: rollback previous results before processing prompt
        if (GlobalConfig.INSTANCE.isApplyRollback()) {
            Path inputPath = Paths.get(inputFile);
            try {
                List<String> lines = Files.readAllLines(inputPath);
                // if the prompt contains #norollback, #merge, #merge-incomplete or #patch, we should not apply rollback
                boolean applyRollback = lines.stream().noneMatch(l -> l.startsWith("#norollback") ||
                        l.startsWith("#merge") || l.startsWith("#patch"));

                if (applyRollback) {
                    String rollbackFile = inputFile.replace(".txt", "-parsed.rollback");
                    if (new File(rollbackFile).exists()) {
                        processRollback(rollbackFile, SRC_FOLDER);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        // PHASE 1: processing prompt (inputFile) inclusions and directives, generate the request, save it to outputFile
        if (!processingResponse) {
            try {
                Path inputPath = Paths.get(inputFile);
                if (!Files.exists(inputPath)) {
                    System.err.println("Input file not found: " + inputFile);
                    System.exit(1);
                }
                String promptFolder = inputPath.getParent().toString();

                List<String> lines = Files.readAllLines(inputPath);
                try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputFile), StandardCharsets.UTF_8)) {
                    for (String line : lines) {
                        boolean isRelativePath = line.startsWith("./") || line.startsWith("../");
                        boolean isPathFromContentRoot = line.startsWith("/");

                        if (isRelativePath || isPathFromContentRoot) { // file of folder inclusion - read files and include
                            String pathPrefix = ""; // this will be prepended to the line with path
                            if (isRelativePath) pathPrefix = promptFolder + "/";
                            if (isPathFromContentRoot) pathPrefix = ".";
                            // Windows does not support wildcards ** in normalize(), so we need to temporally remove it
                            line = line.replaceAll("\\*\\*", "all_files_wildcard");
                            line = line.replaceAll("\\*", "wildcard");
                            String pattern = Path.of(pathPrefix + line).normalize().toString();
                            // we restore the wildcards
                            String finalPattern = pattern.replaceAll("all_files_wildcard", "\\*\\*")
                                    .replaceAll("wildcard", "\\*");
                            Files.walkFileTree(Paths.get("."), new SimpleFileVisitor<Path>() {
                                @Override
                                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                                if (pathMatches(file.toString(), "./"+finalPattern)) {
                                    System.out.println("Included file in the request: "+file);
                                    writeToFile(file, writer);
                                }
                                return FileVisitResult.CONTINUE;
                                }
                            });

/*
                            if (line.endsWith("/**")) {
//                                line = pathPrefix + line.substring(0, line.length() - 3);
//                                if (Files.isDirectory(Paths.get(line))) {
//                                    Files.walk(Paths.get(line)).filter(Files::isRegularFile)
//                                            .forEach(p -> writeToFile(p, writer));
//                                } else {
//                                    System.err.println("ERROR: The folder does not exist: " + line);
//                                }
                            } else if (line.endsWith("/*.java")) {
                                line = pathPrefix + line.substring(0, line.length() - 6);
                                if (Files.isDirectory(Paths.get(line))) {
                                    Files.walk(Paths.get(line))
                                            .filter(p -> p.toString().endsWith(".java"))
                                            .forEach(p -> writeToFile(p, writer));
                                } else {
                                    System.err.println("ERROR: The folder does not exist: " + line);
                                }
                            } else {
                                boolean optionalInclusion = line.endsWith("?");
                                if (optionalInclusion) {
                                    line = line.substring(0, line.length() - 1);
                                }
                                Path filePath = Paths.get(pathPrefix + line);
                                if (Files.exists(filePath)) {
                                    writeToFile(filePath, writer);
                                } else if (!optionalInclusion) {
                                    System.err.println("ERROR: The file does not exist: " + line);
                                    System.exit(-1);
                                }
                            }

 */
                        } else if (line.startsWith("#temperature:")) {
                            localConfig.setTemperature(Double.valueOf(
                                    line.substring("#temperature:".length()).trim()));
                        } else if (line.contains("#src")) {
                            localConfig.setWriteResponseToSrc(true);
                        } else if (line.startsWith("#model: ")) {
                            localConfig.setModel(line.substring(8));
                        } else if (line.equals("#run")) {
                            autoRunJavaCode = true;
                        } else if (line.startsWith("#seed:")) {
                            String substring = line.substring("#seed:".length()).trim();
                            int seed = Integer.parseInt(substring);
                            localConfig.setSeed(seed);
                        } else if (line.startsWith("#test")) {
                            localConfig.setWriteResponseToTest(true);
                        } else if (line.startsWith("#save-to:")) {
                            localConfig.setSaveResponseTo(line.substring("#save-to:".length()).trim());
                        } else if (line.startsWith("#merge-incomplete:")) {
                            String[] mergeIncompleteArr =
                                    line.substring("#merge-incomplete:".length()).split(",");
                            // apply trim() to every element put it to the list
                            localConfig.setMergeIncompleteList(
                                    Arrays.stream(mergeIncompleteArr).map(String::trim)
                                        .collect(Collectors.toList()));
                            // for #merge-incomplete we should not apply rollback
                            GlobalConfig.INSTANCE.setApplyRollback(false);
                        } else if (line.startsWith("#merge-incomplete")) {
                            localConfig.setMergeIncomplete(true);
                        } else if (line.startsWith("#nomerge")) {
                            localConfig.setNoMerge(true);
                        } else if (line.startsWith("#merge")) {
                            localConfig.setCreateMerge(true);
                            // for #merge we should not apply rollback
                            GlobalConfig.INSTANCE.setApplyRollback(false);
                        } else if (line.startsWith("#patch")) {
                            localConfig.setCreatePatch(true);
                            // for #patch we should not apply rollback
                            GlobalConfig.INSTANCE.setApplyRollback(false);
                        } else if (line.startsWith("#apply-patch")) {
                            localConfig.setApplyPatch(true);
                        } else if (line.startsWith("#apply-merge")) {
                            localConfig.setApplyMerge(true);
                        } else if (line.startsWith("#rollback")) {
                            localConfig.setApplyRollback(true);
                        } else if (line.startsWith("#norollback")) {
                            localConfig.setApplyRollback(false);
                            GlobalConfig.INSTANCE.setApplyRollback(false);
                        } else if (line.startsWith("#")) {
                            // this is a comment in prompt, not include
                        } else { // just a regular line - include it
                            writer.write(line);
                            writer.newLine();
                        }
                    }
                }

            } catch (IOException e) {
                System.err.println("An error occurred during prompt processing: " + e.getMessage());
                return;
            }
            System.out.println(outputFile + " has been generated.");
        }

        // PHASE 2: send request to the AI to get the response: outputFile (request) => responseFile (response)
        if (!processingResponse && GlobalConfig.INSTANCE.isGenerateResponse()) {
            GptRequestRunner gptRequestRunner = new GptRequestRunner(localConfig);
            gptResponse = gptRequestRunner.executeAndSaveGptRequest(outputFile, responseFile);
            System.out.println("Response from ChatGPT is written to " + responseFile);

            if (autoRunJavaCode && gptResponse != null) {
                CodeExecutor.executeJavaChatResponse(gptResponse);
            }
        }

        // PHASE 3: create or update a patch, if response was parsed before and there are some code changes
        // go through all files in parsed folder and create a patch for each file
        if (GlobalConfig.INSTANCE.isCreatePatch() || localConfig.isCreatePatch()) {
            // create patches for all files in -parsed folder
            System.out.println("Input file: "+inputFile);
            String parsedFolder = inputFile.replace("-response.txt", ".txt")
                    .replace(".txt", "-parsed");
            System.out.println("Parsed folder: "+parsedFolder);
            String originalFolder = null; // this is a folder for which we create a patch
            if (new File(parsedFolder).exists()) originalFolder = parsedFolder;
            System.out.println("Original folder: "+originalFolder);
            if (originalFolder != null) { // we have -parsed or -patched
                System.out.println("\n*********** JAIG Patch Generator ***********");
                String patchName = parsedFolder + ".patch";
                if (localConfig.getSaveResponseTo() != null) {
                    String saveResponseTo = localConfig.getSaveResponseTo();
                    if (saveResponseTo.startsWith("/"))
                        saveResponseTo = saveResponseTo.substring(1);
                    PatchGenerator.INSTANCE.createSaveToFilePatch(originalFolder,
                            saveResponseTo, patchName);
                } else {
                    PatchGenerator.INSTANCE.createPatches(originalFolder, SRC_FOLDER, patchName);
                }
            }
        }

        // if we get -response.txt as input, read gptResponse from file
        if (processingResponse) {
            try {
                // read response from file inputFile
                gptResponse = FileUtils.readFileToString(new File(inputFile), StandardCharsets.UTF_8);
                // now inputFile is the prompt file
                inputFile = inputFile.replace("-response.txt", ".txt");
            } catch (IOException e) {
                System.err.println("ERROR: cannot read response from file "+inputFile);
                return;
            }
        }

        // PHASE 4: parsing the results, if possible
        // parsing is possible if number of "package ..." lines equal to number of classes and interfaces
        boolean autoParseIsPossible = checkGPTResponseForAutoParse(gptResponse);

        String parsedCodeFolder = null;
        if (GlobalConfig.INSTANCE.isParseJavaCode() || localConfig.isParseJavaCode()) {
            parsedCodeFolder = parseCode(inputFile, autoParseIsPossible, localConfig);
        } else {
            if (autoParseIsPossible) {
                System.out.println("Automatic parsing is possible for this response.");
                System.out.println("However, automatic response parsing is turned off in configuration.");
                System.out.println("Turn it on if you need the response to be parsed.");
            }
            System.out.println("The work of JAIG is completed.");
        }

        // we proceed to PHASES 5 & 6 only if code was parsed (parsedCodeFolder != null)

        String patchedCodeFolder = null;

        // PHASE 5.1: patch generated code if there's a .patch file
        if (parsedCodeFolder != null && (GlobalConfig.INSTANCE.isApplyPatch() || localConfig.isApplyPatch())) {
            String patchFilePath = new File(parsedCodeFolder) + ".patch";
            if (new File(patchFilePath).exists()) {
                patchedCodeFolder = parsedCodeFolder.replace("-parsed", "-patched");
                System.out.println("\n*********** JAIG Code AutoPatcher ***********");
                System.out.println("We have found patch "+patchFilePath+", it will be applied");
                try {
                    FileUtils.copyDirectory(new File(parsedCodeFolder), new File(patchedCodeFolder));
                } catch (IOException e) {
                    System.err.println("ERROR creating patch: cannot copy directory " + parsedCodeFolder + " to directory " + patchedCodeFolder);
                    return;
                }
                PatchApplier.INSTANCE.patchFolder(parsedCodeFolder, patchedCodeFolder, patchFilePath);
            }
        }

        // prompts to automatically merge the code
        List<String> mergePromptFiles = new ArrayList<>();

        // PHASE 5.2: merge code
        if (parsedCodeFolder != null &&
                (GlobalConfig.INSTANCE.isCreateMerge()
                        || localConfig.isCreateMerge()
                        || localConfig.isMergeIncomplete()
                        || localConfig.getMergeIncompleteList() != null) &&
                !localConfig.isNoMerge()) {
            Path parsedFilePath = Path.of(parsedCodeFolder);
            Path promptFolderPath = parsedFilePath.getParent();
            Path parsedOldFilePath = Path.of(parsedCodeFolder + "-old");
            String finalSRC_FOLDER = SRC_FOLDER;

            // Use case 1 (mergePrompt):
            // we have changes in source code + changes in generated code => we need to merge it
            // Use case 2 (mergeIncompletePrompt):
            // AI generated not complete code, but only updates => we need to merge it with existing code
            if (Files.exists(parsedOldFilePath)) { // case 1: use prompt merge3 defined in JAIG.yaml
                System.out.println("\n*********** Creating Merge of 3 files: old, new and updated ***********");
                System.out.println("We have changes in source code + changes in generated code => we need to merge it");

                try {
                    String finalParsedCodeFolder = parsedCodeFolder;
                    Files.walkFileTree(parsedOldFilePath, new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            if (!localConfig.isMergeIncomplete() &&
                                    !isInMergeList(localConfig, file)) return FileVisitResult.CONTINUE;
                            try {
                                // read contents of the file to List<String>
                                List<String> parsedOldFileLines = Files.readAllLines(file);
                                // get the path to the file in parsed folder
                                String parsedFile = file.toString().replace("-old", "");

                                // read contents of the file to List<String>
                                Path parsedFilePath = Path.of(parsedFile);
                                // TODO: check if file parsedFilePath exists
                                //List<String> parsedFileLines = Files.readAllLines(parsedFilePath);
                                // check if we have the changes
//                                // we have to ignore first 5 lines in the file which contain the header
                                parsedOldFileLines = parsedOldFileLines.subList(5, parsedOldFileLines.size());

                                // TODO: if there were no changes in the generated file, we don't need to merge
                                // TODO: and we should not overwrite the changes
//                                parsedFileLines = parsedFileLines.subList(5, parsedFileLines.size());
//                                if (parsedOldFileLines.equals(parsedFileLines)) {
//                                    System.out.println("No merging needed: no changes found in " + parsedFile);
//                                    return FileVisitResult.CONTINUE;
//                                }
                                // check if we have the changes in the source code in src/main/java
                                Path srcPath = Path.of(finalSRC_FOLDER+"/"+parsedOldFilePath.relativize(file));
                                // also check save-to and merge with it
                                if (localConfig.getSaveResponseTo() != null) {
                                    String saveResponseTo = localConfig.getSaveResponseTo();
                                    if (saveResponseTo.startsWith("/")) saveResponseTo = saveResponseTo.substring(1);
                                    srcPath = Path.of(saveResponseTo);
                                }
                                // if file srcPath exists
                                if (Files.exists(srcPath)) {
                                    // read contents of the file to List<String>
                                    List<String> srcFileLines = Files.readAllLines(srcPath);

                                    // check if we have the changes
                                    // we have to ignore first 5 lines in the file which contain the header
                                    srcFileLines = srcFileLines.subList(5, srcFileLines.size());
                                    if (parsedOldFileLines.equals(srcFileLines)) {
                                        System.out.println("No merging needed: no changes found in " + srcPath);
                                        return FileVisitResult.CONTINUE;
                                    }
                                } else {
                                    System.out.println("No merging needed: file " + srcPath+ " not found");
                                    return FileVisitResult.CONTINUE;
                                }

                                // we have changes - merge them
                                String mergePrompt = GlobalConfig.INSTANCE.getMergePrompt();
                                String oldParsedFileName = "../../" + promptFolderPath.relativize(file);
                                String parsedFileName = "../../" + promptFolderPath.relativize(parsedFilePath);
                                String backupPath = parsedFileName.replace("-parsed", "-backup");

                                mergePrompt = mergePrompt.replace("[[old]]", oldParsedFileName);
                                mergePrompt = mergePrompt.replace("[[new]]", parsedFileName);
                                mergePrompt = mergePrompt.replace("[[updated]]", backupPath);
                                if (localConfig.getSaveResponseTo() != null) {
                                    mergePrompt = mergePrompt + "\n\n" +
                                            "#save-to: " + localConfig.getSaveResponseTo();
                                }
                                generateMergeFolder(finalParsedCodeFolder, file, mergePrompt, mergePromptFiles);
                            } catch (IOException e) {
                                System.err.println("MERGE ERROR: cannot read file " + file);
                                e.printStackTrace();
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else if (localConfig.isMergeIncomplete() || localConfig.getMergeIncompleteList() != null) {
                // case 2: use prompt mergeIncomplete defined in JAIG.yaml
                System.out.println("\n*********** Creating Merge of 2 files: old and new ***********");
                System.out.println("AI generated not complete code, but only updates => we need to merge it with existing code");
                try {
                    String finalParsedCodeFolder = parsedCodeFolder;
                    Files.walkFileTree(parsedFilePath, new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            if (!localConfig.isMergeIncomplete() &&
                                    !isInMergeList(localConfig, file)) return FileVisitResult.CONTINUE;
                            try {
                                String mergeIncompletePrompt = GlobalConfig.INSTANCE.getMergeIncompletePrompt();
                                String parsedFileName = "../../" + promptFolderPath.relativize(file);
                                String backupPath = parsedFileName.replace("-parsed", "-backup");
                                mergeIncompletePrompt = mergeIncompletePrompt.replace("[[old]]", backupPath);
                                mergeIncompletePrompt = mergeIncompletePrompt.replace("[[new]]", parsedFileName);
                                generateMergeFolder(finalParsedCodeFolder, file, mergeIncompletePrompt, mergePromptFiles);
                            } catch (IOException e) {
                                System.err.println("MERGE ERROR: cannot read file " + file);
                                e.printStackTrace();
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            System.out.println();
        }

        List<String> rollbackLog = new ArrayList<>();

        // PHASE 6: create backup and write parsed and patched code to SRC_FOLDER
        if (writeResponseToSrc(parsedCodeFolder, localConfig, 
                patchedCodeFolder, TEST_FOLDER, SRC_FOLDER, rollbackLog)) return;

        // PHASE 7: create rollback file
        if (!rollbackLog.isEmpty() &&
                (GlobalConfig.INSTANCE.isCreateRollback() || localConfig.isCreateRollback())) {
            String rollbackFileName = inputFile.replace(".txt","-parsed.rollback");
            try {
                if (new File(rollbackFileName).exists()) {
                    List<String> rollbackLines = Files.readAllLines(Path.of(rollbackFileName));
                    // compare rollbackLines with rollbackLog
                    boolean rollbackLinesEqRollbackLog = true;
                    if (rollbackLines.size() != rollbackLog.size()) {
                        rollbackLinesEqRollbackLog = false;
                    } else {
                        for (int i = 0; i < rollbackLines.size(); i++) {
                            if (!rollbackLog.get(i).equals(rollbackLines.get(i))) {
                                rollbackLinesEqRollbackLog = false;
                                break;
                            }
                        }
                    }
                    String initialRollbackFileName = rollbackFileName.replace("-parsed", "-full");
                    // if -full.rollback didn't exist before, and there are some changes in rollbacks - create it
                    if (!rollbackLinesEqRollbackLog && !new File(initialRollbackFileName).exists()) {
                        new File(rollbackFileName).renameTo(new File(initialRollbackFileName));
                        System.out.println("Rollback file " + rollbackFileName + " is renamed to " + initialRollbackFileName);
                    }
                }
            } catch (IOException e) {
                System.err.println("ERROR: cannot read rollback file to "+rollbackFileName);
                e.printStackTrace();
            }
            try {
                FileUtils.writeLines(new File(rollbackFileName),
                        StandardCharsets.UTF_8.toString(), rollbackLog);
                System.out.println("Rollback file is written to "+rollbackFileName);
            } catch (IOException e) {
                System.err.println("ERROR: cannot write rollback file to "+rollbackFileName);
                e.printStackTrace();
            }
        }

        // PHASE 8: apply merge prompts
        if ((GlobalConfig.INSTANCE.isApplyMerge() || localConfig.isApplyMerge()) && !mergePromptFiles.isEmpty()) {
            System.out.println("\n*********** JAIG Merge Prompts Applier ***********");
            for (String mergePrompt: mergePromptFiles) {
                System.out.println("Applying prompt "+mergePrompt);
                processPrompt(mergePrompt);
            }
        }

    }

    private boolean writeResponseToSrc(String parsedCodeFolder, LocalConfig localConfig, String patchedCodeFolder, String TEST_FOLDER, String SRC_FOLDER, List<String> rollbackLog) {
        if (parsedCodeFolder != null &&
                (GlobalConfig.INSTANCE.isWriteResponseToSrc() || localConfig.isWriteResponseToSrc())) {

            // TODO: -backup-initial folder
//            String backupFolder = parsedCodeFolder.replace("-parsed", "-backup");
//            String fullRollbackFile = parsedCodeFolder.replace("-parsed", "full.rollback");
//            if (!Files.exists(Path.of(fullRollbackFile))) {
//                String backupFolderInitial = parsedCodeFolder.replace("-parsed", "-backup-initial");
//                if (Files.exists(Path.of(backupFolder))) {
//                    // rename -backup folder to -backup-initial
//                    new File(backupFolder).renameTo(new File(backupFolderInitial));
//                }
//            }

            // if we have patched code, use it, otherwise use parsed code
            String destinationFolder = parsedCodeFolder;
            if (patchedCodeFolder != null) destinationFolder = patchedCodeFolder;

            // walk through all files in parsedCodeFolder and copy files from SRC_FOLDER to backup
            Path generatedSourcesFolderPath = Path.of(destinationFolder);
            // Create a backup for every file and copy file to src folder
            try {
                // check if we should write response to test (#test directive is used in prompt)
                // otherwise, write response to SRC_FOLDER (usually src/main/java)
                String targetFolder = localConfig.isWriteResponseToTest()? TEST_FOLDER : SRC_FOLDER;
                // if save-to: is used, write to the content root
                if (localConfig.getSaveResponseTo() != null) {
                    targetFolder = ".";
                }
                String final_targetFolder = windowsCompatiblePath(targetFolder);
                // Windows compatible path
                destinationFolder = windowsCompatiblePath(destinationFolder);
                System.out.println("Writing files from folder "+destinationFolder+" to folder "+targetFolder);

                // traverse -parsed folder to find all files generated from response
                Files.walkFileTree(generatedSourcesFolderPath, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        // generated sources are stored in -parsed folder
                        String generatedFolderPosfix = "-parsed";
                        if (file.toString().contains("-patched")) generatedFolderPosfix = "-patched";

                        // create backup for the file in src
                        if (GlobalConfig.INSTANCE.isCreateSrcBackup() || localConfig.isCreateSrcBackup()) {
                            // get the path to the file in src folder
                            String srcFile = file.toString().replaceFirst(
                                    ".*" + generatedFolderPosfix,
                                    final_targetFolder);
                            srcFile = windowsCompatiblePath(srcFile);
                            // this is a new file - no need to create a backup
                            if (!Files.exists(Path.of(srcFile))) {
                                rollbackLog.add("Delete  "+srcFile);
                            } else { // file exists - creating a backup
                                // path to the backup folder
                                String filePath = windowsCompatiblePath(file.toString());
                                String generatedFile = filePath.replaceFirst(
                                        "(.*)/(.*)" + generatedFolderPosfix,
                                        "$1/$2-backup");

                                try {
                                    // compare contents of the files
                                    String srcFileContent = FileUtils.readFileToString(
                                            new File(srcFile), StandardCharsets.UTF_8);
                                    // read content of the file
                                    String targetFileContent = FileUtils.readFileToString(
                                            new File(file.toString()), StandardCharsets.UTF_8);
                                    // if files are equal, no need to create backup
                                    if (srcFileContent.equals(targetFileContent)) {
                                        System.out.println(
                                                "No backup created: " + srcFile + "\n" +
                                                        "               and " + file + " are identical");
                                        return FileVisitResult.CONTINUE;
                                    }
                                    // files are different - we need a backup
                                    // create backup folder if it doesn't exist
                                    if (!Files.exists(Path.of(generatedFile))) {
                                        Files.createDirectories(Path.of(generatedFile).getParent());
                                    }

                                    // copy src file to backup folder
                                    Files.copy(Path.of(srcFile), Path.of(generatedFile), StandardCopyOption.REPLACE_EXISTING);
                                    System.out.println(
                                            "Created backup: copied " + srcFile + "\n" +
                                                    "                to " + generatedFile);
                                    rollbackLog.add("Restore " + srcFile + "\n" +
                                            "from    " + generatedFile);
                                } catch (IOException e) {
                                    System.out.println("WARNING: wasn't able to copy " + srcFile + " to " + generatedFile);
                                    e.printStackTrace();
                                    return FileVisitResult.CONTINUE;
                                }
                            }
                        }

                        // copy generated file to final_targetFolder folder
                        String srcFolderFilePath = file.toString().replaceFirst(
                                ".*"+generatedFolderPosfix,
                                final_targetFolder);
                        try {
                            if (!Files.exists(Path.of(srcFolderFilePath))) {
                                Files.createDirectories(Path.of(srcFolderFilePath));
                            }
                            Files.copy(file, Path.of(srcFolderFilePath), StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            System.err.println("ERROR: wasn't able to copy "+file+" to "+srcFolderFilePath);
                            return FileVisitResult.TERMINATE;
                        }
                        return FileVisitResult.CONTINUE;
                    }

                });
            } catch (IOException e) {
                System.err.println("ERROR: wasn't able to go through all files in folder "+generatedSourcesFolderPath);
                return true;
            }
        }
        return false;
    }

    private static boolean isInMergeList(LocalConfig localConfig, Path file) {
        if (localConfig.getMergeIncompleteList() != null) {
            String fileNameNoExtension = file.getFileName().toString()
                    .replaceFirst("[.][^.]+$", "");
            return localConfig.getMergeIncompleteList().contains(fileNameNoExtension);
        }
        // if merge list is not defined, merge all files
        return true;
    }

    /**
     * Create folder -merge and save merge prompt to the file in merged folder
     *
     * @param finalParsedCodeFolder - folder with parsed code
     * @param file - file in parsed folder
     * @param mergePrompt - merge prompt
     * @param mergePromptFiles - list of merge prompt files
     * @throws IOException
     */
    private static void generateMergeFolder(String finalParsedCodeFolder, Path file,
                                            String mergePrompt, List<String> mergePromptFiles) throws IOException {
        // create folder -merge
        String mergeFolder = finalParsedCodeFolder.replace("-parsed", "-merge");
        // create merged folder if it doesn't exist
        String mergeFileFolder = mergeFolder + "/" + file.getFileName().toString();
        // create folder mergeFileFolder in mergeFolder
        Files.createDirectories(Path.of(mergeFileFolder));
        System.out.println("Created merge folder: " + mergeFileFolder);

        String mergePromptFile = mergeFileFolder + "/" + file.getFileName() + ".merge.txt";
        // save merge prompt to the file in merged folder
        FileUtils.writeStringToFile(
                new File(mergePromptFile),
                mergePrompt, StandardCharsets.UTF_8);
        System.out.println("Created merge prompt: " + mergePromptFile);
        mergePromptFiles.add(mergePromptFile);
    }

    /**
     * Parse code if possible. If parsing is not possible, return null.
     * If parsing is possible, return the folder with parsed code.
     * If save-to: is used, save the parsed response to the file.
     *
     * @param inputFile - file with the initial request
     * @param autoParseIsPossible - true if parsing is possible (found packages)
     * @param localConfig - local config for this request
     * @return folder with parsed code or null if parsing is not possible
     */
    private String parseCode(String inputFile, boolean autoParseIsPossible, LocalConfig localConfig) {
        String responseFile = inputFile.replace(".txt","-response.txt");
        String parsedCodeFolder = null;
        if (autoParseIsPossible) {
            System.out.println("\n********** JAIG Code Parser **********");
            System.out.println("The generated code contains Java packages and will be automatically parsed...");
            stopAutoParseByUser();
            String parsedFolder = inputFile.replace(".txt","")+"-parsed";
            prepareParsedFolders(parsedFolder, localConfig);
            // If not interrupted, we continue with the next phase...
            parsedCodeFolder = new CodeParser().parse(responseFile, parsedFolder);
            // parsedCodeFolder is null if parsing failed, it is used
            // to skip PHASES 5 & 6
        } else if (localConfig.getSaveResponseTo() != null) { // save-to: is used
            String parsedFolder = inputFile.replace(".txt","")+"-parsed";
            prepareParsedFolders(parsedFolder, localConfig);
            try {
                FileUtils.copyFile(
                        new File(responseFile),
                        new File(parsedFolder+"/"+ localConfig.getSaveResponseTo()));
                // parsedCodeFolder is null if parsing failed, otherwise we set it to parsedFolder
                parsedCodeFolder = parsedFolder;
                System.out.println("Response is saved to file: "+ localConfig.getSaveResponseTo());
            } catch (IOException e) {
                System.err.println("ERROR: cannot save response to "+ localConfig.getSaveResponseTo());
            }
        } else {
            System.out.println("This response cannot be parsed.");
            System.out.println("If you need the response to be parsed, it should contain packages for each class or interface.");
        }
        return parsedCodeFolder;
    }

    public void stopAutoParseByUser() {
        System.out.println("You have "+GlobalConfig.INSTANCE.getPreParseCountdown()+" seconds to prevent automatic parsing.\n" +
                "Press Ctrl-C or STOP the execution to prevent the parsing.");
        Thread countdownThread = new Thread(() -> {
            int frame = GlobalConfig.INSTANCE.getPreParseCountdown();
            while (frame>=0) {
                String loadingText = "Parsing countdown... " + frame;
                System.out.print("\r" + loadingText);
                frame--;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        countdownThread.start();
        try {
            countdownThread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        System.out.println("\n\nYou didn't stop me! Parsing the code...");
    }

    private void writeToFile(Path filePath, BufferedWriter writer) {
        try {
            List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line.startsWith("/***") || line.startsWith("***")) {
                    // this is a comment in code, bypass it
                    continue;
                }
                writer.write(line);
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("An error occurred while reading file: " + filePath);
        }
    }


    /**
     * Check that there are equal number of lines in gptResponse
     * which start from "package " and end on ";"
     * and number of lines which start with "public class" and end with "{".
     *
     * @param gptResponse response with the code received from Chat GPT
     * @return true if we should automatically parse this file
     */
    private boolean checkGPTResponseForAutoParse(String gptResponse) {
        String[] lines = gptResponse.split("\n");

        // Pattern for package
        Pattern patternPackage = Pattern.compile("^package .*;$");
        int countPackage = 0;

        // Get patterns from the global config
        List<String> javaFileNameRegexps = GlobalConfig.INSTANCE.getJavaFileNameRegexps();
        Map<Pattern, Integer> patternCounts = new HashMap<>();

        // Initialize patterns with count 0
        for (String regex : javaFileNameRegexps) {
            patternCounts.put(Pattern.compile("^" + regex + ".*\\{$"), 0);
        }

        for (String line : lines) {
            // Check for package pattern match
            if (patternPackage.matcher(line).matches()) {
                countPackage++;
            }

            // Check for other pattern matches
            for (Map.Entry<Pattern, Integer> entry : patternCounts.entrySet()) {
                if (entry.getKey().matcher(line).matches()) {
                    patternCounts.put(entry.getKey(), entry.getValue() + 1);
                }
            }
        }

        int totalOtherPatternCounts = patternCounts.values().stream().mapToInt(Integer::intValue).sum();

        return countPackage > 0 && countPackage == totalOtherPatternCounts;
    }


    /**
     * If we already had a folder "-parsed-old", we remove it
     * If we already had a folder "-parsed", we need to rename it to "-parsed-old"
     *
     * @param parsedFolder - folder "prompt-parsed"
     */
    private void prepareParsedFolders(String parsedFolder, LocalConfig localConfig) {
        // if we already had a folder "-parsed-old", we remove it
        // if we already had a folder "-parsed", we need to rename it to "-parsed-old"
        File parsedFolderFile = new File(parsedFolder);
        if (parsedFolderFile.exists()) {
            File parsedFolderFileOld = new File(parsedFolder +"-old");
            if (parsedFolderFileOld.exists()) {
                try {
                    FileUtils.deleteDirectory(parsedFolderFileOld);
                    System.out.println("Deleted folder "+parsedFolderFileOld);
                } catch (IOException e) {
                    System.out.println("Unable to delete folder "+parsedFolderFileOld);
                }
            }
            // we rename the folder "-parsed" to "-parsed-old"
            // we do it to keep the previous version of the parsed code
            // only if we merge the code
            boolean doWeNeedParsedOld = !localConfig.isNoMerge() &&
                    (GlobalConfig.INSTANCE.isCreateMerge() || localConfig.isCreateMerge()) &&
                    !GlobalConfig.INSTANCE.isApplyRollback() && !localConfig.isApplyRollback();
            // if we don't need to merge the code, we don't need to keep the old version
            // if we automatically rollback the code, we don't need it either
            if (doWeNeedParsedOld) {
                if (parsedFolderFile.renameTo(parsedFolderFileOld)) {
                    System.out.println("Renamed folder " + parsedFolderFile + " to " + parsedFolderFileOld);
                } else {
                    System.out.println("Unable to rename folder " + parsedFolderFile + " to " + parsedFolderFileOld);
                }
            }
        }
        // create folder "prompt-parsed"
        if (parsedFolderFile.mkdir()) {
            System.out.println("Created folder " + parsedFolderFile);
        } else {
            System.out.println("Unable to create folder " + parsedFolderFile);
        }

    }

}
