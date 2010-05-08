/* This code is part of a plugin for Freenet. It is distributed 
 * under the GNU General Public License, version 3 (or at your option
 * any later version). See http://www.gnu.org/ for details of the GPL. */

package plugin.frirc.connections;

import java.io.*;
import java.net.*;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactoryConfigurationError;

import plugin.frirc.IRCServer;
import plugin.frirc.message.IRCMessage;

public class LocalClient extends FrircConnection {

	public LocalClient (Socket s, IRCServer server) 
	throws IOException, TransformerConfigurationException, ParserConfigurationException, TransformerFactoryConfigurationError {
		super(s,null,null);
		this.server = server;

		//System.out.println("Serving: "+socket); 
		in = 
			new BufferedReader(
					new InputStreamReader(
							socket.getInputStream()));

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
				if (in.ready())
				{
					String str = in.readLine();
					if (str== null ) break;  
	
					System.out.println("RECEIVED: " + str);
					server.message(this, new IRCMessage(str));
				}
				Thread.sleep(100);
			}
			
		} catch (IOException e) {
			System.out.println("Disconnected with.."+socket);			
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			try {
				socket.close();
			} catch(IOException e) {}
		}
	}


	public void sendMessage(IRCMessage message)
	{
		if (message != null)
		{
			System.out.println("SENT: " + message);
			out.println(message.toString());
		}
	}
	
	
}
