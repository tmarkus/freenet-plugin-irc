package plugin.frirc.message;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import plugin.frirc.ChannelManager;
import plugin.frirc.Frirc;

public class MessageCreator extends MessageBase{
	
	private ChannelManager cm;
	
	public MessageCreator(ChannelManager cm)
	{
		super();
		this.cm = cm;
	}
	
	/**
	 * Add a random assortment of identity hints to use for fetching new message queues
	 * @param xmlDoc
	 * @param rootElement
	 */
	
	private void addRandomIdentities(Document xmlDoc, Element rootElement)
	{
		Element hintsElement = xmlDoc.createElement("IdentityHints");
		
		List<Map<String, String>> identities = new LinkedList<Map<String, String>>(cm.getChannelIdentities());
		
		if (identities.size() > 0)
		{
			Random random = new Random();
			
			for(int i=0; i < Frirc.MAX_IDENTITY_HINTS; i++)
			{
				Element identityElement = xmlDoc.createElement("identity");
				identityElement.setTextContent(identities.get(random.nextInt(identities.size())).get("ID"));
				hintsElement.appendChild(identityElement);
			}
		}
		rootElement.appendChild(hintsElement);
	}
	
	/**
	 * Add a random selection of channels that we're in
	 * @param xmlDoc
	 * @param rootElement
	 */
	
	private void addRandomChannels(Document xmlDoc, Element rootElement)
	{
		Element hintsElement = xmlDoc.createElement("channelHints");
		
		List<String> channels = new ArrayList<String>(cm.getServer().getChannelSearcher().getChannels());
		
		if (channels.size() > 0)
		{
			Random random = new Random();
			
			for(int i=0; i < Frirc.MAX_CHANNEL_HINTS; i++)
			{
				Element channelElement = xmlDoc.createElement("channel");
				channelElement.setTextContent(channels.get(random.nextInt(channels.size())));
				hintsElement.appendChild(channelElement);
			}
		}
		rootElement.appendChild(hintsElement);
	}
	
	
	/**
	 * 
	 */
	
	public synchronized StringWriter createChannelPing(Map<String, String> identity)
	{
		Document xmlDoc;
		synchronized(mDocumentBuilder) { 
			xmlDoc = mDOM.createDocument(null, Frirc.NAMESPACE, null);
		}

		Element rootElement = xmlDoc.getDocumentElement();

		/* Create the identity Element */
		Element messageElement = xmlDoc.createElement("message");
		addRandomIdentities(xmlDoc, rootElement);
		addRandomChannels(xmlDoc, rootElement);
		
		messageElement.setAttribute("type", "channelping");
		messageElement.setAttribute("timestamp", Long.toString(System.currentTimeMillis()));
		messageElement.setTextContent("ping pong!");
		rootElement.appendChild(messageElement);

		return getXMLString(xmlDoc);
	}

	
	/**
	 * Convenience method to get the Document to a String
	 * @param xmlDoc
	 * @return
	 */
	
	private StringWriter getXMLString(Document xmlDoc)
	{
		StringWriter result = new StringWriter();

		DOMSource domSource = new DOMSource(xmlDoc);
		StreamResult resultStream = new StreamResult(result);
		synchronized(mSerializer) {
			try {
				mSerializer.transform(domSource, resultStream);
			} catch (TransformerException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return result;
	}

	/**
	 * Create an XML file resembling a privmsg (can go to either channel or user in IRC)
	 * @param message
	 * @return
	 */
	
	public synchronized StringWriter createPrivMessage(IRCMessage message)
	{
		Document xmlDoc;
		synchronized(mDocumentBuilder) { // TODO: Figure out whether the DocumentBuilder is maybe synchronized anyway 
			xmlDoc = mDOM.createDocument(null, Frirc.NAMESPACE, null);
		}

		Element rootElement = xmlDoc.getDocumentElement();

		//Create the identity Element
		Element messageElement = xmlDoc.createElement("message");
		messageElement.setAttribute("type", "privmsg");
		messageElement.setAttribute("timestamp", Long.toString(System.currentTimeMillis()));
		addRandomIdentities(xmlDoc, rootElement);
		addRandomChannels(xmlDoc, rootElement);
		
		messageElement.setTextContent(message.getValue());
		rootElement.appendChild(messageElement);

		return getXMLString(xmlDoc);
	}

	/**
	 * Create an XML message for leaving a channel
	 * @param message
	 * @return
	 */
	
	public synchronized StringWriter createPartMessage(IRCMessage message)
	{
		Document xmlDoc;
		synchronized(mDocumentBuilder) { // TODO: Figure out whether the DocumentBuilder is maybe synchronized anyway 
			xmlDoc = mDOM.createDocument(null, Frirc.NAMESPACE, null);
		}

		Element rootElement = xmlDoc.getDocumentElement();

		//Create the identity Element
		Element messageElement = xmlDoc.createElement("message");
		messageElement.setAttribute("type", "part");
		messageElement.setAttribute("timestamp", Long.toString(System.currentTimeMillis()));
		addRandomIdentities(xmlDoc, rootElement);
		addRandomChannels(xmlDoc, rootElement);
		
		messageElement.setTextContent(message.getValue());
		rootElement.appendChild(messageElement);

		return getXMLString(xmlDoc);
	}
	
}
