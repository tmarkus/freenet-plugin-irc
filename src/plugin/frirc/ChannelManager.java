package plugin.frirc;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import com.db4o.ObjectContainer;

import freenet.client.FetchContext;
import freenet.client.FetchException;
import freenet.client.FetchResult;
import freenet.client.HighLevelSimpleClient;
import freenet.client.async.ClientGetCallback;
import freenet.client.async.ClientGetter;
import freenet.keys.FreenetURI;
import freenet.node.FSParseException;
import freenet.support.SimpleFieldSet;

public class ChannelManager extends Thread implements ClientGetCallback{

	private String channel;
	private HighLevelSimpleClient hl;
	private HighLevelSimpleClient low_priority_hl;
	private IRCServer server;
	
	private HashSet<ClientGetter> requests = new HashSet<ClientGetter>();  			//manage all outstanding connections
	private Map<String, Boolean> isCalibrated = new HashMap<String, Boolean>();		//store whether an identity is calibrated yet or not
	private HashSet<HashMap<String,String>> channelIdentities = 
					new HashSet<HashMap<String,String>>(); 							// which identities are in which channel?
	
	private FetchContext singleFC;
	private FetchContext ULPRFC;
	
	private final int TRY_SPAWN_AGAIN = 3 * 60 * 1000; //ms, try to setup WoT listeners again
	
	
	public ChannelManager(String channel, IRCServer server, HighLevelSimpleClient hl, HighLevelSimpleClient low_priority_hl)
	{
		this.channel = channel;
		this.hl = hl;
		this.low_priority_hl = low_priority_hl;
		this.server = server;
		
		FetchContext fc = hl.getFetchContext();
		fc.maxNonSplitfileRetries = 1;
		fc.followRedirects = true;
		fc.ignoreStore = true;
		this.singleFC = fc;
		
		FetchContext fc2 = hl.getFetchContext();
		fc2.maxNonSplitfileRetries = -1;
		fc2.followRedirects = true;
		fc2.ignoreStore = true;
		this.ULPRFC = fc2;
	}
	
	private synchronized void setupWoTListener(Map<String, String> identity)
	{
			FreenetURI fetchURI;
			try {
				fetchURI = Frirc.idToRequestURI(identity.get("ID"), channel);
				requests.add(hl.fetch(fetchURI, 20000, null, this, singleFC));
				System.out.println("Trying to see whether a user is publishing at: " + fetchURI);
			} catch (FetchException e) {
				e.printStackTrace();
			}
	}

	/**
	 * Function only called for messages meant for this channel
	 * @param message
	 */
	public void processMessage(Message message, HashMap<String, String> identity)
	{
			if (message.getType().equals("PRIVMSG"))
			{
				if (!server.getOwnIdentities().contains(identity)) //message coming from some freenet client?
				{
					//check if the nickname is in the channel already
					if (!channelIdentities.contains(identity))
					{
						channelIdentities.add(identity);
						server.sendLocalMessage(new Message(":" + identity.get("nick")+"!"+identity.get("nick") + "@freenet" + " JOIN " + channel));	// not -> simulate join message
					}
		
					// emulate message coming from the nick
					server.sendLocalMessage(new Message(":" + identity.get("nick") + "@freenet PRIVMSG " + channel + " :" + message.getValue()));
				}
				else //message coming from our own local client
				{
					//insert message into freenet
				}
			
			}
	}

	/**
	 * Return the set of identities which are in this channel
	 * @return
	 */
	
	public HashSet<HashMap<String, String>> getIdentities()
	{
		return channelIdentities;
	}
	
	public String getChannel()
	{
		return this.channel;
	}
	
	
	@Override
	public void run()
	{
		while(true)
		{
			try {

				//setup listeners for all the people in my WoT
				for(Map<String, String> identity : server.getAllIdentities())
				{
					if (!isCalibrated.get(identity.get("ID")))
					{
						setupWoTListener(identity);
					}
				}
				
				Thread.sleep(100000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}


	@Override
	public synchronized void onFailure(FetchException fe, ClientGetter cg, ObjectContainer oc) {

		String id = Frirc.requestURItoID(cg.getURI());
		if (isCalibrated.containsKey(id))
		{
			if (!isCalibrated.get(id))
			{
				isCalibrated.put(id, true);
				try {
					requests.add(hl.fetch(cg.getURI(), 20000, null, this, ULPRFC));
				} catch (FetchException e) {
					e.printStackTrace();
				}
			}
		}
	}

	@Override
	public synchronized void onSuccess(FetchResult fr, ClientGetter cg, ObjectContainer oc) {
		 
		String id = Frirc.requestURItoID(cg.getURI());
		
		if (isCalibrated.get(id) == true)
		{
			System.out.println("Received uri thingy");
			//really process the message
		}
		else //not calibrated yet, so increase the current index and try again
		{
			isCalibrated.put(id, false);
			try {
				requests.add(hl.fetch(Frirc.getNextIndexURI(cg.getURI()),20000, null, this, singleFC));
			} catch (FetchException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void onMajorProgress(ObjectContainer arg0) {
	}
	
/*
	
	
	
		//keep listening to our trusted identities, all the time
		//setup threads where needed, loop over just discovered created identities
		for(HashMap<String, String> identity : identities)
		{
			if (Integer.parseInt(identity.get("Value")) >= 0)
			{
				setupWoTListener(identity, channel);
			}
		}
		
		doMaintenance(channel);
	}
	
	/**
	 * Perform various maintenance tasks for the IRC server
	 * @param channel
	 */
	/*
	private void doMaintenance(String channel)
	{
		while(true)
		{
			if (last_channel_spawn.get(channel) == null || last_channel_spawn.get(channel) < (System.currentTimeMillis() - TRY_SPAWN_AGAIN))
			{
				System.out.println("Setting up listeners...");
				last_channel_spawn.put(channel, System.currentTimeMillis()); //timestamp latest attempt to setup listeners

				//get trust tree for which nick? (an instance of OwnIdentity)
				String nick = "";
				for(String nickItem : channelUsers.get(channel))
				{
					for(Map<String, String> identity : ownIdentities)
					{
						if (identity.get("nick").equals(nickItem))
						{
							nick = nickItem;
						}
					}
				}
				
				getAllIdentities(channel, nick);
				return;
			}
			
			
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
			//stop listening if the server is no longer running
			if (stopThread()) return;
		}

	}
		
	/**
	 * Method for FreenetClient's that signal them leaving a channel (due to timeout or other)
	 * @param connection
	 */
	
	/*
	
	public void leaveChannel(FreenetClient connection)
	{
		//simulate a leave message from our client
		message(connection, new Message("PART " + connection.getChannel()));

		//disassociate nick with connection
		nickToInput.remove( getNickByCon(connection) );
	}


	*/
	
}
