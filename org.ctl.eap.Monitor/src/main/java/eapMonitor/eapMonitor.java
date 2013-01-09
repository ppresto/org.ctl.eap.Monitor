package eapMonitor;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.OperationsException;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.remote.JMXConnector;

import java.io.*;
import java.text.DecimalFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

/* 
 * @author Patrick.Presto@centurylink.com
 * 
 */
public class eapMonitor
{
	private String url;
	private int verbatim, port;
	private JMXConnector connector;
	private MBeanServerConnection connection;
	private String warning, critical;
	private String attributeName, attributeKey, replaceName, useAttr, webConnector = null;
	private Long totalTime;
    private String methodName;
	private String object;
	private String username, password, addr, securityRealmName;
	private List<String> additionalArgs = new ArrayList<String>();
	private List <String>calc = new ArrayList<String>();
	public String [] samples;
	private boolean disableWarnCrit = false;
	private boolean datasources, appHealth, transactions, messaging, jvmHealth = false;
	private boolean argCheck = true;
	List<Map<String,String>> eapAttrList = new ArrayList <Map<String,String>>();
    private Object defaultValue;
    private static final int RETURN_OK = 0; // 	 The plugin was able to check the service and it appeared to be functioning properly
	private static final String OK_STRING = "EAP OK -"; 
	private static final int RETURN_WARNING = 1; // The plugin was able to check the service, but it appeared to be above some "warning" threshold or did not appear to be working properly
	private static final String WARNING_STRING = "EAP WARNING -"; 
	private static final int RETURN_CRITICAL = 2; // The plugin detected that either the service was not running or it was above some "critical" threshold
	private static final String CRITICAL_STRING = "EAP CRITICAL -"; 
	private static final int RETURN_UNKNOWN = 3; // Invalid command line arguments were supplied to the plugin or low-level failures internal to the plugin (such as unable to fork, or open a tcp socket) that prevent it from performing the specified operation. Higher-level errors (such as name resolution errors, socket timeouts, etc) are outside of the control of plugins and should generally NOT be reported as UNKNOWN states.
	private static final String UNKNOWN_STRING = "EAP UNKNOWN";
	
	private Object checkData;
	private Object infoData;
	//List<Map<String,String>> eapAttrList = new ArrayList <Map<String,String>>();
	private ModelControllerClient client = null;

	public eapMonitor() throws Exception {
	}
	public eapMonitor(boolean check) throws Exception {
		argCheck = check;
	}
	public eapMonitor(eapMonitor monitor){
		argCheck = false;
		object = monitor.getObject();
		disableWarnCrit = monitor.getdisableWarnCrit();
		warning = monitor.getWarning();
		critical = monitor.getCritical();
	}
	public eapMonitor(eapMonitor monitor, List<Map<String,String>> eapAttributesList){
		argCheck = false;
		object = monitor.getObject();
		disableWarnCrit = monitor.getdisableWarnCrit();
		warning = monitor.getWarning();
		critical = monitor.getCritical();
		eapAttrList = eapAttributesList;
	}
	public eapMonitor(List<Map<String,String>> eapAttributesList){
		argCheck = false;
		eapAttrList = eapAttributesList;
	}

	public void parseArgs(String [] args) throws Exception {
			for(int i=0;i<args.length;i++){
				String option = args[i];
				if(option.equals("-help"))
				{
					printHelp();
					System.exit(RETURN_UNKNOWN);
				}
                else if(option.equals("-host")) {
					addr = args[++i];
				}
                else if(option.equals("-port")) {
					port = Integer.parseInt(args[++i]);
				}
                else if(option.equals("-realm")) {
                	securityRealmName = args[++i];
				}
                else if(option.equals("-u")) {
					username = args[++i];
				}
                else if(option.equals("-p")) {
					password = args[++i];
				}
                else if(option.equals("-O")) {
                	object = args[++i];
				}
                else if(option.equals("-A")) {
					attributeName = args[++i];
				}
                else if(option.equals("-C")) {
					attributeKey = args[++i];
				}
                else if(option.equals("-w")) {
					warning = args[++i];
				}
                else if(option.equals("-c")) {
					critical = args[++i];
				}
                else if(option.equals("-x")) {
                	disableWarnCrit = true;
				}
                else if(option.equals("-r")) {
                	replaceName = args[++i];
                }
                else if(option.equals("-samples")) {
                	samples = args[++i].split(",");
                }
                else if(option.equals("-calc")) {
                	calc.add(args[++i]);
                }
                else if(option.equals("-add")) {
                	additionalArgs.add(args[++i]);
                }
                else if(option.equals("-jvmhealth")) {
                	jvmHealth = true;
                	argCheck = false;
                }
                else if(option.equals("-datasources")) {
                	datasources = true;
                	argCheck = false;
                }
                else if(option.equals("-apphealth")) {
                	appHealth = true;
                	argCheck = false;
                }
                else if(option.equals("-transactions")) {
                	transactions = true;
                	argCheck = false;
                }
                else if(option.equals("-messaging")) {
                	messaging = true;
                	argCheck = false;
                }
                else if(option.equals("-connector")) {
                	webConnector = args[++i];
                	argCheck = false;
                }
			}
            if ( argCheck ) {
            	if( addr == null || securityRealmName == null || (username == null && password == null)){
            		argCheck = false;
            		printHelp();
	            	throw new Exception("New Monitor - required options are missing");
            	}
            }
		}
	
	private void printHelp() {
		InputStream is = eapMonitor.class.getClassLoader().getResourceAsStream("resources/eapMonitor/HELP");
		BufferedReader reader = new BufferedReader(new InputStreamReader(is));
		StringBuilder help = new StringBuilder();
		try{
			while(true){
				String s = reader.readLine();
				//System.out.println(s);
				if(s==null)
					break;
				help.append(s +"\n");
			}
		} catch (IOException e) {
			help.append(e);
		}finally{
			try {
				reader.close();
			} catch (IOException e) {
				help.append(e);
			}
			System.out.print(help.toString());
		}	
	}

	public String getHost() {
		return addr;
	}
	public Integer getPort() {
		return port;
	}
	public String getRealm() {
		return securityRealmName;
	}
	public String getUserName() {
		return username;
	}
	public String getPassword() {
		return password;
	}
	public String getObject() {
		return object;
	}
	public String getAttribute() {
		return attributeName;
	}
	public String getAttributeKey() {
		return attributeKey;
	}
	public boolean getdisableWarnCrit() {
		return disableWarnCrit;
	}
	public String getValue() {
		return checkData.toString();
	}
	public String getWarning() {
		return warning;	
	}
	public String getCritical() {
		return critical;
	}
	public void setWarning(String value) {
		warning = value;	
	}
	public void setCritical(String value) {
		critical = value;
	}
	public boolean monitorJvmHealth() {
		return jvmHealth;
	}
	public boolean monitorDataSources() {
		return datasources;
	}
	public boolean monitorAppHealth() {
		return appHealth;
	}
	public boolean monitorTransactions() {
		return transactions;
	}
	public boolean monitorMessaging() {
		return messaging;
	}
	public String monitorConnector() {
		return webConnector;
	}
	public List<String> searchForAdditionalMonitors () {
		return additionalArgs;
	}
	public String [] getSamples() {
		return samples;
	}
	public String getReplaceName() {
		return replaceName;
	}
	public Map<String,String> replaceName(Map<String,String> map, String newName) {
		map.put("Attribute", newName);
		return map;
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
	
	private boolean compare(String level, Object checkData) {
		if (warning != null && critical != null){
			if(isNumeric(checkData.toString()) && Double.parseDouble(warning) > Double.parseDouble(critical) ) {
				Number check = Double.parseDouble(checkData.toString());
				if(check.doubleValue()==Math.floor(check.doubleValue())) {
					return check.doubleValue()<=Double.parseDouble(level);
				} else {
					return check.longValue()>=Long.parseLong(level);
				}
		}
			if(isNumeric(checkData.toString()) && Double.parseDouble(warning) < Double.parseDouble(critical) ) {
				Number check = Double.parseDouble(checkData.toString());
				if(check.doubleValue()==Math.floor(check.doubleValue())) {
					return check.doubleValue()>=Double.parseDouble(level);
				} else {
					return check.longValue()>=Long.parseLong(level);
				}
			}
		}
			if(isNumeric(checkData.toString()) ) {
				Number check = Double.parseDouble(checkData.toString());
				if(check.doubleValue()==Math.floor(check.doubleValue())) {
					return check.doubleValue()>=Double.parseDouble(level);
				} else {
					return check.longValue()>=Long.parseLong(level);
				}
			}
			if(checkData instanceof String) {
				return checkData.equals(level);
			}
			if(checkData instanceof Boolean) {
				return checkData.equals(Boolean.parseBoolean(level));
			}
			throw new RuntimeException(level + "is not of type Number,String or Boolean");
		}
	
	public Map<String, String> validate(Map<String,String> map){
		int status;
		String thresholds;
		Map<String,String> eapAttr = map;
		checkData = eapAttr.get("Value");
		if (!calc.isEmpty() && isNumeric(checkData.toString())){
			for ( String c : calc){
	    		String operator = (String) c.subSequence(0,1);
	    		String expr = c.substring(1);
	    		if (isNumeric(expr)){
	    			checkData = calc(checkData,operator,expr);
	    		}
	    		else
	    			checkData = calc(checkData,operator,findAttrValue(expr));
			}
			eapAttr.put("Value", checkData.toString());
    	}	
		if(critical != null && compare( critical, checkData)){
			status = RETURN_CRITICAL;	
			eapAttr.put("status",String.valueOf(status));
			eapAttr.put("statusDesc",CRITICAL_STRING);
		}else if (warning != null && compare( warning, checkData)){
			status = RETURN_WARNING;
			eapAttr.put("status",String.valueOf(status));
			eapAttr.put("statusDesc",WARNING_STRING);
		}else{
			status = RETURN_OK;
			eapAttr.put("status",String.valueOf(status));
			eapAttr.put("statusDesc",OK_STRING);
		}
		if (!disableWarnCrit){
            if (warning != null){thresholds = ";"+ warning;} else{thresholds = ";";}
            if (critical != null){thresholds = thresholds +";"+ critical;} else{thresholds = thresholds +";";}
    	} else {
    		//append nothing for warning/critical thresholds to perf data.
    		thresholds = ";;";
    	}
		if (replaceName != null){
			replaceName(eapAttr, replaceName);
    	} 
            eapAttr.put("perfData", eapAttr.get("Attribute") +"="+ eapAttr.get("Value")+thresholds+";;");
		return eapAttr;
	}
	
	public static boolean isNumeric(String str) throws NumberFormatException {  
		  try {  
		    double d = Double.parseDouble(str);  
		  }  
		  catch(NumberFormatException nfe) {  
		    return false;  
		  }  
		  return true;  
		}
	
	public Object calc(Object checkData, String operator, String number){
		double newData = 0;
		if(operator.equals("/")){
			newData = Double.parseDouble(checkData.toString()) / Double.parseDouble(number);  
		} else if(operator.equals("*")) {
			newData = Double.parseDouble(checkData.toString()) * Double.parseDouble(number);
		} else if(operator.equals("-")) {
			newData = Double.parseDouble(checkData.toString()) - Double.parseDouble(number); 
		} else if(operator.equals("+")) {
			newData = Double.parseDouble(checkData.toString()) + Double.parseDouble(number); 
		} else newData = 0;
		return (Object)Double.parseDouble(decimalPlaces((float)newData));
	}
	public String findAttrValue(String attribute) {
		for (Map<String, String> a: eapAttrList){
		    for (Map.Entry<String, String> entry : a.entrySet()){
	    		if (entry.getKey().equals("Attribute") && entry.getValue().equals(attribute)){
	    			return(a.get("Value"));
	    		}
	    	}
    	}
		return "0";
	}
}

