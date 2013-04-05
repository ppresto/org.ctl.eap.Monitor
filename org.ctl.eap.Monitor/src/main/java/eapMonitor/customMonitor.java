package eapMonitor;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jboss.dmr.*;

public class customMonitor {
	private eapClient client = null;
	public customMonitor(eapClient c){
		this.client = c;
	}
	
	public List<String> getChildResourceNames(String appObject, String propertyList) throws Exception {
    	List<String> appList = new ArrayList <String>();
    	try {	
    		List<Property> objects = client.getObjectList(appObject, propertyList, false, false);
    		if(objects != null){
	    		for (Property obj : objects){
	    				appList.add(obj.getName());
	    		}
    		}
			return appList;
    	} catch (Exception e){
    		e.printStackTrace();
    		return appList;
    	} 	
    }
	
	public List<String> getChildResourceNames(String appObject, String propertyList, boolean runtime, boolean recursive) throws Exception {
    	List<String> appList = new ArrayList <String>();
    	try {	
    		List<Property> objects = client.getObjectList(appObject, propertyList, runtime, recursive);
    		if(objects != null){
	    		for (Property obj : objects){
	    				appList.add(obj.getName());
	    		}
    		}
    		
			return appList;
    	} catch (Exception e){
    		e.printStackTrace();
    		return appList;
    	} 	
    }
	
public List<Map<String,String>> buildMonitor(String object) throws Exception, InterruptedException {
    	Map<String,String> appAttr = new HashMap <String,String>();
    	List<Map<String,String>> appList = new ArrayList <Map<String,String>>();
    	eapMonitor monitor = null;
    	try {
    		monitor = new eapMonitor(false);
			String monArgs = object;
			monitor.parseArgs(monArgs.split(" "));
			appAttr = client.getAttribute(monitor.getObject(), monitor.getAttribute(), monitor.getAttributeKey(), monitor.getSamples(), true, true);
			appList.add(monitor.validate(appAttr));
			return appList;
    	} catch (IOException e){
    		e.printStackTrace();
    		return appList;
    	} catch (InterruptedException e) {
    		e.printStackTrace();
    		return appList;
    	}	
    }

public List<Map<String,String>> buildMonitor(List<Map<String,String>> eapAttributeList, String object) throws Exception, InterruptedException {
	Map<String,String> appAttr = new HashMap <String,String>();
	List<Map<String,String>> appList = new ArrayList <Map<String,String>>();
	eapMonitor monitor = null;
	try {
		monitor = new eapMonitor(eapAttributeList);
		String monArgs = object;
		monitor.parseArgs(monArgs.split(" "));
		appAttr = client.getAttribute(monitor.getObject(), monitor.getAttribute(), monitor.getAttributeKey(), monitor.getSamples(), true, true);
		appList.add(monitor.validate(appAttr));
		return appList;
	} catch (IOException e){
		e.printStackTrace();
		return appList;
	} catch (InterruptedException e) {
		e.printStackTrace();
		return appList;
	}	
}
}