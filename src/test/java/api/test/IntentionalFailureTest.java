package api.test;

import org.openqa.selenium.StaleElementReferenceException;
import org.testng.annotations.Test;

import java.net.Socket;

import static org.testng.Assert.assertEquals;

public class IntentionalFailureTest {

    // Failure Type 1: Flaky — simulates stale element timing issue
    @Test
    public void flaky_staleElement_shouldRerun() {
        throw new StaleElementReferenceException(
                "Element is no longer attached to the DOM — timing issue during page transition"
        );
    }

    // Failure Type 2: Environment — connection refused to a service that is not running
    @Test
    public void environment_serviceDown_shouldRerun() throws Exception {
        new Socket("localhost", 9999).close();
    }

    // Failure Type 3: Product Bug — app returns wrong value, retry will not fix it
    @Test
    public void productBug_wrongAppBehaviour_shouldSkip() {
        String actualAppResponse = "Free plan limit reached";
        assertEquals(actualAppResponse, "Welcome back!",
                "App returned unexpected response — product bug, not a test issue"
        );
    }

    // Failure Type 4: Test Code Bug — wrong expected value in assertion, retry will not fix it
    @Test
    public void testCodeBug_wrongExpectedValue_shouldSkip() {
        int actualStatusCode = 200;
        assertEquals(actualStatusCode, 201,
                "Wrong expected status code — test code bug, assertion is incorrect"
        );
    }
}