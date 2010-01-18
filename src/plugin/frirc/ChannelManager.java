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
	
	private HashSet<HashMap<String,String>> channelIdentities = 
					new HashSet<HashMap<String,String>>(); 							// which identities are in which channel?
	
	private MessageManager mm;
	
	public ChannelManager(String channel, IRCServer server, PluginRespirator pr)
	{
		//schedule all the requests from this application with a high priority class so they should finish sooner
		hl = pr.getNode().clientCore.makeClient(RequestStarter.MAXIMUM_PRIORITY_CLASS);
		low_priority_hl = pr.getNode().clientCore.makeClient(RequestStarter.PREFETCH_PRIORITY_CLASS);
	
		this.channel = channel;
		this.server = server;
		this.pr = pr;
		
		this.identityManager = new IdentityManager(pr, null);
		this.mm = new MessageManager(this, this.identityManager, pr, hl, low_priority_hl);
	}
	
	/**
	 * Return the set of identities which are in this channel
	 * @return
	 */
	
	public HashSet<HashMap<String, String>> getIdentities()
	{
		return channelIdentities;
	}
	
	/**
	 * Return the set of own identities which are in this channel
	 * @return
	 */
	
	public HashSet<HashMap<String, String>> getOwnIdentities()
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
	
	public HashSet<HashMap<String, String>> getChannelIdentities()
	{
		return this.channelIdentities;
	}

	public void addIdentity(HashMap<String, String> identity)
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
	
	public List<HashMap<String, String>> getOwnIdentityChannelMembers()
	{
		ArrayList<HashMap<String, String>> members = new ArrayList<HashMap<String, String>>();
		
		for(HashMap<String, String> member : identityManager.getOwnIdentities())
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
		while(true)
		{
			try {

				//setup listeners for all the people in my WoT
				for(Map<String, String> identity : getIdentities())
				{
					/*
					if (!isCalibrated.get(identity.get("ID")))
					{
						//setupWoTListener(identity);
					}
					*/
				}
				
				Thread.sleep(100000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}
