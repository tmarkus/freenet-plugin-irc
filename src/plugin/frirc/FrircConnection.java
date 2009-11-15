package plugin.frirc;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.net.Socket;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;

import org.w3c.dom.DOMImplementation;

import com.db4o.ObjectContainer;

import freenet.node.RequestClient;


public abstract class FrircConnection extends Thread implements RequestClient {

	  protected Socket socket;
	  protected BufferedReader in;
	  protected PrintWriter out;
	  protected DocumentBuilder mDocumentBuilder;
	  protected DOMImplementation mDOM;
	  protected Transformer mSerializer;
	  protected String nick;
	  protected String channel;
	  
	  public IRCServer getServer() {
		return server;
	}

	public void setServer(IRCServer server) {
		this.server = server;
	}

	public void setSocket(Socket socket) {
		this.socket = socket;
	}

	public boolean isLocal()
	{
		return true;
	}
	

	  IRCServer server;

	  public FrircConnection(Socket socket, String channel, String nick) throws ParserConfigurationException, TransformerConfigurationException, TransformerFactoryConfigurationError
	  {
		  this.socket = socket;
		  this.channel = channel;
		  this.nick = nick;

		  
		  //setup xml
			DocumentBuilderFactory xmlFactory = DocumentBuilderFactory.newInstance();
			xmlFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			// DOM parser uses .setAttribute() to pass to underlying Xerces
			xmlFactory.setAttribute("http://apache.org/xml/features/disallow-doctype-decl", true);
			mDocumentBuilder = xmlFactory.newDocumentBuilder(); 
			mDOM = mDocumentBuilder.getDOMImplementation();

			mSerializer = TransformerFactory.newInstance().newTransformer();
			mSerializer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			mSerializer.setOutputProperty(OutputKeys.INDENT, "yes"); /* FIXME: Set to no before release. */
			mSerializer.setOutputProperty(OutputKeys.STANDALONE, "no");

	  
	  }
	  
	  public Socket getSocket()
	  {
		  return socket;
	  }


		@Override
		public boolean persistent() {
			// TODO Auto-generated method stub
			return false;
		}


		@Override
		public void removeFrom(ObjectContainer arg0) {
			// TODO Auto-generated method stub
			
		}


	public String getNick()
	{
		return nick;
	}

	public void setNick(String nick)
	{
		this.nick = nick;
	}

	public String getChannel()
	{
		return this.channel;
	}

	public void setSchannel(String channel)
	{
		this.channel = channel;
	}
	
}
