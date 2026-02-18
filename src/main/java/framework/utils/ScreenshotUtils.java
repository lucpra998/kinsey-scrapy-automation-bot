package framework.utils;

import framework.config.FrameworkConstants;
import org.openqa.selenium.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Screenshot helper. Captures only when screenshots.enabled=true.
 */
public final class ScreenshotUtils {

	private ScreenshotUtils() {
	}

	/**
	 * Captures screenshot and returns absolute/relative path (based on config).
	 *
	 * @param driver WebDriver
	 * @param name   file prefix
	 * @return saved file path or null if disabled/failure
	 */
	public static String capture(WebDriver driver, String name) {
		if (!FrameworkConstants.SCREENSHOTS_ENABLED)
			return null;

		try {
			Files.createDirectories(Paths.get(FrameworkConstants.SCREENSHOT_DIR));

			String safe = (name == null) ? "SHOT" : name.replaceAll("[^a-zA-Z0-9._-]", "_");
			String filePath = FrameworkConstants.SCREENSHOT_DIR + "/" + safe + "_" + System.currentTimeMillis()
					+ ".png";

			File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
			Files.copy(src.toPath(), Paths.get(filePath), StandardCopyOption.REPLACE_EXISTING);

			return filePath;
		} catch (Exception e) {
			return null;
		}
	}
}
