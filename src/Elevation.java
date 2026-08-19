import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Restarts the application with administrator rights.
 *
 * Windows only grants those rights at process start, so a running program cannot
 * simply ask for them. The usual answer is to start a second copy through
 * ShellExecute with the "runas" verb, which is what triggers the User Account
 * Control prompt. PowerShell exposes that as Start-Process -Verb RunAs, so no
 * native code or extra tooling is needed.
 *
 * The relaunch is run synchronously on purpose: if the prompt is declined the
 * child never starts, and the original window has to stay alive and carry on
 * without administrator rights rather than disappearing.
 */
public final class Elevation {

	private Elevation() {
	}

	/** Tells a relaunched copy not to ask again, which stops an endless loop. */
	public static final String MARKER = "--elevated";

	/**
	 * Asks Windows to start this application again as administrator.
	 *
	 * @return true when a new elevated window was started and this process should
	 *         quit, false when the prompt was declined or elevation is impossible,
	 *         in which case the caller carries on unelevated.
	 */
	public static boolean relaunchAsAdmin(List<String> passThrough) {
		List<String> command = relaunchCommand(passThrough);
		if (command == null) {
			return false;
		}

		System.out.println();
		System.out.println("Asking for administrator rights so the Windows folders can be cleaned too...");

		try {
			Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
			if (!process.waitFor(3, TimeUnit.MINUTES)) {
				process.destroyForcibly();
				return false;
			}
			if (process.exitValue() != 0) {
				System.out.println("Administrator rights were refused, carrying on without them.");
				return false;
			}
			System.out.println("A new window has opened with administrator rights.");
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Builds the PowerShell command that restarts this application elevated, or null
	 * if the running copy cannot work out how to start itself again.
	 */
	private static List<String> relaunchCommand(List<String> passThrough) {
		String target;
		List<String> arguments = new ArrayList<>();

		Path exe = launcherExe();
		if (exe != null) {
			target = exe.toString();
		} else {
			// Running from the plain jar rather than the packaged exe.
			Path jar = runningJar();
			Path java = Paths.get(System.getProperty("java.home"), "bin", "java.exe");
			if (jar == null || !Files.isRegularFile(java)) {
				return null;
			}
			target = java.toString();
			arguments.add("-jar");
			arguments.add(jar.toString());
		}

		arguments.add(MARKER);
		arguments.addAll(passThrough);

		StringBuilder argumentList = new StringBuilder();
		for (String argument : arguments) {
			if (argumentList.length() > 0) {
				argumentList.append(',');
			}
			argumentList.append(quote(argument));
		}

		String workingDirectory = quote(Paths.get(target).getParent().toString());
		String script = "try { Start-Process -FilePath " + quote(target)
				+ " -ArgumentList " + argumentList
				+ " -WorkingDirectory " + workingDirectory
				+ " -Verb RunAs -ErrorAction Stop; exit 0 } catch { exit 1 }";

		return List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script);
	}

	/** The packaged WinCleaner.exe, set by the jpackage launcher, or null. */
	private static Path launcherExe() {
		String path = System.getProperty("jpackage.app-path");
		if (path == null || path.isBlank()) {
			return null;
		}
		Path exe = Paths.get(path);
		return Files.isRegularFile(exe) ? exe : null;
	}

	private static Path runningJar() {
		try {
			File source = new File(Elevation.class.getProtectionDomain()
					.getCodeSource().getLocation().toURI());
			return source.isFile() && source.getName().endsWith(".jar") ? source.toPath() : null;
		} catch (Exception e) {
			return null;
		}
	}

	/** Wraps a value in a PowerShell single quoted string. */
	private static String quote(String value) {
		return "'" + value.replace("'", "''") + "'";
	}
}
