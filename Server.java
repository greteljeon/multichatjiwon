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
	// 연결된 클라이언트
	private final Map<String, User> clientIdToClientMap = new HashMap<>();	
	private AtomicBoolean closed = new AtomicBoolean(false);
	
	public Server(int port) throws IOException
	{
		// 셀렉터 생성, 서비스 포트 설정, 논블로킹 설정, 서버소켓채널 등록 
		selector = Selector.open();
		serverSocketChannel = ServerSocketChannel.open();
		serverSocketChannel.bind(new InetSocketAddress(port));
		serverSocketChannel.configureBlocking(false);
		serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
		System.out.println("**server start**");
	}

	// 서버 시작
	public void start()
	{
		try {
			while(!closed.get() && !Thread.currentThread().isInterrupted())
			{	
				int selectedNum = selector.select();		 // blocking
				Set<SelectionKey> selectedKeys = selector.selectedKeys();
				// 이벤트 처리
				for(SelectionKey selectionKey : selectedKeys)
				{
					if(selectionKey.isAcceptable())		//	OP_ACCEPT
					{
						System.out.println("**isAcceptable**");
						accept(selectionKey);
					}
					if(selectionKey.isWritable())	// OP_WRITE
					{
						System.out.println("**isWritable**");
						writeToChannel(selectionKey);
					}
					if(selectionKey.isReadable())	// OP_READ
					{
						System.out.println("**isReadable**");
						readAndParse(selectionKey);
					}
				}
				selectedKeys.clear();
			}
		}catch (Exception e) {
			e.printStackTrace();
			System.out.println("[server start() error]");
		}
	}// start()
	public void close()
	{
		if(closed.compareAndSet(false, true))
		{
			clientIdToClientMap.clear();
		}
	}

	// ServerSocketChannel ACCEPT
	private void accept(SelectionKey key) throws IOException {
		ServerSocketChannel sc = (ServerSocketChannel) key.channel();
		SocketChannel socketChannel = sc.accept();	//	 연결 대기
		
		if(socketChannel != null && socketChannel.isConnected()) {
			socketChannel.configureBlocking(false);
			System.out.println(socketChannel);
			System.out.println("socketChannel connect");
		
			// 채팅명(ID) 받기 위해 OP_READ로 변경
			User user = new User(socketChannel);
			user.registerEvent(selector, SelectionKey.OP_READ);
		} else
			System.out.println("not ready yet: " + socketChannel);
	} // accept
	
	// read한 메시지 파싱
	private void readAndParse(SelectionKey selectionKey) throws IOException{
		User user = (User) selectionKey.attachment();
		
		int read = user.readFromChannel();
		if(read == -1) {
			System.out.println("!!! read=-1");
			removeClient(selectionKey);
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
			clientIdToClientMap.put(user.getId(), user);
			user.registerEvent(selector, SelectionKey.OP_WRITE);
			System.out.println("["+user.getId()+"님 서버 입장하였습니다.]");
			return;
		}else if(readResult.getResult() == ReadResult.Result.TEXT)
		{
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
		} else if(readResult.getResult() == ReadResult.Result.FILE) {	
			ByteBuffer fileBuffer = readResult.getTextBuffer();
			fileBuffer.mark();
			
			for(String key : clientIdToClientMap.keySet())
			{
				fileBuffer.reset();
				if(user.getId() != key)
				{
					User otherUser = clientIdToClientMap.get(key);
					ByteBuffer clonedBuffer = ByteBuffer.allocate(fileBuffer.remaining());				
					clonedBuffer.put(fileBuffer);
					clonedBuffer.flip();
					otherUser.registerWriteBuffer(clonedBuffer);
					otherUser.registerEvent(selector, SelectionKey.OP_WRITE);
				}
			}
		}
		
	}
	
	private void writeToChannel(SelectionKey selectionKey) throws IOException  {
		User user = (User) selectionKey.attachment();
		user.write();
		user.removeEvent(selector, SelectionKey.OP_WRITE);
	}
	
	private void removeClient(SelectionKey selectionKey) throws IOException {
		User user = (User) selectionKey.attachment();
		String id = user.getId();
		SocketChannel socketChannel = user.getSocketChannel();
		if(socketChannel!=null)
		{
			clientIdToClientMap.remove(user.getId());
			
			System.out.println("["+id+"님의 연결이 종료되었습니다.]");
			System.out.println("현재 채팅방 사용자:");
			for(String key: clientIdToClientMap.keySet())
			{
				User otherUser = clientIdToClientMap.get(key);
				System.out.print("["+otherUser.getId() + "] ");
			}
			socketChannel.close();
		}
	}
	
}
