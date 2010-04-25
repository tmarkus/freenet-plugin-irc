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
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Set;
import java.util.Map.Entry;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactoryConfigurationError;

import plugin.frirc.message.IncomingMessageHandler;
import plugin.frirc.message.MessageCreator;

import freenet.pluginmanager.PluginRespirator;

public class IRCServer extends Thread {  
	static final int PORT = 6667; // assign to next available Port.
	static final String SERVERNAME = "freenetIRCserver";
	private PluginRespirator pr;
	private ServerSocket serverSocket;
	private IdentityManager identityManager;
	
	//local outgoing connections
	private Map<Map<String, String>, LocalClient> locals = new HashMap<Map<String, String>, LocalClient>();
	private List<ChannelManager> channels = new ArrayList<ChannelManager>();
	
	
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
	
	public void sendLocalMessage(IRCMessage message, Map<String, String> identity)
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
		for(Entry<Map<String, String>, LocalClient> pair : locals.entrySet())
		{
			if (pair.getValue() == source)
			{
				return pair.getKey();
			}
		}
		return null;
	}
	
	/**
	 * Retrieve a ChannelManager by means of a channelString
	 * @param channel
	 * @return
	 */
	
	private ChannelManager getChannelManager(String channel, Map<String, String> identity)
	{
		ChannelManager manager = null;
		for(ChannelManager channelManagerItem : channels)
		{
			if (channelManagerItem.getChannel().equals(channel)) manager = channelManagerItem;
		}
		if (manager == null) //setup a new channelmanager
		{
			System.out.println("Creating new ChannelManager with identity: " + identity.get("ID"));
			
			manager = new ChannelManager(channel, this, pr, identity);
			channels.add(manager);
			manager.setupListeners(); //start listening to other WoT identities also having content
			manager.start();
		}
		return manager;
	}
	
	/**
	 * Send all the local clients in some channel identified by the ChannelManager a message 
	 * @param manager
	 * @param message
	 */
	
	public void sendAllLocalClientsInChannel(ChannelManager manager, IRCMessage message)
	{
		for(Map<String, String> identityItem : locals.keySet())
		{
			if (manager.inChannel(identityItem))
			{
				locals.get(identityItem).sendMessage(message);
			}
		}
	}
	
	/**
	 * Retrieve the identities which are connected locally
	 * @return
	 */
	
	public Set<Map<String, String>> getLocals()
	{
		return locals.keySet();
	}

	/**
	 * Stop tracking a channel
	 * @param cm
	 */
	
	public void removeChannel(ChannelManager cm)
	{
		channels.remove(cm);
	}
	
	/**
	 * Process and possibly reply to IRC messages
	 * @param source
	 * @param message
	 */
	
	public synchronized void message(LocalClient source, IRCMessage message)
	{
		/**
		 * NICK
		 */
		
		//associate nick with connection
		if (message.getType().equals("NICK") && !message.getNick().equals(""))
		{	
			//remove old identity map
			Map<String, String> old_identity = getIdentityByConnection(source);
			Map<String, String> new_identity = identityManager.getIdentityByNick(message.getNick());
			locals.remove(old_identity);
			locals.put(new_identity, source);
			
			if (old_identity != null) // we are dealing with a nickCHANGE
			{
				//confirm the nickchange to all local clients
				for(LocalClient local : locals.values())
				{
					local.sendMessage(IRCMessage.createNickChangeMessage(old_identity, new_identity));
				}
			}
			
			//tell client if nick is not a known a WoT identity that we know the user has
			if (!identityManager.getOwnIdentities().contains(new_identity))
			{
				source.sendMessage(IRCMessage.createServerNoticeMessage(message.getNick(), "Could not associate that nick with a WoT identity. Reload the plugin if you just added it or check whether it is actually correct. Joining channels will NOT work!"));
				source.sendMessage(new IRCMessage("QUIT"));
			}
		}


		/**
		 * USER
		 * Process the server login messages after USER
		 */
		else if (message.getType().equals("USER") && !message.getValue().equals(""))
		{
			for(IRCMessage loginMessage : IRCMessage.createGenericServerLoginMessages(getIdentityByConnection(source)))
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
			source.sendMessage(new IRCMessage("QUIT"));
			cleanup();

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
			source.sendMessage(IRCMessage.createServerNoticeMessage(message.getNick(), "Modes not supported at this time."));
		}

		
		/**
		 * Join a channel
		 */

		else if (message.getType().equals("JOIN") && !message.getChannel().equals(""))
		{
			//retrieve the nick associated with the connection
			String channel = message.getChannel();
			Map<String, String> identity = getIdentityByConnection(source);
			
			
			ChannelManager manager = getChannelManager(channel, identity); 
			manager.addIdentity(identity);
			
			//inform all localClients in the same channel that the user has joined
			sendAllLocalClientsInChannel(manager, IRCMessage.createJOINMessage(identity, channel)); 
			
			//inform the joining client about who is in the channel
			source.sendMessage(IRCMessage.createChannelModeMessage(channel));
			for(IRCMessage messageItem : IRCMessage.createChannelJoinNickList(identity, channel, manager.getIdentities()))
			{
				source.sendMessage(messageItem);
			}
		}
		
		/**
		 * PING
		 */
		
		else if (message.getType().equals("PING"))
		{
			source.sendMessage(new IRCMessage("PONG " + message.getValue()));
		}

		/**
		 * PART
		 */
		
		else if (message.getType().equals("PART"))
		{
			HashMap<String, String> identity = (HashMap<String, String>) getIdentityByConnection(source);
			ChannelManager manager = getChannelManager(message.getChannel(), identity);

			IncomingMessageHandler incoming = new IncomingMessageHandler(manager, identityManager);
			incoming.processMessage(message, identity);
		}

		/**
		 * Message for channel
		 */

		else if (message.getType().equals("PRIVMSG")) 
		{
			//retrieve the nick associated with the connection
			String channel = message.getChannel();
			HashMap<String, String> identity = (HashMap<String, String>) getIdentityByConnection(source);

			ChannelManager cm = getChannelManager(channel, identity);
			IncomingMessageHandler incoming = new IncomingMessageHandler(cm, identityManager);
			incoming.processMessage(message, identity);
		}

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

		*/

		
	
	}

	public void run()
	{
		try {
			try
			{
			serverSocket = new ServerSocket(PORT);
			}
			catch(IOException e)
			{
				serverSocket = new ServerSocket(PORT+1);
			}
			
			InetAddress  addrs= InetAddress.getLocalHost();         
			// Or InetAddress  addrs= InetAddress.getByName("localhost");
			// Or InetAddress  addrs= InetAddress.getByName("127.0.0.1");  

			System.out.println("TCP/Server running on : "+ addrs +" ,Port "+serverSocket.getLocalPort());

			while(true) {
				// Blocks until a connection occurs:
				Socket socket = serverSocket.accept();
					new LocalClient(socket, this);  // Handle an incoming Client.
			}
		} catch (IOException e1) {
			// If it fails, close the socket,
			// otherwise the thread will close it:
			try {
				serverSocket.close();
			} catch (IOException e) {
			}
			catch(NullPointerException e)
			{
				//nullpointerexception for when the serverSocket is already closed
			}
			
			System.out.println("Closed the IRC server...");
			cleanup();
			return;
		}
		catch (TransformerConfigurationException e) {
			e.printStackTrace();
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		} catch (TransformerFactoryConfigurationError e) {
			e.printStackTrace();
		}

	
	}

	public void cleanup()
	{
		//do something smart with clearing channels and signalling threads to stop listening etc
		for(ChannelManager manager : channels)
		{
			manager.terminate();
		}
			
		channels.clear();
		locals.clear();
	}

	public void terminate()
	{
		cleanup();
		try {
			serverSocket.close();
		} catch (IOException e) {
		}
		catch(NullPointerException e)
		{
			//nullpointerexception for when the serverSocket is already closed
		}
	}
	
	
	public boolean stopThread()
	{
		if (serverSocket == null || serverSocket.isClosed()) return true;
		return false;
	}
	
}
