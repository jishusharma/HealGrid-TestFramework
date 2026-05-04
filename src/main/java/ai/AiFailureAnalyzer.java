package ai;

import org.w3c.dom.*;

import javax.xml.parsers.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;

public class AiFailureAnalyzer {

    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL = "llama-3.1-8b-instant";
    private static final String SUREFIRE = "target/surefire-reports/junitreports";
    private static final String PROMPT = "src/test/resources/ai_prompts/failure-analysis-prompt.txt";
    private static final String OUTPUT = "target/ai-failure-report.json";

    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("CLAUDE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("[AiFailureAnalyzer] CLAUDE_API_KEY not set. Skipping.");
            return;
        }

        List<String> failures = parseFailures();
        if (failures.isEmpty()) {
            System.out.println("[AiFailureAnalyzer] No failures found. Skipping.");
            return;
        }

        System.out.println("[AiFailureAnalyzer] " + failures.size() + " failure(s) found. Calling Claude API...");

        String systemPrompt = Files.readString(Path.of(PROMPT)).strip();
        String userMessage = buildUserMessage(failures);
        String result = callClaudeApi(apiKey, systemPrompt, userMessage);

        handleOutput(result, detectEnv());
    }

    // --- Parsing ---

    private static List<String> parseFailures() throws Exception {
        List<String> failures = new ArrayList<>();
        File dir = new File(SUREFIRE);
        File[] xmlFiles = dir.listFiles((d, n) -> n.startsWith("TEST-") && n.endsWith(".xml"));
        if (xmlFiles == null) return failures;

        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();

        for (File xml : xmlFiles) {
            Document doc = builder.parse(xml);
            NodeList testcases = doc.getElementsByTagName("testcase");

            for (int i = 0; i < testcases.getLength(); i++) {
                Element tc = (Element) testcases.item(i);
                String className = tc.getAttribute("classname");
                String methodName = tc.getAttribute("name");

                NodeList failNodes = tc.getElementsByTagName("failure");
                NodeList errorNodes = tc.getElementsByTagName("error");

                if (failNodes.getLength() == 0 && errorNodes.getLength() == 0) continue;

                Element el = (Element) (failNodes.getLength() > 0 ? failNodes.item(0) : errorNodes.item(0));
                String message = el.getAttribute("message");
                String type = el.getAttribute("type");
                String[] lines = el.getTextContent().trim().split("\n");
                String trace = String.join("\n", Arrays.copyOfRange(lines, 0, Math.min(5, lines.length)));

                failures.add(className + "#" + methodName + " | " + type + ": " + message + "\n" + trace);
            }
        }
        return failures;
    }

    // --- Prompt construction ---

    private static String buildUserMessage(List<String> failures) {
        StringBuilder sb = new StringBuilder("Test failures:\n\n");
        for (int i = 0; i < failures.size(); i++) {
            sb.append(i + 1).append(". ").append(failures.get(i)).append("\n\n");
        }
        return sb.toString();
    }

    // --- API call ---

    private static String callClaudeApi(String apiKey, String system, String user) throws Exception {
        String body = "{\"model\":\"" + MODEL + "\",\"max_tokens\":1000," +
                "\"messages\":[" +
                "{\"role\":\"system\",\"content\":\"" + escapeJson(system) + "\"}," +
                "{\"role\":\"user\",\"content\":\"" + escapeJson(user) + "\"}" +
                "]}";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("[AiFailureAnalyzer] API error " + response.statusCode() + ": " + response.body());
        }

        return extractTextGroq(response.body());
    }

    private static String extractTextGroq(String apiResponse) {
        String marker = "\"content\":\"";
        int start = apiResponse.indexOf(marker);
        if (start < 0) throw new RuntimeException("[AiFailureAnalyzer] Unexpected response: " + apiResponse);
        start += marker.length();

        StringBuilder result = new StringBuilder();
        for (int i = start; i < apiResponse.length(); i++) {
            char c = apiResponse.charAt(i);
            if (c == '\\' && i + 1 < apiResponse.length()) {
                char next = apiResponse.charAt(++i);
                switch (next) {
                    case '"':
                        result.append('"');
                        break;
                    case 'n':
                        result.append('\n');
                        break;
                    case 'r':
                        result.append('\r');
                        break;
                    case 't':
                        result.append('\t');
                        break;
                    case '\\':
                        result.append('\\');
                        break;
                    default:
                        result.append(next);
                }
            } else if (c == '"') {
                break;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    // Walks the JSON character-by-character — no library needed, handles all escape sequences
    private static String extractText(String apiResponse) {
        String marker = "\"text\":\"";
        int start = apiResponse.indexOf(marker);
        if (start < 0) throw new RuntimeException("[AiFailureAnalyzer] Unexpected response: " + apiResponse);
        start += marker.length();

        StringBuilder result = new StringBuilder();
        for (int i = start; i < apiResponse.length(); i++) {
            char c = apiResponse.charAt(i);
            if (c == '\\' && i + 1 < apiResponse.length()) {
                char next = apiResponse.charAt(++i);
                switch (next) {
                    case '"':
                        result.append('"');
                        break;
                    case 'n':
                        result.append('\n');
                        break;
                    case 'r':
                        result.append('\r');
                        break;
                    case 't':
                        result.append('\t');
                        break;
                    case '\\':
                        result.append('\\');
                        break;
                    default:
                        result.append(next);
                }
            } else if (c == '"') {
                break;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    // --- Output routing ---

    private static String detectEnv() {
        if (System.getenv("BUILD_NUMBER") != null) return "jenkins";
        if (new File("/.dockerenv").exists() || "true".equals(System.getenv("DOCKER_ENV"))) return "docker";
        return "local";
    }

    private static void handleOutput(String json, String env) throws Exception {
        System.out.println("\n[AiFailureAnalyzer] ENV=" + env);
        System.out.println("[AiFailureAnalyzer] Result:\n" + json);

        if ("docker".equals(env) || "jenkins".equals(env)) {
            Files.createDirectories(Path.of("target"));
            Files.writeString(Path.of(OUTPUT), json);
            System.out.println("[AiFailureAnalyzer] Written to: " + OUTPUT);
        }
    }

    // --- Utilities ---

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}