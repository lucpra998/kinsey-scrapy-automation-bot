package framework.base;

import framework.utils.WaitUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * Base Page Object class. Provides shared safe wrappers for common
 * interactions.
 */
public abstract class BasePage {

	/** WebDriver used by this page object. */
	protected final WebDriver driver;

	protected BasePage(WebDriver driver) {
		this.driver = driver;
	}

	/**
	 * Waits for an element to be visible and returns it.
	 *
	 * @param locator By locator
	 * @param seconds wait time
	 * @return visible element
	 */
	protected WebElement visible(By locator, long seconds) {
		return WaitUtils.waitVisible(driver, locator, seconds);
	}

	/**
	 * Safe click with retries inside WaitUtils.
	 *
	 * @param locator By locator
	 * @param seconds wait time
	 */
	protected void click(By locator, long seconds) {
		WaitUtils.clickWhenReady(driver, locator, seconds);
	}

	/**
	 * Safe type (clear + send keys).
	 *
	 * @param locator By locator
	 * @param value   text to type
	 * @param seconds wait time
	 */
	protected void type(By locator, String value, long seconds) {
		WaitUtils.type(driver, locator, value, seconds);
	}

	/**
	 * Safe text getter (returns null if missing).
	 *
	 * @param locator By locator
	 * @param seconds wait time
	 * @return text or null
	 */
	protected String text(By locator, long seconds) {
		return WaitUtils.safeGetText(driver, locator, seconds);
	}
}
