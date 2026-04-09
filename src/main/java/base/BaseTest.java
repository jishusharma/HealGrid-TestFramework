package base;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;
import org.testng.annotations.*;
import utils.GenericUtil;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class BaseTest {
    protected static final ThreadLocal<WebDriver> threadLocalDriver = new ThreadLocal<>();
    protected static final String TARGET = "target";
    protected static Properties config;
    protected static final String CONFIG_PROP = "config.properties";
    protected static final Logger LOGGER = LogManager.getLogger(BaseTest.class);
    private static WebDriverPool webDriverPool;
    private static final AtomicInteger driverCounter = new AtomicInteger(0);
    private static final ReentrantLock setupLock = new ReentrantLock();
    private static final ReentrantLock teardownLock = new ReentrantLock();

    @BeforeSuite(alwaysRun = true)
    public void setUpSuite() {
        String healeniumHost = System.getProperty("healenium.host", "localhost");
        try {
            URL url = new URL("http://" + healeniumHost + ":7878/healenium/report");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            conn.connect();
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                String msg = "Healenium backend not ready (HTTP " + responseCode + "). Please start Docker containers.";
                LOGGER.error(msg);
                throw new RuntimeException(msg);
            }
            LOGGER.info("Healenium backend is reachable.");
        } catch (Exception e) {
            String msg = "Healenium backend is not reachable at " + healeniumHost + ":7878. Please start Docker containers. Error: " + e.getMessage();
            LOGGER.error(msg);
            throw new RuntimeException(msg, e);
        }
        // --- original setup continues ---
        config = GenericUtil.getPropertiesFile(CONFIG_PROP);
        webDriverPool = new WebDriverPool(CustomThreadSafeDriver.DriverType.CHROME, 10, 5);
    }

    @BeforeMethod(alwaysRun = true)
    public void setUp() throws Exception {
        setupLock.lock();
        try {
            LOGGER.info("Setting up test method");
            WebDriver driver = webDriverPool.borrowDriver();
            threadLocalDriver.set(driver);
            LOGGER.debug("DRIVER NAME = " + getDriverName());
        } finally {
            setupLock.unlock();
        }

        WebDriver driver = getDriver();
        driver.get(config.getProperty("DemoQAMainPageUrl"));
        if (Boolean.parseBoolean(System.getProperty("headless", "false"))) {
            // window size already set via ChromeOptions --window-size
        } else {
            driver.manage().window().maximize();
        }
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        WebDriver driver = threadLocalDriver.get();
        if (driver != null) {
            teardownLock.lock();
            try {
                webDriverPool.returnDriver(driver);
            } finally {
                teardownLock.unlock();
            }
        }
        threadLocalDriver.remove();
    }

    @AfterSuite(alwaysRun = true)
    public void tearDownSuite() {
        if (webDriverPool != null) {
            webDriverPool.close();
        }
    }

    protected WebDriver getDriver() {
        return threadLocalDriver.get();
    }

    public String getDriverName() {
        return getDriver().getClass().getSimpleName() + "-" + Thread.currentThread().getId();
    }

    public static void incrementDriverCounter() {
        driverCounter.incrementAndGet();
    }
}