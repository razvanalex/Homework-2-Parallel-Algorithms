/**
 * Class for a message exchanged between miners and wizards.
 */
public class Message {
	private int parentRoom;
	private int currentRoom;
	private String data;

	/**
	 * Creates a {@code Message} object.
	 * 
	 * @param parentRoom  the previous room
	 * @param currentRoom the current room
	 * @param data        the data contained in the message (e.g., solution of the
	 *                    puzzle)
	 */
	public Message(int parentRoom, int currentRoom, String data) {
		this.parentRoom = parentRoom;
		this.currentRoom = currentRoom;
		this.data = data;
	}

	/**
	 * Creates a {@code Message} object.
	 * 
	 * @param currentRoom the current room
	 * @param data        data contained in the message
	 */
	public Message(int currentRoom, String data) {
		this.currentRoom = currentRoom;
		this.data = data;
	}

	/**
	 * Gets the parent room from the message.
	 * 
	 * @return the parent room ID
	 */
	public int getParentRoom() {
		return parentRoom;
	}

	/**
	 * Sets the parent room in the message.
	 * 
	 * @param parentRoom the parent room ID to be set
	 */
	public void setParentRoom(int parentRoom) {
		this.parentRoom = parentRoom;
	}

	/**
	 * Gets the current room from the message.
	 * 
	 * @return the current room ID
	 */
	public int getCurrentRoom() {
		return currentRoom;
	}

	/**
	 * Sets the current room in the message.
	 * 
	 * @param currentRoom the current room ID to be set
	 */
	public void setCurrentRoom(int currentRoom) {
		this.currentRoom = currentRoom;
	}

	/**
	 * Gets the data in the message.
	 * 
	 * @return the data in the message
	 */
	public String getData() {
		return data;
	}

	/**
	 * Sets the data in the message.
	 * 
	 * @param data the data to be set in the message
	 */
	public void setData(String data) {
		this.data = data;
	}

	@Override
	public String toString() {
		return "<" + parentRoom + ", " + currentRoom /* + ", " + data */ + ">";
	}
}
