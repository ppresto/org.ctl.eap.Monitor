package eapMonitor;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.OperationsException;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.remote.JMXConnector;

import java.io.*;
import java.text.DecimalFormat;
import java.util.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.jboss.dmr.*;
import org.jboss.as.controller.client.*;
import org.jboss.as.controller.client.helpers.*;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.RealmCallback;

/**
 * 
 * EAPMultiQuery uses the remote EAP Managment Interface to gather performance data
 * 
 * @author Patrick.Presto@centurylink.com
 * 
 */
public class eapMultiQuery
{
	private String url;
	private int verbatim, port;
	private JMXConnector connector;
	private MBeanServerConnection connection;
	private String warning, critical;
	private String attributeName, infoAttribute, replaceName, useAttr;
	private Long totalTime;
	private String attributeKey, infoKey;
    private String methodName;
	private String object;
	private String username, password, addr, securityRealmName;
	private List<String> additionalArgs = new ArrayList<String>();
	private List <String>calc = new ArrayList<String>();
	private String [] samples;
	private boolean disableWarnCrit = false;
    private Object defaultValue;
    private static final int RETURN_OK = 0; // 	 The plugin was able to check the service and it appeared to be functioning properly
	private static final String OK_STRING = "JMX OK -"; 
	private static final int RETURN_WARNING = 1; // The plugin was able to check the service, but it appeared to be above some "warning" threshold or did not appear to be working properly
	private static final String WARNING_STRING = "JMX WARNING -"; 
	private static final int RETURN_CRITICAL = 2; // The plugin detected that either the service was not running or it was above some "critical" threshold
	private static final String CRITICAL_STRING = "JMX CRITICAL -"; 
	private static final int RETURN_UNKNOWN = 3; // Invalid command line arguments were supplied to the plugin or low-level failures internal to the plugin (such as unable to fork, or open a tcp socket) that prevent it from performing the specified operation. Higher-level errors (such as name resolution errors, socket timeouts, etc) are outside of the control of plugins and should generally NOT be reported as UNKNOWN states.
	private static final String UNKNOWN_STRING = "JMX UNKNOWN";
	
	private Object checkData;
	private Object infoData;
	List<Map<String,String>> eapAttrList = new ArrayList <Map<String,String>>();
	private ModelControllerClient client = null;

	public eapMultiQuery() throws Exception {
		//tmp
	}

    public int runCommand(String... args) throws Exception {
    	Map<String,String> monitorData = new HashMap <String,String>();
        List<Map<String,String>> eapAttrList = new ArrayList <Map<String,String>>();
        eapMonitor monitor = new eapMonitor();
        eapMonitor nextMonitor = new eapMonitor();
        eapClient client = null;
       try {
			monitor.parseArgs(args);
			client = new eapClient(monitor.getHost(), monitor.getPort(), monitor.getUserName(), monitor.getPassword(), monitor.getRealm());
			if ( monitor.getObject() != null && monitor.getAttribute() == null ) {
				monitorData = client.getAttributes(monitor.getObject(), true, true);
				System.out.println("\n"+ monitorData.get("Attribute") +"\n");
				System.out.println(monitorData.get("Value"));
				System.exit(0);
			}
			else if (monitor.monitorDataSources()) {
				eapAttrList = getDataSources(client);
				return checkAttrList(eapAttrList);
			}
			else {
				monitorData = monitor.validate(client.getAttribute(monitor.getObject(), monitor.getAttribute(), true, true));
				eapAttrList.add(monitorData);
				
				List<String> additionalMonitors = monitor.searchForAdditionalMonitors();
				if (!additionalMonitors.isEmpty()) {
					for( String m : additionalMonitors) {
						nextMonitor = new eapMonitor(monitor);
						nextMonitor.parseArgs(m.split(" "));
						eapAttrList.add(nextMonitor.validate(client.getAttribute(nextMonitor.getObject(), nextMonitor.getAttribute(), true, true)));	
					}
				}
				return checkAttrList(eapAttrList);
			}
			
       } catch (Exception e) {
			e.printStackTrace();
			return RETURN_UNKNOWN;
       } finally {
       	client.close();
       }
	return RETURN_UNKNOWN;  
    }

    public List<Map<String,String>> getDataSources(eapClient client) throws Exception, InterruptedException {
    	Map<String,String> dsAttr = new HashMap <String,String>();
    	List<Map<String,String>> dsList = new ArrayList <Map<String,String>>();
    	try {
    		List<Property> ds = client.getDataSources();
    		for (Property datasource : ds){

    				eapMonitor monitor = new eapMonitor(false);
    				String monArgs = "-O /subsystem=datasources/data-source="+datasource.getName()+"/statistics=pool -A MaxWaitTime -w 3 -c 8 -r "+datasource.getName()+".maxWait";
    				monitor.parseArgs(monArgs.split(" "));
	    			dsAttr = client.getAttribute(monitor.getObject(), monitor.getAttribute(), true, true);
	    			dsList.add(monitor.validate(dsAttr));
	    			
	    			monitor = new eapMonitor(false);
    				monArgs = "-O /subsystem=datasources/data-source="+datasource.getName()+"/statistics=pool -A TimedOut -w 5 -r "+datasource.getName()+".timedOut";
    				monitor.parseArgs(monArgs.split(" "));
	    			dsAttr = client.getAttribute(monitor.getObject(), monitor.getAttribute(), true, true);
	    			dsList.add(monitor.validate(dsAttr));
	    			
	    			monitor = new eapMonitor(false);
    				monArgs = "-O /subsystem=datasources/data-source="+datasource.getName()+"/statistics=pool -A TotalBlockingTime -r "+datasource.getName()+".totBlkTim";
    				monitor.parseArgs(monArgs.split(" "));
	    			dsAttr = client.getAttribute(monitor.getObject(), monitor.getAttribute(), true, true);
	    			dsList.add(monitor.validate(dsAttr));
	    			
	    			monitor = new eapMonitor(false);
	    			monArgs = "-O /subsystem=datasources/data-source="+datasource.getName()+"/statistics=pool -A AvailableCount e -w 5 -r "+datasource.getName()+".availCount";
    				monitor.parseArgs(monArgs.split(" "));
	    			dsAttr = client.getAttribute(monitor.getObject(), monitor.getAttribute(), true, true);
	    			dsList.add(monitor.validate(dsAttr));
	    			//get 90% of the available connections to use as a warning threshold for ActiveCount below.
	    			int acWarn = (int) (Double.parseDouble(monitor.getValue())*.9);

	    			monitor = new eapMonitor(false);
    				monArgs = "-O /subsystem=datasources/data-source="+datasource.getName()+"/statistics=pool -A ActiveCount e -w "+ String.valueOf(acWarn) +" -r "+datasource.getName()+".curCount";
    				monitor.parseArgs(monArgs.split(" "));
	    			dsAttr = client.getAttribute(monitor.getObject(), monitor.getAttribute(), true, true);
	    			dsList.add(monitor.validate(dsAttr));	    			
    		}
			return dsList;
    	} catch (IOException e){
    		e.printStackTrace();
    		return dsList;
    	} catch (InterruptedException e) {
    		e.printStackTrace();
    		return dsList;
    	}	
    }
    
    public int checkAttrList (List<Map<String,String>> eapAttrList) {
    	if (checkState(eapAttrList, RETURN_CRITICAL)) {
        	return RETURN_CRITICAL;
        }
        if (checkState(eapAttrList, RETURN_WARNING)) {
        	return RETURN_WARNING;
        }
        if (checkState(eapAttrList, RETURN_OK)) {
        	return RETURN_OK;
        }
        return RETURN_UNKNOWN;
    }
    
    public boolean checkState(List<Map<String,String>> eapAttrList, int RETURN_STATE) {
    	StringBuilder MsgDesc = new StringBuilder();
    	StringBuilder MsgCheckData = new StringBuilder();
    	StringBuilder MsgPerfData = new StringBuilder(" |");
    	boolean foundState = false;
    	for (Map<String, String> a: eapAttrList){
	    		if (a.get("status").matches(Integer.toString(RETURN_STATE))){
	    			if (!foundState){
	    				MsgDesc.append(a.get("statusDesc"));
	    				foundState = true;
	    			}
	    		}
    	}
    	if (foundState){
	    	for (Map<String, String> a: eapAttrList) {
	    		if (a.get("status").matches(Integer.toString(RETURN_STATE))) {
	    			MsgCheckData.append(" "+ a.get("Attribute") +"="+ a.get("Value"));
	    		}
	    	}
	    	for (Map<String, String> a: eapAttrList){
			    for (Map.Entry<String, String> entry : a.entrySet()){
		    		if (entry.getKey().equals("perfData")){
		    			MsgPerfData.append(" "+ entry.getValue());
		    		}
		    	}
	    	}
		    System.out.println(MsgDesc.toString() + MsgCheckData.toString() + MsgPerfData.toString());
		    return true;
    	}    	
		return false;
    }
	public static void main(String[] args) throws IOException
    {
        try {
			System.exit(new eapMultiQuery().runCommand(args));
		} catch (Exception e) {
			e.printStackTrace();
		}
    }
}

