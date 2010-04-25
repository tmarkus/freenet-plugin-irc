/* This code is part of a plugin for Freenet. It is distributed 
 * under the GNU General Public License, version 3 (or at your option
 * any later version). See http://www.gnu.org/ for details of the GPL. */


package plugin.frirc;

import java.net.MalformedURLException;

import javax.xml.transform.TransformerFactoryConfigurationError;

import freenet.keys.FreenetURI;
import freenet.pluginmanager.FredPlugin;
import freenet.pluginmanager.FredPluginHTTP;
import freenet.pluginmanager.FredPluginTalker;
import freenet.pluginmanager.FredPluginThreadless;
import freenet.pluginmanager.PluginHTTPException;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;
import freenet.support.api.HTTPRequest;


public class Frirc implements FredPlugin, FredPluginHTTP, FredPluginThreadless, FredPluginTalker {

	
	public static String NAMESPACE = "frirc";
	public static String WoT_NAMESPACE = "plugins.WoT.WoT";
	
	public static int SAVEPOINT = 240 * 1000 ; //ms
	public static long TIMEOUT = 5*60  * 1000; //ms
	
	private IRCServer IRCServer;
	
	public static int MAX_IDENTITY_HINTS = 3;
	
	
	public static long currentIndex()
	{
		return System.currentTimeMillis() / SAVEPOINT;
	}
	
	
	@Override
	public void runPlugin(PluginRespirator pr) {
		
		try {
			
			System.out.println("Now starting the IRC server...");
			 
			this.IRCServer = new IRCServer(pr);
			this.IRCServer.start();
			
		} catch (TransformerFactoryConfigurationError e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void terminate() {
		if (IRCServer != null) IRCServer.terminate();
	}

	@Override
	public String handleHTTPGet(HTTPRequest arg0) throws PluginHTTPException {
		return "<html><header><title>Frirc instructions</title></header><body>" +
		"<h1>Hi!</h1>" +
		"You should be able to connect to a simple IRC-like server on localhost:6667 .<br />" +
		"Set your IRC username and nick to the nickname of one of your personal WoT-identities or you won't be able to connect. <br />" +
		"You need a seperate IRC client in order to make use of Frirc!" +
		"</body></html>";
	}

	@Override
	public String handleHTTPPost(HTTPRequest arg0) throws PluginHTTPException {
		return null;
	}

	@Override
	public void onReply(String arg0, String arg1, SimpleFieldSet arg2,
			Bucket arg3) {
	}

	/**
	 * Convert a request URI to an ID (strip of the keytype and possible path etc at the end of it)
	 * @param requestURI
	 * @return
	 */
	
	public static String requestURItoID(String requestURI)
	{
		//System.out.println("URI = "+requestURI.toString());
		
		String id = requestURI.split("/")[0]; //remove everything after the first slash
		if (id.split("@").length > 1)
		{
			id = id.split("@")[1]; //remove keytype (SSK / USK)
		}
		
		//System.out.println("ID = " + id);
		
		return id;
	}

	public static String requestURItoID(FreenetURI requestURI)
	{
		return requestURItoID(requestURI.toString());
	}

	
	public static int requestURIToIndex(FreenetURI requestURI)
	{
		String right = requestURI.toString().split("/")[1];
		return Integer.parseInt( right.split("-")[3] );
	}
	

	public static int requestURIToWaypoint(FreenetURI requestURI)
	{
		String right = requestURI.toString().split("/")[1];
		return Integer.parseInt( right.split("-")[2] );
	}

	/**
	 * Extract the channelname from a full request URI (a channel feed with an index etc etc)
	 * @param requestURI
	 * @return
	 */
	
	public static String requestURItoChannel(FreenetURI requestURI)
	{
		return "#" + requestURI.toString().split("/")[1].split("-")[1];
	}
	
	public static FreenetURI idToRequestURI(String id, String channel)
	{
		try {
			FreenetURI request =  new FreenetURI("SSK@" + id + "/" + Frirc.NAMESPACE + "-" + cleanChannel(channel) + "-" + Frirc.currentIndex() + "-0/feed");
			return request;
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	/**
	 * Rewrite an existing request URI to a insert URI
	 * @param requestURI
	 * @param insertID
	 * @return
	 */
	
	public static FreenetURI requestURIToInsertURI(FreenetURI requestURI, String insertID)
	{
		try {
			FreenetURI insertURI =  new FreenetURI("SSK@" + insertID + "/" + Frirc.NAMESPACE + "-" + cleanChannel(requestURItoChannel(requestURI)) + "-" + requestURIToWaypoint(requestURI) + "-"+requestURIToIndex(requestURI));
			return insertURI;
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public static String cleanChannel(String channel)
	{
		return channel.replace("#", "");
	}

	public static FreenetURI getNextIndexURI(FreenetURI uri)
	{
		String channel = requestURItoChannel(uri);
		String id = requestURItoID(uri);
		int waypoint = requestURIToWaypoint(uri);
		int number = requestURIToIndex(uri);
		
		if (waypoint != currentIndex()) number = -1; //new waypoint so reset the counter
		
		try {
			return  new FreenetURI("SSK@" + id + "/" + Frirc.NAMESPACE + "-" + cleanChannel(channel) + "-" + Frirc.currentIndex() + "-" + (number+1) + "/feed");
		} catch (MalformedURLException e) {
			e.printStackTrace();
			return null;
		}
	}
}
