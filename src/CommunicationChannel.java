import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Class that represents a pair of two elements of any type. This implementation
 * allows changing the value of elements, thus the class is mutable.
 * 
 * @param <F>
 *            the type of the first element from pair
 * @param <S>
 *            the type of the second element from pair
 */
class Pair<F, S> {

	/**
	 * The first element of the pair.
	 */
	private F first;

	/**
	 * The second element of the pair.
	 */
	private S second;

	/**
	 * Constructor for a pair. You must initialize both elements, when you
	 * instantiate a new pair.
	 */
	public Pair(F first, S second) {
		this.first = first;
		this.second = second;
	}

	/**
	 * @return the second element from pair
	 */
	public S getSecond() {
		return second;
	}

	/**
	 * @param second
	 *            the second element to set
	 */
	public void setSecond(S second) {
		this.second = second;
	}

	/**
	 * @return the first element from pair
	 */
	public F getFirst() {
		return first;
	}

	/**
	 * @param first
	 *            the first element to set
	 */
	public void setFirst(F first) {
		this.first = first;
	}
}

/**
 * Class that implements the channel used by wizards and miners to communicate.
 * 
 * The communication channel will store ONE complete message from wizards
 * (instead of two, as it is), making the miners "thinking" only to their jobs
 * and not how to reassemble the message. The miners will send only a single
 * message per solved puzzle, hence the wizards will get only one message from
 * miners at a time.
 */
public class CommunicationChannel {

	/**
	 * A hash map used to store the queue for each wizard. The key is the thread
	 * id of the wizard, and the value is a pair of (atomic) boolean and a queue
	 * where the boolean is used to store the lock status and the queue is the
	 * actual communication channel. For each wizard, there is a channel.
	 */
	private ConcurrentHashMap<Long, Pair<AtomicBoolean, ArrayBlockingQueue<Message>>> wizardToMinerBuffer;

	/**
	 * A hash map used to store the queue for each miner. The key is the thread
	 * id of the miner, and the value is a pair of (atomic) boolean and a queue
	 * where the boolean is used to store the lock status and the queue is the
	 * actual communication channel. For each miner, there is a channel.
	 */
	private ConcurrentHashMap<Long, Pair<AtomicBoolean, ArrayBlockingQueue<Message>>> minerToWizardBuffer;

	/**
	 * The current message that is being created from wizards messages. The hash
	 * map stores the id of wizard as key and the pair of current stage
	 * (Integer) and the message (Message) that's being created. This map is
	 * used because of the way of wizards to send the messages, split in parts.
	 */
	private ConcurrentHashMap<Long, Pair<Integer, Message>> crtNewMessage;

	/**
	 * Semaphore used to limit the number of miners from the wizard channel at a
	 * given time. The semaphore is limited at the number of channels (or
	 * wizards).
	 */
	private Semaphore wizardChannelSem;

	/**
	 * Semaphore used to limit the number of wizards from the miner channel at a
	 * given time. The semaphore is limited at the number of channels (or
	 * miners).
	 */
	private Semaphore minerChannelSem;

	/**
	 * The maximum size of an array blocking queue.
	 */
	private final int MAX_QUEUE_SIZE = 1000;

	/**
	 * Creates a {@code CommunicationChannel} object.
	 */
	public CommunicationChannel() {
		wizardToMinerBuffer = new ConcurrentHashMap<>();
		minerToWizardBuffer = new ConcurrentHashMap<>();

		crtNewMessage = new ConcurrentHashMap<>();

		wizardChannelSem = new Semaphore(0);
		minerChannelSem = new Semaphore(0);
	}

	/**
	 * Puts a message on the miner channel (i.e., where miners write to and
	 * wizards read from). It also creates a new channel for each new miner.
	 * 
	 * @param message
	 *            message to be put on the channel
	 */
	public void putMessageMinerChannel(Message message) {
		long crtMinerId = Thread.currentThread().getId();

		Pair<AtomicBoolean, ArrayBlockingQueue<Message>> pair = minerToWizardBuffer
				.get(crtMinerId);

		if (pair == null) {
			pair = new Pair<>(new AtomicBoolean(true),
					new ArrayBlockingQueue<Message>(MAX_QUEUE_SIZE));
			minerToWizardBuffer.put(crtMinerId, pair);
			minerChannelSem.release();
		}

		try {
			pair.getSecond().put(message);
		} catch (InterruptedException ex) {
			ex.printStackTrace();
		}

		pair.getFirst().set(false);
	}

	/**
	 * Gets a message from the miner channel (i.e., where miners write to and
	 * wizards read from). Only the number of minerToWizardBuffer threads can
	 * access the function at a time. Each wizard will find a nonempty and
	 * unlocked channel to read from.
	 * 
	 * @return message from the miner channel
	 */
	public Message getMessageMinerChannel() {
		Message msg = null;

		try {
			minerChannelSem.acquire();
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}

		while (true) {
			for (Map.Entry<Long, Pair<AtomicBoolean, ArrayBlockingQueue<Message>>> e : minerToWizardBuffer
					.entrySet()) {
				ArrayBlockingQueue<Message> queue = e.getValue().getSecond();
				AtomicBoolean isLocked = e.getValue().getFirst();
				if (queue.isEmpty() || isLocked.get())
					continue;

				synchronized (isLocked) {
					isLocked.set(true);

					try {
						msg = queue.take();
					} catch (InterruptedException ex) {
						ex.printStackTrace();
					}
					minerChannelSem.release();

					isLocked.set(false);
				}

				return msg;
			}
		}
	}

	/**
	 * Puts a message on the wizard channel (i.e., where wizards write to and
	 * miners read from). Each wizard has to assemble the message he wants to
	 * put. This is done in two stages. In the first stage, the wizard put an
	 * END message and this will reset the stages, or the first part of the
	 * message, and will go to the next stage. In the second stage, the message
	 * is completed and put in the queue of that thread. In the end, the buffer
	 * is reset.
	 * 
	 * @param message
	 *            message to be put on the channel
	 */
	public void putMessageWizardChannel(Message message) {
		long crtWizardId = Thread.currentThread().getId();
		Pair<Integer, Message> crtNewMsg = crtNewMessage.get(crtWizardId);

		// Creates a new temporary buffer
		if (crtNewMsg == null) {
			crtNewMsg = new Pair<>(0, null);
			crtNewMessage.putIfAbsent(crtWizardId, crtNewMsg);
		}

		if (crtNewMsg.getFirst() == 0) {
			// This is the first stage
			if (message.getData().equals(Wizard.END)) {
				return;
			} else {
				crtNewMsg.setSecond(
						new Message(message.getCurrentRoom(), -1, ""));
				crtNewMsg.setFirst(crtNewMsg.getFirst() + 1);
			}

		} else if (crtNewMsg.getFirst() == 1) {
			// This is the second stage
			Message m = crtNewMsg.getSecond();
			m.setCurrentRoom(message.getCurrentRoom());
			m.setData(message.getData());

			// Check if the message is already in the buffer, or not.
			// Do not allow duplicates in the queue!
			if (!contains(wizardToMinerBuffer, m)) {
				Pair<AtomicBoolean, ArrayBlockingQueue<Message>> pair = wizardToMinerBuffer
						.get(crtWizardId);

				// Create a new channel for the new come wizard
				if (pair == null) {
					pair = new Pair<>(new AtomicBoolean(true),
							new ArrayBlockingQueue<Message>(MAX_QUEUE_SIZE));
					wizardToMinerBuffer.put(crtWizardId, pair);
					wizardChannelSem.release();
				}

				// Put the message on the channel
				try {
					pair.getSecond().put(m);
				} catch (InterruptedException ex) {
					ex.printStackTrace();
				}

				pair.getFirst().set(false);
			}

			// Reset the buffer
			crtNewMsg.setFirst(0);
			crtNewMsg.setSecond(null);
		}
	}

	/**
	 * Gets a message from the wizard channel (i.e., where wizards write to and
	 * miners read from). Only number_of_wizards miners can access this function
	 * at a time. Each miner will find a nonempty and unlocked channel to read
	 * from.
	 * 
	 * @return message from the miner channel
	 */
	public Message getMessageWizardChannel() {
		Message msg = null;

		try {
			wizardChannelSem.acquire();
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}

		while (true) {
			for (Map.Entry<Long, Pair<AtomicBoolean, ArrayBlockingQueue<Message>>> e : wizardToMinerBuffer
					.entrySet()) {
				ArrayBlockingQueue<Message> queue = e.getValue().getSecond();
				AtomicBoolean isLocked = e.getValue().getFirst();
				if (queue.isEmpty() || isLocked.get())
					continue;

				synchronized (isLocked) {
					isLocked.set(true);

					try {
						msg = queue.take();
					} catch (InterruptedException ex) {
						ex.printStackTrace();
					}

					isLocked.set(false);
				}

				wizardChannelSem.release();
				return msg;
			}
		}
	}

	/**
	 * Checks if a map contains a given element.
	 * 
	 * @param map
	 *            the map to be checked
	 * @param element
	 *            the element that may be or not in the map
	 * @return whether the element is in the map or not.
	 */
	private boolean contains(
			ConcurrentHashMap<Long, Pair<AtomicBoolean, ArrayBlockingQueue<Message>>> map,
			Message element) {
		Message msg;
		Iterator<Message> it;

		for (Map.Entry<Long, Pair<AtomicBoolean, ArrayBlockingQueue<Message>>> e : map
				.entrySet()) {
			ArrayBlockingQueue<Message> queue = e.getValue().getSecond();

			for (it = queue.iterator(); it.hasNext();) {
				msg = it.next();
				if (msg.getCurrentRoom() == element.getCurrentRoom()
						&& msg.getData().equals(element.getData())) {
					return true;
				}
			}
		}

		return false;
	}
}
