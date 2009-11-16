package plugin.frirc;
import java.io.*;
import java.net.*;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactoryConfigurationError;

class ClientInput extends FrircConnection {

	public ClientInput (Socket s, IRCServer server) 
	throws IOException, TransformerConfigurationException, ParserConfigurationException, TransformerFactoryConfigurationError {
		super(s,null,null);
		this.server = server;

		//System.out.println("Serving: "+socket); 
		in = 
			new BufferedReader(
					new InputStreamReader(
							socket.getInputStream()));


		start(); // Calls run()
	}
	public void run() {
		try {
			while (true)
			{  
				String str = in.readLine();
				if (str== null ) break;  

				System.out.println("RECEIVED: " + str);
				server.message(this, str);
			}
			System.out.println("Disconnected with.."+socket);
			
		} catch (IOException e) {

		} finally {
			try {
				socket.close();
			} catch(IOException e) {}
		}
	}


	public boolean isLocalClientInput()
	{
		return true;
	}

}
