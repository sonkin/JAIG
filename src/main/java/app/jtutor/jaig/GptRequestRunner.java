package app.jtutor.jaig;

import app.jtutor.jaig.config.GlobalConfig;
import app.jtutor.jaig.config.LocalConfig;
import com.azure.ai.openai.OpenAIAsyncClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatRequestMessage;
import com.azure.ai.openai.models.ChatRequestUserMessage;
import com.azure.ai.openai.models.ChatResponseMessage;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.http.netty.NettyAsyncHttpClientBuilder;
import com.azure.core.util.CoreUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class GptRequestRunner {
    private final LocalConfig localConfig;

    public GptRequestRunner(LocalConfig localConfig) {
        this.localConfig = localConfig;
    }

    public GptRequestRunner() {
        this.localConfig = null;
    }

    public String executeAndSaveGptRequest(String inputFilePath, String outputFilePath) {
        try {
            // Read the input file
            List<String> lines = Files.readAllLines(Path.of(inputFilePath), StandardCharsets.UTF_8);
            // Filter out all lines started from # (comments)
//            lines = lines.stream()
//                    .filter(line -> !line.startsWith("#"))
//                    .collect(Collectors.toList());
            String inputFileContent = String.join("\n", lines);

            // Make request to OpenAI API
            String response = gptRequest(inputFileContent);

            // Write the response to the output file in UTF-8 encoding
            Files.writeString(Path.of(outputFilePath), response,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            return response;
        } catch (IOException e) {
            System.out.println("An error occurred during file processing: " + e.getMessage());
            return null;
        }
    }


    public static String[] javaKeywords = {
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
            "for", "if", "goto", "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
            "volatile", "while"
    };

    public static boolean isJavaKeyword(String input) {
        for (String keyword : javaKeywords) {
            if (keyword.equals(input)) {
                return true;
            }
        }
        return false;
    }
    public static final String ANSI_BOLD = "\u001B[1m";
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GRAY = "\u001B[90m";

    public static int countDoubleQuotes(String input) {
        int count = 0;
        for (int i = 0; i < input.length(); i++) {
            if (input.charAt(i) == '"') {
                count++;
            }
        }
        return count;
    }

    static class LineSymbolsCounter {
        private static final int LINE_WIDTH = 40;
        PrintWriter writer;
        int counter = 0;

        public LineSymbolsCounter(PrintWriter writer) {
            this.writer = writer;
        }

        public void add(String line) {
            if (line.contains("\n")) {
                counter = 0;
            } else {
                counter += line.length();
                if (counter> LINE_WIDTH && (
                        line.startsWith(" ") || line.endsWith(" ") || line.endsWith("\t") ||
                        line.endsWith("{") || line.endsWith("}") || line.endsWith(";") ||
                        line.endsWith("]") || line.endsWith(")"))) {
                    writer.println();
                    counter = 0;
                }
            }
        }
    }

    public String gptRequest(String inputText) {
        if (GlobalConfig.INSTANCE.getOpenAIApiKey() == null && GlobalConfig.INSTANCE.getKey() == null) {
            System.err.println("You need to provide key or openAIApiKey. Please add it to JAIG.yaml");
            System.exit(-1);
        }

        String model = GlobalConfig.INSTANCE.getModel();
        Double temperature = GlobalConfig.INSTANCE.getTemperature();
        // local configuration has a higher priority
        if (localConfig != null) {
            if (localConfig.getModel() != null) model = localConfig.getModel();
            if (localConfig.getTemperature() != null) temperature = localConfig.getTemperature();
        }

        if(GlobalConfig.INSTANCE.getEndpoint().contains("azure")){
            if (GlobalConfig.INSTANCE.getDeploymentIdOrModel() != null) {
                model = GlobalConfig.INSTANCE.getDeploymentIdOrModel();
                System.out.println("Using temperature: " + temperature);
                System.out.println("Using deployment model: " + model);
            }
        } else {
            System.out.println("Using temperature: " + temperature);
            System.out.println("Using model: " + model);
        }

        StringBuilder result = new StringBuilder();
        inputText = inputText.replaceAll("\\\"", "\\\\\"");
        inputText = inputText.replaceAll("\\\n", "\\\\n");
        inputText = inputText.replaceAll("\\\t", " ");
        inputText = inputText.replaceAll("```", " ");

        Flux<String> eventStream;
        if (GlobalConfig.INSTANCE.isGptProxy()) {
             eventStream = getJTutorResponseEventStream(inputText, model, temperature);
        } else {
            eventStream = getEventStream(GlobalConfig.INSTANCE.getEndpoint(), inputText, model, temperature);
        }
        AtomicInteger doubleQuotesCount = new AtomicInteger();
        LoadingProcess loadingProcess = new LoadingProcess();
        loadingProcess.start();
        PrintWriter out = new PrintWriter(System.out, true, StandardCharsets.UTF_8);
        LineSymbolsCounter lineSymbolsCounter = new LineSymbolsCounter(out);

        AtomicBoolean excludeLine = new AtomicBoolean(false);
        AtomicBoolean commentLine = new AtomicBoolean(false);
        AtomicBoolean commentMultiLine = new AtomicBoolean(false);
        AtomicBoolean firstLine = new AtomicBoolean(false);
        try {
            eventStream.doOnNext(
                    content -> {
                        loadingProcess.stop();
                        if (!firstLine.get()) {
                            firstLine.set(true);
                            out.println(ANSI_BOLD+
                                    "========================== AI RESPONSE ==========================\n"
                                    +ANSI_RESET);
                        }

                        // exclude line if it starts from ```
                        // because GPT marks code blocks with ```java ... ```
                        if (content.startsWith("``") || content.startsWith("`[[[newline]]]")) {
                            excludeLine.set(true);
                        } else {
                            boolean skipLine = excludeLine.get();
                            if (content.contains("[[[newline]]]") ||
                                    (!GlobalConfig.INSTANCE.isGptProxy() && content.contains("\n"))) {
                                excludeLine.set(false);
                            }
                            if (skipLine) {
                                return;
                            }
                        }
                        if (excludeLine.get()) return;

                        String ss = content.replace("[[[space]]]", " ");
                        String resultString = ss; // string without ANSI codes
                        ss = ss.replace("{", ANSI_BLUE + '{' + ANSI_RESET);
                        ss = ss.replace("}", ANSI_BLUE + '}' + ANSI_RESET);
                        doubleQuotesCount.addAndGet(countDoubleQuotes(ss));

                        if (isJavaKeyword(ss.trim()) && !commentLine.get() && ! commentMultiLine.get()) {
                            result.append(resultString);
                            out.print(ANSI_BLUE + ss + ANSI_RESET);
                        } else {
                            if (doubleQuotesCount.get() % 2 == 1) {
                                String sss = ss.replace("[[[newline]]]", "\\n");
                                result.append(resultString.replace("[[[newline]]]", "\\n"));
                                lineSymbolsCounter.add(sss);
                                sss = highlightComments(sss, commentLine, commentMultiLine);
                                out.print(ANSI_GREEN + sss);
                            } else {
                                String sss = ss.replace("[[[newline]]]", "\n");
                                result.append(resultString.replace("[[[newline]]]", "\n"));
                                lineSymbolsCounter.add(sss);
                                sss = highlightComments(sss, commentLine, commentMultiLine);
                                if (sss.contains("\"")) {
                                    out.print(sss + ANSI_RESET);
                                } else {
                                    out.print(sss);
                                }
                            }
                        }
                        out.flush();
                    })
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
            .doOnError(error -> out.println("\n\nConnection error: \n" + error.getMessage()))
            .doOnComplete(() -> out.println(ANSI_RESET+ANSI_BOLD+
                    "\n\n====================== END OF AI RESPONSE ======================"+ANSI_RESET))
            .doFinally(signalType -> loadingProcess.stop())
            .blockLast();
        } catch (Exception e) {
            System.err.println("An error occurred during GPT request: " + e.getMessage());
            System.out.println(e.getCause().getMessage());
        }

        return result.toString();
    }

    private Flux<String> getOpenAIResponseEventStream(String inputText, String model, Double temperature) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMinutes(2));  // Set the timeout

        WebClient client = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl(GlobalConfig.INSTANCE.getEndpoint()) // OpenAI Chat API endpoint
                .defaultHeader("Authorization", "Bearer " + GlobalConfig.INSTANCE.getOpenAIApiKey()) // Authorization header with Bearer token
                .defaultHeader("Content-Type", "application/json")
                .build();

        // extract seed from local or global config if it is provided
        String seedString = "";
        Integer seed = GlobalConfig.INSTANCE.getSeed();
        if (localConfig != null && localConfig.getSeed() != null) {
            seed = localConfig.getSeed();
        }
        if (seed != null) {
            seedString = "\"seed\": " + seed + ",";
        }

        // Creating the request body
        String requestBody = """
            {
                "model": "%s",
                "messages": [
                    {
                        "role": "user",
                        "content": "%s"
                    }
                ],
                "temperature": %f,
                %s
                "stream": true
            }
            """.formatted(model, inputText, temperature, seedString);

        Flux<String> eventStream = client.post()
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(String.class);

        return eventStream;
    }

    private Flux<String> getAzureOpenAIResponseEventStream(String inputText, Double temperature){

        //Azure OpenAI Client
        OpenAIAsyncClient client = new OpenAIClientBuilder()
                .httpClient(new NettyAsyncHttpClientBuilder().responseTimeout(Duration.ofMinutes(2)).build())
                .credential(new AzureKeyCredential(GlobalConfig.INSTANCE.getOpenAIApiKey()))
                .endpoint(GlobalConfig.INSTANCE.getEndpoint())
                .buildAsyncClient();

        // extract seed from local or global config if it is provided
        Integer seed = GlobalConfig.INSTANCE.getSeed();
        if (localConfig != null && localConfig.getSeed() != null) {
            seed = localConfig.getSeed();
        }

        //inputText's content from the .txt file will be used as ChatRequestMessage
        List<ChatRequestMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new ChatRequestUserMessage(inputText));

        //ChatCompletionsOptions config to enable Streaming Mode
        ChatCompletionsOptions options = new ChatCompletionsOptions(chatMessages);
        options.setStream(true);
        options.setN(1);
        options.setTemperature(temperature);
//            options.setMaxTokens(1000);
        options.setSeed(seed.longValue());
        options.setLogitBias(new HashMap<>());

        //get the response's content as a Flux<String> for further processing
        Flux<String> chatCompletions = client
                .getChatCompletionsStream(GlobalConfig.INSTANCE.getDeploymentIdOrModel(), options)
                .map(c -> {
                    if (CoreUtils.isNullOrEmpty(c.getChoices())) {
                        return "";
                    }

                    ChatResponseMessage delta = c.getChoices().get(0).getDelta();

                    return delta.getContent() == null ? "" : delta.getContent();
                });
        return chatCompletions;
    }

    private Flux<String> extractContentFromJSONStream(Flux<String> jsonStream) {
        ObjectMapper objectMapper = new ObjectMapper();

        return jsonStream.map(jsonString -> {
            // Check if the string is the special token "[DONE]"
            // which means that the response is complete
            if ("[DONE]".equals(jsonString)) {
                return ""; // Skip this token
            }

            try {
                JsonNode rootNode = objectMapper.readTree(jsonString);
                JsonNode choicesNode = rootNode.path("choices");

                if (!choicesNode.isArray() || choicesNode.isEmpty()) {
                    return ""; // Or handle the absence of "choices" as needed
                }

                JsonNode firstChoice = choicesNode.get(0);
                JsonNode deltaNode = firstChoice.path("delta");
                JsonNode contentNode = deltaNode.path("content");

                return contentNode.asText(); // Extracts the content as String
            } catch (Exception e) {
                // Handle parsing exceptions
                e.printStackTrace();
                return "";
            }
        })
        .filter(content -> !content.isEmpty()); // Filter out empty strings
    }

    private Flux<String> getEventStream(String endpoint, String inputText, String model, Double temperature){
        if(endpoint.contains("azure")){
            return getAzureOpenAIResponseEventStream(inputText, temperature);
        } else {
            return extractContentFromJSONStream(getOpenAIResponseEventStream(inputText, model, temperature));
        }
    }

    private Flux<String> getJTutorResponseEventStream(String inputText, String model, Double temperature) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMinutes(2));  // Set the timeout

        WebClient client = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl("https://jtutor.app")
                .defaultHeader("Origin", "https://jtutor.app")
                .defaultHeader("Content-Type", "application/json")
                .build();

        Flux<String> eventStream = client.post()
                .uri(getJTutorUri(model, temperature,
                        GlobalConfig.INSTANCE.getOpenAIApiKey(),
                        GlobalConfig.INSTANCE.getKey()))
                .bodyValue("{\"request\":\""+ inputText +"\"}")
                .retrieve()
                .bodyToFlux(String.class);
        return eventStream;
    }

    private static Function<UriBuilder, URI> getJTutorUri(String finalModel, Double finalTemperature, String finalApiKey, String finalKey) {
        return uriBuilder -> uriBuilder
                .path("/stream-gpt")
                .queryParam("model", finalModel)
                .queryParam("temperature", finalTemperature)
                .queryParam("apiKey", finalApiKey)
                .queryParam("key", finalKey)
                .build();
    }

    public String highlightComments(String ss, AtomicBoolean commentLine, AtomicBoolean commentMultiLine) {
        if (ss.trim().startsWith("//")) {
            commentLine.set(true);
            ss = ss.replace("//", ANSI_GRAY + "\u2215\u2215");
        }

        if (ss.trim().startsWith("/*")) {
            commentMultiLine.set(true);
            ss = ss.replace("/*", ANSI_GRAY + "/*");
        }
        if (commentLine.get() && ss.contains("\n")) {
            ss = ss.replace("\n", "\n" + ANSI_RESET);
            commentLine.set(false);
        }
        if (commentMultiLine.get() && ss.contains("*/")) {
            ss = ss.replace("*/", "*/" + ANSI_RESET);
            commentMultiLine.set(false);
        }
        return ss;
    }
}
