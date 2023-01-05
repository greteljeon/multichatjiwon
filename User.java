package selector;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

public class User {

	public enum State {
		IDLE, HEADER, BODY
	}

//	private static final int DEFAULT_BUFFER_SIZE = 8192;
	private static final int DEFAULT_BUFFER_SIZE = 100;

	private static ByteBuffer inputMessageBuffer = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE);

	private static final int HEADER_LENGTH = 4;
	private static final short MESSAGE_TYPE_REGISTER = 1;
	private static final short MESSAGE_TYPE_REGISTER_REPLY = 2;
	private static final short MESSAGE_TYPE_TEXT = 3;
	private static final short MESSAGE_TYPE_FILE = 4;
	private static final short MESSAGE_TYPE_BYE = 5;

	private final SocketChannel socketChannel;
	private final Queue<ByteBuffer> writeQueue = new LinkedList<ByteBuffer>();
	private final Queue<ByteBuffer> fileQueue = new LinkedList<ByteBuffer>();
	private final AtomicInteger eventOps = new AtomicInteger(0);
	private String Id;
	private State state = State.IDLE;
	int messageLength;
	int type;

	public User(SocketChannel socketChannel) {
		this.socketChannel = socketChannel;
	}

	public SocketChannel getSocketChannel() {
		return socketChannel;
	}

	public void setId(String Id) {
		this.Id = Id;
	}

	public String getId() {
		return Id;
	}

	public int readFromChannel() throws IOException {
		return socketChannel.read(inputMessageBuffer);
	}
	
	public void updateInputBuffer(int demand) {
		if(inputMessageBuffer.remaining() < demand)
		{
			ByteBuffer newBuffer = ByteBuffer.allocate(inputMessageBuffer.position() + demand);
			inputMessageBuffer.flip();
			newBuffer.put(inputMessageBuffer);
			inputMessageBuffer = newBuffer;
		}
	}

	public ReadResult parseMessage() {
		try {
			inputMessageBuffer.flip();

			if (state == State.IDLE) {
				state = State.HEADER;
			}

			if (state == State.HEADER) {
				if (inputMessageBuffer.remaining() < HEADER_LENGTH)
					return ReadResult.underflow(HEADER_LENGTH);

				messageLength = inputMessageBuffer.getShort();
				type = inputMessageBuffer.getShort();
				System.out.println("**messageLength: " + messageLength + " / type: " + type);
				state = State.BODY;
			}

			if (state == State.BODY) {
				if (inputMessageBuffer.remaining() < messageLength) {
					return ReadResult.underflow(messageLength);
				}
				if (type == MESSAGE_TYPE_REGISTER) {
					System.out.println("register");
					byte[] idBytes = new byte[messageLength];
					inputMessageBuffer.get(idBytes);
					this.Id = new String(idBytes);

					ByteBuffer outputMessageBuffer = ByteBuffer.allocate(HEADER_LENGTH);
					outputMessageBuffer.putShort((short) 0);
					outputMessageBuffer.putShort(MESSAGE_TYPE_REGISTER_REPLY);
					registerWriteBuffer(outputMessageBuffer);
					resetState();

					return ReadResult.register();
				} else if (type == MESSAGE_TYPE_TEXT) {
					System.out.println("text");
					byte[] readData = new byte[messageLength];
					byte[] sendId = getId().getBytes();
					inputMessageBuffer.get(readData);
					ByteBuffer outputMessageBuffer = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE);
					outputMessageBuffer.putShort((short) messageLength);
					outputMessageBuffer.putShort(MESSAGE_TYPE_TEXT);
					outputMessageBuffer.put(readData);
					outputMessageBuffer.put(sendId);
					outputMessageBuffer.flip();
					resetState();

					return ReadResult.text(outputMessageBuffer);
				} else if (type == MESSAGE_TYPE_FILE) {
					System.out.println("file");
					byte[] readData = new byte[messageLength];
					inputMessageBuffer.get(readData); // 버퍼만큼 받음
					ByteBuffer outputMessageBuffer = ByteBuffer.allocate(messageLength + 4);
					
					outputMessageBuffer.putShort((short)messageLength);
					outputMessageBuffer.putShort(MESSAGE_TYPE_FILE);
					outputMessageBuffer.put(readData);
					outputMessageBuffer.flip();
					resetState();

					return ReadResult.file(outputMessageBuffer);
				}
			}
		} finally {
			inputMessageBuffer.compact();
			System.out.println("**after compact()**");
		}
		return null;
	}

	public void registerWriteBuffer(ByteBuffer byteBuffer) {
		writeQueue.add(byteBuffer);
	}

	public void fileWriteBuffer(ByteBuffer byteBuffer) {
		writeQueue.add(byteBuffer);
	}

	public void write() throws IOException {
		while (!writeQueue.isEmpty()) {
			ByteBuffer writeBuffer = writeQueue.poll();
			if (writeBuffer.hasRemaining()) {
				socketChannel.write(writeBuffer);
				System.out.println(">>writeBuffer: " + writeBuffer.toString());
			}
		}
	}

	public void removeEvent(Selector selector, int removeOps) throws IOException {
		int currentOps = this.eventOps.get();
		if ((currentOps & removeOps) == 0)
			return;

		int newOps = currentOps & ~removeOps;
		if (eventOps.compareAndSet(currentOps, newOps)) {
			socketChannel.register(selector, newOps, this);
		}

	}

	public void registerEvent(Selector selector, int interestOps) throws IOException {
		int currentOps = this.eventOps.get();
		if ((currentOps & interestOps) == interestOps)
			return;

		int newOps = currentOps | interestOps;
		if (eventOps.compareAndSet(currentOps, newOps)) {
			socketChannel.register(selector, newOps, this);
		}
	}

	private void resetState() {
		state = State.IDLE;
	}

}