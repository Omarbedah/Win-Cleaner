import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Recycle Bin support.
 *
 * Java has no API for the Recycle Bin, so the actual emptying is delegated to
 * PowerShell's Clear-RecycleBin, which is present on every supported version of
 * Windows. The size is measured directly from the per drive $Recycle.Bin folders.
 */
public final class RecycleBin {

	private RecycleBin() {
	}

	/** Best effort total size of the Recycle Bin across all local drives. */
	public static long size() {
		long total = 0L;
		for (File root : File.listRoots()) {
			Path bin = root.toPath().resolve("$Recycle.Bin");
			try {
				if (Files.isDirectory(bin, LinkOption.NOFOLLOW_LINKS)) {
					total += FileHelper.sizeOf(bin);
				}
			} catch (RuntimeException ignored) {
				// Unreadable drive, e.g. an empty card reader. Skip it.
			}
		}
		return total;
	}

	/**
	 * Empties the Recycle Bin on every drive.
	 *
	 * @return null on success, otherwise a human readable reason.
	 */
	public static String empty() {
		String script = "$ErrorActionPreference='Stop'; "
				+ "try { Clear-RecycleBin -Force -Confirm:$false; 'OK' } "
				+ "catch { 'FAIL ' + $_.Exception.Message }";
		try {
			ProcessBuilder builder = new ProcessBuilder(
					"powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script);
			builder.redirectErrorStream(true);
			Process process = builder.start();

			StringBuilder output = new StringBuilder();
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					output.append(line.trim()).append(' ');
				}
			}

			if (!process.waitFor(2, TimeUnit.MINUTES)) {
				process.destroyForcibly();
				return "PowerShell did not finish in time";
			}

			String text = output.toString().trim();
			if (text.startsWith("FAIL")) {
				return text.substring(4).trim();
			}
			if (process.exitValue() != 0) {
				return text.isEmpty() ? "PowerShell exited with code " + process.exitValue() : text;
			}
			return null;
		} catch (Exception e) {
			return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
		}
	}
}
