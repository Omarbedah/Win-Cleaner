import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

/**
 * Win Cleaner - frees disk space by emptying the Windows temporary and cache
 * folders, and optionally the Recycle Bin.
 *
 * Run with no arguments for the interactive menu, or see {@link #printHelp()}
 * for the command line switches.
 */
public class WinCleaner {

	public static final String VERSION = "1.0.0";

	private static final Scanner INPUT = new Scanner(System.in);

	public static void main(String[] args) {
		// A copy started by Elevation carries a marker so that it does not ask again.
		List<String> options = new ArrayList<>();
		boolean relaunched = false;
		for (String arg : args) {
			if (Elevation.MARKER.equalsIgnoreCase(arg)) {
				relaunched = true;
			} else {
				options.add(arg);
			}
		}

		boolean scanOnly = false;
		boolean cleanNow = false;
		boolean recycleBin = false;
		boolean assumeYes = false;
		boolean noElevate = false;

		for (String arg : options) {
			switch (arg.toLowerCase(Locale.ROOT)) {
			case "-s":
			case "--scan":
				scanOnly = true;
				break;
			case "-c":
			case "--clean":
				cleanNow = true;
				break;
			case "-r":
			case "--recycle-bin":
				recycleBin = true;
				break;
			case "-a":
			case "--all":
				cleanNow = true;
				recycleBin = true;
				break;
			case "-y":
			case "--yes":
				assumeYes = true;
				break;
			case "-n":
			case "--no-elevate":
				noElevate = true;
				break;
			case "-h":
			case "--help":
				printHelp();
				return;
			case "-v":
			case "--version":
				System.out.println("Win Cleaner " + VERSION);
				return;
			default:
				System.out.println("Unknown option: " + arg);
				printHelp();
				return;
			}
		}

		boolean interactive = !scanOnly && !cleanNow && !recycleBin;

		// Double clicking the exe lands here. Ask for administrator rights up front so
		// the Windows folders are cleaned too, instead of quietly leaving them out. A
		// run that was given switches is left alone, so a scheduled task never stalls
		// on a prompt nobody is there to answer.
		if (interactive && !relaunched && !noElevate && !Targets.isElevated()
				&& Elevation.relaunchAsAdmin(options)) {
			return;
		}

		printBanner();

		if (scanOnly) {
			scan(Targets.forThisMachine(), true);
		} else if (cleanNow || recycleBin) {
			if (cleanNow) {
				clean(Targets.forThisMachine(), assumeYes);
			}
			if (recycleBin) {
				emptyRecycleBin(assumeYes);
			}
		} else {
			menu();
		}

		// Keep the results readable when the window was opened by a double click
		// rather than from a command prompt that stays around afterwards.
		if (interactive || relaunched) {
			pauseBeforeClosing();
		}
	}

	/** Waits for Enter so a double clicked window does not vanish with the results. */
	private static void pauseBeforeClosing() {
		System.out.println();
		System.out.print("Press Enter to close this window...");
		if (INPUT.hasNextLine()) {
			INPUT.nextLine();
		}
		System.out.println();
	}

	// ---------------------------------------------------------------- menu

	private static void menu() {
		while (true) {
			System.out.println();
			System.out.println("  (1) Scan - show what can be freed, delete nothing");
			System.out.println("  (2) Clean temporary files");
			System.out.println("  (3) Empty Recycle Bin");
			System.out.println("  (4) Exit");
			System.out.println();
			System.out.print("Choose an option [1-4]: ");

			if (!INPUT.hasNextLine()) {
				System.out.println();
				return;
			}
			String choice = INPUT.nextLine().trim();

			switch (choice) {
			case "1":
				scan(Targets.forThisMachine(), true);
				break;
			case "2":
				clean(Targets.forThisMachine(), false);
				break;
			case "3":
				emptyRecycleBin(false);
				break;
			case "4":
			case "q":
			case "exit":
				System.out.println("Goodbye.");
				return;
			default:
				System.out.println("That is not one of the options.");
				break;
			}
		}
	}

	// --------------------------------------------------------------- tasks

	/** Measures every target without deleting anything. */
	private static void scan(List<CleanTarget> targets, boolean print) {
		CleanResult total = new CleanResult();

		if (print) {
			System.out.println();
			System.out.println("Scanning " + targets.size() + " location(s)...");
			System.out.println();
		}

		for (CleanTarget target : targets) {
			CleanResult result = new CleanResult();
			FileHelper.clean(target.path(), result, true);
			total.add(result);
			if (print) {
				System.out.println(row(target.name(), result.bytes(), result.files()));
				System.out.println("      " + shorten(target.path()));
			}
		}

		if (print) {
			System.out.println();
			System.out.println("  Total reclaimable: " + FileHelper.humanBytes(total.bytes())
					+ " in " + String.format("%,d", total.files()) + " items");
			long bin = RecycleBin.size();
			if (bin > 0) {
				System.out.println("  Recycle Bin:       " + FileHelper.humanBytes(bin) + " (option 3)");
			}
			System.out.println();
			System.out.println("Nothing was deleted.");
		}
	}

	/** Shows a preview, asks for confirmation, then deletes. */
	private static void clean(List<CleanTarget> targets, boolean assumeYes) {
		if (targets.isEmpty()) {
			System.out.println("No cleanable folders were found on this machine.");
			return;
		}

		scan(targets, true);

		if (!assumeYes) {
			System.out.println();
			System.out.println("Close any running installers first; files still in use are skipped.");
			if (!confirm("Delete these files permanently?")) {
				System.out.println("Cancelled. Nothing was deleted.");
				return;
			}
		}

		System.out.println();
		System.out.println("Cleaning...");
		System.out.println();

		CleanResult total = new CleanResult();
		for (CleanTarget target : targets) {
			CleanResult result = new CleanResult();
			FileHelper.clean(target.path(), result, false);
			total.add(result);

			String line = row(target.name(), result.bytes(), result.files());
			if (result.skipped() > 0) {
				line += "  [" + String.format("%,d", result.skipped()) + " in use, skipped]";
			}
			System.out.println(line);
		}

		System.out.println();
		System.out.println("  Freed " + FileHelper.humanBytes(total.bytes())
				+ " by removing " + String.format("%,d", total.files()) + " items.");
		if (total.skipped() > 0) {
			System.out.println("  " + String.format("%,d", total.skipped())
					+ " items were locked by a running program and left alone.");
		}
		if (!Targets.isElevated()) {
			System.out.println();
			System.out.println("  Tip: restart as administrator to also clean C:\\Windows\\Temp"
					+ " and the prefetch cache.");
		}
	}

	private static void emptyRecycleBin(boolean assumeYes) {
		System.out.println();
		System.out.println("Measuring the Recycle Bin...");
		long before = RecycleBin.size();

		if (before == 0L) {
			System.out.println("The Recycle Bin is already empty.");
			return;
		}
		System.out.println("The Recycle Bin holds " + FileHelper.humanBytes(before) + ".");

		if (!assumeYes && !confirm("Empty it permanently?")) {
			System.out.println("Cancelled. The Recycle Bin was left alone.");
			return;
		}

		String error = RecycleBin.empty();
		if (error != null) {
			System.out.println("Could not empty the Recycle Bin: " + error);
			return;
		}
		long freed = Math.max(0L, before - RecycleBin.size());
		System.out.println("Recycle Bin emptied, " + FileHelper.humanBytes(freed) + " freed.");
	}

	// --------------------------------------------------------------- output

	private static void printBanner() {
		System.out.println();
		System.out.println("Win Cleaner " + VERSION);
		System.out.println("=======================================");
		System.out.println(Targets.isElevated()
				? "Running as administrator - system folders included."
				: "Running as a standard user - system folders are skipped.");
	}

	private static void printHelp() {
		System.out.println();
		System.out.println("Win Cleaner " + VERSION + " - clears Windows temp and cache folders.");
		System.out.println();
		System.out.println("Usage: WinCleaner [options]");
		System.out.println();
		System.out.println("  -s, --scan          Report what can be freed, delete nothing");
		System.out.println("  -c, --clean         Clean the temporary folders");
		System.out.println("  -r, --recycle-bin   Empty the Recycle Bin");
		System.out.println("  -a, --all           Same as --clean --recycle-bin");
		System.out.println("  -y, --yes           Do not ask for confirmation");
		System.out.println("  -n, --no-elevate    Do not ask for administrator rights");
		System.out.println("  -h, --help          Show this help");
		System.out.println("  -v, --version       Show the version");
		System.out.println();
		System.out.println("With no options an interactive menu is shown, and Win Cleaner asks for");
		System.out.println("administrator rights so that C:\\Windows\\Temp and the prefetch cache are");
		System.out.println("included. A run that is given switches never prompts, so for a shortcut or");
		System.out.println("a scheduled task tick \"Run as administrator\" there instead.");
	}

	/** One aligned "name .... size (n items)" line. */
	private static String row(String name, long bytes, long files) {
		String label = name.length() > 28 ? name.substring(0, 27) + "." : name;
		return String.format(Locale.ROOT, "  %-28s %10s  %9s items",
				label, FileHelper.humanBytes(bytes), String.format("%,d", files));
	}

	/** Keeps long paths readable in an 80 column console. */
	private static String shorten(Path path) {
		String text = path.toString();
		return text.length() <= 68 ? text : text.substring(0, 30) + "..." + text.substring(text.length() - 35);
	}

	private static boolean confirm(String question) {
		System.out.print(question + " [y/N]: ");
		if (!INPUT.hasNextLine()) {
			System.out.println();
			return false;
		}
		String answer = INPUT.nextLine().trim().toLowerCase(Locale.ROOT);
		return answer.equals("y") || answer.equals("yes");
	}
}
