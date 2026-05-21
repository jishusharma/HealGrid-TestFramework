package api.test;

import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.testng.annotations.Test;

import java.net.Socket;

import static org.testng.Assert.assertEquals;

public class IntentionalFailureTest {

    // Category: UI_INTERACTION - stale element/timing issue.
    @Test
    public void uiInteraction_staleElement_shouldClassifyUiInteraction() {
        throw new StaleElementReferenceException(
                "Element is no longer attached to the DOM - timing issue during page transition"
        );
    }

    // Category: INFRA - connection refused to a service that is not running.
    @Test
    public void infra_serviceDown_shouldClassifyInfra() throws Exception {
        new Socket("localhost", 9999).close();
    }

    // Category: ASSERTION - app returns the wrong value; retry will not fix it.
    @Test
    public void assertion_productBehavior_shouldClassifyAssertion() {
        String actualAppResponse = "Free plan limit reached";
        assertEquals(actualAppResponse, "Welcome back!",
                "App returned unexpected response - product bug, not a test issue"
        );
    }

    // Category: ASSERTION - test expected value is wrong; retry will not fix it.
    @Test
    public void assertion_wrongExpectedValue_shouldClassifyAssertion() {
        int actualStatusCode = 200;
        assertEquals(actualStatusCode, 201,
                "Wrong expected status code - test code bug, assertion is incorrect"
        );
    }

    // Category: CONFIG - missing/invalid credential or environment value.
    @Test
    public void config_missingCredential_shouldClassifyConfig() {
        throw new IllegalStateException(
                "Missing env variable API key credential - invalid configuration"
        );
    }

    // Category: SETUP - suite/bootstrap failure before test intent is reached.
    @Test
    public void setup_beforeMethodFailure_shouldClassifySetup() {
        throw new RuntimeException(
                "BeforeMethod setup failed while preparing test data"
        );
    }

    // Category: TIMEOUT - wait condition did not complete in time.
    @Test
    public void timeout_waitCondition_shouldClassifyTimeout() {
        throw new TimeoutException(
                "Timed out waiting for element visibility"
        );
    }

    // Category: SKIPPED_DEPENDENCY - TestNG skipped because a dependency failed.
    @Test(dependsOnMethods = "setup_beforeMethodFailure_shouldClassifySetup")
    public void skipped_dependency_shouldClassifySkippedDependency() {
        // TestNG should skip this method before the body executes.
    }
}
