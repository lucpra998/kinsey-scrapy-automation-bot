package framework.utils;

import framework.config.FrameworkConstants;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/**
 * Central explicit-wait utilities and safe interaction helpers.
 */
public final class WaitUtils {

	private WaitUtils() {
	}

	/** Creates a WebDriverWait with the given timeout. */
	public static WebDriverWait wait(WebDriver driver, long seconds) {
		return new WebDriverWait(driver, Duration.ofSeconds(seconds));
	}

	/** Waits for visibility of an element and returns it. */
	public static WebElement waitVisible(WebDriver driver, By locator, long seconds) {
		return wait(driver, seconds).until(ExpectedConditions.visibilityOfElementLocated(locator));
	}

	/** Waits for an element to be clickable and returns it. */
	public static WebElement waitClickable(WebDriver driver, By locator, long seconds) {
		return wait(driver, seconds).until(ExpectedConditions.elementToBeClickable(locator));
	}

	/** Returns true if an element becomes visible within the timeout. */
	public static boolean isVisible(WebDriver driver, By locator, long seconds) {
		try {
			waitVisible(driver, locator, seconds);
			return true;
		} catch (TimeoutException e) {
			return false;
		}
	}

	/** Waits for the document ready state to be complete. */
	public static void waitForPageLoad(WebDriver driver, long seconds) {
		wait(driver, seconds).until(d -> "complete"
				.equals(String.valueOf(((JavascriptExecutor) d).executeScript("return document.readyState"))));
	}

	/** Applies CSS zoom based on configured zoom percentage. */
	public static void applyZoom(WebDriver driver) {
		try {
			((JavascriptExecutor) driver).executeScript(
					"document.documentElement.style.zoom = '" + (int) (FrameworkConstants.WINDOW_ZOOM * 100) + "%';");
		} catch (Exception ignored) {
		}
	}

	/** Scrolls an element into view centered in the viewport. */
	public static void scrollIntoViewCenter(WebDriver driver, WebElement el) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center', inline:'nearest'});",
				el);
	}

	/** Performs a JavaScript click on the element. */
	public static void jsClick(WebDriver driver, WebElement el) {
		((JavascriptExecutor) driver).executeScript("arguments[0].click();", el);
	}

	/** Clicks with retries for stale/intercepted elements and JS fallback. */
	public static void clickWhenReady(WebDriver driver, By locator, long seconds) {
		int attempts = 0;
		while (attempts < 3) {
			try {
				WebElement el = waitVisible(driver, locator, seconds);
				scrollIntoViewCenter(driver, el);
				try {
					waitClickable(driver, locator, seconds).click();
				} catch (ElementClickInterceptedException e) {
					jsClick(driver, el);
				}
				return;
			} catch (StaleElementReferenceException e) {
				attempts++;
			} catch (ElementClickInterceptedException e) {
				attempts++;
			} catch (TimeoutException e) {
				throw e;
			} catch (Exception e) {
				attempts++;
				if (attempts >= 3)
					throw new RuntimeException("Failed clicking: " + locator, e);
			}
		}
		throw new RuntimeException("Failed clicking after retries: " + locator);
	}

	/** Types text into an element after clearing it. */
	public static void type(WebDriver driver, By locator, String value, long seconds) {
		WebElement el = waitVisible(driver, locator, seconds);
		scrollIntoViewCenter(driver, el);
		el.clear();
		el.sendKeys(value);
	}

	/** Returns trimmed text if visible; null otherwise. */
	public static String safeGetText(WebDriver driver, By locator, long seconds) {
		try {
			WebElement el = waitVisible(driver, locator, seconds);
			scrollIntoViewCenter(driver, el);
			return el.getText() == null ? null : el.getText().trim();
		} catch (Exception e) {
			return null;
		}
	}

	/** Wait until any locator is visible (results OR banners OR login). */
	public static void waitAnyVisible(WebDriver driver, long seconds, By... locators) {
		WebDriverWait w = wait(driver, seconds);
		w.until(d -> {
			for (By by : locators) {
				try {
					WebElement el = d.findElement(by);
					if (el.isDisplayed())
						return true;
				} catch (Exception ignored) {
				}
			}
			return false;
		});
	}

	/** Best-effort spinner stabilization. */
	public static void waitInvisibleIfPresent(WebDriver driver, By locator, long seconds) {
		try {
			wait(driver, seconds).until(ExpectedConditions.invisibilityOfElementLocated(locator));
		} catch (Exception ignored) {
		}
	}

	/** Wait for non-empty visible text (Bug C stabilization). */
	public static void waitTextNotEmpty(WebDriver driver, By locator, long seconds) {
		WebDriverWait w = wait(driver, seconds);
		w.until(d -> {
			try {
				WebElement el = d.findElement(locator);
				if (!el.isDisplayed())
					return false;
				String t = el.getText();
				return t != null && !t.trim().isEmpty();
			} catch (Exception e) {
				return false;
			}
		});
	}

	/** Offline page detector (Bug D). */
	public static boolean isOfflinePage(WebDriver driver) {
		try {
			String url = driver.getCurrentUrl();
			if (url != null && url.startsWith("chrome-error://"))
				return true;

			String title = driver.getTitle();
			if (title == null)
				return false;
			String t = title.toLowerCase();
			return t.contains("no internet") || t.contains("internet disconnected") || t.contains("dns");
		} catch (Exception e) {
			return false;
		}
	}

	/** Blocked/CAPTCHA detector (best-effort, stricter to reduce false positives). */
	public static boolean isBlockedPage(WebDriver driver) {
		try {
			String title = String.valueOf(driver.getTitle()).toLowerCase();
			String url = String.valueOf(driver.getCurrentUrl()).toLowerCase();

			// Strong signals from title or URL
			if (title.contains("access denied") || title.contains("captcha") || url.contains("captcha")
					|| url.contains("blocked")) {
				return true;
			}

			String src = driver.getPageSource();
			if (src == null)
				return false;
			String s = src.toLowerCase();

			boolean hasRecaptcha = s.contains("recaptcha") || s.contains("g-recaptcha")
					|| s.contains("verify you are human") || s.contains("are you a robot");
			boolean accessDenied = s.contains("access denied") || s.contains("unusual traffic");

			return hasRecaptcha || accessDenied;
		} catch (Exception e) {
			return false;
		}
	}

	/** Driver/session invalid detector (DevTools disconnect, invalid session). */
	public static boolean isDriverInvalid(Throwable t) {
		if (t == null)
			return false;
		String m = String.valueOf(t.getMessage()).toLowerCase();
		return m.contains("invalid session id") || m.contains("no such session") || m.contains("disconnected")
				|| m.contains("not connected to devtools");
	}

	/** Network-like selenium errors detector. */
	public static boolean isNetworkLike(Throwable t) {
		if (t == null)
			return false;
		String m = String.valueOf(t.getMessage()).toLowerCase();
		return m.contains("net::err") || m.contains("timeout") || m.contains("connection") || m.contains("dns");
	}

	/** Retry wrapper for transient failures (Bug D). */
	public static void runWithRetry(int attempts, int sleepMs, Runnable action) {
		RuntimeException last = null;
		for (int i = 0; i <= attempts; i++) {
			try {
				action.run();
				return;
			} catch (RuntimeException e) {
				last = e;
				try {
					Thread.sleep(Math.max(0, sleepMs));
				} catch (InterruptedException ignored) {
				}
			}
		}
		throw last;
	}

	/** Maintenance page detector (best-effort). */
	public static boolean isMaintenancePage(WebDriver driver) {
		try {
			String title = String.valueOf(driver.getTitle()).toLowerCase();
			String url = String.valueOf(driver.getCurrentUrl()).toLowerCase();
			if (title.contains("maintenance") || title.contains("service unavailable") || url.contains("maintenance")) {
				return true;
			}

			String src = driver.getPageSource();
			if (src == null)
				return false;
			String s = src.toLowerCase();
			return s.contains("maintenance") || s.contains("temporarily unavailable") || s.contains("service unavailable")
					|| s.contains("scheduled maintenance");
		} catch (Exception e) {
			return false;
		}
	}
}