package plugin.frirc.message;

import java.io.StringWriter;
import java.util.HashMap;

import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import plugin.frirc.Frirc;

public class MessageCreator extends MessageBase{
	
	public MessageCreator()
	{
		super();
	}
	
	/**
	 * 
	 */
	
	public synchronized StringWriter createChannelPing(HashMap<String, String> identity)
	{
		Document xmlDoc;
		synchronized(mDocumentBuilder) { // TODO: Figure out whether the DocumentBuilder is maybe synchronized anyway 
			xmlDoc = mDOM.createDocument(null, Frirc.NAMESPACE, null);
		}

		Element rootElement = xmlDoc.getDocumentElement();

		/* Create the identity Element */
		Element messageElement = xmlDoc.createElement("Message");
		//addRandomIdentities(xmlDoc, rootElement);
		
		messageElement.setAttribute("type", "channelping");
		messageElement.setAttribute("timestamp", Long.toString(System.currentTimeMillis()));
		messageElement.setTextContent("ping pong!");
		rootElement.appendChild(messageElement);

		System.out.println("Created channelping message");
		
		return getXMLString(xmlDoc);
	}

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

	/*
	
	private synchronized void pushPrivMessage(Message message)
	{
		Document xmlDoc;
		synchronized(mDocumentBuilder) { // TODO: Figure out whether the DocumentBuilder is maybe synchronized anyway 
			xmlDoc = mDOM.createDocument(null, Frirc.NAMESPACE, null);
		}

		Element rootElement = xmlDoc.getDocumentElement();

		//Create the identity Element
		Element messageElement = xmlDoc.createElement("Message");
		messageElement.setAttribute("type", "privmsg");
		messageElement.setAttribute("timestamp", Long.toString(System.currentTimeMillis()));
		addRandomIdentities(xmlDoc, rootElement);
		
		messageElement.setTextContent(message.getValue());
		rootElement.appendChild(messageElement);

		insertNewMessage(getXMLString(xmlDoc));
	}

	*/
	
}
