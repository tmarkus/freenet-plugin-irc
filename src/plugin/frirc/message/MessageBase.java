package plugin.frirc.message;

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

public class MessageBase {

	protected DocumentBuilder mDocumentBuilder;
	protected DOMImplementation mDOM;
	protected Transformer mSerializer;
	
	public MessageBase()
	{
		//setup xml
		DocumentBuilderFactory xmlFactory = DocumentBuilderFactory.newInstance();
		try {
			xmlFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		// DOM parser uses .setAttribute() to pass to underlying Xerces
		xmlFactory.setAttribute("http://apache.org/xml/features/disallow-doctype-decl", true);
		mDocumentBuilder = xmlFactory.newDocumentBuilder(); 
		mDOM = mDocumentBuilder.getDOMImplementation();

		mSerializer = TransformerFactory.newInstance().newTransformer();
		mSerializer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		mSerializer.setOutputProperty(OutputKeys.INDENT, "yes"); /* FIXME: Set to no before release. */
		mSerializer.setOutputProperty(OutputKeys.STANDALONE, "no");
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (TransformerConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (TransformerFactoryConfigurationError e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
	
}
