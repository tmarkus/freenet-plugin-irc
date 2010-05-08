package plugin.frirc.message;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import freenet.client.FetchResult;
import freenet.keys.FreenetURI;

import plugin.frirc.ChannelManager;
import plugin.frirc.Frirc;
import plugin.frirc.IRCServer;
import plugin.frirc.IdentityManager;

public class IncomingXMLMessageParser extends MessageBase {

	
	IdentityManager im;
	ChannelManager cm;
	public IncomingXMLMessageParser(IdentityManager im, ChannelManager cm)
	{
		super();
		this.im = im;
		this.cm = cm;
	}
	
	public IRCMessage parse(FetchResult fr, FreenetURI uri)
	{
		try {
			Document doc = mDocumentBuilder.parse(new InputSource(new ByteArrayInputStream(fr.asByteArray())));
			
			XPathFactory factory = XPathFactory.newInstance();
			XPath xpath = factory.newXPath();

			XPathExpression expr = xpath.compile("//message/@type");
			String type = (String) expr.evaluate(doc, XPathConstants.STRING);

			Map<String,String> identity = im.getIdentityByID(Frirc.requestURItoID(uri));
			
			System.out.println("URI of the message we're trying to parse: " + uri);
			System.out.println("Type of message was detected as: '" + type + "'");
			
			//all messages contain identity hints so process them before anything else
			expr = xpath.compile("//IdentityHints/identity");
			NodeList identityNodes = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
			
			//setup listeners for all the hinted identities
			//TODO: this place is pretty weird to include this functionality.... MOVE IT!
			for (int i = 0; i < identityNodes.getLength(); i++) {
			    String identityHint = identityNodes.item(i).getTextContent();
			    cm.getMessageManager().calibrate(im.getIdentityByID(identityHint));
				System.out.println("I found an identity hint and it is: " + identityNodes.item(i).getTextContent()); 
			
				for(Map<String, String> own_identity : cm.getOwnIdentities())
				{
					if (own_identity.get("ID").equals(identityHint))
					{
						String mode = "+v";
						String nick = im.getNickByID(Frirc.requestURItoID(uri)); //the nick of the identity that listed us!
						IRCMessage message = new IRCMessage(":" + IRCServer.SERVERNAME + " MODE " + cm.getChannel() + " " + mode + " " +nick);
						
						cm.getServer().sendAllLocalClientsInChannel(cm, message);
						cm.getServer().addBlacklist(message);
					}
				}
			}
			
			//process channelhints
			expr = xpath.compile("//channels/channel");
			NodeList channels = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
	
			for (int i = 0; i < identityNodes.getLength(); i++) {
			    String channelName = identityNodes.item(i).getTextContent();
			    cm.getServer().getChannelSearcher().addChannel(channelName, identity);
			}
			
			//process a channel message
			if (type.equals("privmsg"))
			{
				expr = xpath.compile("//message/@timestamp");
				//int timestamp = ((Double) expr.evaluate(doc, XPathConstants.NUMBER)).intValue();
	
				expr = xpath.compile("//message/text()");
				String messageText = (String) expr.evaluate(doc, XPathConstants.STRING);
	
				String channel = Frirc.requestURItoChannel(uri);
				
				IRCMessage message = IRCMessage.createChannelMessage(identity, channel, messageText);
				return message;
			}
			
			//regular channel ping
			else if (type.equals("channelping"))
			{
				String channel = Frirc.requestURItoChannel(uri);
				IRCMessage message = IRCMessage.createJOINMessage(identity, channel);
				return message;
			}
			else if (type.equals("part"))
			{
				String channel = Frirc.requestURItoChannel(uri);
				IRCMessage message = IRCMessage.createPartMessage(identity, channel);
				return message;
			}
			
		} catch (IOException e) {
			System.err.println("Something went wrong with parsing the XML!");
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (XPathExpressionException e) {
			e.printStackTrace();
		}
		
		return null; //return null by default (channelping and anything you cannot parse)
	}
	
	
}
