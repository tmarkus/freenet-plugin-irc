package plugin.frirc;

import java.io.StringWriter;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

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
	private Map<HashMap<String, String>, Boolean> isCalibrated = new HashMap<HashMap<String, String>, Boolean>();		//store whether an identity is calibrated yet or not
	private HashMap<HashMap<String, String>, String> identityLastDNF = new HashMap<HashMap<String, String>, String>();		//store the last url that could not be retrieved for the identity
	
	private HighLevelSimpleClient hl;
	private HighLevelSimpleClient low_priority_hl;
	
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
	
	public void calibrate(HashMap<String, String> identity)
	{
			isCalibrated.put(identity, false);
			
			FreenetURI fetchURI;
			try {
				fetchURI = Frirc.idToRequestURI(identity.get("ID"), cm.getChannel());
				pendingRequests.add(hl.fetch(fetchURI, 20000, this, this, singleFC));
				System.out.println("Trying to see whether a user is publishing at: " + fetchURI);
			} catch (FetchException e) {
				e.printStackTrace();
			}
	}
	
	/**
	 * Insert a new message
	 * @param message
	 */

	private void insertNewMessage(HashMap<String, String> identity, StringWriter message)
	{
		try {
			FreenetURI requestURI = new FreenetURI(identityLastDNF.get(IdentityManager.getIdentityInMap(identity, identityLastDNF.keySet())));
			updateDNF(identity, Frirc.getNextIndexURI(requestURI));
			
		    HashMap<String, String> ownIdentity = IdentityManager.getIdentityInMap(identity, new HashSet<HashMap<String, String>>(im.getOwnIdentities()));
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
	public synchronized void onFailure(FetchException fe, ClientGetter cg, ObjectContainer oc) {

		String id = Frirc.requestURItoID(cg.getURI());

		//lookup identity for URL and setup calibration
		IdentityManager manager = new IdentityManager(pr, cm.getOwnIdentityChannelMembers().get(0));

		try {
			while(!manager.allReady())
			{
				Thread.sleep(100);
			}
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
		
		HashMap<String, String> identity = (HashMap<String, String>) manager.getIdentityByID(id);
		
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
				MessageCreator mc = new MessageCreator();
				insertNewMessage(identity, mc.createChannelPing(identity));
			}
			else //we've calibrated an identity other than our own, do something with it?
			{
				System.out.println("Hello, I've calibrated another user");
			}
		}
	}


	/**
	 * store latest DNF for identity
	 * @param identity
	 * @param uri
	 */
	
	private synchronized void updateDNF(HashMap<String, String> identity, FreenetURI uri)
	{
		if (IdentityManager.identityInMap(identity, identityLastDNF.keySet()))
		{
			identityLastDNF.put(IdentityManager.getIdentityInMap(identity, identityLastDNF.keySet()), uri.toString());
		}
		else
		{
			identityLastDNF.put(identity, uri.toString());
		}
	}
	
	@Override
	public synchronized void onSuccess(FetchResult fr, ClientGetter cg, ObjectContainer oc) {
		 
		String id = Frirc.requestURItoID(cg.getURI());

		//lookup identity for URL and setup calibration
		IdentityManager manager = new IdentityManager(pr,  cm.getOwnIdentityChannelMembers().get(0));

		try {
			while(!manager.allReady())
			{
				Thread.sleep(100);
			}
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
		
		HashMap<String, String> identity = (HashMap<String, String>) manager.getIdentityByID(id);

		if (isCalibrated.get(IdentityManager.getIdentityInMap(identity, isCalibrated.keySet())) == true)
		{
			System.out.println("Received uri thingy and will proceed with processing the message");
			//really process the message
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
		return true;
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
		for(HashMap<String, String> identity : im.getOwnIdentities()) //FIXME: change to request only rank1 identities, this is a hack for testing!
		{
			calibrate(identity);
		}
	}
}
