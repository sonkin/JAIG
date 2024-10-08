package app.jtutor.jaig;

import app.jtutor.jaig.config.GlobalConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class RefactoringProcessor {
    public void process(String[] args) {
        System.out.println("\n************* JAIG Refactoring Mode *************");
        if (args.length > 2) {
            int linesFrom = Integer.parseInt(args[1]);
            int linesTo = Integer.parseInt(args[2]);
            if (linesFrom == linesTo) {
                noFragmentSelected();
            }
            if (Integer.parseInt(args[3]) == 1) linesTo--;
            processFragment(args[0], linesFrom, linesTo);
        } else {
            processFile(args[0]);
        }
    }

    private void processFragment(String fileName, int linesFrom, int linesTo) {
        try {
            Scanner scanner = new Scanner(System.in);
            Path filePath = Path.of(fileName);

            List<String> lines = Files.lines(filePath).toList();

            String codeFragment = IntStream.range(linesFrom - 1, linesTo)
                    .mapToObj(lines::get)
                    .collect(Collectors.joining("\n"));

            System.out.println("Your code fragment is:");
            System.out.println(codeFragment);

            GptRequestRunner gptRequestRunner = new GptRequestRunner();
            StringBuilder fullPrompt = new StringBuilder();
            StringBuilder dialog = new StringBuilder();
            String lastResponse = null;

            // prepare file name for the dialog
            String dialogFileName = prepareDialogFileName(filePath);
            // create folder
            Files.createDirectories(Path.of(dialogFileName).getParent());

            while (true) {
                System.out.println();
                displayCommands(lastResponse);

                Map<String, String> promptsLibrary = GlobalConfig.INSTANCE.getPromptsLibrary();
                String prompt = readPrompt(scanner, promptsLibrary);

                if (prompt.isEmpty()) {
                    System.out.println("Finishing the conversation...");
                    break;
                }
                if (prompt.equals("1")) {
                    displayPromptsLibrary(promptsLibrary);
                    continue;
                }
                if (prompt.equals("2") && lastResponse != null) {
                    replaceCodeFragment(fileName, lines, linesFrom, linesTo, codeFragment, lastResponse);
                    break;
                }

                if (lastResponse == null) {
                    // Add the previous requests and responses to the current request
                    appendInitialCodeFragment(dialog, fullPrompt, codeFragment);
                }
                dialog.append("\n\n************************* PROMPT: **************************\n\n").append(prompt);
                fullPrompt.append(prompt);

                lastResponse = gptRequestRunner.gptRequest(fullPrompt.toString());

                // Add the previous requests and responses to the next request
                fullPrompt.append("\n\n").append(lastResponse).append("\n\n");
                dialog.append("\n\n************************ RESPONSE: *************************\n\n").append(lastResponse);

                // write the dialog to the file
                Files.writeString(Path.of(dialogFileName), dialog);
            }

            scanner.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void processFile(String fileName) {
        try {
            Scanner scanner = new Scanner(System.in);
            Path filePath = Path.of(fileName);
            List<String> lines = Files.lines(filePath).toList();

            String codeFragment = String.join("\n", lines);

            System.out.println("Your code fragment is:");
            System.out.println(codeFragment);

            GptRequestRunner gptRequestRunner = new GptRequestRunner();
            StringBuilder fullPrompt = new StringBuilder();
            StringBuilder dialog = new StringBuilder();
            String lastResponse = null;

            // prepare file name for the dialog
            String dialogFileName = prepareDialogFileName(filePath);
            // create folder
            Files.createDirectories(Path.of(dialogFileName).getParent());

            while (true) {
                System.out.println();
                displayCommands(lastResponse);

                Map<String, String> promptsLibrary = GlobalConfig.INSTANCE.getPromptsLibrary();
                String prompt = readPrompt(scanner, promptsLibrary);

                if (prompt.isEmpty()) {
                    System.out.println("Finishing the conversation...");
                    break;
                }
                if (prompt.equals("1")) {
                    displayPromptsLibrary(promptsLibrary);
                    continue;
                }
                if (prompt.equals("2") && lastResponse != null) {
                    replaceFile(fileName, codeFragment, lastResponse);
                    break;
                }

                if (lastResponse == null) {
                    // Add the previous requests and responses to the current request
                    appendInitialCodeFragment(dialog, fullPrompt, codeFragment);
                }
                dialog.append("\n\n************************* PROMPT: **************************\n\n")
                        .append(prompt);

                fullPrompt.append(prompt);

                lastResponse = gptRequestRunner.gptRequest(fullPrompt.toString());

                // Add the previous requests and responses to the next request
                fullPrompt.append("\n\n").append(lastResponse).append("\n\n");

                dialog.append("\n\n************************ RESPONSE: *************************\n\n")
                        .append(lastResponse);

                // write the dialog to the file
                Files.writeString(Path.of(dialogFileName), dialog);
            }

            scanner.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void noFragmentSelected() {
        System.out.println("You should select at least one line of code");
        System.out.println("    If you selected lines, but still see this message,");
        System.out.println("    probably you are not focused on the code editor window");
        System.exit(1);
    }


    private String readPrompt(Scanner scanner, Map<String, String> promptsLibrary) {
        StringBuilder inputText = new StringBuilder();

        int lineNumber = 0;

        while (lineNumber < 1) {
            String line = scanner.nextLine();

            // Commands and prompt library processing

            // if line is a digit, just return it
            if (line.matches("\\d+")) {
                return line;
            } else if (promptsLibrary.containsKey(line)) {
                return promptsLibrary.get(line);
            }

            inputText.append(line).append("\n");

            // Check if the input line is empty (contains only '\n')
            if (line.trim().isEmpty()) {
                lineNumber++;
            } else {
                lineNumber = 0;
            }
        }

        // Display the collected input (excluding the double newline)
        return inputText.toString().trim();
    }

    private void displayCommands(String lastResponse) {
        System.out.println("Commands (enter command number to run it):");
        System.out.println("1) show prompts library (" + GlobalConfig.INSTANCE.getPromptsLibraryShortHint() + ")");
        if (lastResponse != null) {
            System.out.println("2) replace the code fragment with the last response");
        }
        System.out.println("Enter PROMPT (double ENTER to send it) or enter nothing to finish:");
    }

    private void displayPromptsLibrary(Map<String, String> promptsLibrary) {
        System.out.println("Prompts library:");
        for (String promptName : promptsLibrary.keySet()) {
            // format prompts to align values on the same level
            String formattedPromptName = String.format("%-12s", promptName);
            System.out.println(formattedPromptName + promptsLibrary.get(promptName));
        }
    }

    private void replaceCodeFragment(String fileName, List<String> lines, int linesFrom, int linesTo, String codeFragment, String lastResponse) throws IOException {
        // get the whitespace from the first line of the code fragment
        String firstLine = codeFragment.split("\n")[0];
        String whitespace = firstLine.substring(0, firstLine.indexOf(firstLine.trim()));

        // Replace lines from linesFrom to linesTo with the lastResponse
        List<String> updatedLines = processLastResponse(lastResponse, whitespace);

        // write lines before selection to the file
        updatedLines.addAll(0, lines.subList(0, linesFrom - 1));

        // write lines after selection to the file
        updatedLines.addAll(lines.subList(linesTo, lines.size()));
        // Write the updated lines to the file
        Files.write(Path.of(fileName), updatedLines);
    }

    private void replaceFile(String fileName, String codeFragment, String lastResponse) throws IOException {
        // get the whitespace from the first line of the code fragment
        String firstLine = codeFragment.split("\n")[0];
        String whitespace = firstLine.substring(0, firstLine.indexOf(firstLine.trim()));

        // Replace lines from the File with the lastResponse
        List<String> updatedLines = processLastResponse(lastResponse, whitespace);

        // Write the updated lines to the file
        Files.write(Path.of(fileName), updatedLines);
    }

    private boolean isCodeLine(String line) {
        return line.startsWith("//") || line.endsWith("{") || line.endsWith(";") || line.endsWith("/*") ||
                line.contains(" = ") || line.endsWith("/**") || line.startsWith("@");
    }

    private boolean isCodeContinued(String nextLine) {
        return nextLine.endsWith("{") || nextLine.startsWith("//") ||
                nextLine.contains(" = ") || nextLine.endsWith(";") ||
                nextLine.startsWith("/*") || nextLine.startsWith("@") ||
                nextLine.startsWith("*") || nextLine.endsWith(",") ||
                nextLine.endsWith("||") || nextLine.endsWith("&&") ||
                nextLine.endsWith(")") || nextLine.endsWith("(") ||
                nextLine.endsWith("+") || nextLine.endsWith("->") ||
                nextLine.contains(" != ") || nextLine.endsWith("\"");
    }

    private void appendInitialCodeFragment(StringBuilder dialog, StringBuilder fullPrompt, String codeFragment) {
        dialog.append("********************** CODE FRAGMENT: **********************\n\n")
                .append(codeFragment).append("\n\n");
        fullPrompt.append("This is the code fragment:\n\n")
                .append(codeFragment).append("\n\n");
    }

    private String prepareDialogFileName(Path filePath) {
        String dialogFileName = "ai_dialogs/" + filePath.getFileName() + "/";
        // append the current date and time as yy_mm_dd_hh_mm
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yy_MM_dd_HH_mm");
        dialogFileName += java.time.LocalDateTime.now().format(formatter);
        dialogFileName += ".txt";
        return dialogFileName;
    }

    private List<String> processLastResponse(String lastResponse, String whitespace) {
        List<String> updatedLines = new ArrayList<>();
        String[] lastResponseLines = lastResponse.split("\n");
        // add the whitespace to the last response lines - the same as in the code fragment
        for (int i = 0; i < lastResponseLines.length; i++) {
            if (!lastResponseLines[i].startsWith("```")) {
                lastResponseLines[i] = whitespace + lastResponseLines[i];
            }
        }

        // extract the code fragment from the last response
        int startCodeLine = 0;
        // this is the safest way to detect the end of the code fragment
        boolean codeWrappedInBackticks = false;
        while (startCodeLine < lastResponseLines.length) {
            String line = lastResponseLines[startCodeLine].trim();
            if (line.startsWith("```")) {
                codeWrappedInBackticks = true;
                break;
            }
            if (isCodeLine(line)) {
                break;
            }
            startCodeLine++; // skip the lines which are not Java code
        }

        int countQuotes = 0;
        for (int i = startCodeLine; i < lastResponseLines.length; i++) {
            String line = lastResponseLines[i];
            if (line.contains("{")) countQuotes++;
            if (line.contains("}")) countQuotes--;
            updatedLines.add(lastResponseLines[i]);
            if (line.startsWith("```")) break;
            // if we have no backticks - we should count { and } to detect the end of the code
            if (countQuotes == 0 && !codeWrappedInBackticks) {
                // check the next 2 lines - may be code is continued
                // if the next line is empty - skip it
                if (i + 1 < lastResponseLines.length && lastResponseLines[i + 1].trim().isEmpty()) {
                    updatedLines.add(lastResponseLines[i + 1]);
                    i++;
                }
                // now detect if the code is continued
                if (i + 1 < lastResponseLines.length && isCodeContinued(lastResponseLines[i + 1].trim())) continue;
                break; // this is not a code, stop here
            }
        }
        return updatedLines;
    }
}