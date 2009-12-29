/* This code is part of a plugin for Freenet. It is distributed 
 * under the GNU General Public License, version 3 (or at your option
 * any later version). See http://www.gnu.org/ for details of the GPL. */


package plugin.frirc;

/**
 * IRC server manages:
 * - channelmanagers
 * - private conversations
 */

import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.ArrayList;
import java.util.Set;
import java.util.Map.Entry;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactoryConfigurationError;

import com.db4o.ObjectContainer;

import freenet.client.FetchContext;
import freenet.client.FetchException;
import freenet.client.FetchResult;
import freenet.client.HighLevelSimpleClient;
import freenet.client.async.ClientGetCallback;
import freenet.client.async.ClientGetter;
import freenet.keys.FreenetURI;
import freenet.node.FSParseException;
import freenet.node.RequestClient;
import freenet.pluginmanager.FredPluginTalker;
import freenet.pluginmanager.PluginNotFoundException;
import freenet.pluginmanager.PluginRespirator;
import freenet.pluginmanager.PluginTalker;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;

public class IRCServer extends Thread {  
	static final int PORT = 6667; // assign to next available Port.
	static final String SERVERNAME = "freenetIRCserver";
	private PluginRespirator pr;
	private ServerSocket serverSocket;
	private IdentityManager identityManager;
	
	//local outgoing connections
	private HashMap<HashMap<String, String>, LocalClient> locals = new HashMap<HashMap<String, String>, LocalClient>();
	
	
	public IRCServer(PluginRespirator pr)
	{
		this.pr = pr;
		this.identityManager = new IdentityManager(pr, null);
	}

	/**
	 * Set a specific mode for a user in some channel
	 * @param source The connection from which the modeset originates (used for distributing the outgoinig messages to the correct channel)
	 * @param nick The nick to which the modeset should be applied
	 * @param channel The channel in which the nick resides
	 * @param mode The actual modeset change ('+v' etc)
	 */
	/*
	public void setUserChannelMode(FrircConnection source, String nick, String channel, String mode)
	{
		System.out.println("Setting mode " + mode + " for nick: " + nick);
		outQueue.get(source).add(new Message(":" + SERVERNAME + " MODE " + channel + " " + mode + " " +nick));
	}
	
	*/
	

	/**
	 * Send a message to locally clients
	 */
	
	public void sendLocalMessage(Message message, Map<String, String> identity)
	{
		locals.get(identity).sendMessage(message);
	}

	/**
	 * Find an identity through its socketConnection object
	 * @param source
	 * @return
	 */
	
	private Map<String, String> getIdentityByConnection(LocalClient source)
	{
		for(Entry<HashMap<String, String>, LocalClient> pair : locals.entrySet())
		{
			if (pair.getValue() == source)
			{
				return pair.getKey();
			}
		}
		return null;
	}
	
	
	
	/**
	 * Process and possibly reply to IRC messages
	 * @param source
	 * @param message
	 */
	
	public synchronized void message(LocalClient source, Message message)
	{
		/**
		 * NICK
		 */
		
		//associate nick with connection
		if (message.getType().equals("NICK") && !message.getNick().equals(""))
		{	
			//remove old identity map
			Map<String, String> old_identity = getIdentityByConnection(source);
			HashMap<String, String> new_identity = identityManager.getIdentityByNick(message.getNick());
			locals.remove(old_identity);
			locals.put(new_identity, source);
			
			if (old_identity != null) // we are dealing with a nickCHANGE
			{
				//confirm the nickchange to all local clients
				for(LocalClient local : locals.values())
				{
					local.sendMessage(Message.createNickChangeMessage(old_identity, new_identity));
				}
			}
			
			//tell client if nick is not a known a WoT identity that we know the user has
			if (!identityManager.getOwnIdentities().contains(new_identity))
			{
				source.sendMessage(Message.createServerNoticeMessage(message.getNick(), "Could not associate that nick with a WoT identity. Reload the plugin if you just added it or check whether it is actually correct. Joining channels will NOT work!"));
				source.sendMessage(new Message("QUIT"));
			}
		}


		/**
		 * USER
		 * Process the server login messages after USER
		 */
		else if (message.getType().equals("USER") && !message.getValue().equals(""))
		{
			for(Message loginMessage : Message.createGenericServerLoginMessages(getIdentityByConnection(source)))
			{
				source.sendMessage(loginMessage);
			}
		}

		/**
		 * QUIT
		 * Process the QUIT signal (disconnect local connection)
		 */
		else if (message.getType().equals("QUIT"))
		{
			source.sendMessage(new Message("QUIT"));
			try {
				source.getSocket().close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		/**
		 * MODE
		 */
		
		else if (message.getType().equals("MODE"))
		{
			source.sendMessage(Message.createServerNoticeMessage(message.getNick(), "Modes not supported at this time."));
		}
		
		/*
		
		else if (message.getType().equals("PART"))
		{
			String channel = message.getChannel();
			String nick = getNickByCon(source);
			
			//quit all of the listening threads for this channel (threads should check with the service itself?, but only if
			// the source is a FreenetClientInput  (isRemote)
			
			//send confirmation to all local clients in the same channel
			for(String channelUser : channelUsers.get(channel))
			{
				if (nickToInput.get(channelUser) != null)
				{
					for(FrircConnection connection : nickToInput.get(channelUser))
					{
						if (connection.isLocal())
						{
							outQueue.get(source).add(new Message(":" + nick + "!" + nick + "@freenet PART " + channel));
						}
					}
				}
			}

			//remove nick from channel
			channelUsers.get(channel).remove(nick);
		}
		
/*		
		
		/**
		 * Join a channel
		 */

		/*
		
		else if (message.getType().equals("JOIN") && !message.getChannel().equals(""))
		{
			//retrieve the nick associated with the connection
			String nick = getNickByCon(source);
			String channel = message.getChannel();

			//add the user to the channel
			if (!channelUsers.containsKey(channel))
			{
				channelUsers.put(channel, new HashSet<String>());
			}
			channelUsers.get(channel).add(nick);

			if (source.getClass().toString().equals("class plugin.frirc.ClientInput"))
			{
				//setup listeners to find other users in this channel (currently query FULL wot)
				getAllIdentities(channel, nick);

				//setup freenet output for the messages from the local joiner
				setupFreenetPublisher(nick, channel, source);
			}



			//inform all clients in the same channel that the user has joined us
			for(String channelUser: channelUsers.get(message.getChannel()))
			{
				for(FrircConnection connection : nickToInput.get(channelUser))
				{
					System.out.println("channelUser = " + channelUser);
					if (connection.isLocal())
					{
						System.out.println("Join message sent to " + channelUser + " about " + nick);
						outQueue.get(connection).add(new Message(":" + nick+"!"+nick + "@freenet" + " JOIN " + channel));
					}
				}
			}

			//inform the joining clients about who is 
			if (source.isLocal())
			{

				outQueue.get(source).add(new Message(":" + SERVERNAME + " MODE " + channel + " +nt"));
				outQueue.get(source).add(new Message(":" + SERVERNAME + " 331 " + nick + " " + channel + " :We eten vandaag hutspot"));

				for(String channelUser: channelUsers.get(message.getChannel()))
				{
					outQueue.get(source).add(new Message(":" + SERVERNAME + " 353 " + nick + " = " + channel + " :" + channelUser));
				}

				outQueue.get(source).add(new Message(":" + SERVERNAME + " 366 " + nick + " " + channel + " :End of /NAMES list"));

				//outQueue.get(con).add(new Message("TOPIC " + messageObject.getValue() + ":We eten hutspot vandaag"));
			}
		}
		
		
		*/
		
		/*
		 * WHO
		 */
		
		
		/*
		
		else if (message.getType().equals("WHO"))
		{
			String nick = getNickByCon(source);
			String channel = message.getChannel();

			for(String channelUser: channelUsers.get(message.getChannel()))
			{
				outQueue.get(source).add(new Message(":" + SERVERNAME + " 352 " + channelUser + " freenet " + channelUser + " H :0 " + channelUser));
			}

			outQueue.get(source).add(new Message("315 " + nick + " " + channel + " :End of /WHO list."));
		}

		else if (message.getType().equals("PING"))
		{
			outQueue.get(source).add(new Message("PONG " + message.getValue()));
		}
		*/

		/**
		 * Message for channel
		 */

		/*
		else if (message.getType().equals("PRIVMSG")) 
		{
			String nick = getNickByCon(source);

			//iterate over all the users(connections) and send them the privmsg, except the originating user!

			for(String channelUser: channelUsers.get(message.getChannel()))
			{
				if (nickToInput.get(channelUser) != null)
				{
					for (FrircConnection out : nickToInput.get(channelUser))
					{
						if (	( !source.isLocal() && out.isLocal() && !channelUser.equals(nick)) ||	//deliver locally 
								( source.isLocal() && !out.isLocal() && channelUser.equals(nick) )		//publish to freenet
							) 
						{
							System.out.println("DEBUG: Message added to some outqueue");
							outQueue.get(out).add(new Message(":" + nick + "@freenet PRIVMSG " + message.getChannel() + " :" + message.getValue()));
							
							break;
							//TODO: probably some memory leak here...(outQueue never cleaned up)
						}
					}
				}
			}
		}
	
		*/
	
	}

	public void run()
	{
		try {
			serverSocket = new ServerSocket(PORT);

			InetAddress  addrs= InetAddress.getLocalHost();         
			// Or InetAddress  addrs= InetAddress.getByName("localhost");
			// Or InetAddress  addrs= InetAddress.getByName("127.0.0.1");  

			System.out.println("TCP/Server running on : "+ addrs +" ,Port "+serverSocket.getLocalPort());

			while(true) {
				// Blocks until a connection occurs:
				Socket socket = serverSocket.accept();
				try {
					new LocalClient(socket, this);  // Handle an incoming Client.
				} catch(IOException e) {
					// If it fails, close the socket,
					// otherwise the thread will close it:
					socket.close();
					serverSocket.close();
					
					return;
				} catch (TransformerConfigurationException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (ParserConfigurationException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (TransformerFactoryConfigurationError e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}

	public void terminate()
	{
		if (serverSocket != null)
		{
		try {
			serverSocket.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		}
	
		//do something smart with clearing channels and signalling threads to stop listening etc
	}

	public boolean stopThread()
	{
		if (serverSocket == null || serverSocket.isClosed()) return true;
		return false;
	}
	
}
