package observability;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.*;
import java.sql.*;
import java.nio.file.*;
import java.util.*;

public class TestResultPersister {

    private static final Set<String> INFRA_KEYWORDS = Set.of(
            "connection refused", "healenium not reachable", "host unreachable",
            "nosuchdriverexception", "unable to obtain"
    );

    public static void main(String[] args) throws Exception {
        String reportsDir = args.length > 0 ? args[0] : "target/surefire-reports";
        String dbHost = envOr("DB_HOST", "localhost");
        String dbPort = envOr("DB_PORT", "5432");
        String dbName = envOr("DB_NAME", "healenium");
        String dbUser = envOr("DB_USER", "healenium_user");
        String dbPass = envOr("DB_PASSWORD", "healenium_password");
        String build = System.getenv("BUILD_NUMBER");

        String url = "jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName;
        Connection conn = DriverManager.getConnection(url, dbUser, dbPass);
        conn.setAutoCommit(false);

        String sql = "INSERT INTO healenium.test_results (test_name, suite, browser, status, duration_ms, build, error_message) VALUES (?, ?, ?, ?, ?, ?, ?)";
        PreparedStatement stmt = conn.prepareStatement(sql);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();

        Files.list(Paths.get(reportsDir))
                .filter(p -> p.getFileName().toString().startsWith("TEST-") && p.toString().endsWith(".xml"))
                .forEach(path -> {
                    try {
                        Document doc = builder.parse(path.toFile());
                        NodeList cases = doc.getElementsByTagName("testcase");
                        for (int i = 0; i < cases.getLength(); i++) {
                            Element tc = (Element) cases.item(i);
                            String testName = tc.getAttribute("name");
                            String className = tc.getAttribute("classname");
                            String suite = deriveSuite(className);
                            double time = Double.parseDouble(tc.getAttribute("time"));
                            long durationMs = (long) (time * 1000);

                            String status;
                            String errorMsg = null;
                            NodeList failures = tc.getElementsByTagName("failure");
                            if (failures.getLength() > 0) {
                                Element fail = (Element) failures.item(0);
                                String message = fail.getAttribute("message");
                                if (message != null && isInfra(message)) {
                                    status = "INFRA";
                                } else {
                                    status = "FAILED";
                                }
                                errorMsg = message;
                            } else if (tc.getElementsByTagName("skipped").getLength() > 0) {
                                status = "SKIPPED";
                            } else {
                                status = "PASSED";
                            }

                            // Derive browser from classname or leave null
                            String browser = null;
                            if (className != null && className.startsWith("tests.mobile")) {
                                browser = "mobile";
                            } else if (className != null && className.startsWith("tests.DemoQA")) {
                                browser = "chrome";
                            }

                            stmt.setString(1, testName);
                            stmt.setString(2, suite);
                            stmt.setString(3, browser);
                            stmt.setString(4, status);
                            stmt.setLong(5, durationMs);
                            stmt.setString(6, build);
                            stmt.setString(7, errorMsg);
                            stmt.addBatch();
                        }
                    } catch (Exception e) {
                        System.err.println("Error processing file: " + path + " - " + e.getMessage());
                    }
                });

        stmt.executeBatch();
        conn.commit();
        stmt.close();
        conn.close();
        System.out.println("Test results persisted to DB.");
    }

    private static String deriveSuite(String className) {
        if (className == null) return "unknown";
        if (className.startsWith("api.test")) return "api";
        if (className.startsWith("tests.mobile")) return "mobile";
        if (className.startsWith("tests.DemoQA") || className.startsWith("tests.Ai")) return "grid";
        // Fallback: second package segment (e.g., tests.DemoQAElementsPageTest -> "demoqaelements")
        String[] parts = className.split("\\.");
        if (parts.length >= 2) {
            return parts[1].replace("Tests", "").toLowerCase();
        }
        return "unknown";
    }

    private static boolean isInfra(String message) {
        String lower = message.toLowerCase();
        for (String keyword : INFRA_KEYWORDS) {
            if (lower.contains(keyword)) return true;
        }
        return false;
    }

    private static String envOr(String key, String defaultVal) {
        String val = System.getenv(key);
        return val != null ? val : defaultVal;
    }
}