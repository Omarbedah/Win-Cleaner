import java.util.Scanner;

public class WinCleaner {
//test
	public static void main(String[] args) {
		Scanner input = new Scanner(System.in);
		boolean running = true;

		System.out.println("Welcom to win cleaner v1");
		System.out.println("-------------------");

		while (running) {
			System.out.println("You have three choises chose one of them :");
			System.out.printf("(1) Clean Temperley Folder%n(2) Empty Recycle Bin%n(3) Exit%n ");
			System.out.println("chose the number of the choise:");

			double num1 = input.nextInt();

			if (num1 == 1) {
				System.out.println("You choise to  Clean the Temperley Folder");
				
				running = false;
				
			} else if (num1 == 2) {
				System.out.println("you choise to Empty Recycle Bin");
				running = false;
			} else if (num1 == 3) {
				System.out.println("Goodbye");
				running = false;
			} else {
				System.out.println("Your choice is incorrect");
				
			}
			
			
		} 

	}

}
