package base;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;
import org.testng.annotations.*;
import utils.GenericUtil;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Properties;
import java.lang.reflect.Method;

public class BaseTest {
    protected static Properties config;
    protected static final String CONFIG_PROP = "config.properties";
    protected static final Logger LOGGER = LogManager.getLogger(BaseTest.class);
    private ThreadLocal<String> driverName = new ThreadLocal<>();

    @BeforeSuite(alwaysRun = true)
    public void setUpSuite() {
        config = GenericUtil.getPropertiesFile(CONFIG_PROP);

        String execution = System.getProperty("execution", "local");
        boolean healEnabled = Boolean.parseBoolean(
                System.getProperty("heal.enabled", config.getProperty("heal.enabled", "true")));

        if (!"browserstack".equalsIgnoreCase(execution) && healEnabled) {
            String healeniumHost = System.getProperty("healenium.host", "localhost");
            try {
                URL url = new URL("http://" + healeniumHost + ":7878/healenium/report");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(2000);
                conn.setReadTimeout(2000);
                conn.connect();
                if (conn.getResponseCode() != 200) {
                    throw new RuntimeException("Healenium backend not ready");
                }
                LOGGER.info("Healenium backend is reachable.");
            } catch (Exception e) {
                throw new RuntimeException("Healenium not reachable", e);
            }
        }
    }

    @Parameters({"browser"})
    @BeforeMethod(alwaysRun = true)
    public void setUp(@Optional("chrome") String browser, Method method) {
        WebDriver driver = DriverFactory.createDriver(method.getName(), browser);
        DriverManager.setDriver(driver);

        String name = "T" + Thread.currentThread().getId();
        driverName.set(name);
        LOGGER.info("Setting up: {} | browser: {} | thread: {}",
                method.getName(), browser, Thread.currentThread().getId());

        boolean isHeadless = Boolean.parseBoolean(System.getProperty("headless", "false"));
        if (!isHeadless) {
            driver.manage().window().maximize();
        }
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown(Method method) {
        LOGGER.info("Tearing down: {} | thread: {}",
                method.getName(), Thread.currentThread().getId());
        DriverManager.quitDriver();
    }

    public String getDriverName() {
        return driverName.get();
    }
}