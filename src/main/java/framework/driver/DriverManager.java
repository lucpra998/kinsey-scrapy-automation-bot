package framework.driver;

import org.openqa.selenium.WebDriver;

/**
 * ThreadLocal storage for WebDriver to make parallel execution safe.
 */
public final class DriverManager {

	private static final ThreadLocal<WebDriver> DRIVER = new ThreadLocal<>();

	private DriverManager() {
	}

	/** Sets the WebDriver for the current thread. */
	public static void set(WebDriver driver) {
		DRIVER.set(driver);
	}

	/** Returns the WebDriver for the current thread. */
	public static WebDriver get() {
		return DRIVER.get();
	}

	/** Clears the WebDriver for the current thread. */
	public static void unload() {
		DRIVER.remove();
	}
}