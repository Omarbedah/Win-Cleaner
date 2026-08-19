import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Safety net in front of every delete.
 *
 * The rule is deliberately strict: a folder may only be emptied if it sits inside
 * one of the small number of cache locations listed in {@link #allowedRoots()}.
 * Anything else is refused, so a missing environment variable, a junction or a typo
 * can never turn into "delete C:\Windows\System32".
 *
 * Adding a new folder to {@link Targets} therefore also means adding it here. That
 * friction is intentional for a tool whose job is deleting files.
 */
public final class Guard {

	private Guard() {
	}

	/** Folders that must survive intact no matter what. */
	private static final String[] PROTECTED_VARS = {
			"SystemRoot", "windir", "ProgramFiles", "ProgramFiles(x86)", "ProgramData",
			"USERPROFILE", "LOCALAPPDATA", "APPDATA", "PUBLIC", "SystemDrive"
	};

	public static boolean isSafeToClean(Path candidate) {
		if (candidate == null) {
			return false;
		}

		Path path;
		try {
			if (!Files.isDirectory(candidate, LinkOption.NOFOLLOW_LINKS)) {
				return false;
			}
			// Resolve junctions, symlinks and "..", then judge the real location on disk.
			path = candidate.toRealPath();
		} catch (IOException | RuntimeException e) {
			return false;
		}

		// 1. Never a drive root ("C:\") and never a top level folder ("C:\Windows").
		if (path.getNameCount() < 2) {
			return false;
		}

		// 2. Never a protected folder, and never a parent of one.
		for (Path guarded : protectedPaths()) {
			if (path.equals(guarded) || guarded.startsWith(path)) {
				return false;
			}
		}

		// 3. Must be one of the known cache locations, or something inside one.
		for (Path root : allowedRoots()) {
			if (path.startsWith(root)) {
				return true;
			}
		}
		return false;
	}

	/** The only places on disk this tool is ever allowed to delete from. */
	private static List<Path> allowedRoots() {
		List<Path> roots = new ArrayList<>();

		// Scratch space.
		add(roots, System.getenv("TEMP"));
		add(roots, System.getenv("TMP"));
		add(roots, System.getProperty("java.io.tmpdir"));
		addUnder(roots, windowsDir(), "Temp");
		addUnder(roots, windowsDir(), "Prefetch");

		// Per user caches. Listed one by one on purpose: neither %LOCALAPPDATA% nor
		// %LOCALAPPDATA%\Microsoft\Windows may be opened up as a whole, because both
		// also hold data that is not a cache.
		String local = localAppData();
		addUnder(roots, local, "CrashDumps");
		addUnder(roots, local, "D3DSCache");
		addUnder(roots, local, "Microsoft", "Windows", "INetCache");
		addUnder(roots, local, "Microsoft", "Windows", "Explorer");
		addUnder(roots, local, "Microsoft", "Windows", "WER");

		return roots;
	}

	private static List<Path> protectedPaths() {
		List<Path> paths = new ArrayList<>();
		for (String variable : PROTECTED_VARS) {
			add(paths, System.getenv(variable));
		}
		add(paths, System.getProperty("user.home"));

		String windows = windowsDir();
		for (String critical : new String[] { "System32", "SysWOW64", "WinSxS", "assembly",
				"Fonts", "Boot", "INF", "servicing", "System", "security", "Installer" }) {
			addUnder(paths, windows, critical);
		}

		// The folder that holds every user profile, normally C:\Users.
		String profile = System.getenv("USERPROFILE");
		if (profile != null) {
			Path parent = Paths.get(profile).getParent();
			if (parent != null) {
				add(paths, parent.toString());
			}
		}
		return paths;
	}

	private static String windowsDir() {
		String root = System.getenv("SystemRoot");
		return (root == null || root.isBlank()) ? System.getenv("windir") : root;
	}

	private static String localAppData() {
		String local = System.getenv("LOCALAPPDATA");
		if (local != null && !local.isBlank()) {
			return local;
		}
		String profile = System.getenv("USERPROFILE");
		return profile == null ? null : Paths.get(profile, "AppData", "Local").toString();
	}

	private static void addUnder(List<Path> into, String base, String... children) {
		if (base != null && !base.isBlank()) {
			add(into, Paths.get(base, children).toString());
		}
	}

	private static void add(List<Path> into, String raw) {
		if (raw == null || raw.isBlank()) {
			return;
		}
		try {
			Path path = Paths.get(raw);
			into.add(Files.exists(path) ? path.toRealPath() : path.toAbsolutePath().normalize());
		} catch (IOException | RuntimeException ignored) {
			// An unusable value simply contributes nothing to the check.
		}
	}
}
