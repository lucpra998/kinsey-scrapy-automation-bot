package framework.config;

import java.io.InputStream;
import java.util.Properties;

/**
 * Loads config.properties from the classpath and provides typed accessors.
 * Supports System Property override (-Dkey=value) for CI/CD runs.
 */
public final class ConfigLoader {

	/** Cached properties loaded from config.properties on classpath. */
	private static final Properties PROPS = load();

	private ConfigLoader() {
		// Utility class
	}

	/**
	 * Loads config.properties from the classpath.
	 *
	 * @return loaded properties
	 */
	private static Properties load() {
		try (InputStream is = ConfigLoader.class.getClassLoader().getResourceAsStream("config.properties")) {
			if (is == null) {
				throw new RuntimeException(
						"config.properties not found on classpath (src/test/resources or src/main/resources).");
			}
			Properties p = new Properties();
			p.load(is);
			return p;
		} catch (Exception e) {
			throw new RuntimeException("Failed to load config.properties", e);
		}
	}

	/**
	 * Returns a String property (System property overrides file property).
	 *
	 * @param key property key
	 * @return trimmed value
	 */
	public static String getString(String key) {
		String v = getString(key, null);
		if (v == null || v.trim().isEmpty()) {
			throw new RuntimeException("Missing config property: " + key);
		}
		return v.trim();
	}

	/**
	 * Returns a String property or default (System property overrides file
	 * property).
	 *
	 * @param key          property key
	 * @param defaultValue fallback value
	 * @return trimmed value or defaultValue
	 */
	public static String getString(String key, String defaultValue) {
		String sys = System.getProperty(key);
		if (sys != null && !sys.trim().isEmpty())
			return sys.trim();

		String v = PROPS.getProperty(key);
		return (v == null || v.trim().isEmpty()) ? defaultValue : v.trim();
	}

	/**
	 * Returns an int property or default.
	 *
	 * @param key          property key
	 * @param defaultValue fallback value
	 * @return parsed int
	 */
	public static int getInt(String key, int defaultValue) {
		try {
			return Integer.parseInt(getString(key, String.valueOf(defaultValue)));
		} catch (Exception e) {
			throw new RuntimeException("Invalid int for key: " + key, e);
		}
	}

	/**
	 * Returns a boolean property or default.
	 *
	 * @param key          property key
	 * @param defaultValue fallback value
	 * @return parsed boolean
	 */
	public static boolean getBoolean(String key, boolean defaultValue) {
		return Boolean.parseBoolean(getString(key, String.valueOf(defaultValue)));
	}

	/**
	 * Returns a double property or default.
	 *
	 * @param key          property key
	 * @param defaultValue fallback value
	 * @return parsed double
	 */
	public static double getDouble(String key, double defaultValue) {
		try {
			return Double.parseDouble(getString(key, String.valueOf(defaultValue)));
		} catch (Exception e) {
			throw new RuntimeException("Invalid double for key: " + key, e);
		}
	}
}
