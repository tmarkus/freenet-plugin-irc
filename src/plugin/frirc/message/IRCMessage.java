/* This code is part of a plugin for Freenet. It is distributed 
 * under the GNU General Public License, version 3 (or at your option
 * any later version). See http://www.gnu.org/ for details of the GPL. */

package plugin.frirc.message;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import plugin.frirc.ChannelSearcher;
import plugin.frirc.IRCServer;
import plugin.frirc.IdentityManager;
import plugins.WoT.Identity;

import freenet.pluginmanager.PluginRespirator;

/**
 * This should be a proper parser someday, please contribute ;)
 * @author tmarkus
 *
 */

public class IRCMessage {

	private String type = "";
	private String nick = "";
	private String username = "";
	private String value = "";
	private String channel = "";
	private String raw = "";
	
	/**
	 * Constructor for existing messages
	 * @param message
	 */
	
	public IRCMessage(String message)
	{
		//System.out.println(message);
		this.raw = message;
		
		String[] split = message.split(" ");
		
		if (split[0].equals("QUIT"))
		{
			this.type = "QUIT";
		}
		
		else if (split[0].equals("LIST"))
		{
			this.type = "LIST";
			if (split.length > 1) this.value = split[1];
		}
		
		else if (split[0].equals("NICK"))
		{
			this.type = "NICK";
			this.nick = split[1];
		}
		
		else if (split[0].equals("USER"))
		{
			this.type = "USER";
			this.username = split[1];
			if (split.length > 1) this.value = "extra_info_we_will_ignore";
		}

		else if (split[0].equals("MODE"))
		{
			this.type = "MODE";
			this.channel = split[1].toLowerCase();
			if (split.length > 2) this.value = split[2];
		}
		
		else if (split[1].equals("NOTICE"))
		{
			this.type = "NOTICE";
			
			//FIXME: set other fields too
		}
		
		
		else if (split[0].equals("WHO"))
		{
			this.type = "WHO";
			this.channel = split[1].toLowerCase();
		}
		
		else if (split[0].equals("JOIN"))
		{
			this.type = "JOIN";
			if (split.length > 1) this.channel = split[1].toLowerCase();
		}

		else if (split[0].equals("PART") && split.length == 2)
		{
			this.type = "PART";
			this.channel = split[1].toLowerCase();
		}
		
		else if (split[1].equals("PART") && split.length == 3)
		{
			this.type = "PART";
			this.channel = split[2].toLowerCase();
			this.username = split[0];
		}
		
		else if (split[0].equals("TOPIC"))
		{
			this.type = "TOPIC";
			if (split.length > 1) this.channel = split[1].toLowerCase();
			if (split.length > 2) this.value = split[2];
		}
		
		else if (split[0].equals(":" + IRCServer.SERVERNAME))
		{
			if (split[1].equals("001") || split[1].equals("375") || split[1].equals("372") || split[1].equals("376"))
			{
				this.type = split[1];
				this.nick = split[2];
				for(int i=3; i < split.length; i++)
				{
					this.value += split[i] + " ";
				}
			}
			else if (split[1].equals("004"))
			{
				this.type = split[1];
				this.nick = split[2];
				this.value = split[3];
			}
			else if (split[1].matches("331") || split[1].matches("353") || split[1].matches("366"))
			{
				this.type = split[1];
				this.raw = message;
			}
			else if (split[1].matches("352"))
			{
				this.type = split[1];
				this.raw = message;
			}
		
		}
		else if (split.length == 3 && split[1].equals("JOIN"))
		{
			this.type = split[1];
			this.channel = split[2].toLowerCase();
			this.value = split[0];
		}
		else if (split.length == 3 && split[1].equals("MODE"))
		{
			this.type = "MODE";
			this.raw = message;
		}
		else if (split.length > 2 && split[1].equals("PRIVMSG"))
		{
			this.type = "PRIVMSG";
			this.username = split[0];
			this.channel = split[2].toLowerCase();
			
			for(int i=3; i < split.length; i++)
			{
				this.value += split[i] + " ";
			}
			this.value = this.value.substring(1);
		}
		else if (split.length > 2 && split[0].equals("PRIVMSG"))
		{
			this.type = "PRIVMSG";
			this.channel = split[1].toLowerCase();
			
			for(int i=2; i < split.length; i++)
			{
				this.value += split[i] + " ";
			}
		
			this.value = this.value.substring(1);
		}
		
		
		else if (split[0].equals("PING"))
		{
			this.type = "PING";
			this.value = split[1];
		}
		
		else
		{
			System.out.println("UNABLE TO PARSE MESSAGE, storing as raw: " + message);
			this.raw = message;
		}
		
	}
	
	/**
	 * Constructor for new messages
	 */
	
	public IRCMessage(){
		
	}

	
	public final String getType()
	{
		return this.type;
	}
	
	/**
	 * Convert the message to a string
	 */
	
	public String toString()
	{
		/*
		
		if (type.equals("NICK"))
		{
			return type + " " + nick;
		}
		else if (type.equals("JOIN"))
		{
			if (channel.equals(""))
			{
				return type + " " + value;
			}
			else
			{
				return value + " " + type + " " + channel;
			}
		}	
		else if (type.equals("TOPIC"))
		{
			return type + " " + channel +  " " + value;
		}	
		else if (type.equals("001") || type.equals("375") || type.equals("372") || type.equals("376"))
		{
			return ":" + IRCServer.SERVERNAME + " " + type + " " + nick + " " + value;
		}
		else if (type.equals("004"))
		{
			return ":" + IRCServer.SERVERNAME + " " + type + " " + nick + " " + IRCServer.SERVERNAME + " " + value;
		}
		else if (type.matches("331") || type.matches("353") || type.matches("366"))
		{
			return this.raw;
		}
		*/
		
		return raw;
		
		
		//fail safe (should never be returned)
		
//		return "ERROR: " + type;
	}
	
	public String getValue()
	{
		return this.value;
	}
	
	public String getNick()
	{
		return this.nick;
	}

	public String getChannel()
	{
		return this.channel;
	}

	public String getUser()
	{
		return this.username;
	}

	/**
	 * Return the identity that belongs to this identity
	 * @param pr
	 * @return a single identity that matches the nickname of the message
	 */
	
	public Map<String, String> getIdentity(PluginRespirator pr, Map<String, String> ownIdentity)
	{
		IdentityManager manager = new IdentityManager(pr, ownIdentity);
		for(Map<String, String> identity : manager.getAllIdentities())
		{
			if (identity.get("nick").equals(this.nick)) return identity;
		}
		return null;
	}
	
	
	

	/**
	 * Create a new IRC join message object
	 * @param identity
	 * @param channel
	 * @return
	 */
	public static IRCMessage createJOINMessage(Map<String,String> identity, String channel)
	{
		return new IRCMessage(":" + identity.get("nick")+"!"+identity.get("nick") + "@freenet" + " JOIN " + channel);
	}
	
	public static IRCMessage createChannelMessage(Map<String, String> identity, String channel, IRCMessage message)
	{
		return new IRCMessage(":" + identity.get("nick") + "@freenet PRIVMSG " + channel + " :" + message.getValue());
	}
	
	public static IRCMessage createChannelMessage(Map<String, String> identity, String channel, String messageText)
	{
		return new IRCMessage(":" + identity.get("nick") + "@freenet PRIVMSG " + channel + " :" + messageText);
	}

	
	public static IRCMessage createNickChangeMessage(Map<String, String> old_identity, Map<String, String> new_identity)
	{
		return new IRCMessage(":" + old_identity.get("nick") + "!" + old_identity.get("nick") + "@freenet NICK :" + new_identity.get("nick"));	
	}
	
	public static IRCMessage createServerNoticeMessage(String nick, String notice)
	{
		return new IRCMessage(":" + IRCServer.SERVERNAME + " NOTICE " + nick + " :" + notice);
	}

	public static List<IRCMessage> createGenericServerLoginMessages(Map<String, String> identity)
	{
		List<IRCMessage> messages = new ArrayList<IRCMessage>();

		messages.add(createGenericServerLoginMessage(identity, "001", ":Welcome to freenet irc"));
		messages.add(createGenericServerLoginMessage(identity, "004",  IRCServer.SERVERNAME + " freenet"));
		messages.add(createGenericServerLoginMessage(identity, "375", ":- Hi!"));
		messages.add(createGenericServerLoginMessage(identity, "372", ":- Welcome!"));
		messages.add(createGenericServerLoginMessage(identity, "376", ":End of /MOTD command"));
		
		return messages;
	}

	public static IRCMessage createGenericServerLoginMessage(Map<String, String> identity, String code, String message)
	{
		return new IRCMessage(":" + IRCServer.SERVERNAME + " "+code+" " + identity.get("nick") + " " + message);
	}

	public static IRCMessage createChannelModeMessage(String channel)
	{
		return new IRCMessage(":" + IRCServer.SERVERNAME + " MODE " + channel + " +nt");
	}
	
	public static List<IRCMessage> createChannelJoinNickList(Map<String, String> identity, String channel, HashSet<Map<String, String>> channelIdentities)
	{
		List<IRCMessage> messages = new ArrayList<IRCMessage>();
		
		for(Map<String, String> channelIdentity : channelIdentities)
		{
			messages.add(new IRCMessage(":" + IRCServer.SERVERNAME + " 353 " + identity.get("nick") + " = " + channel + " :" + channelIdentity.get("nick")));
		}
		
		messages.add(new IRCMessage(":" + IRCServer.SERVERNAME + " 366 " + identity.get("nick") + " " + channel + " :End of /NAMES list"));

		return messages;
	}
	
	public static List<IRCMessage> createListChannels(Map<String, String> identity, ChannelSearcher channelSearcher)
	{
		List<IRCMessage> messages = new ArrayList<IRCMessage>();
		messages.add(new IRCMessage(":" + IRCServer.SERVERNAME + " 321 " + identity.get("nick") + " Channel :Users  Name"));
		
		for(String channel : channelSearcher.getChannels())
		{
			//messages.add(new IRCMessage(":" + IRCServer.SERVERNAME + " 322 " + identity.get("nick") + " " + channel + " " + manager.getIdentities().size() + " :" + manager.getTopic()));
			messages.add(new IRCMessage(":" + IRCServer.SERVERNAME + " 322 " + identity.get("nick") + " " + channel + " " + channelSearcher.getChannelCount(channel) + " :" + ""));
		}
		
		messages.add(new IRCMessage(":" + IRCServer.SERVERNAME + " 323 " + identity.get("nick") + " :End of /LIST"));
		return messages;
	}
	
	public static List<IRCMessage> createWhoChannelList(Map<String, String> identity, String channel, HashSet<Map<String, String>> channelIdentities)
	{
		List<IRCMessage> messages = new LinkedList<IRCMessage>();
		for(Map<String, String> channelIdentity : channelIdentities)
		{
			//messages.add(new IRCMessage(":" + IRCServer.SERVERNAME + " 352 " + channelUser + " freenet " + channelUser + " H :0 " + channelUser)));	
		}
		messages.add(new IRCMessage("315 " + identity.get("nick") + " " + channel + " :End of /WHO list."));
		
		return messages;
	}
	
	
	
	public static IRCMessage createPartMessage(Map<String, String> identity, String channel)
	{
		return new IRCMessage(":" + identity.get("nick") + "!" + identity.get("nick") + "@freenet PART " + channel);

	}
	
}
