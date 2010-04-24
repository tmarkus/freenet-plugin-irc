package plugin.frirc.message;

import java.io.StringWriter;
import java.util.HashMap;

import plugin.frirc.ChannelManager;
import plugin.frirc.IRCMessage;

public class IncomingMessageHandler extends MessageBase{

	private ChannelManager cm;
	
	public IncomingMessageHandler(ChannelManager cm)
	{
		super();
		this.cm = cm;
	}
	
	
	/**
	 * Function only called for messages meant for this channel
	 * @param message
	 */
	public void processMessage(IRCMessage message, HashMap<String, String> identity)
	{
			if (message.getType().equals("PRIVMSG"))
			{
				if (!cm.getOwnIdentities().contains(identity)) //message coming from some freenet client?
				{
					//check if the nickname is in the channel already
					if (!cm.getChannelIdentities().contains(identity)) // not? send JOIN message
					{
						cm.getChannelIdentities().add(identity);
						cm.getServer().sendLocalMessage(IRCMessage.createJOINMessage(identity, cm.getChannel()), identity);
					}
		
					// emulate message coming from the nick
					cm.getServer().sendLocalMessage(IRCMessage.createChannelMessage(identity, cm.getChannel(), message), identity);
				}
				else //message coming from our own local client
				{
					//insert message into freenet
					MessageCreator mc = new MessageCreator();
					StringWriter messageString = mc.createPrivMessage(message);
					cm.getMessageManager().insertNewMessage(identity, messageString);
				}
			}
	}
}
