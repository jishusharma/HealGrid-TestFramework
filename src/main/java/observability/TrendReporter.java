package observability;

import java.sql.*;
import java.nio.file.*;
import java.util.*;

public class TrendReporter {

    private static final int DEFAULT_BUILDS = 5;

    public static void main(String[] args) throws Exception {
        int window = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_BUILDS;

        String dbHost = envOr("DB_HOST", "localhost");
        String dbPort = envOr("DB_PORT", "5432");
        String dbName = envOr("DB_NAME", "healenium");
        String dbUser = envOr("DB_USER", "healenium_user");
        String dbPass = envOr("DB_PASSWORD", "healenium_password");

        String url = "jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName;
        Connection conn = DriverManager.getConnection(url, dbUser, dbPass);

        // Find the last N distinct build numbers (latest first)
        String buildsSQL = "SELECT build FROM healenium.test_results " +
                "WHERE build IS NOT NULL " +
                "GROUP BY build " +
                "ORDER BY MAX(run_at) DESC LIMIT " + window;
        Statement s = conn.createStatement();
        ResultSet brs = s.executeQuery(buildsSQL);
        List<String> buildListDesc = new ArrayList<>();
        while (brs.next()) {
            buildListDesc.add(brs.getString("build"));
        }
        brs.close();
        s.close();

        if (buildListDesc.isEmpty()) {
            writeHtml("<html><body><h1>No builds found in the database.</h1></body></html>");
            conn.close();
            return;
        }

        // Reverse to chronological order for the chart
        List<String> buildLabels = new ArrayList<>(buildListDesc);
        Collections.reverse(buildLabels);

        // Query per-build, per-suite totals and passed counts
        String placeholders = String.join(",", Collections.nCopies(buildLabels.size(), "?"));
        String sql = "SELECT build, suite, " +
                "COUNT(*) AS total, " +
                "SUM(CASE WHEN status = 'PASSED' THEN 1 ELSE 0 END) AS passed " +
                "FROM healenium.test_results " +
                "WHERE build IN (" + placeholders + ") " +
                "GROUP BY build, suite " +
                "ORDER BY build, suite";
        PreparedStatement pstmt = conn.prepareStatement(sql);
        for (int i = 0; i < buildLabels.size(); i++) {
            pstmt.setString(i + 1, buildLabels.get(i));
        }
        ResultSet rs = pstmt.executeQuery();

        // Structure: suite -> (build -> passRate)
        Map<String, Map<String, Double>> suitePassRates = new LinkedHashMap<>();
        while (rs.next()) {
            String suite = rs.getString("suite");
            String build = rs.getString("build");
            long total = rs.getLong("total");
            long passed = rs.getLong("passed");
            double rate = total > 0 ? (passed * 100.0 / total) : 0.0;
            suitePassRates.computeIfAbsent(suite, k -> new LinkedHashMap<>()).put(build, rate);
        }
        rs.close();
        pstmt.close();
        conn.close();

        // Build HTML with Chart.js
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        html.append("<meta charset=\"UTF-8\">\n");
        html.append("<title>Trend Report - Pass Rate per Suite</title>\n");
        html.append("<script src=\"https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js\"></script>\n");
        html.append("<style>");
        html.append("body { font-family: Arial, sans-serif; margin: 40px; }");
        html.append("h1 { color: #333; }");
        html.append(".chart-container { max-width: 900px; margin: 0 auto; }");
        html.append("</style>\n");
        html.append("</head>\n<body>\n");
        html.append("<h1>Pass Rate Trend (Last ").append(window).append(" Builds)</h1>\n");
        html.append("<div class=\"chart-container\">\n");
        html.append("<canvas id=\"trendChart\" width=\"800\" height=\"400\"></canvas>\n");
        html.append("</div>\n");

        // Prepare data for JS
        StringBuilder labelsJson = new StringBuilder("[");
        for (int i = 0; i < buildLabels.size(); i++) {
            if (i > 0) labelsJson.append(", ");
            labelsJson.append("\"").append(buildLabels.get(i)).append("\"");
        }
        labelsJson.append("]");

        StringBuilder datasetsJson = new StringBuilder();
        int suiteIndex = 0;
        String[] colors = {"#4CAF50", "#2196F3", "#FF9800", "#9C27B0", "#F44336", "#00BCD4", "#FFEB3B", "#795548"};
        for (Map.Entry<String, Map<String, Double>> entry : suitePassRates.entrySet()) {
            String suite = entry.getKey();
            Map<String, Double> rates = entry.getValue();
            if (suiteIndex > 0) datasetsJson.append(", ");
            datasetsJson.append("{\n");
            datasetsJson.append("  label: \"").append(suite).append("\",\n");
            datasetsJson.append("  data: [");
            boolean first = true;
            for (String b : buildLabels) {
                if (!first) datasetsJson.append(", ");
                Double r = rates.get(b);
                datasetsJson.append(r != null ? String.format("%.1f", r) : "null");
                first = false;
            }
            datasetsJson.append("],\n");
            String color = colors[suiteIndex % colors.length];
            datasetsJson.append("  borderColor: \"").append(color).append("\",\n");
            datasetsJson.append("  backgroundColor: \"").append(color).append("33\",\n");
            datasetsJson.append("  tension: 0.2,\n");
            datasetsJson.append("  fill: false\n");
            datasetsJson.append("}");
            suiteIndex++;
        }

        html.append("<script>\n");
        html.append("const ctx = document.getElementById('trendChart').getContext('2d');\n");
        html.append("new Chart(ctx, {\n");
        html.append("  type: 'line',\n");
        html.append("  data: {\n");
        html.append("    labels: ").append(labelsJson).append(",\n");
        html.append("    datasets: [").append(datasetsJson).append("]\n");
        html.append("  },\n");
        html.append("  options: {\n");
        html.append("    responsive: true,\n");
        html.append("    scales: {\n");
        html.append("      y: { min: 0, max: 100, title: { display: true, text: 'Pass Rate (%)' } },\n");
        html.append("      x: { title: { display: true, text: 'Build' } }\n");
        html.append("    },\n");
        html.append("    plugins: {\n");
        html.append("      legend: { position: 'bottom' },\n");
        html.append("      tooltip: { callbacks: { label: ctx => ctx.raw !== null ? ctx.dataset.label + ': ' + ctx.raw.toFixed(1) + '%' : '' } }\n");
        html.append("    }\n");
        html.append("  }\n");
        html.append("});\n");
        html.append("</script>\n");
        html.append("</body>\n</html>");

        writeHtml(html.toString());
        System.out.println("Trend report written to target/observability/trend-report.html");
    }

    private static void writeHtml(String content) throws Exception {
        Files.createDirectories(Paths.get("target/observability"));
        Files.writeString(Paths.get("target/observability/trend-report.html"), content);
    }

    private static String envOr(String key, String defaultVal) {
        String val = System.getenv(key);
        return val != null ? val : defaultVal;
    }
}