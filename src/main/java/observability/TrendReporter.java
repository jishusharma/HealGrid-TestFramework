package observability;

import java.sql.*;
import java.util.*;

public class TrendReporter {

    private static final int DEFAULT_BUILDS = 5;

    public static void main(String[] args) throws Exception {
        int builds = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_BUILDS;

        String dbHost = envOr("DB_HOST", "localhost");
        String dbPort = envOr("DB_PORT", "5432");
        String dbName = envOr("DB_NAME", "healenium");
        String dbUser = envOr("DB_USER", "healenium_user");
        String dbPass = envOr("DB_PASSWORD", "healenium_password");

        String url = "jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName;
        Connection conn = DriverManager.getConnection(url, dbUser, dbPass);

        // Find the last N distinct build numbers (ordered by latest run_at)
        String buildsSQL = "SELECT build FROM healenium.test_results " +
                "WHERE build IS NOT NULL " +
                "GROUP BY build " +
                "ORDER BY MAX(run_at) DESC LIMIT " + builds;
        Statement s = conn.createStatement();
        ResultSet brs = s.executeQuery(buildsSQL);
        List<String> buildList = new ArrayList<>();
        while (brs.next()) {
            buildList.add(brs.getString("build"));
        }
        brs.close();
        s.close();

        if (buildList.isEmpty()) {
            java.nio.file.Files.createDirectories(java.nio.file.Paths.get("target/observability"));
            java.nio.file.Files.writeString(java.nio.file.Paths.get("target/observability/trend-report.txt"),
                    "No builds found in the database.\n");
            System.out.println("No builds found.");
            conn.close();
            return;
        }

        // Build a parameterised query with the list of builds
        String placeholders = String.join(",", Collections.nCopies(buildList.size(), "?"));
        String sql = "SELECT suite, status, COUNT(*) AS cnt FROM healenium.test_results " +
                "WHERE build IN (" + placeholders + ") " +
                "GROUP BY suite, status ORDER BY suite, status";
        PreparedStatement pstmt = conn.prepareStatement(sql);
        for (int i = 0; i < buildList.size(); i++) {
            pstmt.setString(i + 1, buildList.get(i));
        }
        ResultSet rs = pstmt.executeQuery();

        Map<String, Map<String, Long>> suiteData = new LinkedHashMap<>();
        while (rs.next()) {
            String suite = rs.getString("suite");
            String status = rs.getString("status");
            long cnt = rs.getLong("cnt");
            suiteData.computeIfAbsent(suite, k -> new HashMap<>()).put(status, cnt);
        }

        StringBuilder report = new StringBuilder();
        report.append("Trend over last ").append(builds).append(" builds (").append(buildList.get(0)).append(" ... ").append(buildList.get(buildList.size() - 1)).append(")\n\n");
        report.append(String.format("%-15s | %-8s | %s%n", "Suite", "Pass Rate", "Total Runs"));
        report.append("-".repeat(45)).append("\n");
        for (Map.Entry<String, Map<String, Long>> entry : suiteData.entrySet()) {
            String suite = entry.getKey();
            Map<String, Long> counts = entry.getValue();
            long total = counts.values().stream().mapToLong(Long::longValue).sum();
            long passed = counts.getOrDefault("PASSED", 0L);
            String passRate = total > 0 ? String.format("%.0f%%", (double) passed / total * 100) : "N/A";
            report.append(String.format("%-15s | %-8s | %d%n", suite, passRate, total));
        }

        java.nio.file.Files.createDirectories(java.nio.file.Paths.get("target/observability"));
        java.nio.file.Files.writeString(java.nio.file.Paths.get("target/observability/trend-report.txt"), report.toString());
        System.out.println("Trend report written to target/observability/trend-report.txt");

        rs.close();
        pstmt.close();
        conn.close();
    }

    private static String envOr(String key, String defaultVal) {
        String val = System.getenv(key);
        return val != null ? val : defaultVal;
    }
}