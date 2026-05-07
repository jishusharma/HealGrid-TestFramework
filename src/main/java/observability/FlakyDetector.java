package observability;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class FlakyDetector {

    private static final int DEFAULT_WINDOW = 5;

    public static void main(String[] args) throws Exception {
        int window = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_WINDOW;
        String suiteFilter = args.length > 1 ? args[1] : null;

        String dbHost = envOr("DB_HOST", "localhost");
        String dbPort = envOr("DB_PORT", "5432");
        String dbName = envOr("DB_NAME", "healenium");
        String dbUser = envOr("DB_USER", "healenium_user");
        String dbPass = envOr("DB_PASSWORD", "healenium_password");

        String url = "jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName;
        Connection conn = DriverManager.getConnection(url, dbUser, dbPass);

        // Query the last N runs for each test (excluding INFRA status, as it's environment noise)
        String sql = "SELECT test_name, status, run_at FROM healenium.test_results " +
                "WHERE status IN ('PASSED', 'FAILED') " +
                (suiteFilter != null ? "AND suite = ? " : "") +
                "ORDER BY test_name, run_at DESC";
        PreparedStatement stmt = conn.prepareStatement(sql);
        if (suiteFilter != null) {
            stmt.setString(1, suiteFilter);
        }
        ResultSet rs = stmt.executeQuery();

        // Group results by test name, keeping only the last 'window' entries
        Map<String, List<String>> testHistory = new LinkedHashMap<>();
        while (rs.next()) {
            String testName = rs.getString("test_name");
            String status = rs.getString("status");
            testHistory.computeIfAbsent(testName, k -> new ArrayList<>()).add(status);
        }

        // Trim each list to window size (the most recent 'window' runs)
        testHistory.forEach((test, statuses) -> {
            if (statuses.size() > window) {
                statuses.subList(window, statuses.size()).clear();
            }
        });

        // Detect flaky: at least one transition PASSED<->FAILED in the window
        List<Map<String, Object>> flakyReport = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : testHistory.entrySet()) {
            String test = entry.getKey();
            List<String> statuses = entry.getValue();
            int transitions = 0;
            for (int i = 0; i < statuses.size() - 1; i++) {
                if (!statuses.get(i).equals(statuses.get(i + 1))) {
                    transitions++;
                }
            }
            String verdict = transitions > 0 ? "FLAKY" : "STABLE";
            Map<String, Object> result = new HashMap<>();
            result.put("test", test);
            result.put("transitions", transitions);
            result.put("verdict", verdict);
            flakyReport.add(result);
        }

        // Write JSON report
        java.nio.file.Files.createDirectories(java.nio.file.Paths.get("target/observability"));
        String json = flakyReport.stream()
                .map(m -> String.format("  { \"test\": \"%s\", \"transitions\": %d, \"verdict\": \"%s\" }",
                        m.get("test"), m.get("transitions"), m.get("verdict")))
                .collect(Collectors.joining(",\n", "[\n", "\n]"));
        java.nio.file.Files.writeString(java.nio.file.Paths.get("target/observability/flaky-report.json"), json);
        System.out.println("Flaky report written to target/observability/flaky-report.json");

        rs.close();
        stmt.close();
        conn.close();
    }

    private static String envOr(String key, String defaultVal) {
        String val = System.getenv(key);
        return val != null ? val : defaultVal;
    }
}