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
	static final int PORT=6667; // assign to next available Port.
	static final String SERVERNAME = "freenetIRCserver";
	private PluginRespirator pr;
	private ServerSocket serverSocket;
	private IdentityManager identityManager;
	
	//local outgoing connections
	private HashMap<HashMap<String, String>, ClientOutput> locals = new HashMap<HashMap<String, String>, ClientOutput>();
	
	
	public IRCServer(PluginRespirator pr)
	{
		this.pr = pr;
		this.identityManager = new IdentityManager(pr);
	}

	
	
	/**
	 * Send a message to a locally connected irc client
	 */
	
	public void sendLocalMessage(Message message)
	{
		
	}
	
	/**
	 * Receive a message from a local client
	 * @param con
	 * @param message
	 */

	public void message(FrircConnection con, String message)
	{
		Message messageObject = new Message(message);
		message(con, messageObject);
		
		//message channel specific? hand it over to the correct Channelmanager
		
		//channel something generic? process it in the ircserver
	}
	
	/**
	 * Set a specific mode for a user in some channel
	 * @param source The connection from which the modeset originates (used for distributing the outgoinig messages to the correct channel)
	 * @param nick The nick to which the modeset should be applied
	 * @param channel The channel in which the nick resides
	 * @param mode The actual modeset change ('+v' etc)
	 */
	
	public void setUserChannelMode(FrircConnection source, String nick, String channel, String mode)
	{
		System.out.println("Setting mode " + mode + " for nick: " + nick);
		outQueue.get(source).add(new Message(":" + SERVERNAME + " MODE " + channel + " " + mode + " " +nick));
	}
	
	/**
	 * Process and possibly reply to IRC messages
	 * @param source
	 * @param messageObject
	 */
	
	public synchronized void message(FrircConnection source, Message messageObject)
	{
		//associate nick with connection
		if (messageObject.getType().equals("NICK") && !messageObject.getNick().equals(""))
		{	
			String old_nick = "";
			if (getNickByCon(source) != null) 	old_nick = getNickByCon(source);
			else								old_nick = messageObject.getNick();
			
			
			//confirm the nickchange
			outQueue.get(source).add(new Message(":" + old_nick + "!" + old_nick + "@freenet NICK :" + messageObject.getNick()));
			
			//tell client if nick is not a known a WoT identity that we know the user has
			if (!ownIdentities.contains(getIdentityByNick(messageObject.getNick())))
			{
				outQueue.get(source).add(new Message(":" + SERVERNAME + " NOTICE " + messageObject.getNick() + " :Could not associate that nick with a WoT identity. Reload the plugin if you just added it or check whether it is actually correct. Joining channels will NOT work!"));
			}

			associateNickWithConnection(messageObject.getNick(), source);
			changeNick(old_nick, messageObject.getNick());
		}


		else if (messageObject.getType().equals("USER") && !messageObject.getValue().equals(""))
		{
			String nick = getNickByCon(source);
			
			outQueue.get(source).add(new Message(":" + SERVERNAME + " 001 " + nick + " :Welcome to freenet irc"));
			outQueue.get(source).add(new Message(":" + SERVERNAME + " 004 " + nick + " " + SERVERNAME + " freenet"));
			outQueue.get(source).add(new Message(":" + SERVERNAME + " 375 " + nick + " :- Hi!"));
			outQueue.get(source).add(new Message(":" + SERVERNAME + " 372 " + nick + " :- Welcome!"));
			outQueue.get(source).add(new Message(":" + SERVERNAME + " 376 " + nick + " :End of /MOTD command"));
		}

		else if (messageObject.getType().equals("QUIT"))
		{
			outQueue.get(source).add(new Message("QUIT"));
			try {
				source.getSocket().close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		else if (messageObject.getType().equals("MODE"))
		{
			String nick = getNickByCon(source);
			outQueue.get(source).add(new Message(":" + SERVERNAME + " NOTICE " + nick + " :Modes not supported at this time."));
		}
		
		
		else if (messageObject.getType().equals("PART"))
		{
			String channel = messageObject.getChannel();
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
		
		
		
		/**
		 * Join a channel
		 */

		else if (messageObject.getType().equals("JOIN") && !messageObject.getChannel().equals(""))
		{
			//retrieve the nick associated with the connection
			String nick = getNickByCon(source);
			String channel = messageObject.getChannel();

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
			for(String channelUser: channelUsers.get(messageObject.getChannel()))
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

				for(String channelUser: channelUsers.get(messageObject.getChannel()))
				{
					outQueue.get(source).add(new Message(":" + SERVERNAME + " 353 " + nick + " = " + channel + " :" + channelUser));
				}

				outQueue.get(source).add(new Message(":" + SERVERNAME + " 366 " + nick + " " + channel + " :End of /NAMES list"));

				//outQueue.get(con).add(new Message("TOPIC " + messageObject.getValue() + ":We eten hutspot vandaag"));
			}
		}
		
		/*
		 * WHO
		 */
		
		else if (messageObject.getType().equals("WHO"))
		{
			String nick = getNickByCon(source);
			String channel = messageObject.getChannel();

			for(String channelUser: channelUsers.get(messageObject.getChannel()))
			{
				outQueue.get(source).add(new Message(":" + SERVERNAME + " 352 " + channelUser + " freenet " + channelUser + " H :0 " + channelUser));
			}

			outQueue.get(source).add(new Message("315 " + nick + " " + channel + " :End of /WHO list."));
		}

		else if (messageObject.getType().equals("PING"))
		{
			outQueue.get(source).add(new Message("PONG " + messageObject.getValue()));
		}


		/**
		 * Message for channel
		 */

		else if (messageObject.getType().equals("PRIVMSG")) 
		{
			String nick = getNickByCon(source);

			//iterate over all the users(connections) and send them the privmsg, except the originating user!

			for(String channelUser: channelUsers.get(messageObject.getChannel()))
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
							outQueue.get(out).add(new Message(":" + nick + "@freenet PRIVMSG " + messageObject.getChannel() + " :" + messageObject.getValue()));
							
							break;
							//TODO: probably some memory leak here...(outQueue never cleaned up)
						}
					}
				}
			}
		}
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
					new ClientOutput(socket, this);  // Handle an incoming Client.
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
