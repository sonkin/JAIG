package app.jtutor.jaig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * This class is not used anywhere, just for the case if synchronous request
 * to GPT will be needed
 */
public class GptSyncRequest {
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String OPENAI_API_KEY = "<INSERT KEY HERE>";

    private static String makeOpenAIRequestViaJTutor(String inputText) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL("https://jtutor.app/stream-gpt");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Origin","https://jquiz.vercel.app");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Transfer-Encoding", "trailers");
            connection.setDoOutput(true);
            inputText = inputText.replaceAll("\\\"", "\\\\\"");
            inputText = inputText.replaceAll("\\\n", "\\\\n");
            System.out.println(inputText);
            String requestData = "{\"request\":\""+inputText+"\"}";

            try (OutputStream outputStream = connection.getOutputStream()) {
                byte[] requestDataBytes = requestData.getBytes("UTF-8");
                outputStream.write(requestDataBytes, 0, requestDataBytes.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {

                // Response Input Stream
                InputStream stream = connection.getInputStream();
                InputStreamReader reader = new InputStreamReader(stream, "utf-8");
                int c;
                StringBuilder response = new StringBuilder();
                while ((c = reader.read()) != -1) {
                    char character = (char) c;
                    System.out.print(character); // Print each character as it arrives
                    response.append(character);
                }
                System.out.println(response.toString());
                return response.toString();

            } else {
                System.out.println(connection.getResponseMessage());
                throw new IOException("OpenAI API request failed with response code: " + responseCode);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private static String makeOpenAIRequest(String inputText) throws IOException {
        URL url = new URL(OPENAI_API_URL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", "Bearer " + OPENAI_API_KEY);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        inputText = inputText.replaceAll("\\\"", "\\\\\"");
        inputText = inputText.replaceAll("\\\n", "\\\\n");
        System.out.println(inputText);
        String requestData = "{\n" +
                "\"model\": \"gpt-3.5-turbo\"" + ",\n" +
                "  \"messages\": [{\"role\": \"user\", \"content\":\""+inputText+"\"}]" +
                "}";

        try (OutputStream outputStream = connection.getOutputStream()) {
            byte[] requestDataBytes = requestData.getBytes("UTF-8");
            outputStream.write(requestDataBytes, 0, requestDataBytes.length);
        }

        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String result = extractMessage(reader.readLine());

            return result;
        } else {
            throw new IOException("OpenAI API request failed with response code: " + responseCode);
        }
    }


    public static String extractMessage(String jsonResponse) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            JsonNode choicesNode = rootNode.get("choices");
            JsonNode messageNode = choicesNode.get(0).get("message");
            return messageNode.get("content").asText();
        } catch (Exception e) {
            System.out.println("An error occurred while extracting the message: " + e.getMessage());
        }
        return null;
    }


}
