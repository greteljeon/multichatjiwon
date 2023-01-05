package selector;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;

public class ChatClient {
//	private static final int READ_BUFFER_SIZE = Integer.getInteger("readBufferSize", 8192);
	private static final int READ_BUFFER_SIZE = Integer.getInteger("readBufferSize", 100);
	private static final String DEFAULT_FILE_PATH = "./WebContent/WEB-INF/lib/";
	private static final String DEFAULT_FILE_PATH2 = "/home/jeon/eclipse-workspace/ProjectMultiChat/WebContent/WEB-INF/lib/";
//	private static final String DEFAULT_FILE_PATH = "D:\\dev\\workspace\\MultiChat\\src\\main\\webapp\\WEB-INF\\lib\\";
//	private static final String DEFAULT_FILE_PATH2 = "D:\\dev\\workspace\\MultiChat\\src\\main\\webapp\\WEB-INF\\lib\\";


	private static Socket socket;
	private static final short MESSAGE_TYPE_REGISTER = 1;
	private static final short MESSAGE_TYPE_REGISTER_REPLY = 2;
	private static final short MESSAGE_TYPE_TEXT = 3;
	private static final short MESSAGE_TYPE_FILE = 4;
//	private static final short MESSAGE_TYPE_BYE = 5;
	
	private static List<Object> getAddress() { // 연결 주소 입력
		Scanner sc = new Scanner(System.in);
		String address = sc.nextLine();
		String ip = "";
		int port = 0;
		try {
			if (address == null || address.length() < 4) {
				ip = InetAddress.getLocalHost().toString();
				ip = ip.substring(ip.indexOf("/") + 1);
				port = 9900;
			} else {
				ip = address.substring(0, address.indexOf("/")); // 127.0.1.1
				port = Integer.parseInt(address.substring(address.indexOf("/") + 1, address.length()));
			}
			System.out.println("[server ip]: " + ip + "[server port]: " + port);
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("client address -- Exception");
		}
		List<Object> list = new ArrayList<Object>();
		list.add(ip);
		list.add(port);
//		sc.close();

		return list;
	}

	public static void main(String[] args) throws IOException {
		String ip;
		int port;
		System.out.println("[ip/port 입력]");
		try {
			List<Object> list = getAddress(); // ip, port 입력 받기
			ip = (String) list.get(0);
			port = (int) list.get(1);
			new ChatClient(ip, port);
		} catch (Exception e) {
			e.printStackTrace();
			stopClient();
		}
	}

	public static void stopClient() {
		try {
			System.out.println("[연결 끊음]");
			if (socket != null && !socket.isClosed()) {
				socket.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	} // stopClient()

	public ChatClient(String ip, int port) {
		startClient(ip, port);
	}

	// ip&port 주소 입력
	private void startClient(String ip, int port) { // connect server
		Scanner sc = new Scanner(System.in);
		try {
			System.out.println("<대화명 입력>");
			String id = sc.nextLine();
			System.out.println("id: " + id);

			socket = new Socket(ip, port);
			System.out.println("*--client connection success--*");
			System.out.println("[연결 완료" + socket.getRemoteSocketAddress() + "]");

			send(MESSAGE_TYPE_REGISTER, id); // ID 등록(register)

			ListeningThread lt = new ListeningThread(socket); // read 스레드
			Thread thread = new Thread(lt);
			thread.start();

			sendProtocol();
		} catch (Exception e) {
			System.out.println("[서버통신안됨]");
			e.printStackTrace();
			stopClient();
		}
		sc.close();
	}// startClient()

	void send(Short type, String data) throws IOException {
		int dataLen = data.getBytes().length;

		byte[] dataLengthArray = shortToByteArray((short) dataLen);
		byte[] typeArray = shortToByteArray(type);

		if (dataLen > 32767) {
			System.out.println("data length가 short보다 큼");
			return;
		}
		byte[] sendByteArray = new byte[4 + dataLen];
		System.arraycopy(dataLengthArray, 0, sendByteArray, 0, 2);
		System.arraycopy(typeArray, 0, sendByteArray, 2, 2);
		System.arraycopy(data.getBytes(), 0, sendByteArray, 4, dataLen);

		OutputStream outputStream = socket.getOutputStream();
		outputStream.write(sendByteArray);

		outputStream.flush();
		System.out.println("[전송 완료]");
	} // send()

	// 파일 보내는 순서
	void sendFile(Short type, String fileName) throws IOException {
		File file = new File(DEFAULT_FILE_PATH + fileName); // 보낼 파일
		if (!file.exists()) {
			System.out.println("파일 없음");
			return;
		}
		System.out.println("file exist");
		FileInputStream fileInputStream = new FileInputStream(file);
		OutputStream outputStream = socket.getOutputStream();

		long fileLen = file.length();
		System.out.println("fileLen: " + fileLen);
		
		byte[] readBuffer = new byte[READ_BUFFER_SIZE];
		byte[] dataLengthArray = shortToByteArray((short) fileLen); // 파일사이즈
		byte[] typeArray = shortToByteArray(type); 					// 메시지 타입
		
		outputStream.write(dataLengthArray);
		outputStream.write(typeArray);
	
		int totalRead = 0;
        while (totalRead < fileLen) {
            int read = fileInputStream.read(readBuffer, 0, READ_BUFFER_SIZE);
            if (read < 0) {
                break;
            }
            outputStream.write(readBuffer, 0, read);
            totalRead += read;
        }
		outputStream.flush();
		System.out.println("[전송 완료]");
	} // send()

	void sendProtocol() throws IOException {
		Scanner sc = new Scanner(System.in);
		
		System.out.println("-------------------------");
		System.out.println("File을 보내고 싶으면 'FILE' 입력");
		System.out.println("채팅 종료하고 싶으면 'EXIT' 입력");
		System.out.println("-------------------------");
		
		while (true) {
			String chatMessage = null;
			
			if(sc.hasNext()) {
				chatMessage = sc.nextLine();
			}
			System.out.println("chatMessage1: "+chatMessage);
//			if(chatMessage==null) {
//				System.out.println("hi");
//				break;
//			}
			if (chatMessage.equalsIgnoreCase("FILE")) {
				System.out.println("파일 주소 입력(파일명)▼");
				String fileName = sc.nextLine();
				sendFile(MESSAGE_TYPE_FILE, fileName);
			}else if(chatMessage.equalsIgnoreCase("EXIT")){
				System.out.println("채팅 종료");
				break;
			}else {
				send(MESSAGE_TYPE_TEXT, chatMessage);	
			}
		} 
	} 

	// short --> Byte
	public byte[] shortToByteArray(short length) {
		byte[] data = new byte[2];
		data[0] = (byte) (length >> 8);
		data[1] = (byte) length;

		return data;
	}

	private byte[] allocateBuffer(long fileSize) {
		byte[] fileContent;
		if (fileSize >= (READ_BUFFER_SIZE-4)) {
			fileContent = new byte[READ_BUFFER_SIZE];
		} else {
			fileContent = new byte[(int)fileSize];
		}
//		System.out.println("fileContent: "+fileContent.length);
		return fileContent;
	}

	// 받는 스레드
	class ListeningThread implements Runnable {
		Socket socket;

		public ListeningThread(Socket socket) {
			this.socket = socket;
		}

		@Override
		public void run() {
			System.out.println("수신 준비");
			try {
				while (true) {
					receiveProtocol();
				}
			} catch (Exception e) {
				e.printStackTrace();
				System.out.println("Client run() IOException");
			} finally {
				stopClient();
				System.out.println("end");
			}
		}
		private int readUShort(byte[] readHeaderByte, int offset) {
	        int ch1 = readHeaderByte[offset] & 0xff;
	        int ch2 = readHeaderByte[offset + 1] & 0xff;
//	        return (short) ((ch1 << 8) + ch2);
	        return ((ch1 << 8) + ch2);
	    }
		
		// 받기
		private void receiveProtocol() throws IOException {
//			System.out.println("**receiveProtocol**");
			InputStream inputStream = socket.getInputStream();

			int readBytes = 0;
			byte[] readHeaderByte = new byte[4]; 
			while(readBytes < 4) {
				readBytes = inputStream.read(readHeaderByte);
			}
			int dataSize = readUShort(readHeaderByte, 0);
			int dataType = readUShort(readHeaderByte, 2);
			
//			System.out.println("**dataSize: "+dataSize);
//			System.out.println("**dataType: "+dataType);
			
			byte[] messageByte = allocateBuffer(dataSize);
			byte[] idBytes = null;
			int offset = 0;
			if (dataType == MESSAGE_TYPE_TEXT) {
				// 메시지 읽기
				messageByte = readFull(inputStream, messageByte, 0, dataSize);

				if (inputStream.available() > 0) {
					idBytes = new byte[inputStream.available()];
					while (inputStream.available() > 0) { // 보낸 클라이언트ID 담기
						inputStream.read(idBytes);
					} // while
				}
				String sendId = new String(idBytes, "UTF-8");
				receive(sendId, messageByte);
				
			} else if (dataType == MESSAGE_TYPE_FILE) {
				messageByte = new byte[dataSize];
				messageByte = readFull(inputStream, messageByte, offset, messageByte.length);
				
				receiveFile(messageByte);
			}
		}// listeningthread
		
		// 텍스트 수신
		private void receive(String sendId, byte[] receiveMessageByte) throws IOException {
//			System.out.println("**receive()**");
			try {
				System.out.print(sendId + ": ");
				String message = new String(receiveMessageByte, StandardCharsets.UTF_8);
				System.out.println(message);
			} catch (Exception e) {
				e.printStackTrace();
				stopClient();
			}
		} // receive()

		// 파일 받기
		private void receiveFile(byte[] fileContextBytes) {
			System.out.println("**receiveFile**");
			Scanner sc = new Scanner(System.in);

			try {
				//System.out.println("저장되는 파일명▼");
//				newFileName = sc.nextLine();
				Random random = new Random();
				int num = random.nextInt(1000);
				String arr = num + ".txt";
				String newFileName = arr;
//				System.out.println(newFileName);
				
				File file = new File(DEFAULT_FILE_PATH2 + newFileName);
				FileOutputStream fileOutputStream = new FileOutputStream(file); // 파일 생성
				System.out.println(newFileName + "파일 생성");

				if (file.exists()) {
//					System.out.println("파일 작성 시작");
					fileOutputStream.write(fileContextBytes);
				}
				System.out.println("파일 수신 완료");
			} catch (Exception e) {
				e.printStackTrace();
				System.out.println("receiveFile fail");
			}
			sc.close();
		}

		private byte[] readFull(InputStream inputStream, byte[] bytes, int offset, int size) throws IOException {
			int left = size;
			while (true) {
				int readCount = inputStream.read(bytes, offset, left);
				if (readCount == -1) {
					break;
				}
				offset += readCount;
				left -= readCount;
				if (left <= 0) {
					break;
				}
			} // while
			return bytes;
		}
	}
}