package eapMonitor;

import java.io.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.jboss.dmr.*;
import org.jboss.as.controller.client.*;
import org.jboss.as.controller.client.helpers.*;

import javax.management.ObjectName;
import javax.management.OperationsException;
import javax.management.openmbean.CompositeDataSupport;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.RealmCallback;

public class eapClient {
	private ModelControllerClient client = null;
	private String [] Attributes;
	private String [] samples;
	private Long totalTime;
	
	public eapClient(String addr, Integer port, String user, String password, String securityRealmName) throws Exception {
		try {
    		this.client = createClient(addr, port, user, password, securityRealmName);
    	}
    	catch (IOException e1) {
    		e1.printStackTrace();
    	}
    	//return client;
	}
	
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

	public void getJBossVersion() throws Exception {
		ModelNode op = new ModelNode();
		op.get(ClientConstants.OP).set("read-resource");
 
		ModelNode returnVal = client.execute(op);
		System.out.println("release-version: " + returnVal.get("result").get("release-version").asString());
		System.out.println("release-codename: " + returnVal.get("result").get("release-codename").asString());
	}

	public List<Property> getObjectList(String object, String property, boolean includeRuntime, boolean recursive) throws Exception {
		ModelNode op = new ModelNode();
		op.get(ClientConstants.OP).set("read-resource");
		op.get("recursive").set(recursive);
		op.get("include-runtime").set(includeRuntime);
	    ModelNode response = new ModelNode();
	    List<Property> objectList = null;
	    Attributes = object.split("/");
		if (Attributes != null && Attributes.length > 0){
			for(int i=0; i<Attributes.length; i++){
				if (Attributes[i].split("=").length == 2) {
					String [] op_addr = Attributes[i].split("=");
					op.get(ClientConstants.OP_ADDR).add(op_addr[0],op_addr[1]);
				}
			}
		}
	    response = client.execute(new OperationBuilder(op).build());
	    reportFailure(response);  
	    try {
	    	ModelNode test = response.get(ClientConstants.RESULT).get(property);
	    	if (test.isDefined()){
	    		objectList = test.asPropertyList();
	    	}	
	    }
	    catch (Exception e){
	    	e.printStackTrace();
	    }
	    return objectList;
	}
	
	@SuppressWarnings("static-access")
	public Map<String,String> getAttribute(String object, String attribute, String attributeKey, String [] samples, boolean recursive, boolean runtime) throws IOException, InterruptedException {
		/*String command = "-O "+object+" -A "+attribute;
		if(attributeKey != null)
			command = command+" -C "+attributeKey;
		if(samples != null)
			command = command+" samples "+samples.toString();
		System.out.println(command);
		*/
		final ModelNode request = new ModelNode();
		Map<String,String> eapAttr = new HashMap<String,String>();
		ModelNode response = null;
		ModelNode attr = null;
		Object value = null;
		request.get(ClientConstants.OP).set("read-resource");
		Attributes = object.split("/");
		if (Attributes != null && Attributes.length > 0){
			for(int i=0; i<Attributes.length; i++){
				if (Attributes[i].split("=").length == 2) {
					//System.out.println("object: "+Attributes[i]);
					String [] op_addr = Attributes[i].split("=");
					//System.out.println("op_addr: "+op_addr[0]+" = "+op_addr[1]);
					request.get(ClientConstants.OP_ADDR).add(op_addr[0],op_addr[1]);
				}
			}
		}
		request.get("recursive").set(recursive);
		request.get("include-runtime").set(runtime);
        try {
        	if (samples != null && samples.length == 3){
        		totalTime = (long) 0;
        		int sample = Integer.parseInt(samples[0]);
        		int delay = Integer.parseInt(samples[1]);
        		String algorithm = samples[2];
        		List<Long> values = new ArrayList();
        		for(int i=0; i<sample; i++){
	        		Long startTime = System.nanoTime();
	        		response = client.execute(new OperationBuilder(request).build());
	        		value = response.get(ClientConstants.RESULT).get(attribute).asLong();
	                Thread.currentThread().sleep(delay);
	                Long endTime = System.nanoTime();
	                totalTime = endTime - startTime + totalTime;
	                values.add((Long) value);
        		}
        		if(algorithm.matches("getDiffOverTime")){
        			value = getDiffOverTime(values, totalTime);
        		}
        	} else {

        			response = client.execute(new OperationBuilder(request).build());
        			if (attributeKey != null)
        				value = response.get(ClientConstants.RESULT).get(attribute).get(attributeKey).toString().replaceAll("^\"|\"$|L$", ""); 
        			else
        				value = response.get(ClientConstants.RESULT).get(attribute).toString().replaceAll("^\"|\"$|L$", ""); 
        			if (value.toString().matches("undefined")){
        				value = 0;
        			}
        			
        	}
        } catch (IOException e) {
    		e.printStackTrace();
    	} catch (InterruptedException e) {
    		e.printStackTrace();
    	} catch (NullPointerException e) {
    		value = "0";
    		//e.printStackTrace();
    	}
		eapAttr.put("Attribute", attribute);
		eapAttr.put("Value", value.toString());
        return eapAttr;
	}
	
	public Map<String,String> getAttributes(String object, boolean recursive, boolean runtime) throws IOException, InterruptedException {
		final ModelNode request = new ModelNode();
		Map<String,String> eapAttr = new HashMap<String,String>();
		ModelNode response = null;
		ModelNode attr = null;
		Object value = null;
		request.get(ClientConstants.OP).set("read-resource");
		Attributes = object.split("/");
		if (Attributes != null && Attributes.length > 0){
			for(int i=0; i<Attributes.length; i++){
				if (Attributes[i].split("=").length == 2) {
					//System.out.println("object: "+Attributes[i]);
					String [] op_addr = Attributes[i].split("=");
					//System.out.println("op_addr: "+op_addr[0]+" = "+op_addr[1]);
					request.get(ClientConstants.OP_ADDR).add(op_addr[0],op_addr[1]);
				}
			}
		}
		request.get("recursive").set(recursive);
		request.get("include-runtime").set(runtime);
        try {
			response = client.execute(new OperationBuilder(request).build());
			value = response.get(ClientConstants.RESULT);
        } catch (IOException e) {
    		e.printStackTrace();
    	}
		eapAttr.put("Attribute", object);
		eapAttr.put("Value", value.toString());
        return eapAttr;
	}
	public ModelNode getDSAttributes(String attribute) throws IOException {
		final ModelNode request = new ModelNode();
		request.get(ClientConstants.OP).set("read-resource");
		request.get(ClientConstants.OP_ADDR).add("subsystem", "datasources");
		request.get(ClientConstants.OP_ADDR).add("data-source", attribute);
		request.get(ClientConstants.OP_ADDR).add("statistics", "pool");
		request.get("recursive").set(true);
		request.get("include-runtime").set(true);
		final ModelNode response = client.execute(new OperationBuilder(request).build());
        reportFailure(response);      
        return response.get(ClientConstants.RESULT);
	}
	
	public List<Property> getDataSources() throws IOException {
	    ModelNode request = new ModelNode();
	    ModelNode response = new ModelNode();
	    List<Property> dsList = null;
	    request.get(ClientConstants.OP).set("read-resource");
	    //request.get("recursive").set(true);
	    request.get(ClientConstants.OP_ADDR).add("subsystem", "datasources");
	    response = client.execute(new OperationBuilder(request).build());
	    reportFailure(response);     
	    
	    dsList = response.get(ClientConstants.RESULT).get("data-source").asPropertyList();
	    return dsList;
	}

	public List<Property> getXADataSources() throws IOException {
	    ModelNode request = new ModelNode();
	    ModelNode response = new ModelNode();
	    List<Property> dsList = null;
	    request.get(ClientConstants.OP).set("read-resource");
	    //request.get("recursive").set(true);
	    request.get(ClientConstants.OP_ADDR).add("subsystem", "datasources");
	    response = client.execute(new OperationBuilder(request).build());
	    reportFailure(response);     
	    dsList = (response.get(ClientConstants.RESULT).get("xa-data-source").asPropertyList());	
	    return dsList;
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

	public void close() {
		if(client != null){
			try {
				client.close();
				client = null;
			}
			catch(Exception e) {}
		}
	}
	
	private double getDiffOverTime(List<Long> values, Long time){
		Float value = null;
		value = (float) (values.get(values.size()-1) - values.get(0));
		value = value/time;
		return Double.parseDouble(decimalPlaces(value));
	}
	private String decimalPlaces(Float num){
		DecimalFormat df = new DecimalFormat("###.##");
		return df.format(num);
	}
}	
