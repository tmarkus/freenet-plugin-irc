package plugin.frirc.message;

import java.io.StringWriter;
import java.util.Map;

import plugin.frirc.ChannelManager;
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
					MessageCreator mc = new MessageCreator(cm);
					StringWriter messageString = mc.createPrivMessage(message);
					cm.getMessageManager().insertNewMessage(identity, messageString);
				}
			}
			
			
			else if (message.getType().equals("JOIN"))
			{
				cm.addIdentity(identity);
				cm.getServer().sendAllLocalClientsInChannel(cm, message);
			}
			
			
			else if (message.getType().equals("PART"))
			{
				cm.removeIdentity(identity);
				
				if (im.getOwnNickByID(identity.get("ID")) != null) //one of our own identities?
				{
					//insert message into freenet
					MessageCreator mc = new MessageCreator(cm);
					StringWriter messageString = mc.createPartMessage(message);
					cm.getMessageManager().insertNewMessage(identity, messageString);

					//let all local clients know that we've left
					cm.getServer().sendAllLocalClientsInChannel(cm, message);
					
					//inform all localClients in the same channel that the user has left
					cm.removeIdentity(identity);
					cm.getServer().removeChannel(cm);
					cm.stop();
				}
				else //not one of our own identities?
				{
					cm.removeIdentity(identity);
					cm.getServer().sendAllLocalClientsInChannel(cm, message);
				}
			}
	}
}
