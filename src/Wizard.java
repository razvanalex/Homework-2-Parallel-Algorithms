import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class Wizard extends Thread {
	// test files
	private static String NODE_NAMES = "_data.txt";
	private static String ANSWER = "_answer.txt";
	private static String GRAPH = "_graph.txt";

	// helper messages
	private static String NO_PARENT = "NO_PARENT";

	/**
	 * Message body that specifies the end of the list of unlocked adjacent rooms
	 * sent by the wizards to the miners.
	 */
	public static String END = "END";

	/**
	 * Message body that specifies the end of the program (sent to each miner).
	 */
	public static String EXIT = "EXIT";

	// map of the mine
	private ArrayList<String> roomNames;
	private ArrayList<String> roomAnswers;
	private Integer numberOfRooms;
	private boolean[][] adjacencyMatrix;

	// termination
	private Integer numberOfMiners;
	private AtomicInteger hashesSolved;
	private static Set<String> hashesSolvedSet = new HashSet<String>();

	// communications
	private CommunicationChannel channel;

	/**
	 * Creates a {@code Wizard} object.
	 * 
	 * @param caveInfoPath
	 *            path to the input (i.e., the folder containing the mine map and
	 *            the expected results)
	 * @param numberOfMiners
	 *            number of miners
	 * @param hashesSolved
	 *            number of solved rooms
	 * @param channel
	 *            communication channel between the miners and the wizards
	 */
	public Wizard(String caveInfoPath, Integer numberOfMiners, AtomicInteger hashesSolved,
			CommunicationChannel channel) {
		parseHashes(caveInfoPath);
		parseAnswers(caveInfoPath);
		parseAdjMatrix(caveInfoPath);

		this.numberOfMiners = numberOfMiners;
		this.hashesSolved = hashesSolved;
		this.channel = channel;
	}

	private void parseHashes(String testCase) {
		roomNames = new ArrayList<>();

		try (BufferedReader br = new BufferedReader(new FileReader(testCase + NODE_NAMES))) {
			for (String line; (line = br.readLine()) != null;) {
				roomNames.add(line);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		this.numberOfRooms = roomNames.size();
	}

	private void parseAnswers(String testCase) {
		// Path answers = Paths.get(testCase + ANSWER);
		roomAnswers = new ArrayList<>();

		try (BufferedReader br = new BufferedReader(new FileReader(testCase + ANSWER))) {
			for (String line; (line = br.readLine()) != null;) {
				roomAnswers.add(line);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void parseAdjMatrix(String testCase) {
		// Path adjMatrix = Paths.get(testCase + GRAPH);
		adjacencyMatrix = new boolean[numberOfRooms][numberOfRooms];

		AtomicInteger row = new AtomicInteger(0);
		AtomicInteger column = new AtomicInteger(0);

		try (BufferedReader br = new BufferedReader(new FileReader(testCase + GRAPH))) {
			for (String line; (line = br.readLine()) != null;) {
				String[] splitLine = line.split(", ");
				for (String s : splitLine) {
					adjacencyMatrix[row.getAndIncrement()][column.get()] = s.equals("1");
				}
				column.getAndIncrement();
				row.set(0);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private List<Integer> getInitialRooms() {
		List<Integer> rootNodes = new ArrayList<>();
		boolean visited[] = new boolean[numberOfRooms];

		for (int room = 0; room < numberOfRooms; ++room) {
			if (!visited[room]) {
				visitSubTree(room, visited);
				rootNodes.add(room);
			}
		}
		return rootNodes;
	}

	private List<Integer> getNeighbors(int room) {
		List<Integer> neighbours = new ArrayList<>();

		for (int j = 0; j < numberOfRooms; ++j) {
			if (adjacencyMatrix[room][j])
				neighbours.add(j);
		}

		return neighbours;
	}

	private void visitSubTree(int room, boolean visited[]) {
		LinkedList<Integer> queue = new LinkedList<>();
		visited[room] = true;
		queue.push(room);

		while (!queue.isEmpty()) {
			int current = queue.poll();
			List<Integer> neighbors = getNeighbors(current);
			for (Integer neigh : neighbors) {
				if (!visited[neigh]) {
					visited[neigh] = true;
					adjacencyMatrix[neigh][current] = false;
					queue.add(neigh);
				}
			}
		}
	}

	private boolean checkWorkerMessage(Message message, List<Integer> rootNodes) {
		int parentNode = message.getParentRoom();
		int currentNode = message.getCurrentRoom();

		if (!rootNodes.contains(currentNode) && !adjacencyMatrix[parentNode][currentNode])
			return false;

		return message.getData().equals(roomAnswers.get(message.getCurrentRoom()));
	}

	@Override
	public void run() {
		// Message startMessage = new Message(-1, Wizard.START_LIST);
		Message endMessage = new Message(-1, Wizard.END);

		// put the initial rooms (entrances in the mine)
		List<Integer> rootNodes = getInitialRooms();

		// send the initial entrances
		// network.putMessageWizardChannel(startMessage);
		for (Integer node : rootNodes) {
			channel.putMessageWizardChannel(new Message(-1, Wizard.NO_PARENT));
			channel.putMessageWizardChannel(new Message(node, roomNames.get(node)));
		}
		channel.putMessageWizardChannel(endMessage);

		while (true) {
			if (hashesSolved.get() == numberOfRooms) {
				// network.putMessageWizardChannel(startMessage);
				for (int i = 0; i < numberOfMiners; ++i) {
					channel.putMessageWizardChannel(new Message(-1, Wizard.EXIT));
				}
				channel.putMessageWizardChannel(endMessage);

				System.out.println("All rooms have been solved! Wizard quitting.");
				System.exit(0);
			}

			// sleep random
			try {
				Thread.sleep((long) (Math.random() * 100));
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			// wake up and take one message from the workers
			Message workerMessage = channel.getMessageMinerChannel();
			if (workerMessage == null) {
				continue;
			}

			if (checkWorkerMessage(workerMessage, rootNodes)) {
				// put all the neighbors for hash processing
				Integer currentNode = workerMessage.getCurrentRoom();
				List<Integer> neighbors = getNeighbors(currentNode);

				// network.putMessageWizardChannel(startMessage);
				for (Integer neigh : neighbors) {
					channel.putMessageWizardChannel(new Message(currentNode, roomNames.get(currentNode)));
					channel.putMessageWizardChannel(new Message(neigh, roomNames.get(neigh)));
				}
				channel.putMessageWizardChannel(endMessage);

				synchronized (hashesSolvedSet) {
					if (!hashesSolvedSet.contains(workerMessage.getData())) {
						hashesSolved.getAndIncrement();
						hashesSolvedSet.add(workerMessage.getData());
					}
				}
			} else {
				System.out.println("Received incorrect parent/node or hash! Magic barrier exploded.");
				System.exit(1);
			}
		}
	}
}
