package plugin.frirc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import freenet.node.FSParseException;
import freenet.pluginmanager.FredPluginTalker;
import freenet.pluginmanager.PluginNotFoundException;
import freenet.pluginmanager.PluginRespirator;
import freenet.pluginmanager.PluginTalker;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;

public class IdentityManager implements FredPluginTalker {

	private ArrayList<HashMap<String, String>> ownIdentities = new ArrayList<HashMap<String, String>>(); //list of my own identities
	private ArrayList<HashMap<String, String>> identities = new ArrayList<HashMap<String, String>>(); //list of all identities
	private PluginTalker talker;
	private PluginRespirator pr;
	private boolean locked_all = true;
	private boolean locked_own = true;

	
	/**
	 * Initialize a new IdentityManager
	 * @param pr
	 * @param identity - can either be an identity or NULL for a generic identity manager for resolving stuff (can't calculate trust values)
	 */
	
	public IdentityManager(PluginRespirator pr, Map<String, String> identity)
	{
		this.pr = pr;
		
		try {
			this.talker = pr.getPluginTalker(this, Frirc.WoT_NAMESPACE, "WoT");
		} catch (PluginNotFoundException e) {
			e.printStackTrace();
		}
	
		if (identity != null) sendFCPAllIdentities(identity);
		sendFCPOwnIdentities();
	}
	

	/**
	 * Associate a nick with a WoT identity
	 * @param id
	 * @return
	 */

	public String getNickByID(String id)
	{
		//is the requested nick one of our own?
		if (getOwnNickByID(id) != null) return getOwnNickByID(id);
		
		//check all identities
		for(HashMap<String,String> identity : identities)
		{
			if (identity.get("ID").equals(id)){
				return identity.get("nick");
			}
		}
		
		Logger.error(this.getClass(),"Could not resolve ID ("+id+") to a nickname, this means that the WoT identity is unknown to us or the WoT too old.");
		return "UNRESOLVED";
	}

	
	public boolean allReady()
	{
		return !locked_all;
	}
	
	public boolean ownReady()
	{
		return !locked_own;
	}
	
	
	/**
	 * Retrieve an identity through an ID
	 * @param id
	 * @return
	 */
	
	public Map<String, String> getIdentityByID(String id)
	{

		//check all identities
		for(HashMap<String,String> identity : ownIdentities)
		{
			System.out.println(identity.get("ID"));
			
			if (identity.get("ID").equals(id)){
				return identity;
			}
		}

		
		//check all identities
		for(HashMap<String,String> identity : identities)
		{
			System.out.println(identity.get("ID"));
			
			if (identity.get("ID").equals(id)){
				return identity;
			}
		}
		
		Logger.error(this.getClass(),"Could not resolve ID ("+id+") to a nickname, this means that the WoT identity is unknown to us or the WoT too old.");
		return null;
	}
	
	
	public String getOwnNickByID(String id)
	{
		//check own identities
		for(HashMap<String,String> identity : ownIdentities)
		{
			if (identity.get("ID").equals(id)){
				return identity.get("nick");
			}
		}
		return null;
	}

	
	/**
	 * Retrieve the personal WoT identities to associate with nicknames
	 */
	
	private void sendFCPOwnIdentities(){
		SimpleFieldSet sfs = new SimpleFieldSet(true);
		sfs.putOverwrite("Message", "GetOwnIdentities");
		talker.send(sfs, null);
	}
	
	private synchronized void sendFCPAllIdentities(Map<String, String> identity)
	{
		PluginTalker talker;
		try {
			talker = pr.getPluginTalker(this, Frirc.WoT_NAMESPACE, "");
			SimpleFieldSet sfs = new SimpleFieldSet(true);
			sfs.putOverwrite("Message", "GetTrustees");
			sfs.putOverwrite("Identity", identity.get("ID").split(",")[0]); //a personal identity (associated through source) (only pass the ID, not the full SSK)
			sfs.putOverwrite("Context", ""); //empty means selecting all identities no matter the context
			talker.send(sfs, null);	//send message to WoT plugin
			
			System.out.println("requested identities for identity " + identity.get("ID"));
		} catch (PluginNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	
	public ArrayList<HashMap<String, String>> getAllIdentities()
	{
		return this.identities;
	}
	
	public ArrayList<HashMap<String, String>> getOwnIdentities()
	{
		return this.ownIdentities;
	}

	/**
	 * Process the WoT fcp message containing our identities and add them to the local store
	 * @param sfs
	 */
	
	private void addOwnIdentities(SimpleFieldSet sfs)
	{
		int i = 0;
		try {
			while(!sfs.getString("Identity"+i).equals(""))
			{
				HashMap<String, String> identity = new HashMap<String,String>();
				identity.put("ID", sfs.getString("RequestURI"+i).split("/")[0].replace("USK@", ""));
				identity.put("insertID", sfs.getString("InsertURI"+i).split("/")[0].replace("USK@", ""));
				identity.put("nick", sfs.getString("Nickname"+i));

				ownIdentities.add(identity);
				System.out.println("Identity added from WoT: " + identity.get("nick") + " (" + identity.get("ID") + ")");
				++i;
			}
		} catch (FSParseException e) { //triggered when we've reached the end of the identity list
			locked_own = new Boolean(false);
		}
	}

	
	private void addIdentities(SimpleFieldSet sfs)
	{
		System.out.println("Adding identities");
		
		//clear current identities (requests refresh
		identities.clear();
		
		//iterate over identities and store them (for resolving nick later on)
		int i = 0;
		try {
			while(!sfs.getString("Identity"+i).equals(""))
			{
				HashMap<String, String> identity = new HashMap<String,String>();
				identity.put("ID", sfs.getString("RequestURI"+i).split("/")[0].replace("USK@", ""));
				identity.put("nick", sfs.getString("Nickname"+i));
				identity.put("Value", sfs.getString("Value"+i));
				
				identities.add(identity);
				i++;
			}
		} catch (FSParseException e) { //triggered when we've reached the end of the identity list
			locked_all = new Boolean(false);
			System.out.println("Reached end of identity list");
		}
	}

	/**
	 * Retrieve an identity by its nickname
	 * @param nick
	 * @return
	 */
	
	public HashMap<String, String> getIdentityByNick(String nick)
	{
		for(HashMap<String,String> identity : identities)
		{
			if (identity.get("nick").equals(nick)){
				return identity;
			}
		}
		
		for(HashMap<String,String> identity : ownIdentities)
		{
			if (identity.get("nick").equals(nick)){
				return identity;
			}
		}

		
		return null; //FIXME, if the user nickname doesn't match A OwnIdentity send a notice or something through the irc server and then quit the connection
	}

	
	/**
	 * Method called upon receiving an FCP message, determines message type and calls correct method
	 */
 
	@Override
	public void onReply(String plugin, String channel, SimpleFieldSet sfs, Bucket arg3) {
		try {
			System.out.println("Message received = " + sfs.get("Message"));
 
			if (sfs.getString("Message").equals("OwnIdentities"))
			{
				addOwnIdentities(sfs);
			}
			else if (sfs.getString("Message").equals("Identities"))
			{
				addIdentities(sfs);
			}
			else
			{
				System.out.println("Message received = " + sfs.get("OriginalMessage"));
				System.out.println("Message received = " + sfs.get("Description"));
			}
		} catch (FSParseException e) { //no message field, shouldn't happen
			e.printStackTrace();
		}
 
	}

	public static boolean identityInMap(HashMap<String, String> identity, Set<HashMap<String, String>> identities)
	{
		for(HashMap<String, String> identityItem : identities)
		{
			if (identityItem.get("ID").equals(identity.get("ID"))) return true;
		}
		return false;
	}

	public static HashMap<String, String> getIdentityInMap(HashMap<String, String> identity, Set<HashMap<String, String>> identities)
	{
		for(HashMap<String, String> identityItem : identities)
		{
			if (identityItem.get("ID").equals(identity.get("ID"))) return identityItem;
		}
		return null;
	}
	
}
