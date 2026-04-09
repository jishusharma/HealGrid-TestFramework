package com.demo.utils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class PageObjectGenerator {
    private static final String OUTPUT_DIR = "generated-page-objects";
    private static final Set<String> INTERACTIVE_ATTRIBUTES = new HashSet<>(Arrays.asList(
            "type", "name", "title", "aria-label", "placeholder", "value", "for"
    ));

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java -jar page-object-generator.jar <url>");
            return;
        }
        generatePageObjects(args[0]);
    }

    public static void generatePageObjects(String url) {
        try {
            // Create output directory if it doesn't exist
            Files.createDirectories(Paths.get(OUTPUT_DIR));

            // Connect to the URL and get the HTML document
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .get();

            // Prepare output file
            String className = generateClassName(url);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path outputPath = Paths.get(OUTPUT_DIR, className + "_" + timestamp + ".java");

            // Generate and write the page object class
            try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
                writePackageAndImports(writer);
                writer.write("public class " + className + " {\n\n");

                // Process different types of elements
                processElements(doc, writer);

                // Add constructor
                writer.write("    public " + className + "(WebDriver driver) {\n");
                writer.write("        PageFactory.initElements(driver, this);\n");
                writer.write("    }\n");

                writer.write("}\n");
            }

            System.out.println("Page object class generated successfully at: " + outputPath);

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private static void writePackageAndImports(BufferedWriter writer) throws IOException {
        writer.write("package com.demo.pages;\n\n");
        writer.write("import org.openqa.selenium.WebDriver;\n");
        writer.write("import org.openqa.selenium.WebElement;\n");
        writer.write("import org.openqa.selenium.support.FindBy;\n");
        writer.write("import org.openqa.selenium.support.PageFactory;\n\n");
    }

    private static void processElements(Document doc, BufferedWriter writer) throws IOException {
        Map<String, Integer> elementNameCount = new HashMap<>();

        // Process interactive elements
        processElementGroup(doc, "button", elementNameCount, writer);
        processElementGroup(doc, "input", elementNameCount, writer);
        processElementGroup(doc, "a", elementNameCount, writer);
        processElementGroup(doc, "select", elementNameCount, writer);
        processElementGroup(doc, "textarea", elementNameCount, writer);
        processElementGroup(doc, "form", elementNameCount, writer);
        processElementGroup(doc, "label", elementNameCount, writer);
    }

    private static void processElementGroup(Document doc, String tagName, Map<String, Integer> nameCount,
                                            BufferedWriter writer) throws IOException {
        Elements elements = doc.getElementsByTag(tagName);

        for (Element element : elements) {
            if (isInteractiveElement(element)) {
                String variableName = generateVariableName(element, tagName, nameCount);
                String xpath = generateRobustXPath(element);

                // Write element with documentation
                writer.write("    /**\n");
                writer.write("     * " + generateElementDescription(element, tagName) + "\n");
                writer.write("     * Generated from: " + getElementContext(element) + "\n");
                writer.write("     */\n");
                writer.write("    @FindBy(xpath = \"" + xpath + "\")\n");
                writer.write("    private WebElement " + variableName + ";\n\n");
            }
        }
    }

    private static boolean isInteractiveElement(Element element) {
        // Check if element has any of our target attributes
        for (String attr : INTERACTIVE_ATTRIBUTES) {
            if (element.hasAttr(attr)) {
                return true;
            }
        }
        // Check if element has text content
        return !element.text().trim().isEmpty();
    }

    private static String generateElementDescription(Element element, String tagName) {
        StringBuilder description = new StringBuilder();
        description.append(tagName.substring(0, 1).toUpperCase()).append(tagName.substring(1));
        description.append(" element");

        // Add relevant attributes to description
        for (String attr : INTERACTIVE_ATTRIBUTES) {
            if (element.hasAttr(attr)) {
                description.append(", ").append(attr).append("=\"").append(element.attr(attr)).append("\"");
            }
        }

        if (!element.text().trim().isEmpty()) {
            description.append(", text=\"").append(element.text().trim()).append("\"");
        }

        return description.toString();
    }

    private static String getElementContext(Element element) {
        String parentTag = element.parent().tagName();
        String parentClass = element.parent().attr("class");
        return parentTag + (parentClass.isEmpty() ? "" : "." + parentClass);
    }

    private static String generateClassName(String url) {
        String domain = url.replaceAll("https?://", "")
                .replaceAll("www.", "")
                .split("/")[0]
                .replaceAll("[^a-zA-Z0-9]", "");
        return domain.substring(0, 1).toUpperCase() + domain.substring(1) + "Page";
    }

    private static String generateVariableName(Element element, String tagType, Map<String, Integer> nameCount) {
        String baseName = "";

        // Try to generate name from semantic attributes
        if (element.hasAttr("id")) {
            baseName = element.attr("id");
        } else if (element.hasAttr("name")) {
            baseName = element.attr("name");
        } else if (element.hasAttr("aria-label")) {
            baseName = element.attr("aria-label");
        } else if (element.hasAttr("title")) {
            baseName = element.attr("title");
        } else if (!element.text().trim().isEmpty()) {
            baseName = element.text().trim();
        }

        // Clean up the name
        baseName = baseName.replaceAll("[^a-zA-Z0-9\\s]", "")
                .replaceAll("\\s+", "");

        if (baseName.isEmpty()) {
            baseName = tagType;
        }

        // Ensure valid Java identifier
        baseName = baseName.substring(0, 1).toLowerCase() + baseName.substring(Math.min(baseName.length(), 1));

        // Handle duplicates
        String finalName = baseName;
        nameCount.merge(finalName, 1, Integer::sum);
        if (nameCount.get(finalName) > 1) {
            finalName += nameCount.get(finalName);
        }

        return finalName + tagType.substring(0, 1).toUpperCase() + tagType.substring(1);
    }

    private static String generateRobustXPath(Element element) {
        StringBuilder xpath = new StringBuilder();

        // Start with the element tag
        xpath.append("//").append(element.tagName());

        // Add unique identifiers if available
        if (element.hasAttr("id")) {
            return xpath.append("[@id='").append(element.attr("id")).append("']").toString();
        }

        // Build complex xpath using multiple attributes
        List<String> conditions = new ArrayList<>();

        if (element.hasAttr("name")) {
            conditions.add("@name='" + element.attr("name") + "'");
        }

        if (element.hasAttr("class")) {
            for (String className : element.attr("class").split("\\s+")) {
                if (!className.isEmpty()) {
                    conditions.add("contains(@class, '" + className + "')");
                }
            }
        }

        if (element.hasAttr("type")) {
            conditions.add("@type='" + element.attr("type") + "'");
        }

        String text = element.text().trim();
        if (!text.isEmpty()) {
            conditions.add("normalize-space(.)='" + text + "'");
        }

        if (!conditions.isEmpty()) {
            xpath.append("[");
            xpath.append(String.join(" and ", conditions));
            xpath.append("]");
        }

        return xpath.toString();
    }
}