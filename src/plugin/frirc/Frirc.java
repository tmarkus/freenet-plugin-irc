/* This code is part of a plugin for Freenet. It is distributed 
 * under the GNU General Public License, version 3 (or at your option
 * any later version). See http://www.gnu.org/ for details of the GPL. */


package plugin.frirc;

import java.net.MalformedURLException;

import javax.xml.transform.TransformerFactoryConfigurationError;

import freenet.client.HighLevelSimpleClient;
import freenet.keys.FreenetURI;
import freenet.node.RequestStarter;
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
	
	public static long currentIndex()
	{
		return System.currentTimeMillis() / SAVEPOINT;
	}
	
	
	@Override
	public void runPlugin(PluginRespirator pr) {
		// TODO Auto-generated method stub
		
		//schedule all the requests from this application with a high priority class so they should finish sooner
		HighLevelSimpleClient hl = pr.getNode().clientCore.makeClient(RequestStarter.MAXIMUM_PRIORITY_CLASS);
		HighLevelSimpleClient low_priority_hl = pr.getNode().clientCore.makeClient(RequestStarter.PREFETCH_PRIORITY_CLASS);
		
		try {
			
			System.out.println("Now starting the IRC server...");
			
			 
			this.IRCServer = new IRCServer(hl, low_priority_hl, pr);
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
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String handleHTTPPost(HTTPRequest arg0) throws PluginHTTPException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void onReply(String arg0, String arg1, SimpleFieldSet arg2,
			Bucket arg3) {
		// TODO Auto-generated method stub
		
	}

	/**
	 * Convert a request URI to an ID (strip of the keytype and possible path etc at the end of it)
	 * @param requestURI
	 * @return
	 */
	
	public static String requestURItoID(String requestURI)
	{
		String id = requestURI.split("/")[0]; //remove everything after the first slash
		if (id.split("@").length > 1)
		{
			id = id.split("@")[1]; //remove keytype (SSK / USK)
		}
		return id;
	}
	
	public static String requestURItoID(FreenetURI requestURI)
	{
		return requestURItoID(requestURI.toString());
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
			FreenetURI request =  new FreenetURI("SSK@" + id + "/" + Frirc.NAMESPACE + "-" + cleanChannel(channel));
			return request;
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public static String cleanChannel(String channel)
	{
		return channel.replace("#", "");
	}

	
}
