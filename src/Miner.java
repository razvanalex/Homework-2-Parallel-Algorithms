import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Class for a miner.
 */
public class Miner extends Thread {
	private Integer hashCount;
	private Set<Integer> solvedRooms;
	private CommunicationChannel channel;

	/**
	 * Creates a {@code Miner} object.
	 * 
	 * @param hashCount
	 *            number of times that a miner repeats the hash operation when
	 *            solving a puzzle.
	 * @param solved
	 *            set containing the IDs of the solved rooms
	 * @param channel
	 *            communication channel between the miners and the wizards
	 */
	public Miner(Integer hashCount, Set<Integer> solved, CommunicationChannel channel) {
		this.hashCount = hashCount;
		this.solvedRooms = solved;
		this.channel = channel;
	}

	/**
	 * The code that the miner is running. He waits for a message, mine it and then
	 * sends the answer to the wizard.
	 */
	@Override
	public void run() {
		while (true) {
			Message m = channel.getMessageWizardChannel();

			if (m == null || solvedRooms.contains(m.getCurrentRoom())) {
				continue;
			} else if (m.getData().equals("EXIT")) {
				break;
			}

			String key = encryptMultipleTimes(m.getData(), hashCount);
			Message newMsg = new Message(m.getParentRoom(), m.getCurrentRoom(), key);
			solvedRooms.add(m.getCurrentRoom());

			channel.putMessageMinerChannel(newMsg);
		}
	}

	/**
	 * Function to encrypt a string multiple times. (Is taken from the solver)
	 * 
	 * @param input the input string
	 * @param count the number of times the string is encrypted
	 * @return the new encrypted string.
	 */
	private String encryptMultipleTimes(String input, Integer count) {
		String hashed = input;
		for (int i = 0; i < count; ++i) {
			hashed = encryptThisString(hashed);
		}

		return hashed;
	}

	/**
	 * Encrypts a string using SHA-256.
	 * 
	 * @param input the string to be encrypted
	 * @return the encrypted string
	 */
	private String encryptThisString(String input) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] messageDigest = md.digest(input.getBytes(StandardCharsets.UTF_8));

			// convert to string
			StringBuffer hexString = new StringBuffer();
			for (int i = 0; i < messageDigest.length; i++) {
				String hex = Integer.toHexString(0xff & messageDigest[i]);
				if (hex.length() == 1)
					hexString.append('0');
				hexString.append(hex);
			}
			return hexString.toString();

		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}
}
