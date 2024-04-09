package app.jtutor;

import app.jtutor.banner.BannerPrinter;
import app.jtutor.jaig.JAIGUseCasesProcessor;
import app.jtutor.jaig.RefactoringProcessor;
import app.jtutor.jaig.config.GlobalConfig;

public class JAIG {

    public static void main(String[] args) {
        // here we can pass inputFileOrFolder for testing/debugging purposes
        //String inputFileOrFolder = "./demo/test.txt";
        // inputFileOrFolder must be null for production!
         String inputFileOrFolder = null;
//         String inputFileOrFolder = "./requests/test/prompt.txt";

        BannerPrinter.printBanner();
        System.out.println("********** JAIG: Java AI Generator v.09.04.2024 **********");
        if (inputFileOrFolder == null) {
            if (args.length == 0 || args[0].isEmpty()) {
                System.err.println("You should select the file for which you want to apply JAIG");
                System.exit(1);
            } else {
                inputFileOrFolder = args[0];
            }
        }

        // parsing global config YAIG.yaml
        GlobalConfig.INSTANCE.parseYamlConfig();

        if (inputFileOrFolder.endsWith(".java")) {
            new RefactoringProcessor().process(args);
        } else {
            JAIGUseCasesProcessor jaigUseCasesProcessor = new JAIGUseCasesProcessor();
            jaigUseCasesProcessor.process(inputFileOrFolder);
        }
    }

}

