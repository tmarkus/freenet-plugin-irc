package plugin.frirc;

import java.io.StringWriter;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import plugin.frirc.message.IRCMessage;
import plugin.frirc.message.IncomingMessageHandler;
import plugin.frirc.message.IncomingXMLMessageParser;
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
import freenet.pluginmanager.PluginRespirator;
import freenet.support.SimpleReadOnlyArrayBucket;

/**
 * Class to deal with the insertion of new messages and associated functionality
 * @author tmarkus
 *
 */

public class MessageManager implements ClientGetCallback, RequestClient, ClientPutCallback {

	private IdentityManager im;
	private ChannelManager cm;
	private PluginRespirator pr;
	
	private FetchContext singleFC;
	private FetchContext ULPRFC;
	
	private HashSet<ClientGetter> pendingRequests = new HashSet<ClientGetter>();  			//manage all outstanding connections
	
	private Map<Map<String, String>, Boolean> isCalibrated = new HashMap<Map<String, String>, Boolean>();		//store whether an identity is calibrated yet or not
	private Map<Map<String, String>, String> identityLastDNF = new HashMap<Map<String, String>, String>();		//store the last url that could not be retrieved for the identity
	
	private HighLevelSimpleClient hl;
	private HighLevelSimpleClient low_priority_hl;
	
	private List<FreenetURI> blackList = new LinkedList<FreenetURI>(); //uris which have been requested once and may not be requested again
	
	public MessageManager(ChannelManager cm, IdentityManager im, PluginRespirator pr, HighLevelSimpleClient hl, HighLevelSimpleClient low_priority_hl)
	{
		this.cm = cm;
		this.im = im;
		this.pr = pr;
		
		this.hl = hl;
		this.low_priority_hl = low_priority_hl;

		this.singleFC = hl.getFetchContext();
		this.singleFC.maxNonSplitfileRetries = 1;
		this.singleFC.followRedirects = true;
		this.singleFC.ignoreStore = true;
		
		this.ULPRFC = hl.getFetchContext();
		this.ULPRFC.maxNonSplitfileRetries = -1;
		this.ULPRFC.followRedirects = true;
		this.ULPRFC.ignoreStore = true;
	}
	
	/**
	 * Start calibrating the connection for an identity
	 * @param identity
	 */
	
	public void calibrate(Map<String, String> identity)
	{
			if (!isCalibrated.containsKey(identity)) //don't reset the calibrated status, but keep it
			{
				isCalibrated.put(identity, false);
			}
			
			//FIXME: check that continuesly recalibrating identities is a good thing or not... (maybe high priorities for already known identities in the channel and lower for the rest?)
			if (!isCalibrated(identity)) //only start the calibration process for identities that haven't been calibrated yet
			{
				FreenetURI fetchURI;
				try {
					fetchURI = Frirc.idToRequestURI(identity.get("ID"), cm.getChannel());
					pendingRequests.add(hl.fetch(fetchURI, 20000, this, this, singleFC));
					System.out.println("Trying to see whether a user is publishing at: " + fetchURI);
				} catch (FetchException e) {
					e.printStackTrace();
				}
			}
			else //we're already calibrated so set a ULPR for the next message, not a single fetch
 			{
				FreenetURI fetchURI;
				try {
					fetchURI = Frirc.idToRequestURI(identity.get("ID"), cm.getChannel());
					pendingRequests.add(hl.fetch(fetchURI, 20000, this, this, ULPRFC));
				} catch (FetchException e) {
					e.printStackTrace();
				}
			}
	}
	
	private boolean isCalibrated(Map<String, String> identity)
	{
		for(Map<String, String> identityItem : isCalibrated.keySet())
		{
			if (identityItem.get("ID").equals(identity.get("ID")) && isCalibrated.get(identityItem) == true) return true;
		}
		return false;
	}
	
	/**
	 * Insert a new message
	 * @param message
	 */

	public void insertNewMessage(Map<String, String> identity, StringWriter message)
	{
		try {
			FreenetURI requestURI = new FreenetURI(getLastDNF(identity));
			updateDNF(identity, Frirc.getNextIndexURI(requestURI));
			
		    Map<String, String> ownIdentity = IdentityManager.getIdentityInMap(identity, new HashSet<Map<String, String>>(im.getOwnIdentities()));
			System.out.println("Inserting new message at: " + Frirc.requestURIToInsertURI(requestURI, ownIdentity.get("insertID")));
			FreenetURI insertURI = Frirc.requestURIToInsertURI(requestURI, ownIdentity.get("insertID"));
			
			//insert content at next index
			InsertBlock insertBlock = new InsertBlock(new SimpleReadOnlyArrayBucket(message.toString().getBytes()), null, insertURI);
			hl.insert(insertBlock, false, "feed", false, hl.getInsertContext(true), this);
			
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		catch (InsertException e) {
			e.printStackTrace();
		}
	}


	@Override
	public synchronized void onFailure(FetchException fe, ClientGetter cg, ObjectContainer oc) 
	{
		System.out.println("Failed to retrieve key: " + cg.getURI() + " (DNF)");
		
		String id = Frirc.requestURItoID(cg.getURI());
		Map<String, String> identity = im.getIdentityByID(id);

		//store latest DNF for identity
		updateDNF(identity, cg.getURI());
		
		//mark identity as calibrated
		if (IdentityManager.identityInMap(identity, isCalibrated.keySet()) && !isCalibrated.get(IdentityManager.getIdentityInMap(identity, isCalibrated.keySet())))
		{
			System.out.println("Calibrated: " + identity.get("nick"));
			isCalibrated.put(IdentityManager.getIdentityInMap(identity, isCalibrated.keySet()), true);
			
			//send channelping if the identity that we're calibrating is connected locally
			if (IdentityManager.identityInMap(identity, cm.getServer().getLocals())  )
			{
				MessageCreator mc = new MessageCreator(cm);
				insertNewMessage(identity, mc.createChannelPing(identity));
			}
			else
			{
				System.out.println("Hello, I've calibrated another user, now watching for future messages");
				
				try {
					pendingRequests.add(hl.fetch(cg.getURI(), 20000, this, this, ULPRFC));
				} catch (FetchException e) {
					e.printStackTrace();
				}
			}
		}
	}


	/**
	 * store latest DNF for identity
	 * @param identity
	 * @param uri
	 */
	
	private synchronized void updateDNF(Map<String, String> identity, FreenetURI uri)
	{
		if (IdentityManager.identityInMap(identity, identityLastDNF.keySet()))
		{
			try {
				FreenetURI current_uri = new FreenetURI(identityLastDNF.get(IdentityManager.getIdentityInMap(identity, identityLastDNF.keySet())));
				
				//only accept increasing URIs not older ones (waypoint >, or waypoint == old_waypoint && index is > )
				if (Frirc.requestURIToWaypoint(current_uri) < Frirc.requestURIToWaypoint(uri) || (Frirc.requestURIToWaypoint(current_uri) == Frirc.requestURIToWaypoint(uri) && Frirc.requestURIToIndex(current_uri) < Frirc.requestURIToIndex(uri)  ))
				{
					identityLastDNF.put(IdentityManager.getIdentityInMap(identity, identityLastDNF.keySet()), uri.toString());
				}
			}
			catch (MalformedURLException e) {
				e.printStackTrace();
			}
		}
		else
		{
			identityLastDNF.put(identity, uri.toString());
		}
	}

	private String getLastDNF(Map<String, String> identity)
	{
		for(Map<String, String> identityElement : identityLastDNF.keySet())
		{
			if (identityElement.get("ID").equals(identity.get("ID"))) return identityLastDNF.get(identityElement);
		}
		return null;
	}
	
	
	@Override
	public synchronized void onSuccess(FetchResult fr, ClientGetter cg, ObjectContainer oc) {
		 
		
		System.out.println("The following URI was requested and found: " + cg.getURI());
		
		
		String id = Frirc.requestURItoID(cg.getURI());
		Map<String, String> identity = im.getIdentityByID(id);
		
		if (im.getOwnNickByID(id) != null && isCalibrated.get(identity)) //don't do things with your own onSucces
		{
			return;
		}
		
		if (blackList.contains(cg.getURI())) //a success is only allowed to be triggered once
		{
			blackList.remove(cg.getURI().toString());
			return;
		}
		blackList.add(cg.getURI());
		
		
		//lookup identity for URL and setup calibration
		//IdentityManager manager = new IdentityManager(pr,  cm.getOwnIdentityChannelMembers().get(0));

		if (isCalibrated(identity) && im.getOwnNickByID(id) == null) //check that we're really not processing our own messages
		{
			try
			{
				//convert XML to an IRCMessage
				IncomingXMLMessageParser parser = new IncomingXMLMessageParser(im, cm);
	
				//really process the message
				IRCMessage message = parser.parse(fr, cg.getURI());
				IncomingMessageHandler incomingMessageHandler = new IncomingMessageHandler(cm, im);
	
				//send the locally connected clients the contents of the message (assuming it is a message, handle channelpings, differently)
				incomingMessageHandler.processMessage(message, identity);
			}
			catch(Exception e)
			{
				e.printStackTrace();
			}
			
			
			//Set a ULPR for the next message
			try {
				System.out.println("Watching next key: " + Frirc.getNextIndexURI(cg.getURI()));
				pendingRequests.add(hl.fetch(Frirc.getNextIndexURI(cg.getURI()),20000, this, this, ULPRFC));
			} catch (FetchException e) {
				e.printStackTrace();
			}
			
		}
		else //not calibrated yet, so increase the current index and try again
		{
			try {
				pendingRequests.add(hl.fetch(Frirc.getNextIndexURI(cg.getURI()),20000, this, this, singleFC));
			} catch (FetchException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void onMajorProgress(ObjectContainer arg0) {
	}

	@Override
	public boolean persistent() {
		return false;
	}

	@Override
	public void removeFrom(ObjectContainer arg0) {
	}

	@Override
	public void onFailure(InsertException arg0, BaseClientPutter arg1, ObjectContainer arg2) {

		System.out.println("Inserting data into freenet has FAILED!");
		System.out.println(arg0.getMessage());
		arg0.printStackTrace();
	
	}

	@Override
	public void onFetchable(BaseClientPutter arg0, ObjectContainer arg1) {
	}

	@Override
	public void onGeneratedURI(FreenetURI arg0, BaseClientPutter arg1, ObjectContainer arg2) {
	}

	@Override
	public void onSuccess(BaseClientPutter arg0, ObjectContainer arg1) {
	
		System.out.println("Inserting data into freenet has succeeded!");
	
	}

	public void setupListeners() {
		//setup listeners and try to calibrate them
		for(Map<String, String> identity : im.getAllIdentities())
		{
			calibrate(identity);
		}
	}

	public void terminate() {
		
		//cancel all pending requests that we know about
		for(ClientGetter cg : pendingRequests)
		{
			cg.cancel(null, pr.getNode().clientCore.clientContext);
		}
		
		isCalibrated.clear();
		blackList.clear();
		identityLastDNF.clear();
	}

	/**
	 * Insert a channelping again when upon a changing waypoint (only for own identities in the channel!)
	 */
	
	public void triggerChannelpings() {
		
		for(Map<String, String> identity : im.getOwnIdentities())
		{
			for(Map<String, String> channelIdentity : cm.getChannelIdentities()) //don't start listening to a channelidentity again
			{
				if (identity.get("ID").equals(channelIdentity.get("ID"))) {
					MessageCreator mc = new MessageCreator(cm);
					
					// we want to insert at the new index, not at the latest old one!
					// we act is we checked that the key we want to insert to isn't inserted already (DNF upon request)
					updateDNF(channelIdentity, Frirc.idToRequestURI(channelIdentity.get("ID"), cm.getChannel())); 
					insertNewMessage(identity, mc.createChannelPing(identity));
				}
			}
			
		}
	}

	/**
	 * Cancel long running ULPR's that are too old and no longer of interest
	 */
	
	public void cleanupRequests()
	{
		for(ClientGetter outstandingRequest : pendingRequests)
		{
			if (Frirc.requestURIToWaypoint(outstandingRequest.getURI()) < Frirc.currentIndex() - 2 * Frirc.WAYPOINT_DURATION)
			{
				outstandingRequest.cancel(null, pr.getNode().clientCore.clientContext);
			}
		}
	}


}
