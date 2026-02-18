package framework.utils;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import framework.config.FrameworkConstants;

/**
 * File utilities for loading input datasets.
 */
public final class FileUtils {

	private FileUtils() {
		// Utility class
	}

	/**
	 * Reads UPCs from a text file and returns a clean list.
	 *
	 * @param path file path
	 * @return list of UPC strings
	 */
	public static List<String> readUpcs(String path) {
		try {
			Path p = Paths.get(path);
			if (p.getParent() != null)
				Files.createDirectories(p.getParent());

			List<String> raw = Files.readAllLines(p).stream().map(String::trim).filter(s -> !s.isEmpty())
					.filter(s -> !s.equalsIgnoreCase("UPC")).collect(Collectors.toList());

			if (!FrameworkConstants.DEDUP_UPC)
				return raw;

			return new ArrayList<>(new LinkedHashSet<>(raw));

		} catch (IOException e) {
			throw new RuntimeException("Unable to read UPC file: " + path, e);
		}
	}
}