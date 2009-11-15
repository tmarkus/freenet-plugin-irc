package plugin.frirc;
import java.io.*;
import java.net.*;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactoryConfigurationError;

class ClientOutput extends FrircConnection {

	public ClientOutput (Socket s, IRCServer server) 
	throws IOException, TransformerConfigurationException, ParserConfigurationException, TransformerFactoryConfigurationError {
		super(s, null,null);
		this.server = server;

		//System.out.println("Serving: "+socket); 
		out = 
			new PrintWriter(
					new BufferedWriter(
							new OutputStreamWriter(
									socket.getOutputStream())), true);
		start(); // Calls run()
	}
	public void run() {
		try {
			while (true)
			{  
				Message message = server.getMessageToSend(this);

				if (message != null)
				{
					System.out.println("SENT: " + message);
					out.println(message.toString());
				}

				if (!server.isAlive() || server.isInterrupted())
				{
					socket.close();
					return; //stop the thread?
				}

				Thread.sleep(200);
			}

		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				socket.close();
			} catch(IOException e) {}
		}
	}
}
