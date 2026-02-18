package framework.driver;

import framework.config.FrameworkConstants;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.time.Duration;

/**
 * Creates WebDriver instances with stable defaults for scraping runs.
 */
public final class DriverFactory {

	private DriverFactory() {
		// Utility class
	}

	/**
	 * Initializes Chrome WebDriver with configured window size and headless toggle.
	 * Uses explicit waits only (no implicit wait).
	 *
	 * @return initialized WebDriver
	 */
	public static WebDriver initDriver() {
		WebDriverManager.chromedriver().setup();

		ChromeOptions options = new ChromeOptions();
		options.setPageLoadStrategy(PageLoadStrategy.NORMAL);

//		// Window sizing for predictable layout/locators
//		options.addArguments(
//				"--window-size=" + FrameworkConstants.WINDOW_WIDTH + "," + FrameworkConstants.WINDOW_HEIGHT);

		// Safe stability flags
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-notifications");
		options.addArguments("--disable-gpu");

		if (FrameworkConstants.HEADLESS) {
			options.addArguments("--headless=new");
		}

		// Browser-level scaling that persists across navigations
		options.addArguments("--force-device-scale-factor=" + FrameworkConstants.WINDOW_ZOOM);

		// Reduce automation banners/overlays
		options.setExperimentalOption("excludeSwitches", new String[] { "enable-automation" });
		options.setExperimentalOption("useAutomationExtension", false);

		WebDriver driver = new ChromeDriver(options);
		driver.manage().window().maximize();
		driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(60));
		driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(30));

		// Apply CSS zoom once (extra safety; some sites override rendering)
		try {
			((JavascriptExecutor) driver).executeScript(
					"document.documentElement.style.zoom = '" + (int) (FrameworkConstants.WINDOW_ZOOM * 100) + "%';");
		} catch (Exception ignored) {
			// Best-effort
		}

		return driver;
	}
}
