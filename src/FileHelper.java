import java.io.File;

public class FileHelper {
	public static boolean deleteFile(File file) {
		if (file != null) {
			if (file.exists()) {
				try {
					return file.delete();
				} catch (Exception e) {

					System.out.println("Error");
					return false;
				}
			} else {
				return false;
			}

		} else {
			return false;
		}
	}
}
