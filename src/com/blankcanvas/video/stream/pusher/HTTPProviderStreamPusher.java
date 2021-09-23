package com.blankcanvas.video.stream.pusher;

import java.io.*;
import java.util.*;

import com.wowza.util.*;
import com.wowza.wms.application.*;
import com.wowza.wms.http.*;
import com.wowza.wms.logging.*;
import com.wowza.wms.stream.publish.*;
import com.wowza.wms.vhost.*;

// TODO: if duration triggers unpublish, needs removed from list of pushers
// TODO: add a status action

@SuppressWarnings("serial")
public class HTTPProviderStreamPusher extends HTTProvider2Base {
	private static final String ACTION_STOP = "stop";
	private static final String ACTION_STOP_ALL = "stop_all";
	private static final String ACTION_START = "start";
	
	private static final String QS_KEY_ACTION = "action";
	private static final String QS_KEY_APP = "app";
	private static final String QS_KEY_NAME = "name";
	private static final String QS_KEY_FILE = "file";
	private static final String QS_KEY_DURATION = "duration";
	
	public static final String MODULE_NAME = HTTPProviderStreamPusher.class.getSimpleName();
	
	public static WMSLogger logger = WMSLoggerFactory.getLogger(HTTPProviderStreamPusher.class);	
	public static boolean debug = false;
	private static ArrayList<String> VALID_ACTIONS;
	
	private Map<String, Pusher> pushers = new LinkedHashMap<String, Pusher>();
	private IVHost vhost;

	static {
		VALID_ACTIONS = new ArrayList<String>() {{
	        add(ACTION_START);
	        add(ACTION_STOP);
	        add(ACTION_STOP_ALL);
	    }};
	}

	public void onBind(IVHost vh, HostPort hostPort)
	{
		super.onBind(vh, hostPort);
	}



	@Override
	public void onHTTPRequest(IVHost vh, IHTTPRequest req, IHTTPResponse resp) {
		this.vhost = vh;
		if (!doHTTPAuthentication(vhost, req, resp))
			return;

		String url = req.getHeader("context");
		if (url == null)
			return;


		String queryStr = req.getQueryString();

		Command cmd = parseQueryStringForParams(queryStr);
		
		ResponseAndErrors ret = verifyCommand(cmd);
		
		// No errors, do the command
		if (ret.errors.isEmpty()) {
			// perform commands
			if (ACTION_START.equals(cmd.action)) {
				ret = doStart(cmd);
			}
			else if (ACTION_STOP.equals(cmd.action)) {
				ret = doStop(cmd);
			}
			else if (ACTION_STOP_ALL.equals(cmd.action)) {
				ret = doStopAll(cmd);
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
	// URL and Command parsing
	//
	//
	
	private Command parseQueryStringForParams(String queryStr) {
		Command ret = new Command();
		Map<String, String> qsMap = URLUtilities.parseQueryString(queryStr);

		ret.action	= qsMap.get(QS_KEY_ACTION);
		ret.appName	= qsMap.get(QS_KEY_APP);
		ret.fileName = qsMap.get(QS_KEY_FILE);
		ret.streamName = qsMap.get(QS_KEY_NAME);
		
		String d = qsMap.get(QS_KEY_DURATION);
		try {
			if (! StringUtils.isEmpty(d)) {
				ret.duration = Integer.parseInt(d);
			}			
		} catch (Exception e) {
			ret.duration = Command.DURATION_UNLIMITED;
		}
		
		return ret;
	}

	private ResponseAndErrors verifyCommand(Command cmd) {
		ResponseAndErrors ret = new ResponseAndErrors();
		
		//
		// Do we have an action?
		//
		
		if (StringUtils.isEmpty(cmd.action)) {
			ret.errors.add(String.format("Action not specified with '%s' query param.  Valid: %s", QS_KEY_ACTION, VALID_ACTIONS.toString()));
		} else {
			if (! VALID_ACTIONS.contains(cmd.action))
				ret.errors.add(String.format("Action %s not valid.  Valid: %s", cmd.action, QS_KEY_ACTION));
		}

		// If action is ACTION_STOP_ALL, no need for more info
		if (ACTION_STOP_ALL.equals(cmd.action))	{
			return ret;
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
//			// TODO: Does file exist?
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
	
	
	
	private void reportFromErrors(Command cmd, ResponseAndErrors ret) {

		//
		// Now do the report from the errors
		//

		if (!ret.errors.isEmpty()) {
			ret.report = String.format("404 ERROR %s %s to %s/%s\n", cmd.action, cmd.fileName, cmd.appName, cmd.streamName);
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
			Pusher pusher = pushers.get(key);
			
			ret += String.format("- %s/%s       (%s)\n", pusher.cmd.appName, pusher.cmd.streamName, pusher.cmd.fileName);			
		}
		return ret;
	}



	
	
	//
	// 
	// Actions
	//
	//
	
	private ResponseAndErrors doStart(Command cmd) {
		ResponseAndErrors ret = new ResponseAndErrors();
		
		String id = cmd.appName + ":" +cmd.streamName;
		if (pushers.containsKey(id)) {
			ret.errors.add(String.format("%s: Push already exists: %s/%s\n", cmd.action, cmd.appName, cmd.streamName));
			return ret;
		}
		
		Pusher pusher = startPublishing(cmd);
		
		pushers.put(id, pusher);
		
		ret.report = String.format("%s: file:%s  %s/%s\n", cmd.action, cmd.fileName, cmd.appName, cmd.streamName);
		ret.report += reportActivePushers();
		
		return ret;
	}

	private ResponseAndErrors doStop(Command cmd) {
		ResponseAndErrors ret = new ResponseAndErrors();
		
		String id = cmd.appName + ":" +cmd.streamName;
		Pusher pusher = pushers.get(id);
		
		if (pusher == null) {
			ret.errors.add(String.format("%s: Push does not exist: %s/%s\n", cmd.action, cmd.appName, cmd.streamName));
			return ret;
		}
		
		stopPublishing(pusher);
		
		pushers.remove(id);

		
		ret.report = String.format("doStop: %s  %s/%s\n", cmd.action, cmd.appName, cmd.streamName);
		ret.report += reportActivePushers();
		
		return ret;
	}

	private ResponseAndErrors doStopAll(Command cmd) {
		ResponseAndErrors ret = new ResponseAndErrors();

		ret.report = String.format("%s: Stopped All Publishers: ", ACTION_STOP_ALL);
		
		Set<String> keys = pushers.keySet();
		for (String key : keys) {
			Pusher pusher = pushers.get(key);
			
			stopPublishing(pusher);
			
			ret.report += (key+", ");
 		}
		ret.report += "\n";
		
		pushers.clear();
		
		return ret;
	}

	
	private Pusher startPublishing(Command cmd) {
		Pusher pusher = new Pusher();
		pusher.cmd = cmd;
		pusher.app = vhost.getApplication(cmd.appName);
		pusher.appInst = pusher.app.getAppInstance(cmd.appInstName);

		try
		{
			String streamType = pusher.appInst.getStreamType();

			pusher.streamPublisher = Stream.createInstance(vhost, cmd.appName, cmd.appInstName, cmd.streamName, streamType);
			if (pusher.streamPublisher != null)
			{

				// set to false if want repeat
				pusher.streamPublisher.setUnpublishOnEnd(true);
				// set to true if want repeat
				pusher.streamPublisher.setRepeat(false);

				pusher.streamPublisher.setSendOnMetadata(true);

				// file, start, length (TODO), reset
				pusher.streamPublisher.play("mp4:"+cmd.fileName, 0, cmd.duration, true);
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		return pusher;		
	}

	public void stopPublishing(Pusher cmd)
	{
		if (cmd.streamPublisher == null)
			return;


		if (cmd.streamPublisher.getPublisher()== null || cmd.streamPublisher.getPublisher().getAppInstance()== null)
			logger.info(MODULE_NAME + ".stopPublishing: shutdown: " + cmd.streamPublisher.getName());
		else
			logger.info(MODULE_NAME + ".stopPublishing: shutdown: " + cmd.streamPublisher.getPublisher().getAppInstance().getContextStr() + "/" + cmd.streamPublisher.getName());

		try
		{
			cmd.streamPublisher.closeAndWait();
			cmd.streamPublisher = null;
		}
		catch (Exception e)
		{
			logger.info(MODULE_NAME + ".stopPublishing ", e);
		}
	}



	//
	// 
	// Internal classes
	//
	//
	
	class Command {
		static final int DURATION_UNLIMITED = -1;
		// config
		String action;
		String appName;
		String appInstName = IApplicationInstance.DEFAULT_APPINSTANCE_NAME;
		String fileName;
		String streamName;
		int duration = DURATION_UNLIMITED;
	}


	class Pusher {
		public Stream streamPublisher;

		Command cmd;
		
		// WSE
		IApplication app;
		IApplicationInstance appInst;
	}
	
	class ResponseAndErrors {
		String report = "";
		List<String> errors = new ArrayList<String>();
	}



}
