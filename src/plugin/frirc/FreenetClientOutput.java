package plugin.frirc;

import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.Socket;
import java.util.HashSet;
import java.util.Random;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

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
import freenet.support.SimpleReadOnlyArrayBucket;

public class FreenetClientOutput extends FreenetClient implements ClientPutCallback, ClientGetCallback {

	private FreenetURI insertURI;
	private FreenetURI requestURI;
	private long latest_edition = 0;
	
	
	public FreenetClientOutput(Socket socket, IRCServer server, HighLevelSimpleClient priority, HighLevelSimpleClient low_priority, String channel, String nick) throws TransformerConfigurationException, ParserConfigurationException, TransformerFactoryConfigurationError {
		super(socket, priority, low_priority, channel, nick);
		this.server = server;

		System.out.println("CREATING FREENET OUTPUT THREAD FOR: " + nick);
	}

	public void setInsertURI(FreenetURI insertURI)
	{
		this.insertURI = insertURI;
	}

	public void setRequestURI(FreenetURI requestURI)
	{
		this.requestURI = requestURI;
	}

	@Override
	public void onFailure(InsertException arg0, BaseClientPutter arg1, ObjectContainer arg2) {
	
		//keep track of the uris and skip calling the various functions if we've already processed it
		if (isOldFailedURI(arg1.getURI())) return;
		if (stopThread()) return;
		
		System.out.println("ER GING WAT MIS MET HET INSERTEN VAN EEN FILE");
		arg0.printStackTrace();

		//reset calibration
		this.calibrated = false;
		
		//call startPublishing again since apparantly we haven't found the latest unoccupied index
		startPublishing();
	}

	@Override
	public void onFetchable(BaseClientPutter arg0, ObjectContainer arg1) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onGeneratedURI(FreenetURI arg0, BaseClientPutter arg1,
			ObjectContainer arg2) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onSuccess(BaseClientPutter arg0, ObjectContainer arg1) {
		// TODO Auto-generated method stub
		System.out.println("WOOHOO, I've async inserted a file!!! " + arg0.getURI() );
	}

	@Override
	public void onMajorProgress(ObjectContainer arg0) {
		System.out.println("There is some kind of progress");

	}

	
	@Override

	/**
	 * Callback called when requesting the latest channel-edition doesn't exist yet
	 */

	public synchronized void onFailure(FetchException arg0, ClientGetter arg1, ObjectContainer arg2) {

		//keep track of the uris and skip calling the various functions if we've already processed it
		if (isOldFailedURI(arg1.getURI())) return;
		if (stopThread()) return;
		
		System.out.println("Failed! " + arg1.getURI());
		
		
		if (!calibrated)
		{
			//failed? -> good, we've found our latest index to publish to
			this.calibrated = true;
			System.out.println("Gecalibreerd");
			pushChannelPing();
		}
	}

	@Override
	public synchronized void onSuccess(FetchResult arg0, ClientGetter arg1,
			ObjectContainer arg2) {

		//keep track of the uris and skip calling the various functions if we've already processed it
		if (isOldFailedURI(arg1.getURI())) return;
		if (stopThread()) return;

		
		if (!calibrated)
		{
			//index already exists? increase the count with one and try again
			startPublishing();
		}
	}

	/**
	 * Add a number of random identities to our message (for discovery of new channel members outside of our current trust list)
	 */
	
	private void addRandomIdentities(Document xml, Element message)
	{
		//request a list of nicks in the channel
		//choose n random identities   /n == 1 (just to start with)
		HashSet<String> nicks = server.getNicksInChannel(channel);
		
		Random generator = new Random();
		int index = generator.nextInt(nicks.size());
		System.out.println(index);
		
		//resolve these nicks to id's, add both to the XML
		String nick = (String) (nicks.toArray()[index]);
		
		try
		{
			String requestURI = server.getIdentityByNick(nick).get("ID");
			
			//add them to the xml
			Element identities = xml.createElement("Identities");
			Element identity = xml.createElement("Identity");
			identity.setTextContent(requestURI);
	
			identities.appendChild(identity);
			message.appendChild(identities);
		}
		catch(NullPointerException e)
		{
			System.out.println("NullpointerException at nick: " + nick);
			e.printStackTrace();
		}
		
		}
	
	
	private synchronized void pushChannelPing()
	{
		Document xmlDoc;
		synchronized(mDocumentBuilder) { // TODO: Figure out whether the DocumentBuilder is maybe synchronized anyway 
			xmlDoc = mDOM.createDocument(null, Frirc.NAMESPACE, null);
		}

		Element rootElement = xmlDoc.getDocumentElement();

		/* Create the identity Element */
		Element messageElement = xmlDoc.createElement("Message");
		addRandomIdentities(xmlDoc, rootElement);
		
		messageElement.setAttribute("type", "channelping");
		messageElement.setAttribute("timestamp", Long.toString(System.currentTimeMillis()));
		messageElement.setTextContent("ping pong!");
		rootElement.appendChild(messageElement);

		insertNewMessage(getXMLString(xmlDoc));
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
	
	
	private synchronized void pushPrivMessage(Message message)
	{
		Document xmlDoc;
		synchronized(mDocumentBuilder) { // TODO: Figure out whether the DocumentBuilder is maybe synchronized anyway 
			xmlDoc = mDOM.createDocument(null, Frirc.NAMESPACE, null);
		}

		Element rootElement = xmlDoc.getDocumentElement();

		/* Create the identity Element */
		Element messageElement = xmlDoc.createElement("Message");
		messageElement.setAttribute("type", "privmsg");
		messageElement.setAttribute("timestamp", Long.toString(System.currentTimeMillis()));
		addRandomIdentities(xmlDoc, rootElement);
		
		messageElement.setTextContent(message.getValue());
		rootElement.appendChild(messageElement);

		insertNewMessage(getXMLString(xmlDoc));
	}
	
	/**
	 * Insert a new message
	 * @param result
	 */
	
	private  synchronized void insertNewMessage(StringWriter result)
	{

		if (last_startpoint != Frirc.currentIndex())
		{
			last_startpoint = Frirc.currentIndex();
			latest_edition = 0;
		}

		System.out.println("Inserting new message at: " + insertURI + "-" + last_startpoint + "-" + latest_edition);
		
		try {
			FreenetURI newInsertURI = new FreenetURI(insertURI.toString() + "-" + last_startpoint + "-" + latest_edition);

			//insert content at next index
			InsertBlock insertBlock = new InsertBlock(new SimpleReadOnlyArrayBucket(result.toString().getBytes()), null, newInsertURI);
			hl.insert(insertBlock, false, "feed", false, hl.getInsertContext(true), this);
			latest_edition++;

		} catch (MalformedURLException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (InsertException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	/**
	 * Find the latest key used by this user
	 */

	public synchronized void startPublishing()
	{
		FetchContext fc = hl.getFetchContext();
		fc.maxNonSplitfileRetries = 1;
		
		if (last_startpoint != Frirc.currentIndex())
		{
			last_startpoint = Frirc.currentIndex();
			latest_edition = 0;
		}

		//System.out.println("Bezig met zoeken naar editie...");

		try {
			hl.fetch(new FreenetURI(requestURI.toString() + "-" + Frirc.currentIndex() + "-" + latest_edition + "/feed"), 20000, this, this, fc);
			latest_edition++;
		} catch (FetchException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void run()
	{
		
		startPublishing();
		
		while(true)
		{
			if (calibrated)
			{
				Message message = server.getMessageToSend(this);
				
				if (message != null)
				{
					System.out.println("Message received");
					if (message.getType().equals("PRIVMSG"))
					{
						System.out.println("Sending message");
						pushPrivMessage(message);
					}
				}
			
				//ping the channel if the time index changed
				if (last_startpoint != Frirc.currentIndex())
				{
					pushChannelPing();
				}
			}

			if (stopThread()) return;
			
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}

	

}
