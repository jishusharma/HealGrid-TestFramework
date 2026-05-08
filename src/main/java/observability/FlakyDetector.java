package observability;

import java.sql.*;
import java.nio.file.*;
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

        String sql = "SELECT test_name, status, run_at FROM healenium.test_results " +
                "WHERE status IN ('PASSED', 'FAILED') " +
                (suiteFilter != null ? "AND suite = ? " : "") +
                "ORDER BY test_name, run_at DESC";
        PreparedStatement stmt = conn.prepareStatement(sql);
        if (suiteFilter != null) {
            stmt.setString(1, suiteFilter);
        }
        ResultSet rs = stmt.executeQuery();

        Map<String, List<String>> testHistory = new LinkedHashMap<>();
        while (rs.next()) {
            String testName = rs.getString("test_name");
            String status = rs.getString("status");
            testHistory.computeIfAbsent(testName, k -> new ArrayList<>()).add(status);
        }

        testHistory.forEach((test, statuses) -> {
            if (statuses.size() > window) {
                statuses.subList(window, statuses.size()).clear();
            }
        });

        List<Map<String, Object>> flakyResults = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : testHistory.entrySet()) {
            String test = entry.getKey();
            List<String> statuses = entry.getValue();
            int transitions = countTransitions(statuses);
            String verdict = transitions > 0 ? "FLAKY" : "STABLE";

            // Status change detection: compare with previous window (without most recent run)
            String change = "—";
            if (statuses.size() > 1) {
                List<String> prevStatuses = statuses.subList(1, statuses.size());
                int prevTransitions = countTransitions(prevStatuses);
                if (prevTransitions == 0 && transitions > 0) {
                    change = "🆕 NEW";
                } else if (prevTransitions > 0 && transitions == 0) {
                    change = "✅ RESOLVED";
                }
            }

            List<String> chronological = new ArrayList<>(statuses);
            Collections.reverse(chronological);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("test", test);
            result.put("transitions", transitions);
            result.put("verdict", verdict);
            result.put("history", String.join(" → ", chronological));
            result.put("change", change);
            flakyResults.add(result);
        }

        // Generate HTML with summary and colour‑coded table
        long flakyCount = flakyResults.stream().filter(r -> "FLAKY".equals(r.get("verdict"))).count();
        long stableCount = flakyResults.size() - flakyCount;
        double flakyPct = flakyResults.size() > 0 ? (flakyCount * 100.0 / flakyResults.size()) : 0.0;

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        html.append("<meta charset=\"UTF-8\">\n");
        html.append("<title>Flaky Test Report</title>\n");
        html.append("<style>");
        html.append("body { font-family: Arial, sans-serif; margin: 40px; }");
        html.append("h1 { color: #333; }");
        html.append(".summary { font-size: 1.1em; margin-bottom: 20px; }");
        html.append(".summary .flaky { color: #FF9800; font-weight: bold; }");
        html.append(".summary .stable { color: #4CAF50; font-weight: bold; }");
        html.append("table { border-collapse: collapse; width: 100%; max-width: 1100px; }");
        html.append("th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }");
        html.append("th { background-color: #f2f2f2; }");
        html.append(".stable { background-color: #e8f5e9; }");
        html.append(".flaky { background-color: #fff3e0; }");
        html.append(".badge { padding: 4px 8px; border-radius: 12px; font-weight: bold; }");
        html.append(".badge-stable { background-color: #4CAF50; color: white; }");
        html.append(".badge-flaky { background-color: #FF9800; color: white; }");
        html.append(".badge-new { background-color: #F44336; color: white; font-size: 0.8em; margin-left: 5px; }");
        html.append(".badge-resolved { background-color: #2196F3; color: white; font-size: 0.8em; margin-left: 5px; }");
        html.append("</style>\n");
        html.append("</head>\n<body>\n");
        html.append("<h1>Flaky Test Detection (Last ").append(window).append(" Builds)</h1>\n");

        html.append("<div class=\"summary\">");
        html.append("<span class=\"flaky\">").append(flakyCount).append(" flaky</span> / ");
        html.append("<span class=\"stable\">").append(stableCount).append(" stable</span>");
        html.append(" (").append(String.format("%.1f", flakyPct)).append("% flaky)");
        html.append("</div>\n");

        html.append("<table>\n");
        html.append("<tr><th>Test</th><th>Status</th><th>Transitions</th><th>Recent History</th><th>Status Change</th></tr>\n");

        for (Map<String, Object> row : flakyResults) {
            String verdict = (String) row.get("verdict");
            String rowClass = "STABLE".equals(verdict) ? "stable" : "flaky";
            String badgeClass = "STABLE".equals(verdict) ? "badge-stable" : "badge-flaky";
            String change = (String) row.get("change");
            String changeBadge = "";
            if ("🆕 NEW".equals(change)) {
                changeBadge = "<span class=\"badge badge-new\">NEW</span>";
            } else if ("✅ RESOLVED".equals(change)) {
                changeBadge = "<span class=\"badge badge-resolved\">RESOLVED</span>";
            }
            html.append("<tr class=\"").append(rowClass).append("\">");
            html.append("<td>").append(row.get("test")).append("</td>");
            html.append("<td><span class=\"badge ").append(badgeClass).append("\">").append(verdict).append("</span></td>");
            html.append("<td>").append(row.get("transitions")).append("</td>");
            html.append("<td>").append(row.get("history")).append("</td>");
            html.append("<td>").append(changeBadge).append("</td>");
            html.append("</tr>\n");
        }

        html.append("</table>\n");
        html.append("</body>\n</html>");

        Files.createDirectories(Paths.get("target/observability"));
        Files.writeString(Paths.get("target/observability/flaky-report.html"), html.toString());
        System.out.println("Flaky report written to target/observability/flaky-report.html");

        rs.close();
        stmt.close();
        conn.close();
    }

    private static int countTransitions(List<String> statuses) {
        int transitions = 0;
        for (int i = 0; i < statuses.size() - 1; i++) {
            if (!statuses.get(i).equals(statuses.get(i + 1))) {
                transitions++;
            }
        }
        return transitions;
    }

    private static String envOr(String key, String defaultVal) {
        String val = System.getenv(key);
        return val != null ? val : defaultVal;
    }
}