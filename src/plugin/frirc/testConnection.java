package plugin.frirc;

public class testConnection {

	/**
	 * @param args
	 */
	public static void main(String[] args) {

		try {

			
			String[] test = new String[1];
			test[0] = "test";
			
			Connection.main(test);
			//new IRCServer();
			
		
		} catch (Throwable e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
	}

}

/**
 * TODO
 * - link nickname to wot identity and add it as the address (nickname@WOT)
 * - insert IRC external IRC communication as updates to the KSK (which we monitor with ULPS's)
 * - a ksk-update checker is 
 * 
 * 
 * 
 * 
 */

