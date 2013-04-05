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
        eapMonitor nextMonitor = null;
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
			else if (monitor.monitorJvmHealth()) {
				return getJvmHealth(client);
			}
			else if (monitor.monitorDataSources()) {
				return getDatabaseHealth(client);
			}
			else if (monitor.monitorAppHealth()) {
				return getAppHealth(client);
			}
			else if (monitor.monitorJmsHealth()) {
				return getJmsHealth(client);
			}
			else if (monitor.monitorTransactions()) {
				return getAppTransactions(client);
			}
			else if (monitor.monitorConnector() != null) {
				return getConnector(client, monitor.monitorConnector());
			}
			else {
				monitorData = monitor.validate(client.getAttribute(monitor.getObject(), monitor.getAttribute(), monitor.getAttributeKey(), monitor.getSamples(), true, true));
				eapAttrList.add(monitorData);
				
				List<String> additionalMonitors = monitor.searchForAdditionalMonitors();
				if (!additionalMonitors.isEmpty()) {
					for( String m : additionalMonitors) {
						if (nextMonitor == null){
							nextMonitor = new eapMonitor(monitor, eapAttrList);
						} else {
							nextMonitor = new eapMonitor(nextMonitor, eapAttrList);
						}
						
						nextMonitor.parseArgs(m.split(" "));
						eapAttrList.add(nextMonitor.validate(client.getAttribute(nextMonitor.getObject(), nextMonitor.getAttribute(), nextMonitor.getAttributeKey(), nextMonitor.getSamples(), true, true)));	
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
    
    public int getAppTransactions(eapClient client) throws Exception {
    	customMonitor ah = new customMonitor(client);
		String obj = "/subsystem=transactions";
		String trxObj = "-O "+obj;
		eapAttrList.addAll(ah.buildMonitor(trxObj+" -A number-of-aborted-transactions -r trx.aborted"));
		eapAttrList.addAll(ah.buildMonitor(trxObj+" -A number-of-application-rollbacks -r trx.rollbacks"));
		eapAttrList.addAll(ah.buildMonitor(trxObj+" -A number-of-committed-transactions -r trx.committed"));
		eapAttrList.addAll(ah.buildMonitor(trxObj+" -A number-of-heuristics -r trx.heuristics"));
		eapAttrList.addAll(ah.buildMonitor(trxObj+" -A number-of-inflight-transactions -r trx.inflight"));
		eapAttrList.addAll(ah.buildMonitor(trxObj+" -A nested -r trx.nested"));
		eapAttrList.addAll(ah.buildMonitor(trxObj+" -A number-of-resource-rollbacks -r trx.resourceRollbacks"));
		eapAttrList.addAll(ah.buildMonitor(trxObj+" -A number-of-timed-out-transactions -r trx.timedOut"));
		eapAttrList.addAll(ah.buildMonitor(trxObj+" -A number-of-number-of-transactions -r trx.number"));
		return checkAttrList(eapAttrList);
    }
    
    public int getConnector(eapClient client, String protocol) throws Exception {
    	customMonitor ah = new customMonitor(client);
		String obj = "-O /subsystem=web/connector="+protocol;
		eapAttrList.addAll(ah.buildMonitor(obj+" -A bytesReceived -r "+protocol+".bytesRec"));
		eapAttrList.addAll(ah.buildMonitor(obj+" -A bytesSent -r "+protocol+".bytesSent"));
		eapAttrList.addAll(ah.buildMonitor(obj+" -A errorCount -r "+protocol+".errors"));
		eapAttrList.addAll(ah.buildMonitor(obj+" -A maxTime -r "+protocol+".maxTime"));
		eapAttrList.addAll(ah.buildMonitor(obj+" -A processingTime -samples 2,2000,getDiffOverTime -calc *100 -w 95 -r "+protocol+".Cpu%"));
		eapAttrList.addAll(ah.buildMonitor(obj+" -A requestCount -r "+protocol+".reqCount"));
		return checkAttrList(eapAttrList);
    }
    
    public int getDatabaseHealth(eapClient client) throws Exception {
    	customMonitor ah = new customMonitor(client);
		String obj = "/subsystem=datasources";
		List<String> datasources = ah.getChildResourceNames(obj, "data-source", true, false);
		for (String ds : datasources){
			String dsObj = "-O "+obj+"/data-source="+ds+"/statistics=pool";
			eapAttrList.addAll(ah.buildMonitor(dsObj+" -A MaxWaitTime -w 10000 -r "+ds+".ds.maxWaitTime"));
			eapAttrList.addAll(ah.buildMonitor(dsObj+" -A TimedOut -w 10 -r "+ds+".ds.timedOut"));
			eapAttrList.addAll(ah.buildMonitor(dsObj+" -A TotalBlockingTime -r "+ds+".ds.totBlkTime"));
			eapAttrList.addAll(ah.buildMonitor(dsObj+" -A AvailableCount -r "+ds+".ds.avail"));
			eapAttrList.addAll(ah.buildMonitor(dsObj+" -A ActiveCount -r "+ds+".ds.active"));
			eapAttrList.addAll(ah.buildMonitor(eapAttrList,dsObj+" -A ActiveCount -calc /"+ds+".ds.avail -calc *100 -r "+ds+".ds.usage -w 95"));
		}
		List<String> xadatasources = ah.getChildResourceNames(obj, "xa-data-source", true, false);
		for (String ds : xadatasources){
			String dsObj = "-O "+obj+"/xa-data-source="+ds+"/statistics=pool";
			eapAttrList.addAll(ah.buildMonitor(dsObj+" -A MaxWaitTime -w 10000 -r "+ds+".ds.maxWaitTime"));
			eapAttrList.addAll(ah.buildMonitor(eapAttrList,dsObj+" -A TimedOut -w 10 -r "+ds+".ds.timedOut"));
			eapAttrList.addAll(ah.buildMonitor(eapAttrList,dsObj+" -A TotalBlockingTime -r "+ds+".ds.totBlkTime"));
			eapAttrList.addAll(ah.buildMonitor(eapAttrList,dsObj+" -A AvailableCount -r "+ds+".ds.avail"));
			eapAttrList.addAll(ah.buildMonitor(eapAttrList,dsObj+" -A ActiveCount -r "+ds+".ds.active"));
			eapAttrList.addAll(ah.buildMonitor(eapAttrList,dsObj+" -A ActiveCount -calc /"+ds+".ds.avail -calc *100 -r "+ds+".ds.usage -w 95"));
		}
		if(eapAttrList.isEmpty()){
			Map<String,String> eapAttr = new HashMap <String,String>();
			int status = RETURN_WARNING;
			eapAttr.put("Attribute", "DataSources");
			eapAttr.put("Value", "No Data Sources Found");
			eapAttr.put("status",String.valueOf(status));
			eapAttr.put("statusDesc",WARNING_STRING);
			eapAttrList.add(eapAttr);
		}
		return checkAttrList(eapAttrList);
    }
    
    public int getJmsHealth(eapClient client) throws Exception{
    	customMonitor ah = new customMonitor(client);
    	// Get JMS Messaging Metrics
    	String obj = "/subsystem=messaging";
		List<String> hqservers = ah.getChildResourceNames(obj, "hornetq-server", true, false);
		for (String server : hqservers) {
			obj = obj + "/hornetq-server="+server;
			
			List<String> queues = ah.getChildResourceNames(obj, "jms-queue", true, false);
			for (String queue : queues){
				String qObj = "-O " +obj+ "/jms-queue=" +queue;
				eapAttrList.addAll(ah.buildMonitor(qObj+" -A message-count -r "+queue+".jmsq.msgCount"));
				eapAttrList.addAll(ah.buildMonitor(qObj+" -A delivering-count -r "+queue+".jmsq.deliveringCount"));
				eapAttrList.addAll(ah.buildMonitor(qObj+" -A scheduled-count -r "+queue+".jmsq.scheduledCount"));
				eapAttrList.addAll(ah.buildMonitor(qObj+" -A messages-added -r "+queue+".jmsq.msgAdded"));
			}
		}
		if(eapAttrList.isEmpty()){
			Map<String,String> eapAttr = new HashMap <String,String>();
			int status = RETURN_WARNING;
			eapAttr.put("Attribute", "JMS");
			eapAttr.put("Value", "No Queues Found");
			eapAttr.put("status",String.valueOf(status));
			eapAttr.put("statusDesc",WARNING_STRING);
			eapAttrList.add(eapAttr);
		}
		return checkAttrList(eapAttrList);
    }
    
    public int getAppHealth(eapClient client) throws Exception{
    	customMonitor ah = new customMonitor(client);
    	// Get JMS Messaging Metrics
    	String obj = "/subsystem=messaging";
		List<String> hqservers = ah.getChildResourceNames(obj, "hornetq-server", true, false);
		for (String server : hqservers) {
			obj = obj + "/hornetq-server="+server;
			
			List<String> queues = ah.getChildResourceNames(obj, "jms-queue", true, false);
			for (String queue : queues){
				String qObj = "-O " +obj+ "/jms-queue=" +queue;
			eapAttrList.addAll(ah.buildMonitor(qObj+" -A message-count -r "+queue+".jmsq.msgCount"));
			eapAttrList.addAll(ah.buildMonitor(qObj+" -A delivering-count -r "+queue+".jmsq.deliveringCount"));
			eapAttrList.addAll(ah.buildMonitor(qObj+" -A scheduled-count -r "+queue+".jmsq.scheduledCount"));
			eapAttrList.addAll(ah.buildMonitor(qObj+" -A messages-added -r "+queue+".jmsq.msgAdded"));

			}
		}
		// Get Application Deployments Metrics
    	obj = "";
		List<String> deployments = ah.getChildResourceNames(obj, "deployment", true, false);
		for (String app : deployments){
			obj = "/deployment"+"="+app;
			List<String> subdeployments = ah.getChildResourceNames(obj, "subdeployment",true,false);
			for(String subdeployment : subdeployments) {
				List<String> subSystems = ah.getChildResourceNames(obj+"/subdeployment="+subdeployment, "subsystem", true, false );
				for (String sub: subSystems) {
					if (sub.matches("web")){
						String webObj = "-O "+obj+"/subdeployment="+subdeployment+"/subsystem="+sub;
						//String arg = webObj+" -A active-sessions -r "+subdeployment+".act.sess";
						eapAttrList.addAll(ah.buildMonitor(webObj+" -A active-sessions -r "+subdeployment+".web.act.sess"));
						eapAttrList.addAll(ah.buildMonitor(webObj+" -A expired-sessions -r "+subdeployment+".web.exp.sess"));
						eapAttrList.addAll(ah.buildMonitor(webObj+" -A rejected-sessions -r "+subdeployment+".web.reject.sess"));
						eapAttrList.addAll(ah.buildMonitor(webObj+" -A session-avg-alive-time -r "+subdeployment+".web.avg.sess.alive"));
						eapAttrList.addAll(ah.buildMonitor(webObj+" -A session-max-alive-time -r "+subdeployment+".web.max.sess.alive"));
					}
					if (sub.matches("webservices")) {
						List<String> websvcs = ah.getChildResourceNames(obj+"/subdeployment="+subdeployment+"/subsystem="+sub, "endpoint", true, false );
						for (String websvc: websvcs) {
							String websvcObj = "-O "+obj+"/subdeployment="+subdeployment+"/subsystem="+sub+"/endpoint="+websvc;
							eapAttrList.addAll(ah.buildMonitor(websvcObj+" -A average-processing-time -r "+websvc+".ws.avg.proc.time"));
							eapAttrList.addAll(ah.buildMonitor(websvcObj+" -A fault-count -r "+websvc+".ws.fault-count"));
							eapAttrList.addAll(ah.buildMonitor(websvcObj+" -A request-count -r "+websvc+".ws.request-count"));
							eapAttrList.addAll(ah.buildMonitor(websvcObj+" -A response-count -r "+websvc+".ws.response-count"));	
						}
						
					}
					if (sub.matches("jpa")){
						String jpaObj = "-O "+obj+"/subdeployment="+subdeployment+"/subsystem="+sub;
						eapAttrList.addAll(ah.buildMonitor(jpaObj+" -A completed-transaction-count -r "+subdeployment+".jpa.trx.completed"));
						eapAttrList.addAll(ah.buildMonitor(jpaObj+" -A successful-transaction-count -r "+subdeployment+".jpa.trx.success"));
						eapAttrList.addAll(ah.buildMonitor(jpaObj+" -A query-execution-max-time -r "+subdeployment+".jpa.query.max.time"));
						eapAttrList.addAll(ah.buildMonitor(jpaObj+" -A query-execution-max-time-query-string -r "+subdeployment+".jpa.query.string"));
					}
					if (sub.matches("ejb3")) {
						List<String> ejbs = ah.getChildResourceNames(obj+"/subdeployment="+subdeployment+"/subsystem="+sub, "message-driven-bean", true, false );
						for (String ejb: ejbs) {
							String ejbObj = "-O "+obj+"/subdeployment="+subdeployment+"/subsystem="+sub+"/message-driven-bean="+ejb;
							eapAttrList.addAll(ah.buildMonitor(ejbObj+" -A execution-time -r "+ejb+".ejb.exec.time"));
							eapAttrList.addAll(ah.buildMonitor(ejbObj+" -A invocations -r "+ejb+".ejb.invocations"));
							eapAttrList.addAll(ah.buildMonitor(ejbObj+" -A peak-concurrent-invocations -r "+ejb+".ejb.invocations.cur"));
							eapAttrList.addAll(ah.buildMonitor(ejbObj+" -A pool-available-count -r "+ejb+".ejb.pool.avail"));
							eapAttrList.addAll(ah.buildMonitor(ejbObj+" -A pool-create-count -r "+ejb+".ejb.pool.create"));
							eapAttrList.addAll(ah.buildMonitor(ejbObj+" -A pool-current-size -r "+ejb+".ejb.pool.cur"));
							eapAttrList.addAll(ah.buildMonitor(eapAttrList, ejbObj+" -A pool-current-size -r "+ejb+".ejb.pool.cur.percent -calc /"+ejb+".ejb.pool.avail -calc *100 -w 95"));
							eapAttrList.addAll(ah.buildMonitor(ejbObj+" -A wait-time -r "+ejb+".ejb.wait.time"));
						}
						
					}
				}
			}
			List<String> subsystems = ah.getChildResourceNames(obj, "subsystem",true,false);
			for(String subsystem : subsystems) {
					if (subsystem.matches("web")){
						String webObj = "-O "+obj+"/subsystem="+subsystem;
						//String arg = webObj+" -A active-sessions -r "+subdeployment+".act.sess";
						eapAttrList.addAll(ah.buildMonitor(webObj+" -A active-sessions -r "+app+".web.act.sess"));
						eapAttrList.addAll(ah.buildMonitor(webObj+" -A expired-sessions -r "+app+".web.exp.sess"));
						eapAttrList.addAll(ah.buildMonitor(webObj+" -A rejected-sessions -r "+app+".web.reject.sess"));
						eapAttrList.addAll(ah.buildMonitor(webObj+" -A session-avg-alive-time -r "+app+".web.avg.sess.alive"));
						eapAttrList.addAll(ah.buildMonitor(webObj+" -A session-max-alive-time -r "+app+".web.max.sess.alive"));
					}
			}
		}
		if(eapAttrList.isEmpty()){
			Map<String,String> eapAttr = new HashMap <String,String>();
			int status = RETURN_WARNING;
			eapAttr.put("Attribute", "Deployments");
			eapAttr.put("Value", "None");
			//eapAttr.put("perfData", "Deployments=0");
			eapAttr.put("status",String.valueOf(status));
			eapAttr.put("statusDesc",WARNING_STRING);
			eapAttrList.add(eapAttr);
		}
		return checkAttrList(eapAttrList);
    }
    
    public int getJvmHealth(eapClient client) throws Exception {
    	customMonitor ah = new customMonitor(client); 	
    	String memObj = "-O /core-service=platform-mbean/type=memory";
    	eapAttrList.addAll(ah.buildMonitor(memObj+" -A heap-memory-usage -C max -r TotalMemory"));
    	eapAttrList.addAll(ah.buildMonitor(eapAttrList, memObj+" -A heap-memory-usage -C used -calc /TotalMemory -calc *100 -r UsedMemory% -w 95"));
    	eapAttrList.addAll(ah.buildMonitor(eapAttrList, memObj+" -A non-heap-memory-usage -C used -r NonHeap"));
    	
		memObj = "/core-service=platform-mbean/type=memory-pool";
		List<String> mPools = ah.getChildResourceNames(memObj, "name", true, false);
		for (String pool : mPools){
			String pObj = "-O " +memObj+ "/name=" +pool;
			if (pool.contains("Perm_Gen"))
				eapAttrList.addAll(ah.buildMonitor(eapAttrList, pObj+" -A usage -C used -r PermGen"));
		}
		for (String pool : mPools){
			String pObj = "-O " +memObj+ "/name=" +pool;
			if (pool.contains("Old_Gen"))
				eapAttrList.addAll(ah.buildMonitor(eapAttrList, pObj+" -A usage -C used -r OldGen"));
		}
		for (String pool : mPools){
			String pObj = "-O " +memObj+ "/name=" +pool;
			if (pool.contains("Survivor_Space"))
				eapAttrList.addAll(ah.buildMonitor(eapAttrList, pObj+" -A usage -C used -r Survivor"));
		}
		for (String pool : mPools){
			String pObj = "-O " +memObj+ "/name=" +pool;
			if (pool.contains("Eden_Space"))
				eapAttrList.addAll(ah.buildMonitor(eapAttrList, pObj+" -A usage -C used -r Eden"));
		}
		
		memObj = "-O /core-service=platform-mbean/type=class-loading";
		eapAttrList.addAll(ah.buildMonitor(eapAttrList, memObj+" -A loaded-class-count -r LoadedClassCount"));	
		
		String threadObj = "-O /core-service=platform-mbean/type=threading";
		eapAttrList.addAll(ah.buildMonitor(eapAttrList, threadObj+" -A thread-count -r liveThreads"));
		eapAttrList.addAll(ah.buildMonitor(eapAttrList, threadObj+" -A peak-thread-count -r liveThreadsMax"));
		eapAttrList.addAll(ah.buildMonitor(eapAttrList, threadObj+" -A daemon-thread-count -r DaemonThreads"));
		eapAttrList.addAll(ah.buildMonitor(eapAttrList, threadObj+" -A current-thread-cpu-time -r threadCpu"));
		
		
		return checkAttrList(eapAttrList);
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

