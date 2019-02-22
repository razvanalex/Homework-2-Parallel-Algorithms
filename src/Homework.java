import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Main class.
 */
public class Homework {

	public static void checkForDeadlock() {
		ThreadMXBean bean = ManagementFactory.getThreadMXBean();
		long[] threadIds = bean.findDeadlockedThreads(); // Returns null if no threads are deadlocked.

		if (threadIds != null) {
			ThreadInfo[] infos = bean.getThreadInfo(threadIds);

			for (ThreadInfo info : infos) {
				StackTraceElement[] stack = info.getStackTrace();
				System.out.println(stack);
			}
		}
	}

	public static void main(String[] args) {
		if (args.length < 4) {
			System.out
					.println("Usage: java Homework <caveInfoPath> <numberOfHashes> <numberOfWizards> <numberOfMiners>");
			System.exit(1);
		}

		int numberOfHashes = Integer.parseInt(args[1]);
		int numberOfWizards = Integer.parseInt(args[2]);
		int numberOfMiners = Integer.parseInt(args[3]);

		AtomicInteger hashesSolved = new AtomicInteger(0);
		CommunicationChannel channel = new CommunicationChannel();

		Wizard wizards[] = new Wizard[numberOfWizards];
		for (int i = 0; i < numberOfWizards; ++i) {
			wizards[i] = new Wizard(args[0], numberOfMiners, hashesSolved, channel);
			wizards[i].start();
		}

		Set<Integer> solved = new HashSet<>();
		Miner miners[] = new Miner[numberOfMiners];
		for (int i = 0; i < numberOfMiners; ++i) {
			miners[i] = new Miner(numberOfHashes, solved, channel);
			miners[i].start();
		}

		try {
			for (int i = 0; i < numberOfWizards; ++i) {
				wizards[i].join();
			}

			for (int i = 0; i < numberOfMiners; ++i) {
				miners[i].join();
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
