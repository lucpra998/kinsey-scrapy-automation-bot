package framework.utils;

import com.aventstack.extentreports.ExtentTest;
import framework.listeners.ExtentTestListener;

/**
 * Unified logger for console + ExtentReport.
 */
public final class ReportLogger {

	private ReportLogger() {
		// Utility class
	}

	/**
	 * Returns the thread-safe current ExtentTest instance (can be null).
	 *
	 * @return ExtentTest or null
	 */
	private static ExtentTest getSafeTest() {
		return ExtentTestListener.getTest();
	}

	/**
	 * Logs informational message.
	 *
	 * @param message message
	 */
	public static void info(String message) {
		ExtentTest test = getSafeTest();
		if (test != null)
			test.info("STEP -> " + message);
		System.out.println("STEP -> " + message);
	}

	/**
	 * Logs pass message.
	 *
	 * @param message message
	 */
	public static void pass(String message) {
		ExtentTest test = getSafeTest();
		if (test != null)
			test.pass("STEP -> " + message);
		System.out.println("PASS -> " + message);
	}

	/**
	 * Logs fail message.
	 *
	 * @param message message
	 */
	public static void fail(String message) {
		ExtentTest test = getSafeTest();
		if (test != null)
			test.fail("STEP -> " + message);
		System.out.println("FAIL -> " + message);
	}

	/**
	 * Attaches a screenshot to the Extent report if possible.
	 *
	 * @param screenshotPath screenshot path
	 * @param caption        label in report
	 */
	public static void attachScreenshot(String screenshotPath, String caption) {
		ExtentTest test = getSafeTest();
		if (test != null && screenshotPath != null) {
			try {
				test.addScreenCaptureFromPath(screenshotPath, caption);
			} catch (Exception e) {
				System.out.println("Screenshot attach failed: " + e.getMessage());
			}
		}
	}
}
