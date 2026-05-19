package observability;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.*;
import java.sql.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class TestResultPersister {

    private static final Set<String> INFRA_KEYWORDS = Set.of(
            "connection refused", "healenium not reachable", "host unreachable",
            "healenium is not reachable", "not reachable",
            "nosuchdriverexception", "unable to obtain", "free slot",
            "selenium grid", "no such session", "sessionnotcreatedexception"
    );

    private static final Set<String> CONFIG_KEYWORDS = Set.of(
            "credential", "api key", "access key", "username", "password",
            "missing env", "environment variable", "invalid configuration"
    );

    private static final Set<String> SETUP_KEYWORDS = Set.of(
            "beforemethod", "beforesuite", "configuration failure", "setup failed"
    );

    private static final Set<String> TIMEOUT_KEYWORDS = Set.of(
            "timeoutexception", "timed out", "timeout"
    );

    private static final Set<String> UI_INTERACTION_KEYWORDS = Set.of(
            "elementnotinteractableexception", "element click intercepted",
            "staleelementreferenceexception", "nosuchelementexception",
            "not clickable", "not interactable"
    );

    private static final Set<String> ASSERTION_KEYWORDS = Set.of(
            "assertionerror", "assert", "expected", "but found"
    );

    public static void main(String[] args) throws Exception {
        String reportsDir = args.length > 0 ? args[0] : "target/surefire-reports";
        String dbHost = envOr("DB_HOST", "localhost");
        String dbPort = envOr("DB_PORT", "5432");
        String dbName = envOr("DB_NAME", "healenium");
        String dbUser = envOr("DB_USER", "healenium_user");
        String dbPass = envOr("DB_PASSWORD", "healenium_password");
        String build = resolveBuildId();
        String executionSource = resolveExecutionSource();
        int attemptNumber = parseAttemptNumber(envOr("ATTEMPT_NUMBER", "1"));
        String rerunReason = attemptNumber > 1 ? emptyToNull(System.getenv("RERUN_REASON")) : null;

        String url = "jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName;
        Connection conn = DriverManager.getConnection(url, dbUser, dbPass);
        conn.setAutoCommit(false);
        ensureResultsTable(conn);

        String sql = "INSERT INTO healenium.test_results " +
                "(test_name, suite, browser, status, duration_ms, build, error_message, " +
                "failure_category, failure_reason, error_type, attempt_number, rerun_reason, execution_source) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
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
                            String errorType = null;

                            Element failure = firstChild(tc, "failure");
                            Element error = firstChild(tc, "error");
                            Element skipped = firstChild(tc, "skipped");

                            if (failure != null || error != null) {
                                Element failureElement = failure != null ? failure : error;
                                status = "FAILED";
                                errorMsg = emptyToNull(failureElement.getAttribute("message"));
                                errorType = emptyToNull(failureElement.getAttribute("type"));
                            } else if (tc.getElementsByTagName("skipped").getLength() > 0) {
                                status = "SKIPPED";
                                errorMsg = skipped != null ? emptyToNull(skipped.getAttribute("message")) : null;
                                errorType = "skipped";
                            } else {
                                status = "PASSED";
                            }

                            FailureDetails failureDetails = classify(status, errorType, errorMsg, testName);

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
                            stmt.setString(8, failureDetails.category);
                            stmt.setString(9, failureDetails.reason);
                            stmt.setString(10, failureDetails.errorType);
                            stmt.setInt(11, attemptNumber);
                            stmt.setString(12, rerunReason);
                            stmt.setString(13, executionSource);
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

    private static String envOr(String key, String defaultVal) {
        String val = System.getenv(key);
        return val != null ? val : defaultVal;
    }

    private static void ensureResultsTable(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE SCHEMA IF NOT EXISTS healenium");
            stmt.execute("CREATE TABLE IF NOT EXISTS healenium.test_results (" +
                    "id SERIAL PRIMARY KEY, " +
                    "test_name VARCHAR(300) NOT NULL, " +
                    "suite VARCHAR(100), " +
                    "browser VARCHAR(50), " +
                    "status VARCHAR(20) NOT NULL, " +
                    "duration_ms BIGINT, " +
                    "build VARCHAR(100), " +
                    "run_at TIMESTAMP DEFAULT now(), " +
                    "error_message TEXT, " +
                    "failure_category VARCHAR(50), " +
                    "failure_reason TEXT, " +
                    "error_type VARCHAR(300), " +
                    "attempt_number INT DEFAULT 1, " +
                    "rerun_reason TEXT, " +
                    "execution_source VARCHAR(50)" +
                    ")");
            stmt.execute("ALTER TABLE healenium.test_results ADD COLUMN IF NOT EXISTS failure_category VARCHAR(50)");
            stmt.execute("ALTER TABLE healenium.test_results ADD COLUMN IF NOT EXISTS failure_reason TEXT");
            stmt.execute("ALTER TABLE healenium.test_results ADD COLUMN IF NOT EXISTS error_type VARCHAR(300)");
            stmt.execute("ALTER TABLE healenium.test_results ADD COLUMN IF NOT EXISTS attempt_number INT DEFAULT 1");
            stmt.execute("ALTER TABLE healenium.test_results ADD COLUMN IF NOT EXISTS rerun_reason TEXT");
            stmt.execute("ALTER TABLE healenium.test_results ADD COLUMN IF NOT EXISTS execution_source VARCHAR(50)");
            stmt.execute("UPDATE healenium.test_results SET attempt_number = 1 WHERE attempt_number IS NULL");
            backfillClassification(stmt);
        }
    }

    private static void backfillClassification(Statement stmt) throws SQLException {
        stmt.execute("UPDATE healenium.test_results " +
                "SET failure_category = 'INFRA', " +
                "failure_reason = 'Healenium backend not reachable', " +
                "error_type = COALESCE(error_type, 'java.lang.RuntimeException') " +
                "WHERE status = 'FAILED' AND LOWER(COALESCE(error_message, '')) LIKE '%healenium%not reachable%'");

        stmt.execute("UPDATE healenium.test_results " +
                "SET failure_category = 'UI_INTERACTION', " +
                "failure_reason = 'UI element interaction failed' " +
                "WHERE status = 'FAILED' " +
                "AND failure_category IS NULL " +
                "AND (LOWER(COALESCE(error_message, '')) LIKE '%not interactable%' " +
                "OR LOWER(COALESCE(error_message, '')) LIKE '%not clickable%' " +
                "OR LOWER(COALESCE(error_message, '')) LIKE '%could not be scrolled into view%')");

        stmt.execute("UPDATE healenium.test_results " +
                "SET failure_category = 'SKIPPED_DEPENDENCY', " +
                "failure_reason = COALESCE(failure_reason, 'Test skipped'), " +
                "error_type = COALESCE(error_type, 'skipped') " +
                "WHERE status = 'SKIPPED' AND failure_category IS NULL");
    }

    private static Element firstChild(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        return nodes.getLength() > 0 ? (Element) nodes.item(0) : null;
    }

    private static FailureDetails classify(String status, String errorType, String message, String testName) {
        if ("PASSED".equals(status)) {
            return new FailureDetails(null, null, null);
        }

        if ("SKIPPED".equals(status)) {
            String reason = message != null ? truncate(message, 500) : "Test skipped";
            return new FailureDetails("SKIPPED_DEPENDENCY", reason, errorType);
        }

        String combined = ((errorType == null ? "" : errorType) + " " +
                (message == null ? "" : message) + " " +
                (testName == null ? "" : testName)).toLowerCase(Locale.ROOT);

        String category;
        String reason;
        if (containsAny(combined, CONFIG_KEYWORDS)) {
            category = "CONFIG";
            reason = "Configuration or credential issue";
        } else if (containsAny(combined, INFRA_KEYWORDS)) {
            category = "INFRA";
            reason = reasonForInfra(combined, message);
        } else if (containsAny(combined, SETUP_KEYWORDS) || "setup".equalsIgnoreCase(testName)) {
            category = "SETUP";
            reason = "Test setup failed";
        } else if (containsAny(combined, TIMEOUT_KEYWORDS)) {
            category = "TIMEOUT";
            reason = "Operation timed out";
        } else if (containsAny(combined, UI_INTERACTION_KEYWORDS)) {
            category = "UI_INTERACTION";
            reason = "UI element interaction failed";
        } else if (containsAny(combined, ASSERTION_KEYWORDS)) {
            category = "ASSERTION";
            reason = "Assertion failed";
        } else {
            category = "UNKNOWN";
            reason = message != null ? truncate(message, 500) : "Failure reason unavailable";
        }

        return new FailureDetails(category, reason, errorType);
    }

    private static String reasonForInfra(String combined, String message) {
        if (combined.contains("healenium not reachable")) return "Healenium backend not reachable";
        if (combined.contains("connection refused")) return "Connection refused";
        if (combined.contains("free slot")) return "Selenium Grid slot unavailable";
        if (combined.contains("no such session")) return "Browser session lost";
        if (combined.contains("unable to obtain") || combined.contains("nosuchdriverexception")) {
            return "Browser driver/session could not be created";
        }
        return message != null ? truncate(message, 500) : "Infrastructure dependency unavailable";
    }

    private static boolean containsAny(String text, Set<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }

    private static String resolveBuildId() {
        String explicitLocalBuild = emptyToNull(System.getenv("LOCAL_BUILD_ID"));
        if (explicitLocalBuild != null) return explicitLocalBuild;

        String buildNumber = emptyToNull(System.getenv("BUILD_NUMBER"));
        if (buildNumber != null) return buildNumber;

        String buildId = emptyToNull(System.getenv("BUILD_ID"));
        if (buildId != null) return buildId;

        return "local-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    }

    private static String resolveExecutionSource() {
        String explicit = emptyToNull(System.getenv("EXECUTION_SOURCE"));
        if (explicit != null) return explicit;
        if (emptyToNull(System.getenv("JENKINS_URL")) != null || emptyToNull(System.getenv("BUILD_NUMBER")) != null) {
            return "JENKINS";
        }
        return "LOCAL_MAVEN";
    }

    private static int parseAttemptNumber(String raw) {
        try {
            int parsed = Integer.parseInt(raw);
            return parsed > 0 ? parsed : 1;
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }

    private static class FailureDetails {
        private final String category;
        private final String reason;
        private final String errorType;

        private FailureDetails(String category, String reason, String errorType) {
            this.category = category;
            this.reason = reason;
            this.errorType = errorType;
        }
    }
}
