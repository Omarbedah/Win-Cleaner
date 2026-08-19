import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** The set of Windows cache folders the cleaner knows about. */
public final class Targets {

	private Targets() {
	}

	private static Boolean elevated;

	/**
	 * Builds the target list for the current machine. Folders that do not exist, that
	 * fail the {@link Guard} checks, or that need rights this process does not have
	 * are left out, so the caller can assume every returned target is real, safe and
	 * reachable.
	 */
	public static List<CleanTarget> forThisMachine() {
		List<CleanTarget> targets = new ArrayList<>();

		// Scratch space: the bulk of what a cleaner reclaims.
		add(targets, "User temp folder", tempDir(), false);
		add(targets, "Windows temp folder", windows("Temp"), true);

		// Caches Windows and its apps rebuild on demand.
		add(targets, "Windows prefetch", windows("Prefetch"), true);
		add(targets, "Internet cache", localAppData("Microsoft", "Windows", "INetCache"), false);
		add(targets, "Thumbnail and icon cache", localAppData("Microsoft", "Windows", "Explorer"), false);
		add(targets, "DirectX shader cache", localAppData("D3DSCache"), false);

		// Diagnostic leftovers.
		add(targets, "Windows error reports", localAppData("Microsoft", "Windows", "WER"), false);
		add(targets, "Crash dumps", localAppData("CrashDumps"), false);

		return targets;
	}

	private static void add(List<CleanTarget> into, String name, Path path, boolean needsAdmin) {
		if (path == null || (needsAdmin && !isElevated())) {
			return;
		}
		CleanTarget target = new CleanTarget(name, path, needsAdmin);
		if (target.exists() && target.isSafe()) {
			into.add(target);
		}
	}

	private static Path tempDir() {
		String temp = firstSet(System.getenv("TEMP"), System.getenv("TMP"),
				System.getProperty("java.io.tmpdir"));
		return temp == null ? null : Paths.get(temp);
	}

	private static Path windows(String child) {
		String root = firstSet(System.getenv("SystemRoot"), System.getenv("windir"));
		return root == null ? null : Paths.get(root, child);
	}

	private static Path localAppData(String... children) {
		String root = firstSet(System.getenv("LOCALAPPDATA"));
		if (root == null) {
			String profile = firstSet(System.getenv("USERPROFILE"), System.getProperty("user.home"));
			if (profile == null) {
				return null;
			}
			root = Paths.get(profile, "AppData", "Local").toString();
		}
		return Paths.get(root, children);
	}

	private static String firstSet(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	/**
	 * True when the process can write inside the Windows directory, which in practice
	 * means it was started with "Run as administrator". The probe touches the disk, so
	 * the answer is cached for the life of the process.
	 */
	public static boolean isElevated() {
		if (elevated == null) {
			elevated = probeElevation();
		}
		return elevated;
	}

	private static boolean probeElevation() {
		String root = firstSet(System.getenv("SystemRoot"), System.getenv("windir"));
		if (root == null) {
			return false;
		}
		Path probe = null;
		try {
			probe = Files.createTempFile(Paths.get(root), "wincleaner-probe", ".tmp");
			return true;
		} catch (IOException | RuntimeException e) {
			return false;
		} finally {
			if (probe != null) {
				FileHelper.deleteFile(probe);
			}
		}
	}
}
