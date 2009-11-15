package plugin.frirc;

import java.net.Socket;
import java.util.LinkedList;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactoryConfigurationError;

import freenet.client.HighLevelSimpleClient;
import freenet.keys.FreenetURI;

public abstract class FreenetClient extends FrircConnection{
	
	protected boolean calibrated = false;
	protected long last_startpoint = 0;
	protected LinkedList<FreenetURI> oldFailedURIs = new LinkedList<FreenetURI>();
	protected LinkedList<FreenetURI> oldSuccessURIs = new LinkedList<FreenetURI>();
	protected HighLevelSimpleClient hl;
	protected HighLevelSimpleClient low_priority_hl;
	
	public FreenetClient(Socket socket, HighLevelSimpleClient priority, HighLevelSimpleClient low_priority, String channel, String nick) throws ParserConfigurationException,
			TransformerConfigurationException,
			TransformerFactoryConfigurationError {
		super(socket, channel, nick);
		this.hl = priority;
		this.low_priority_hl = low_priority;
	}

	public boolean isLocal()
	{
		return false;
	}
	
	protected boolean stopThread()
	{
		if (!server.isAlive() || (!server.inChannel(nick, channel) && calibrated))
		{
			System.out.println("Stopping thread because either server is interrupted or nick no longer in channel, type = " + this.getClass());
			System.out.println("details, nick = " + nick + " channel = " + channel);
			
			this.interrupt();
			return true; //stop the thread?
		}
		else
		{
			return false;
		}
	}
	
	protected boolean isOldSuccessURI(FreenetURI uri)
	{
		//keep track of the uris and skip calling the various functions if we've already processed it
		if (oldSuccessURIs.contains(uri))
			{
				System.out.println("OUDE URI GEVONDEN!!!");
			return true;
			}
		oldSuccessURIs.add(uri);
		if (oldSuccessURIs.size() > 10) oldSuccessURIs.remove();
		return false;
	}
	

	protected boolean isOldFailedURI(FreenetURI uri)
	{
		//keep track of the uris and skip calling the various functions if we've already processed it
		if (oldFailedURIs.contains(uri))
			{
				System.out.println("OUDE URI GEVONDEN!!!");
			return true;
			}
		oldFailedURIs.add(uri);
		if (oldFailedURIs.size() > 10) oldFailedURIs.remove();
		return false;
	}
	
	
}
