# JAIG

JAIG is Java AI-powered Code Generator.

JAIG is based on the OpenAI GPT models used for code generation.

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

- Generate a code in IDEA
- Include the needed source files to the request
- Parse the generated Java code and sort into folders within src/main/java
- Configure GPT temperature, model name, etc.
- Merge the code changes with AI updates
- Prompt templates
- Prompt pipelines
- Rollbacks
- Refactoring mode

## Installation

First, you need to take JAIG.jar and JAIG.yaml from JAIG folder and copy it to your project.
Then, you need to specify your openAIApiKey in the JAIG.yaml file.
Then, install it into IDEA as described below.

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
4.	Select JAIG in the list, and select Enable New UI as the icon at the bottom...
5.  Close windows with the OK button

As a result, a button for launching JAIG should appear in the top bar.

## Usage

To use JAIG, you need to:
1. Create a folder
2. Create a prompt file in this folder
3. Select the prompt file and click the JAIG button
4. Wait for the generation to complete

## License

JAIG is released under the MIT License.

