# Win Cleaner

A small Windows disk cleaner written in Java. It empties the temporary and cache
folders Windows fills up over time, and can empty the Recycle Bin. It always shows
you how much space it will free and asks before deleting anything.

@Omarbedah I have a personal message for you at the bottom of the file.

## Download and run the .exe

1. Go to the **Releases** page of this repository and download
   **`WinCleaner-1.0.0-windows.zip`**.
2. Right-click the zip → **Properties** → tick **Unblock** → OK.
   (Optional, but it stops Windows nagging about every file inside.)
3. Right-click the zip → **Extract All...**. **Do this before running anything.**
4. Open the extracted `WinCleaner` folder.
5. Double-click **`WinCleaner.exe`**.
6. Windows asks for administrator rights. Choose **Yes** — a new window opens and
   does the work. Choosing **No** is fine too, it just skips the two Windows
   folders.

You do not need Java installed. A Java runtime is bundled inside the folder.

> ### Do not run the exe on its own
>
> `WinCleaner.exe` needs the `app\` and `runtime\` folders that sit next to it. It
> reads `app\WinCleaner.cfg` to find its code and its bundled Java. Without those
> folders it prints one error line and exits, which looks like **a window opening
> and closing instantly**.
> That means:
>
> - **Do not** double-click the exe while it is still inside the zip. Windows only
>   unpacks that one file to a temp folder, so the rest is missing.
> - **Do not** drag just the exe to your Desktop.
>
> Move or copy the **whole `WinCleaner` folder**. For a Desktop icon, right-click
> the exe and choose _Send to → Desktop (create shortcut)_.

## Using it

Starting the exe with no arguments gives you a menu:

```
  (1) Scan - show what can be freed, delete nothing
  (2) Clean temporary files
  (3) Empty Recycle Bin
  (4) Exit
```

- **Scan** measures every folder and prints a report. Nothing is deleted, so this
  is the safe way to see what you would get back.
- **Clean** shows the same report, then asks `Delete these files permanently? [y/N]`.
  Only `y` or `yes` proceeds; anything else, including just pressing Enter, cancels.
- **Empty Recycle Bin** tells you how much the bin holds and asks before emptying it.
  This is permanent, the files cannot be restored afterwards.

The window waits for Enter before closing, so the results stay on screen.

Files that are open in another program cannot be deleted on Windows. Win Cleaner
skips them, keeps going, and reports how many it left alone. Closing any running
installers before cleaning gets you a bit more space.

## Command line

Useful for a shortcut or a scheduled task:

```
WinCleaner.exe [options]

  -s, --scan          Report what can be freed, delete nothing
  -c, --clean         Clean the temporary folders
  -r, --recycle-bin   Empty the Recycle Bin
  -a, --all           Same as --clean --recycle-bin
  -y, --yes           Do not ask for confirmation
  -n, --no-elevate    Do not ask for administrator rights
  -h, --help          Show this help
  -v, --version       Show the version
```

For example, a silent full clean:

```
WinCleaner.exe --all --yes
```

## What gets cleaned

| Folder                   | Location                                     | Why it is safe to delete                                        |
| ------------------------ | -------------------------------------------- | --------------------------------------------------------------- |
| User temp folder         | `%TEMP%`                                     | Scratch files and installer leftovers. Usually the biggest win. |
| Windows temp folder      | `C:\Windows\Temp`                            | System-wide scratch space. **Administrator only.**              |
| Windows prefetch         | `C:\Windows\Prefetch`                        | App launch cache. Windows rebuilds it. **Administrator only.**  |
| Internet cache           | `%LOCALAPPDATA%\Microsoft\Windows\INetCache` | Cached web content for Edge, IE and other WinINet apps.         |
| Thumbnail and icon cache | `%LOCALAPPDATA%\Microsoft\Windows\Explorer`  | Explorer thumbnails, regenerated when you next browse a folder. |
| DirectX shader cache     | `%LOCALAPPDATA%\D3DSCache`                   | Compiled shaders, recompiled by games on next launch.           |
| Windows error reports    | `%LOCALAPPDATA%\Microsoft\Windows\WER`       | Queued crash reports.                                           |
| Crash dumps              | `%LOCALAPPDATA%\CrashDumps`                  | Application crash dump files.                                   |

Folders that do not exist on your machine, and the administrator-only ones when
you are not elevated, are skipped and never appear in the list.

Emptying these caches is harmless but not free: the first launch of a game may
recompile shaders, and folder thumbnails are regenerated the first time you open a
folder again. Everything comes back on its own.

## What it never touches

Deleting files is the whole job of this tool, so the code is careful about where
it is allowed to do it. Before a single file is removed:

- The folder is matched against a fixed allow-list in `Guard.java`. Anything that
  is not one of the cache folders in the table above is refused outright, so a
  broken environment variable cannot redirect the cleaner somewhere else.
- The path is resolved to its real location first, then checked. Drive roots,
  `C:\Windows`, `System32`, `SysWOW64`, `WinSxS`, `Program Files`, `ProgramData`,
  `AppData` itself, and your user profile and its Documents and Desktop folders
  are all rejected.
- Symbolic links and directory junctions are deleted as links but never followed,
  so cleaning a temp folder cannot reach through a junction into the folder it
  points at.
- The target folders themselves are kept, only their contents are removed, because
  Windows and many applications expect folders like `%TEMP%` to exist.

Deleted files do **not** go to the Recycle Bin. They are gone. Run a scan first if
you want to know what will happen.

## Build from source

Requires a **JDK 17 or newer** on your PATH, for example from
[Adoptium](https://adoptium.net). `jpackage`, which builds the exe, ships with the
JDK, so no other tooling is needed.

```powershell
powershell -ExecutionPolicy Bypass -File build.ps1
```

That produces:

| Output                              | What it is                                          |
| ----------------------------------- | --------------------------------------------------- |
| `dist\WinCleaner\WinCleaner.exe`    | The executable, with a bundled Java runtime         |
| `dist\WinCleaner-1.0.0-windows.zip` | The same folder zipped, about 15 MB, ready to share |
| `dist\WinCleaner-1.0.0.jar`         | Portable jar, needs Java 17+ installed              |

To run the jar instead of the exe:

```
java -jar WinCleaner-1.0.0.jar --scan
```

If the build fails with "Could not clear 'dist'", close any window or terminal
sitting inside that folder and run it again.

### Source layout

| File                   | Role                                                |
| ---------------------- | --------------------------------------------------- |
| `src/WinCleaner.java`  | Entry point, menu, command line arguments, output   |
| `src/Elevation.java`   | Restarting as administrator through the UAC prompt  |
| `src/Targets.java`     | Which folders to clean, and the administrator check |
| `src/Guard.java`       | The allow-list and protected-path rules             |
| `src/FileHelper.java`  | Recursive delete and measure, link handling         |
| `src/CleanTarget.java` | One cleanable folder                                |
| `src/CleanResult.java` | Running totals for a clean or scan                  |
| `src/RecycleBin.java`  | Recycle Bin size and emptying                       |

Build output goes to `build\` and `dist\`. Neither belongs in git — if they were
committed before the ignore rules existed, `git restore --staged dist` unstages
them, because `.gitignore` has no effect on paths git already tracks.

## Requirements

- Windows 10 or 11
- Nothing else for the exe. Java 17+ only if you use the jar or build from source.

## License

MIT, see [LICENSE](LICENSE).

## Message for Omar

I know you're starting your journey learning software development and you're going extremely fast. I just want to say good luck and I can truly see your potential right from the get-go. Keep the motivation alive. Don't try to achieve things extremely fast. Take your time. Learn stuff. Practice on them. And I'm sure you'll be even better than me. Good luck bro.
