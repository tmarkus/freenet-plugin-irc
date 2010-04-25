package plugin.frirc.message;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import plugin.frirc.ChannelManager;
import plugin.frirc.IRCMessage;
import plugin.frirc.IdentityManager;

public class IncomingMessageHandler extends MessageBase{

	private ChannelManager cm;
	private IdentityManager im;
	
	public IncomingMessageHandler(ChannelManager cm, IdentityManager im)
	{
		super();
		this.cm = cm;
		this.im = im;
	}
	
	
	/**
	 * Function only called for messages meant for this channel
	 * @param message
	 */
	public void processMessage(IRCMessage message, Map<String, String> identity)
	{
			if (message.getType().equals("PRIVMSG"))
			{
				if (im.getOwnNickByID(identity.get("ID")) == null) //not one of our own identities
				{
					//check if the nickname is in the channel already
					if (!cm.getChannelIdentities().contains(identity)) // not? send JOIN message
					{
						cm.addIdentity(identity);
						cm.getServer().sendAllLocalClientsInChannel(cm, IRCMessage.createJOINMessage(identity, cm.getChannel()));
					}
		
					// emulate message coming from the nick
					cm.getServer().sendAllLocalClientsInChannel(cm, IRCMessage.createChannelMessage(identity, cm.getChannel(), message));
				}
				else //message coming from our own local client
				{
					//insert message into freenet
					MessageCreator mc = new MessageCreator();
					StringWriter messageString = mc.createPrivMessage(message);
					cm.getMessageManager().insertNewMessage(identity, messageString);
				}
			}
			else if (message.getType().equals("JOIN"))
			{
				cm.addIdentity(identity);
				cm.getServer().sendAllLocalClientsInChannel(cm, message);
			}
	}
}
