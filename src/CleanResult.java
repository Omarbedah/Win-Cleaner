/** Running totals for a clean or scan pass. */
public class CleanResult {

	private long deletedFiles;
	private long deletedBytes;
	private long skippedFiles;
	private long skippedBytes;

	public void addDeleted(long files, long bytes) {
		deletedFiles += files;
		deletedBytes += bytes;
	}

	public void addSkipped(long files, long bytes) {
		skippedFiles += files;
		skippedBytes += bytes;
	}

	public void add(CleanResult other) {
		deletedFiles += other.deletedFiles;
		deletedBytes += other.deletedBytes;
		skippedFiles += other.skippedFiles;
		skippedBytes += other.skippedBytes;
	}

	public long files() {
		return deletedFiles;
	}

	public long bytes() {
		return deletedBytes;
	}

	public long skipped() {
		return skippedFiles;
	}

	public long skippedBytes() {
		return skippedBytes;
	}

	public boolean isEmpty() {
		return deletedFiles == 0 && skippedFiles == 0;
	}
}
