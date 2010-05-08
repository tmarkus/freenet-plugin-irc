package plugin.frirc;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Class to manager the search for channels
 * @author tmarkus
 *
 */

public class ChannelSearcher  {

	private Map<String, Set<String>> channels = new HashMap<String, Set<String>>();
	private IdentityManager im;
	
	public ChannelSearcher(IdentityManager im)
	{
		this.im = im;
	}
	
	/**
	 * Retrieve all channels as a list of strings
	 *FIXME: this doesn't contain the number of people NOR channel topic
	 * @return
	 */
	
	public Collection<String> getChannels()
	{
		return channels.keySet();
	}
	
	/**
	 * Add a new identity to the channel
	 * @param channel
	 * @param identity
	 */
	
	public void addChannel(String channel, Map<String, String> identity)
	{
		if (channels.containsKey(channel))
		{
			channels.get(channel).add(identity.get("ID"));
		}
		else
		{
			channels.put(channel, new HashSet<String>());
			channels.get(channel).add(identity.get("ID"));
		}
	}
	
	/**
	 * Remove all the knowledge about channels that we currently have
	 */
	
	public void reset()
	{
		channels.clear();
	}
}
