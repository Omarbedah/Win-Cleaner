import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributeView;

/**
 * Low level file system helpers used by the cleaner.
 *
 * Everything in here is deliberately defensive: a file we cannot touch is
 * reported as "skipped", never as a crash. Reparse points (symlinks and
 * directory junctions) are never followed, so cleaning a temp folder can never
 * escape into the directory a junction points at.
 */
public class FileHelper {

	/** Deletes a single file. Returns true only if the file is gone afterwards. */
	public static boolean deleteFile(File file) {
		if (file == null || !file.exists()) {
			return false;
		}
		return deleteFile(file.toPath());
	}

	/** Deletes a single file or empty directory, clearing the read-only flag if needed. */
	public static boolean deleteFile(Path path) {
		try {
			Files.delete(path);
			return true;
		} catch (IOException first) {
			// Most common cause on Windows is the read-only attribute. Clear it and retry once.
			if (clearReadOnly(path)) {
				try {
					Files.delete(path);
					return true;
				} catch (IOException ignored) {
					return false;
				}
			}
			return false;
		}
	}

	/**
	 * Deletes everything inside {@code root} but keeps {@code root} itself, because
	 * Windows and a number of applications expect folders like %TEMP% to exist.
	 *
	 * @param dryRun when true nothing is deleted, the walk only measures.
	 */
	public static void clean(Path root, CleanResult result, boolean dryRun) {
		if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
			return;
		}
		cleanContents(root, result, dryRun, 0);
	}

	private static void cleanContents(Path dir, CleanResult result, boolean dryRun, int depth) {
		if (depth > 64) {
			// Runaway nesting: bail out instead of recursing forever.
			return;
		}
		try (DirectoryStream<Path> entries = Files.newDirectoryStream(dir)) {
			for (Path entry : entries) {
				handleEntry(entry, result, dryRun, depth);
			}
		} catch (IOException | RuntimeException e) {
			result.addSkipped(1, 0L);
		}
	}

	private static void handleEntry(Path entry, CleanResult result, boolean dryRun, int depth) {
		BasicFileAttributes attrs;
		try {
			attrs = Files.readAttributes(entry, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
		} catch (IOException e) {
			result.addSkipped(1, 0L);
			return;
		}

		boolean link = isReparsePoint(entry, attrs);

		if (attrs.isDirectory() && !link) {
			cleanContents(entry, result, dryRun, depth + 1);
			// The directory can only go once its contents did.
			if (dryRun) {
				result.addDeleted(1, 0L);
			} else if (deleteFile(entry)) {
				result.addDeleted(1, 0L);
			} else {
				result.addSkipped(1, 0L);
			}
			return;
		}

		// Regular file, or a link/junction which we remove without following it.
		long size = link ? 0L : attrs.size();
		if (dryRun) {
			result.addDeleted(1, size);
		} else if (deleteFile(entry)) {
			result.addDeleted(1, size);
		} else {
			result.addSkipped(1, size);
		}
	}

	/**
	 * True if the path is a symlink, a junction or any other reparse point.
	 * Unreadable paths are reported as links so that callers refuse to recurse.
	 */
	public static boolean isReparsePoint(Path path, BasicFileAttributes attrs) {
		if (attrs.isSymbolicLink() || attrs.isOther()) {
			return true;
		}
		if (!attrs.isDirectory()) {
			return false;
		}
		try {
			// A junction resolves to somewhere else; Path.equals is case insensitive on Windows.
			return !path.toRealPath().equals(path.toAbsolutePath().normalize());
		} catch (IOException e) {
			return true;
		}
	}

	/** Total size in bytes of everything under {@code root}. Unreadable entries count as zero. */
	public static long sizeOf(Path root) {
		CleanResult probe = new CleanResult();
		clean(root, probe, true);
		return probe.bytes();
	}

	private static boolean clearReadOnly(Path path) {
		try {
			DosFileAttributeView view = Files.getFileAttributeView(path, DosFileAttributeView.class,
					LinkOption.NOFOLLOW_LINKS);
			if (view == null) {
				return false;
			}
			view.setReadOnly(false);
			return true;
		} catch (IOException | RuntimeException e) {
			return false;
		}
	}

	/** Formats a byte count the way Windows does, e.g. "1.4 GB". */
	public static String humanBytes(long bytes) {
		if (bytes < 1024L) {
			return bytes + " B";
		}
		String[] units = { "KB", "MB", "GB", "TB" };
		double value = bytes;
		int unit = -1;
		while (value >= 1024.0 && unit < units.length - 1) {
			value /= 1024.0;
			unit++;
		}
		return String.format(java.util.Locale.ROOT, "%.1f %s", value, units[unit]);
	}
}
