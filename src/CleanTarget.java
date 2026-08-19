import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** One folder the cleaner knows how to empty. */
public class CleanTarget {

	private final String name;
	private final Path path;
	private final boolean needsAdmin;

	public CleanTarget(String name, Path path, boolean needsAdmin) {
		this.name = name;
		this.path = path;
		this.needsAdmin = needsAdmin;
	}

	public String name() {
		return name;
	}

	public Path path() {
		return path;
	}

	/** True for folders that only an elevated process can write to. */
	public boolean needsAdmin() {
		return needsAdmin;
	}

	public boolean exists() {
		return Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
	}

	/**
	 * True when this folder is safe to empty. A path only qualifies if it is a real
	 * directory below a known Windows root, and is neither a drive root nor one of
	 * the protected system folders.
	 */
	public boolean isSafe() {
		return Guard.isSafeToClean(path);
	}
}
