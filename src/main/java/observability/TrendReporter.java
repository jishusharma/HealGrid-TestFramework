package observability;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class TrendReporter {

    public static void main(String[] args) throws Exception {
        String dbHost = envOr("DB_HOST", "localhost");
        String dbPort = envOr("DB_PORT", "5432");
        String dbName = envOr("DB_NAME", "healenium");
        String dbUser = envOr("DB_USER", "healenium_user");
        String dbPass = envOr("DB_PASSWORD", "healenium_password");

        String url = "jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName;
        Connection conn = DriverManager.getConnection(url, dbUser, dbPass);

        List<TrendRow> rows = loadTrendRows(conn);
        List<CategoryRow> categoryRows = loadCategoryRows(conn);
        conn.close();

        if (rows.isEmpty()) {
            writeHtml("<html><body><h1>No builds found in the database.</h1></body></html>");
            return;
        }

        String html = buildHtml(rows, categoryRows);
        writeHtml(html);
        System.out.println("Trend report written to target/observability/trend-report.html");
    }

    private static List<TrendRow> loadTrendRows(Connection conn) throws Exception {
        String sql = "SELECT COALESCE(NULLIF(build, ''), 'untracked-local') AS build_label, " +
                "suite, " +
                "COUNT(*) AS total, " +
                "SUM(CASE WHEN status = 'PASSED' THEN 1 ELSE 0 END) AS passed, " +
                "SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) AS failed, " +
                "SUM(CASE WHEN status = 'SKIPPED' THEN 1 ELSE 0 END) AS skipped, " +
                "SUM(CASE WHEN failure_category = 'INFRA' THEN 1 ELSE 0 END) AS infra, " +
                "MAX(run_at) AS last_run " +
                "FROM healenium.test_results " +
                "GROUP BY build_label, suite " +
                "ORDER BY MAX(run_at), build_label, suite";

        List<TrendRow> rows = new ArrayList<>();
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                TrendRow row = new TrendRow();
                row.build = rs.getString("build_label");
                row.suite = nullToUnknown(rs.getString("suite"));
                row.total = rs.getLong("total");
                row.passed = rs.getLong("passed");
                row.failed = rs.getLong("failed");
                row.skipped = rs.getLong("skipped");
                row.infra = rs.getLong("infra");
                row.passRate = row.total > 0 ? row.passed * 100.0 / row.total : 0.0;
                rows.add(row);
            }
        }
        return rows;
    }

    private static List<CategoryRow> loadCategoryRows(Connection conn) throws Exception {
        String sql = "SELECT COALESCE(NULLIF(build, ''), 'untracked-local') AS build_label, " +
                "suite, COALESCE(failure_category, 'UNCLASSIFIED') AS failure_category, COUNT(*) AS count " +
                "FROM healenium.test_results " +
                "WHERE status <> 'PASSED' " +
                "GROUP BY build_label, suite, failure_category " +
                "ORDER BY MAX(run_at) DESC, build_label, suite, failure_category";

        List<CategoryRow> rows = new ArrayList<>();
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                CategoryRow row = new CategoryRow();
                row.build = rs.getString("build_label");
                row.suite = nullToUnknown(rs.getString("suite"));
                row.category = rs.getString("failure_category");
                row.count = rs.getLong("count");
                rows.add(row);
            }
        }
        return rows;
    }

    private static String buildHtml(List<TrendRow> rows, List<CategoryRow> categoryRows) {
        Set<String> labelSet = new LinkedHashSet<>();
        Set<String> suiteSet = new LinkedHashSet<>();
        Map<String, Map<String, Double>> ratesBySuite = new LinkedHashMap<>();

        for (TrendRow row : rows) {
            labelSet.add(row.build);
            suiteSet.add(row.suite);
            ratesBySuite.computeIfAbsent(row.suite, ignored -> new LinkedHashMap<>()).put(row.build, row.passRate);
        }

        List<String> labels = new ArrayList<>(labelSet);
        List<String> suites = new ArrayList<>(suiteSet);

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        html.append("<meta charset=\"UTF-8\">\n");
        html.append("<title>Trend Report - Pass Rate per Suite</title>\n");
        html.append("<script src=\"https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js\"></script>\n");
        html.append("<style>");
        html.append("body { font-family: Arial, sans-serif; margin: 40px; color: #222; }");
        html.append("h1, h2 { color: #333; }");
        html.append(".controls { margin: 16px 0 24px; }");
        html.append("button { margin-right: 8px; padding: 8px 12px; border: 1px solid #aaa; background: #fff; cursor: pointer; }");
        html.append("button.active { background: #1f6feb; color: white; border-color: #1f6feb; }");
        html.append(".chart-container { max-width: 1000px; margin-bottom: 32px; }");
        html.append("table { border-collapse: collapse; width: 100%; max-width: 1200px; margin-bottom: 32px; }");
        html.append("th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }");
        html.append("th { background-color: #f2f2f2; }");
        html.append(".num { text-align: right; }");
        html.append("</style>\n");
        html.append("</head>\n<body>\n");
        html.append("<h1>Pass Rate Trend</h1>\n");
        html.append("<div class=\"controls\">\n");
        html.append("<button id=\"btn5\" class=\"active\" onclick=\"renderTrend(5)\">Last 5</button>\n");
        html.append("<button id=\"btn10\" onclick=\"renderTrend(10)\">Last 10</button>\n");
        html.append("<button id=\"btnAll\" onclick=\"renderTrend(0)\">All Builds</button>\n");
        html.append("</div>\n");
        html.append("<div class=\"chart-container\"><canvas id=\"trendChart\" width=\"900\" height=\"420\"></canvas></div>\n");

        appendBuildSummaryTable(html, rows);
        appendCategoryTable(html, categoryRows);
        appendTrendScript(html, labels, suites, ratesBySuite);

        html.append("</body>\n</html>");
        return html.toString();
    }

    private static void appendBuildSummaryTable(StringBuilder html, List<TrendRow> rows) {
        html.append("<h2>All Build Summary</h2>\n");
        html.append("<table>\n");
        html.append("<tr><th>Build</th><th>Suite</th><th>Total</th><th>Passed</th><th>Failed</th><th>Skipped</th><th>Infra Failures</th><th>Pass Rate</th></tr>\n");
        for (TrendRow row : rows) {
            html.append("<tr>");
            html.append("<td>").append(escape(row.build)).append("</td>");
            html.append("<td>").append(escape(row.suite)).append("</td>");
            html.append("<td class=\"num\">").append(row.total).append("</td>");
            html.append("<td class=\"num\">").append(row.passed).append("</td>");
            html.append("<td class=\"num\">").append(row.failed).append("</td>");
            html.append("<td class=\"num\">").append(row.skipped).append("</td>");
            html.append("<td class=\"num\">").append(row.infra).append("</td>");
            html.append("<td class=\"num\">").append(format(row.passRate)).append("%</td>");
            html.append("</tr>\n");
        }
        html.append("</table>\n");
    }

    private static void appendCategoryTable(StringBuilder html, List<CategoryRow> rows) {
        html.append("<h2>Failure Category Summary</h2>\n");
        if (rows.isEmpty()) {
            html.append("<p>No non-passed results are currently persisted.</p>\n");
            return;
        }
        html.append("<table>\n");
        html.append("<tr><th>Build</th><th>Suite</th><th>Failure Category</th><th>Count</th></tr>\n");
        for (CategoryRow row : rows) {
            html.append("<tr>");
            html.append("<td>").append(escape(row.build)).append("</td>");
            html.append("<td>").append(escape(row.suite)).append("</td>");
            html.append("<td>").append(escape(row.category)).append("</td>");
            html.append("<td class=\"num\">").append(row.count).append("</td>");
            html.append("</tr>\n");
        }
        html.append("</table>\n");
    }

    private static void appendTrendScript(StringBuilder html, List<String> labels, List<String> suites,
                                          Map<String, Map<String, Double>> ratesBySuite) {
        String[] colors = {"#4CAF50", "#2196F3", "#FF9800", "#9C27B0", "#F44336", "#00BCD4", "#795548", "#607D8B"};

        html.append("<script>\n");
        html.append("const allLabels = ").append(toJsArray(labels)).append(";\n");
        html.append("const suiteData = {\n");
        for (int i = 0; i < suites.size(); i++) {
            String suite = suites.get(i);
            html.append("  \"").append(jsEscape(suite)).append("\": [");
            for (int j = 0; j < labels.size(); j++) {
                if (j > 0) html.append(", ");
                Double rate = ratesBySuite.getOrDefault(suite, Map.of()).get(labels.get(j));
                html.append(rate == null ? "null" : format(rate));
            }
            html.append("]");
            html.append(i < suites.size() - 1 ? ",\n" : "\n");
        }
        html.append("};\n");
        html.append("const colors = ").append(toJsArray(List.of(colors))).append(";\n");
        html.append("let chart;\n");
        html.append("function setActive(windowSize) {\n");
        html.append("  document.getElementById('btn5').classList.toggle('active', windowSize === 5);\n");
        html.append("  document.getElementById('btn10').classList.toggle('active', windowSize === 10);\n");
        html.append("  document.getElementById('btnAll').classList.toggle('active', windowSize === 0);\n");
        html.append("}\n");
        html.append("function renderTrend(windowSize) {\n");
        html.append("  setActive(windowSize);\n");
        html.append("  const start = windowSize === 0 ? 0 : Math.max(allLabels.length - windowSize, 0);\n");
        html.append("  const labels = allLabels.slice(start);\n");
        html.append("  const datasets = Object.keys(suiteData).map((suite, idx) => ({\n");
        html.append("    label: suite,\n");
        html.append("    data: suiteData[suite].slice(start),\n");
        html.append("    borderColor: colors[idx % colors.length],\n");
        html.append("    backgroundColor: colors[idx % colors.length] + '33',\n");
        html.append("    tension: 0.2,\n");
        html.append("    fill: false\n");
        html.append("  }));\n");
        html.append("  datasets.push({ label: '95% Threshold', data: labels.map(() => 95), borderColor: '#F44336', borderWidth: 1, borderDash: [5, 5], pointRadius: 0, fill: false });\n");
        html.append("  if (chart) chart.destroy();\n");
        html.append("  chart = new Chart(document.getElementById('trendChart').getContext('2d'), {\n");
        html.append("    type: 'line', data: { labels, datasets },\n");
        html.append("    options: { responsive: true, scales: { y: { min: 0, max: 100, title: { display: true, text: 'Pass Rate (%)' } }, x: { title: { display: true, text: 'Build' } } }, plugins: { legend: { position: 'bottom' } } }\n");
        html.append("  });\n");
        html.append("}\n");
        html.append("renderTrend(5);\n");
        html.append("</script>\n");
    }

    private static String toJsArray(List<String> values) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) builder.append(", ");
            builder.append("\"").append(jsEscape(values.get(i))).append("\"");
        }
        builder.append("]");
        return builder.toString();
    }

    private static void writeHtml(String content) throws Exception {
        Files.createDirectories(Paths.get("target/observability"));
        Files.writeString(Paths.get("target/observability/trend-report.html"), content);
    }

    private static String envOr(String key, String defaultVal) {
        String val = System.getenv(key);
        return val != null ? val : defaultVal;
    }

    private static String nullToUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private static String format(double value) {
        return String.format(Locale.US, "%.1f", value);
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String jsEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static class TrendRow {
        private String build;
        private String suite;
        private long total;
        private long passed;
        private long failed;
        private long skipped;
        private long infra;
        private double passRate;
    }

    private static class CategoryRow {
        private String build;
        private String suite;
        private String category;
        private long count;
    }
}
