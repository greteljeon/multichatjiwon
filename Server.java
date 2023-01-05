package selector;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;


public class Server {
	
	private final Selector selector;
	private final ServerSocketChannel serverSocketChannel;
	private final Map<String, User> clientIdToClientMap = new HashMap<>();
	private AtomicBoolean closed = new AtomicBoolean(false);
	
	public Server(int port) throws IOException
	{
		selector = Selector.open();
		serverSocketChannel = ServerSocketChannel.open();
		serverSocketChannel.bind(new InetSocketAddress(port));
		serverSocketChannel.configureBlocking(false);
		serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
		System.out.println("**server start**");
	}

	public void start() throws IOException
	{
		while(!closed.get() && !Thread.currentThread().isInterrupted())
		{	
			int selectedNum = selector.select();		 // blocking
			Set<SelectionKey> selectedKeys = selector.selectedKeys();

			for(SelectionKey selectionKey : selectedKeys)
			{
				if(selectionKey.isAcceptable())		//	OP_ACCEPT
				{
					System.out.println("**isAcceptable**");
					accept(selectionKey);
				}
				if(selectionKey.isReadable())	// OP_READ
				{
					System.out.println("**isReadable**");
					readAndParse(selectionKey);
				}
				if(selectionKey.isWritable())	// OP_WRITE
				{
					System.out.println("**isWritable**");
					writeToChannel(selectionKey);
				}
			}
			selectedKeys.clear();
		}
	}// start()
	public void close()
	{
		if(closed.compareAndSet(false, true))
		{
			clientIdToClientMap.clear();
		}
	}

	// 
	private void accept(SelectionKey key) throws IOException {
		ServerSocketChannel sc = (ServerSocketChannel) key.channel();
		SocketChannel socketChannel = sc.accept();
		
		if(socketChannel != null && socketChannel.isConnected()) {
			socketChannel.configureBlocking(false);
			System.out.println(socketChannel);
			System.out.println("socketChannel connect");
		
			User user = new User(socketChannel);
			user.registerEvent(selector, SelectionKey.OP_READ);
		} else
			System.out.println("not ready yet: " + socketChannel);
	
	} // accept
	
	// read
	private void readAndParse(SelectionKey selectionKey) throws IOException{
		User user = (User) selectionKey.attachment();
		int read = user.readFromChannel();
		if(read == -1) {
			System.out.println("!!! read=-1");
			removeClient(user);
			return;
		}
		
		ReadResult readResult = user.parseMessage();
		if(readResult.getResult() == ReadResult.Result.UNDERFLOW)
		{
			System.out.println("!!UNDERFLOW!!");
			int demand = readResult.getDemand();
			user.updateInputBuffer(demand);
			user.registerEvent(selector, SelectionKey.OP_READ);
			return;
		}else if(readResult.getResult() == ReadResult.Result.REGISTER)
		{
			System.out.println("**REGISTER**");
			user.registerEvent(selector, SelectionKey.OP_WRITE);
			clientIdToClientMap.put(user.getId(), user);
			return;
		}else if(readResult.getResult() == ReadResult.Result.TEXT)
		{
			System.out.println("**TEXT**");
			ByteBuffer textBuffer = readResult.getTextBuffer();
			textBuffer.mark();
			for(String key : clientIdToClientMap.keySet())
			{
				textBuffer.reset();
				if(user.getId() != key)		
				{
					User otherUser = clientIdToClientMap.get(key);
					ByteBuffer clonedBuffer = ByteBuffer.allocate(textBuffer.remaining());				
					clonedBuffer.put(textBuffer);
					clonedBuffer.flip();
					otherUser.registerWriteBuffer(clonedBuffer);
					otherUser.registerEvent(selector, SelectionKey.OP_WRITE);
				}
			}
			return;
		} else if(readResult.getResult() == ReadResult.Result.FILE) {	//	 ÆÄÀÏ¤©¤©¤©¤©¤©¤©
			System.out.println("**FILE**");
			ByteBuffer fileBuffer = readResult.getFileBuffer();
			fileBuffer.mark();
			
			for(String key : clientIdToClientMap.keySet())
			{
				fileBuffer.reset();
				if(user.getId() != key)
				{
					System.out.println("**!!**");
					User otherUser = clientIdToClientMap.get(key);
					ByteBuffer clonedBuffer = ByteBuffer.allocate(fileBuffer.remaining());				
					clonedBuffer.put(fileBuffer);
					clonedBuffer.flip();
					otherUser.fileWriteBuffer(clonedBuffer);
					otherUser.registerEvent(selector, SelectionKey.OP_WRITE);
				}
			}
		}
		
	}
	
	private void writeToChannel(SelectionKey selectionKey) throws IOException  {
		System.out.println("**writeToChannel**");
		User user = (User) selectionKey.attachment();
		user.write();
		user.removeEvent(selector, SelectionKey.OP_WRITE);
	}
	
	private void removeClient(User user) {
		System.out.println("**removeClient()**");
		try {
			SocketChannel socketChannel = user.getSocketChannel();
			if(socketChannel!=null)
			{
				clientIdToClientMap.remove(user.getId());
				socketChannel.close();
				System.out.println("remove--");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}
