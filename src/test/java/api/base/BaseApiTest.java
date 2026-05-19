package api.base;

import config.ConfigResolver;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.Assert;
import org.testng.ITestContext;

import java.util.regex.Pattern;

public abstract class BaseApiTest {
    private static final Logger LOGGER = LogManager.getLogger(BaseApiTest.class);
    private static boolean suiteInfoLogged = false;
    protected RequestSpecification requestSpec;

    protected abstract String getBaseUri();

    public void setup(ITestContext context) {
        synchronized (BaseApiTest.class) {
            if (!suiteInfoLogged) {
                suiteInfoLogged = true;
                ConfigResolver.setSuiteParameters(
                        context.getSuite().getXmlSuite().getParameters()
                );
                LOGGER.info("TestNG suite: {}", context.getSuite().getName());
                String cmd = System.getProperty("sun.java.command", "IDE or unknown");
                LOGGER.info("Command: {}", cmd);
                LOGGER.info("Execution mode: {}", ConfigResolver.get("execution", "local"));
            }
        }

        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        requestSpec = new RequestSpecBuilder()
                .setBaseUri(getBaseUri())
                .setContentType(ContentType.JSON)
                .build();
    }

    protected void assertJsonNumber(String body, String field, int expected) {
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(field) + "\\\"\\s*:\\s*" + expected + "(\\D|$)");
        Assert.assertTrue(pattern.matcher(body).find(),
                "Expected JSON number field '" + field + "' to be " + expected + ". Body: " + body);
    }

    protected void assertJsonStringPresent(String body, String field) {
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(field) + "\\\"\\s*:\\s*\\\"[^\\\"]+\\\"");
        Assert.assertTrue(pattern.matcher(body).find(),
                "Expected non-empty JSON string field '" + field + "'. Body: " + body);
    }

    protected void assertJsonStringValue(String body, String field, String expected) {
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(field) + "\\\"\\s*:\\s*\\\"" + Pattern.quote(expected) + "\\\"");
        Assert.assertTrue(pattern.matcher(body).find(),
                "Expected JSON string field '" + field + "' to be '" + expected + "'. Body: " + body);
    }
}
