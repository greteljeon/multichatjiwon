package selector;

import java.io.IOException;
import java.net.InetAddress;

public class Main {
	public static void main(String[] args) throws IOException {
		int port = 9900;
		//port 얻어오기 터미널에서 
		String address = InetAddress.getLocalHost().toString();
		System.out.println("현재 포트번호:"+port);

		Server server = new Server(port);
		server.start();
	}
}
