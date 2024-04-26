# JAIG

JAIG is a Java AI-powered Code Generator.

JAIG is based on the OpenAI GPT models used for the code generation.

```
              *        *         *
           ***       **       ***
         *****     ****    *******
       *******   ******   **********
     ********** ********* **************
   ************* ************* *************
  ***███████*****█████******███**██████████***
  *******███****███*███*****███**███***********
  *******███***███***███****███**███***████****
  **██***███**███████████***███**███*****██****
   *████████*███*******███**███**██████████***
    ************* *********** **************
       *********** ********* ************
          *******   ******   ********
            ****    ***     ***
           **      *      **
        *       *      *
```

## JAIG demos and documentation

[Detailed JAIG video presentation](https://youtu.be/fohX8WbdWp8)

[JAIG Features slides](docs/JAIGFeatures.pdf)

[JAIG Brief Reference slides](docs/JAIGBriefReference.pdf)

[JAIG Main ideas](docs/JAIGideas.pdf)

## JAIG main features

- Generate a code in IntelliJ IDEA
- Include the necessary source files to the AI request
- Parse the generated Java code and sort into folders within src/main/java directory
- Configure GPT temperature, model name, etc. globally and for every request
- Merge the code changes with AI updates
- Prompt templates (placeholders + to be filled from requirements yaml file; support for Velocity engine for complicated cases)
- Prompt pipelines (running a sequence of prompts)
- Rollbacks (possibility to restore the state before AI generated code was applied)
- Refactoring (possibility to update the existing code based on AI-generated response in a dialog mode)

## Installation

First, you need to take **JAIG folder** (with JAIG.jar and JAIG.yaml files) and copy it to your project.

Then, you need to specify your *openAIApiKey*, *endpoint* and *deploymentIdOrModel* (if you use Azure OpenAI) in the **JAIG.yaml** file.

Then, install JAIG into IDEA as described below.

## Integration with IntelliJ IDEA

The detailed installation instructions are provided in [Installation Guide](docs/Installation.pdf)

To install JAIG into IntelliJ IDEA, you need to:
1.	Open Settings, find External Tools, click + to add
2.	Specify the settings:
```
      Name: JAIG
      Program: java
      Arguments: -jar JAIG/JAIG.jar $FilePathRelativeToProjectRoot$ $SelectionStartLine$ $SelectionEndLine$ $SelectionEndColumn$
      Working directory: $ProjectFileDir$
```

## Adding JAIG Icon to the IDEA Toolbar

To add an icon (button) for conveniently calling JAIG, you need:
1.	Switch IDEA to the new interface by selecting the Enable new UI checkbox in the settings
2.	Right-click on the IDEA top bar, select Customize Toolbar...
3.	Select the Left section, click +, select Add Action...
4.	Select JAIG in the list, and select Enable New UI as the icon at the bottom
5.  Close windows with the OK button

As a result, a button for launching JAIG should appear in the top bar.

## Usage

To use JAIG, you need to:
1. Create a folder which will contain JAIG artifacts (prompt, request, response, etc.)
2. Create a prompt file in this folder with .txt extension (example: prompt.txt)
3. Describe a prompt in the file (ex: Generate Java application which prints "Hey from AI!")
4. Click the JAIG button when editor is open
5. Wait for the generation to complete

JAIG will generate file prompt_name-response.txt which contains a response from GPT model.

## License

JAIG is released under the MIT License.

