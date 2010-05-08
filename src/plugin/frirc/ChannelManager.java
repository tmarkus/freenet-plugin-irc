package plugin.frirc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import freenet.client.HighLevelSimpleClient;
import freenet.node.RequestStarter;
import freenet.pluginmanager.PluginRespirator;

public class ChannelManager extends Thread {

	private String channel;
	private HighLevelSimpleClient hl;
	private HighLevelSimpleClient low_priority_hl;
	private IRCServer server;
	private IdentityManager identityManager;
	private PluginRespirator pr;
	private String topic = "Topic support isn't implemented";
	
	private HashSet<Map<String,String>> channelIdentities = 
					new HashSet<Map<String,String>>(); 							// which identities are in which channel?
	
	private MessageManager mm;
	
	/**
	 * 
	 * @param channel
	 * @param server
	 * @param pr
	 * @param identity - the local identity that initially joined the channel, we should init WoT with this identity
	 */
	
	public ChannelManager(String channel, IRCServer server, PluginRespirator pr, Map<String, String> identity)
	{
		//schedule all the requests from this application with a high priority class so they should finish sooner
		hl = pr.getNode().clientCore.makeClient(RequestStarter.MAXIMUM_PRIORITY_CLASS);
		low_priority_hl = pr.getNode().clientCore.makeClient(RequestStarter.PREFETCH_PRIORITY_CLASS);
	
		this.channel = channel;
		this.server = server;
		this.pr = pr;
		
		this.identityManager = new IdentityManager(pr, identity);
		this.mm = new MessageManager(this, this.identityManager, pr, hl, low_priority_hl);
	}
	
	/**
	 * Retrieve the message manager belonging to this channel
	 * @return
	 */
	
	public MessageManager getMessageManager()
	{
		return mm;
	}
	
	/**
	 * Return the set of identities which are in this channel
	 * @return
	 */
	
	public HashSet<Map<String, String>> getIdentities()
	{
		return channelIdentities;
	}
	
	/**
	 * Return the set of own identities which are in this channel
	 * @return
	 */
	
	public HashSet<Map<String, String>> getOwnIdentities()
	{
		return channelIdentities;
	}
	
	
	public String getChannel()
	{
		return this.channel;
	}
	
	public String getTopic()
	{
		return this.topic;
	}
	
	
	public IRCServer getServer()
	{
		return this.server;
	}
	
	public HashSet<Map<String, String>> getChannelIdentities()
	{
		return this.channelIdentities;
	}

	public void addIdentity(Map<String, String> identity)
	{
		channelIdentities.add(identity);
		mm.calibrate(identity);
		server.getChannelSearcher().addChannel(channel, identity);
	}
	
	public void removeIdentity(Map<String, String> identity)
	{
		channelIdentities.remove(identity);
		server.getChannelSearcher().removeFromChannel(channel, identity);
	}

	public void setupListeners()
	{
		mm.setupListeners();
	}
	
	/**
	 * Get the ownidentities that are in the channel
	 * @return
	 */
	
	public List<Map<String, String>> getOwnIdentityChannelMembers()
	{
		ArrayList<Map<String, String>> members = new ArrayList<Map<String, String>>();
		
		for(Map<String, String> member : identityManager.getOwnIdentities())
		{
			if (channelIdentities.contains(member)) members.add(member);
		}
		return members;
	}
	
	
	/**
	 * Determine whether an identity is present in the channel or not
	 * @param identity
	 * @return
	 */
	
	public boolean inChannel(Map<String, String> identity)
	{
		return channelIdentities.contains(identity);
	}
	
	/**
	 * Retrieve the identity manager for this channel (tied to a single own WoT identity)
	 * @return
	 */
	
	public IdentityManager getIdentityManager()
	{
		return identityManager;
	}
	
	
	@Override
	public void run()
	{
		long index = Frirc.currentIndex();
		
		while(true)
		{
			if (index != Frirc.currentIndex())
			{
				//setup listeners for all the people in my WoT
				setupListeners();
				
				//trigger a nenw channelping for the ownidentities
				triggerChannelpings();
				
				//cleanup outstanding requests that are no longer of interest
				mm.cleanupRequests();
				
				index = Frirc.currentIndex();
			}
			
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}


	private void triggerChannelpings() {
		mm.triggerChannelpings();
	}

	public void terminate()
	{
		mm.terminate();
		stop();
	}
	
}
