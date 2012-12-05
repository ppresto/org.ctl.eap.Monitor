package eapMonitor;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.jboss.dmr.*;
//import org.jboss.as.*;
import org.jboss.as.controller.client.*;
import org.jboss.as.controller.client.helpers.*;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.RealmCallback;
import java.util.*;


public class test {

	private ModelControllerClient client = null;

	// unauthenticated
	public test(String host, Integer port) throws Exception
	{
		client = ModelControllerClient.Factory.create(InetAddress.getByName(host), port);
	}

	// authenticated
	public test(String host, Integer port, String user, String password, String securityRealmName) throws Exception
	{
		client = createClient(host, port, user, password, securityRealmName);
	}

	public static void main(String[] args) throws Exception {
		//String addr = "127.0.0.1";
		String addr = "151.119.51.86";
		Integer port = 9999;
		String user = "admin";
		String password = "admin123";
		String securityRealmName = "ManagementRealm";

/*
		JbossClient jbossClient = new JbossClient(addr, port); // unauthenticated - this only works if you remove the security-realm from             <native-interface security-realm="ManagementRealm">
*/

		test jbossClient = new test(addr, port, user, password, securityRealmName); // authenticated - for this you need to make sure you have the correct user/pass/realm

		jbossClient.getJBossVersion();

		jbossClient.readResource(true,true);

		List<ModelNode> datasources = jbossClient.getDataSources();
		for(ModelNode datasource : datasources)
		{
			System.out.println("datasource: " + datasource);
		}
		jbossClient.close();

		System.out.println("Done");
   }

		public void getJBossVersion() throws Exception {
			ModelNode op = new ModelNode();
			op.get(ClientConstants.OP).set("read-resource");
 
			ModelNode returnVal = client.execute(op);
			System.out.println("release-version: " + returnVal.get("result").get("release-version").asString());
			System.out.println("release-codename: " + returnVal.get("result").get("release-codename").asString());
 		}

		public void readResource(boolean includeRuntime, boolean recursive) throws Exception {
			ModelNode op = new ModelNode();
			op.get(ClientConstants.OP).set("read-resource");
      op.get("recursive").set(true);
      op.get("include-runtime").set(true);
 
			ModelNode returnVal = client.execute(op);
			System.out.println(returnVal.get("result").toString());
 		}

		public List<ModelNode> getDataSources() throws IOException {
        final ModelNode request = new ModelNode();
        request.get(ClientConstants.OP).set("read-resource");
        request.get("recursive").set(true);
        request.get(ClientConstants.OP_ADDR).add("subsystem", "datasources");
        final ModelNode response = client.execute(new OperationBuilder(request).build());
        reportFailure(response);
        return response.get(ClientConstants.RESULT).get("data-source").asList();
    }

		private static void reportFailure(final ModelNode node) {
        if (!node.get(ClientConstants.OUTCOME).asString().equals(ClientConstants.SUCCESS)) {
            final String msg;
            if (node.hasDefined(ClientConstants.FAILURE_DESCRIPTION)) {
                if (node.hasDefined(ClientConstants.OP)) {
                    msg = String.format("Operation '%s' at address '%s' failed: %s", node.get(ClientConstants.OP), node.get(ClientConstants.OP_ADDR), node.get(ClientConstants.FAILURE_DESCRIPTION));
                } else {
                    msg = String.format("Operation failed: %s", node.get(ClientConstants.FAILURE_DESCRIPTION));
                }
            } else {
                msg = String.format("Operation failed: %s", node);
            }
            throw new RuntimeException(msg);
        }
    }

		public void close()
		{
			if(client != null)
			{
				try
				{
					client.close();
					client = null;
				}
				catch(Exception e) {}
			}
		}


/*
		public void test1() throws UnknownHostException {
    	ModelControllerClient client = null;
    	ModelControllerClient unauthenticatedClient = ModelControllerClient.Factory.create(InetAddress.getByName(addr), port);
    	try {    
    		ModelNode testConnection = new ModelNode();    
				System.out.println("testConnection.get() read-resource");
    		testConnection.get("operation").set("read-resource");  
				System.out.println("unauthenticatedClient.execute(testConnection);");
    		unauthenticatedClient.execute(testConnection);    
				System.out.println("client = unauthenticatedClient;");
    		client = unauthenticatedClient;
    	} catch(Exception e) {    
    		try {
					System.out.println("ModelNode testConnection = new ModelNode();");
    			ModelNode testConnection = new ModelNode();

					System.out.println("testConnection.get(operation).set(read-resource)");
    			testConnection.get("operation").set("read-resource");

					//System.out.println("client = NativeClient.createClient(addr, port, user, password, securityRealmName)");
    			//client = NativeClient.createClient(addr, port, user, password, securityRealmName);
    			//ModelNode node = client.execute(testConnection);
   		    System.out.println(node);
    		}
    		catch (IOException e1)
    		{
    		    e1.printStackTrace();
    		}

    	}
    } 
*/
    
		static ModelControllerClient createClient(final String addr, final int port, final String username, final String password, final String securityRealmName) throws UnknownHostException {     
			final CallbackHandler callbackHandler = new CallbackHandler() {         
				public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {            
					for (Callback current : callbacks) {                
						if (current instanceof NameCallback) {                    
							NameCallback ncb = (NameCallback) current;                    
							ncb.setName(username);                
						} else if (current instanceof PasswordCallback) {                    
							PasswordCallback pcb = (PasswordCallback) current;                    
							pcb.setPassword(password.toCharArray());
							//pcb.setPassword(password);
						} else if (current instanceof RealmCallback) {                    
							RealmCallback rcb = (RealmCallback) current;                    
							rcb.setText(rcb.getDefaultText());                
						} else {                    
							throw new UnsupportedCallbackException(current);                
						}            
					}        
				}  
			};     
			return ModelControllerClient.Factory.create(InetAddress.getByName(addr), port, callbackHandler);
		}
}	
