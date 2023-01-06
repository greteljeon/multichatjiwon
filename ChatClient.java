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
	private static final String DEFAULT_FILE_PATH2 = "C:\\Users\\tmax\\eclipse-workspace\\chatProject\\WebContent\\WEB-INF\\lib\\";

	private static Socket socket;
	private static Thread thread;
	private static final short MESSAGE_TYPE_REGISTER = 1;
	private static final short MESSAGE_TYPE_REGISTER_REPLY = 2;
	private static final short MESSAGE_TYPE_TEXT = 3;
	private static final short MESSAGE_TYPE_FILE = 4;
	
	// 연결할 서버 주소 입력
	private static List<Object> getAddress() { 
		Scanner sc = new Scanner(System.in);
		String address = sc.nextLine();
		String ip = null;
		int port = 0;
		try {
			if (address == null || address.length() < 4) {
				ip = InetAddress.getLocalHost().toString();
				ip = ip.substring(ip.indexOf("/") + 1);
				port = 9900;
			} else {
				ip = address.substring(0, address.indexOf("/")); 
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

		return list;
	}

	public static void main(String[] args) throws IOException {
		String ip;
		int port;
		System.out.println("[ip/port 입력]");
		try {
			// 서버 연결할 ip, port 입력 받기
			List<Object> list = getAddress(); 
			ip = (String) list.get(0);
			port = (int) list.get(1);
			new ChatClient(ip, port);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		if(socket.isClosed())
			System.out.println("[서버와 연결이 종료됩니다.]");
	}

	public static void stopClient(Socket socket) {
		try {
			if(!Thread.interrupted()) {
				Thread.currentThread().interrupt();
			}
			if (socket != null && !socket.isClosed()) {
				socket.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	} 

	public ChatClient(String ip, int port) throws IOException {
		startClient(ip, port);
	}

	// 서버 연결(ip와 포트 주소 입력)과 채팅 준비
	private void startClient(String ip, int port) throws IOException { // connect server
		// 대화명 입력
		Scanner sc = new Scanner(System.in);
		System.out.println("<대화명 입력>");
		String id = sc.nextLine();

		// 서버와 소켓 연결
		socket = new Socket(ip, port);
		System.out.println("*--client connection success--*");
		System.out.println("[서버와 연결 " + socket.getRemoteSocketAddress() + "]");

		// ID 보내기
		send(MESSAGE_TYPE_REGISTER, id);

		// 리스닝스레드 생성 및 시작
		ListeningThread listeningThread = new ListeningThread(socket); // read 스레드
		Thread thread = new Thread(listeningThread);
		thread.start();

		// 채팅 프로토콜 시작
		sendProtocol();
	}

	// send 프로토콜
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
			// 파일 입력
			if (chatMessage.equalsIgnoreCase("FILE")) {
				System.out.println("파일명 입력▼");
				String fileName = sc.nextLine();
				sendFile(MESSAGE_TYPE_FILE, fileName);
			// 채팅 종료
			}else if(chatMessage.equalsIgnoreCase("EXIT")){
				break;
			}else {
			// 일반 채팅
				send(MESSAGE_TYPE_TEXT, chatMessage);	
			}
		} 
		stopClient(socket);
	} 
	
	// 보내기
	void send(Short type, String sendData) throws IOException {
		int dataLen = sendData.getBytes().length;

		//데이터 사이즈와 프로토콜 타입 입력(2+2 = 4bytes)
		byte[] dataLengthArray = shortToByteArray((short) dataLen);
		byte[] typeArray = shortToByteArray(type);

		if (dataLen > 32767) {
			return;
		}
		byte[] sendByteArray = new byte[4 + dataLen];
		System.arraycopy(dataLengthArray, 0, sendByteArray, 0, 2);
		System.arraycopy(typeArray, 0, sendByteArray, 2, 2);
		System.arraycopy(sendData.getBytes(), 0, sendByteArray, 4, dataLen);

		OutputStream outputStream = socket.getOutputStream();
		outputStream.write(sendByteArray);

		outputStream.flush();
	} // send()

	// 파일 보내는 순서
	void sendFile(Short type, String fileName) throws IOException {
		File file = new File(DEFAULT_FILE_PATH + fileName); // 보낼 파일
		if (!file.exists()) {
			System.out.println("해당 파일 없음");
			return;
		}
		FileInputStream fileInputStream = new FileInputStream(file);
		OutputStream outputStream = socket.getOutputStream();

		long fileLen = file.length();
		
		byte[] readBuffer = new byte[READ_BUFFER_SIZE];
		byte[] fileLengthArray = shortToByteArray((short) fileLen); // 파일사이즈
		byte[] typeArray = shortToByteArray(type); 					// 메시지 타입
		
		outputStream.write(fileLengthArray);
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
			try {
				while (true) {
					receiveProtocol();
					if(Thread.interrupted()) {
		                break;
		            }
				} 
			} catch (Exception e) {
				e.printStackTrace();
				System.out.println("Client run() IOException");
			} finally {
				System.out.println("end");
			}
		}
		private int readShort(byte[] readHeaderByte, int offset) {
	        int ch1 = readHeaderByte[offset] & 0xff;
	        int ch2 = readHeaderByte[offset + 1] & 0xff;
	        return ((ch1 << 8) + ch2);
	    }
		
		private void receiveProtocol() {
			try {
				InputStream inputStream = socket.getInputStream();
	
				int readBytes = 0;
				byte[] readHeaderByte = new byte[4]; 
				while(readBytes < 4) {
					readBytes = inputStream.read(readHeaderByte);
					if(readBytes == -1) {
						return;
					}
				}
				int dataSize = readShort(readHeaderByte, 0);
				int dataType = readShort(readHeaderByte, 2);
				
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
						} 
					}
					String sendId = new String(idBytes, "UTF-8");
					receive(sendId, messageByte);
					
				} else if (dataType == MESSAGE_TYPE_FILE) {
					messageByte = new byte[dataSize];
					messageByte = readFull(inputStream, messageByte, offset, messageByte.length);
					
					receiveFile(messageByte);
				} else if(dataType == MESSAGE_TYPE_REGISTER_REPLY) {
					System.out.println("server: [대화명 등록 완료]");
				}
			}catch (Exception e) {
				stopClient(socket);
			}
		}// listeningthread
		
		// 채팅 수신
		private void receive(String sendId, byte[] receiveMessageByte) throws IOException {
			System.out.print(sendId + ": ");
			String message = new String(receiveMessageByte, StandardCharsets.UTF_8);
			System.out.println(message);
		} // receive()

		// 파일 받기
		private void receiveFile(byte[] fileContextBytes) {
			try {
				Random random = new Random();
				int num = random.nextInt(1000);
				String arr = num + ".txt";
				String newFileName = arr;
				
				File file = new File(DEFAULT_FILE_PATH2 + newFileName);
				FileOutputStream fileOutputStream = new FileOutputStream(file); // 파일 생성

				if (file.exists()) {
					fileOutputStream.write(fileContextBytes);
				}
				System.out.println("["+newFileName+ " 파일 저장 완료]");
			} catch (Exception e) {
				e.printStackTrace();
				System.out.println("receiveFile fail");
			}
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