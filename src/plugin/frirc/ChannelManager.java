package plugin.frirc;

import java.io.StringWriter;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import plugin.frirc.message.MessageCreator;

import com.db4o.ObjectContainer;

import freenet.client.FetchContext;
import freenet.client.FetchException;
import freenet.client.FetchResult;
import freenet.client.HighLevelSimpleClient;
import freenet.client.InsertBlock;
import freenet.client.InsertException;
import freenet.client.async.BaseClientPutter;
import freenet.client.async.ClientGetCallback;
import freenet.client.async.ClientGetter;
import freenet.client.async.ClientPutCallback;
import freenet.keys.FreenetURI;
import freenet.node.RequestClient;
import freenet.node.RequestStarter;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.SimpleReadOnlyArrayBucket;

public class ChannelManager extends Thread {

	private String channel;
	private HighLevelSimpleClient hl;
	private HighLevelSimpleClient low_priority_hl;
	private IRCServer server;
	private IdentityManager identityManager;
	private PluginRespirator pr;
	
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
	}
	
	public void removeIdentity(HashMap<String, String> identity)
	{
		channelIdentities.remove(identity);
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
	
	
	
	public boolean inChannel(Map<String, String> identity)
	{
		return channelIdentities.contains(identity);
	}
	
	@Override
	public void run()
	{
		long index = Frirc.currentIndex();
		
		while(true)
		{
			System.out.println("MARK");

			if (index != Frirc.currentIndex())
			{
				//setup listeners for all the people in my WoT
				setupListeners();
				index = Frirc.currentIndex();
			}
			
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}
