package com.blankcanvas.video.stream.pusher;

import java.io.*;
import java.util.*;

import com.wowza.util.*;
import com.wowza.wms.http.*;
import com.wowza.wms.logging.*;
import com.wowza.wms.vhost.*;

@SuppressWarnings("serial")
public class HTTPProviderStreamPusher extends HTTProvider2Base {
	private static final String ACTION_STOP = "stop";
	private static final String ACTION_START = "start";
	
	private static final String QS_KEY_ACTION = "action";
	private static final String QS_KEY_APP = "app";
	private static final String QS_KEY_NAME = "name";
	private static final String QS_KEY_FILE = "file";

	public static final String MODULE_NAME = HTTPProviderStreamPusher.class.getSimpleName();
	
	public static WMSLogger logger = WMSLoggerFactory.getLogger(HTTPProviderStreamPusher.class);	
	public static boolean debug = false;
	private static ArrayList<String> VALID_ACTIONS;
	
	private Map<String, Pusher> pushers = new LinkedHashMap<String, Pusher>();

	static {
		VALID_ACTIONS = new ArrayList<String>() {{
	        add(ACTION_START);
	        add(ACTION_STOP);
	    }};
	}

	public void onBind(IVHost vhost, HostPort hostPort)
	{
		super.onBind(vhost, hostPort);
	}



	@Override
	public void onHTTPRequest(IVHost vhost, IHTTPRequest req, IHTTPResponse resp) {
		if (!doHTTPAuthentication(vhost, req, resp))
			return;

		String url = req.getHeader("context");
		if (url == null)
			return;


		String queryStr = req.getQueryString();

		Pusher cmd = parseQueryStringForParams(queryStr);
		
		ResponseAndErrors ret = verifyCommand(vhost, cmd);
		
		if (ret.errors.isEmpty()) {
			// perform commands
			if (ACTION_START.equals(cmd.action)) {
				ret = doStart(cmd);
			}
			else if (ACTION_STOP.equals(cmd.action)) {
				ret = doStop(cmd);
			}
		}

		
		
		// HTTP Response
		try
		{
			if (! ret.errors.isEmpty()) {
				resp.setResponseCode(404);
				reportFromErrors(cmd, ret);
			}
			resp.setHeader("Content-Type", "text/plain");			
			OutputStream out = resp.getOutputStream();
			byte[] outBytes = ret.report.getBytes();
			out.write(outBytes);
		}
		catch (Exception e)
		{
			logger.error(MODULE_NAME+".onHTTPRequest ", e);
		}
		
	}

	
	//
	// 
	// Actions
	//
	//
	
	private ResponseAndErrors doStart(Pusher cmd) {
		ResponseAndErrors ret = new ResponseAndErrors();
		
		String id = cmd.appName + ":" +cmd.streamName;
		if (pushers.containsKey(id)) {
			ret.errors.add(String.format("%s: Push already exists: %s/%s\n", cmd.action, cmd.appName, cmd.streamName));
			return ret;
		}
		
		pushers.put(id, cmd);
		
		ret.report = String.format("%s: file:%s  %s/%s\n", cmd.action, cmd.fileName, cmd.appName, cmd.streamName);
		ret.report += reportActivePushers();
		
		return ret;
	}

	private ResponseAndErrors doStop(Pusher cmd) {
		ResponseAndErrors ret = new ResponseAndErrors();
		
		String id = cmd.appName + ":" +cmd.streamName;
		if (! pushers.containsKey(id)) {
			ret.errors.add(String.format("%s: Push does not exist: %s/%s\n", cmd.action, cmd.appName, cmd.streamName));
			return ret;
		}
		
		pushers.remove(id);

		
		ret.report = String.format("doStop: %s  %s/%s\n", cmd.action, cmd.appName, cmd.streamName);
		ret.report += reportActivePushers();
		
		return ret;
	}


	//
	// 
	// URL and Command parsing
	//
	//
	
	private Pusher parseQueryStringForParams(String queryStr) {
		Pusher ret = new Pusher();
		Map<String, String> qsMap = URLUtilities.parseQueryString(queryStr);

		ret.action	= qsMap.get(QS_KEY_ACTION);
		ret.appName	= qsMap.get(QS_KEY_APP);
		ret.fileName = qsMap.get(QS_KEY_FILE);
		ret.streamName = qsMap.get(QS_KEY_NAME);
		
		return ret;
	}

	private ResponseAndErrors verifyCommand(IVHost vhost, Pusher cmd) {
		ResponseAndErrors ret = new ResponseAndErrors();
		
		//
		// First get the errors
		//
		
		if (StringUtils.isEmpty(cmd.action)) {
			ret.errors.add(String.format("Action not specified with '%s' query param.  Valid: %s", QS_KEY_ACTION, VALID_ACTIONS.toString()));
		} else {
			if (! VALID_ACTIONS.contains(cmd.action))
				ret.errors.add(String.format("Action %s not valid.  Valid: %s", cmd.action, QS_KEY_ACTION));
		}

		
		if (StringUtils.isEmpty(cmd.appName)) {
			ret.errors.add(String.format("Application not specified with '%s' query param", QS_KEY_APP));
		} else if (vhost.getApplication(cmd.appName)==null) {
			ret.errors.add(String.format("Application '%s' does not exist", cmd.appName));		
		}


		if (StringUtils.isEmpty(cmd.streamName)) {
			ret.errors.add(String.format("Stream name not specified with '%s' query param", QS_KEY_NAME));
		}


		// file name is only required for start
		if (ACTION_START.equals(cmd.action)) {
			if (StringUtils.isEmpty(cmd.fileName)) {
				ret.errors.add(String.format("File not specified with '%s' query param", QS_KEY_FILE));
			} 
//			// Does file exist?
//			else if (vhost.getApplication(appName)==null) {
//				errors.add(String.format("Application '%s' does not exist", appName));
//				error = true;			
//			}
			
		}

		return ret;
	}

	
	//
	// 
	// Reporting
	//
	//
	
	
	
	private void reportFromErrors(Pusher cmd, ResponseAndErrors ret) {

		//
		// Now do the report from the errors
		//

		if (!ret.errors.isEmpty()) {
			ret.report = String.format("404 ERROR %s %s to %s/%s\n", cmd.action, cmd.fileName, cmd.appName,
					cmd.streamName);
			for (String err : ret.errors) {
				ret.report += ("\n" + err);
			}
			ret.report += "\n";
		}
		
		ret.report += reportActivePushers();

	}



	private String reportActivePushers() {
		String ret = "\nActivePushers:\n";
		if (pushers.isEmpty()) {
			ret += "- none\n";
			return ret;
		}
		Set<String> keys = pushers.keySet();
		for (String key : keys) {
			Pusher cmd = pushers.get(key);
			
			ret += String.format("- %s/%s       (%s)\n", cmd.appName, cmd.streamName, cmd.fileName);			
		}
		return ret;
	}



	
	//
	// 
	// Internal classes
	//
	//
	


	class Pusher {
		String action;
		String appName;
		String fileName;
		String streamName;
	}
	
	class ResponseAndErrors {
		String report = "";
		List<String> errors = new ArrayList<String>();
	}


}
