package framework.utils;

import java.io.IOException;
import java.nio.file.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Tracks which UPCs have already been processed so a run can resume after a
 * timeout or cancellation. Each UPC is appended to a plain-text checkpoint
 * file as soon as its CSV row is written.
 *
 * <p>On the next run {@link #loadProcessed()} returns the full set and the
 * test filters them out of the input list, continuing only with the remaining
 * work.
 */
public final class ProgressTracker {

	/** Path of the checkpoint file (relative to the working directory). */
	public static final String CHECKPOINT_FILE = "ScrapingOutputResults/progress/checkpoint.txt";

	private static final Object LOCK = new Object();

	private ProgressTracker() {
	}

	/**
	 * Loads all UPCs that were already written to the checkpoint file.
	 *
	 * @return set of processed UPC strings, or an empty set if no checkpoint
	 *         exists
	 */
	public static Set<String> loadProcessed() {
		Path p = Paths.get(CHECKPOINT_FILE);
		if (!Files.exists(p)) {
			return Collections.emptySet();
		}
		try {
			Set<String> processed = new HashSet<>(Files.readAllLines(p));
			// Remove empty entries that may result from trailing newlines written by
			// markProcessed() or from partial writes interrupted by a timeout.
			processed.remove("");
			return processed;
		} catch (IOException e) {
			return Collections.emptySet();
		}
	}

	/**
	 * Records a UPC as processed by appending it to the checkpoint file.
	 * Thread-safe — safe to call from parallel test threads.
	 *
	 * @param upc the UPC string that was just written to CSV
	 */
	public static void markProcessed(String upc) {
		synchronized (LOCK) {
			try {
				Path p = Paths.get(CHECKPOINT_FILE);
				if (p.getParent() != null) {
					Files.createDirectories(p.getParent());
				}
				Files.writeString(p, upc + System.lineSeparator(), StandardOpenOption.CREATE,
						StandardOpenOption.APPEND);
			} catch (IOException ignored) {
				// Best-effort: a missed checkpoint entry only causes a UPC to be
				// re-processed on the next run, which is safe.
			}
		}
	}
}
