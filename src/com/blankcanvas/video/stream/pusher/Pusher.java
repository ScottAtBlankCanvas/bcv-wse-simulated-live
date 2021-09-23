package com.blankcanvas.video.stream.pusher;

import com.wowza.wms.application.*;
import com.wowza.wms.logging.*;
import com.wowza.wms.stream.publish.*;
import com.wowza.wms.vhost.*;

class Pusher {
	static WMSLogger logger = HTTPProviderStreamPusher.logger;

	Stream streamPublisher;

	Command cmd;	
	// WSE
	IApplication app;
	IApplicationInstance appInst;
	IVHost vhost;
	
	public Pusher(IVHost vhost, Command cmd) {
		this.vhost = vhost;
		this.cmd = cmd;
		this.app = vhost.getApplication(cmd.appName);
		this.appInst = app.getAppInstance(cmd.appInstName);
	}
	
	public boolean startPublishing() {
		boolean success = false;
		
		try  {
			String streamType = appInst.getStreamType();

			streamPublisher = Stream.createInstance(vhost, cmd.appName, cmd.appInstName, cmd.streamName, streamType);
			if (streamPublisher != null) {

				// set to false if want repeat
				streamPublisher.setUnpublishOnEnd(true);
				// set to true if want repeat
				streamPublisher.setRepeat(false);

				streamPublisher.setSendOnMetadata(true);

				// file, start, length (TODO), reset
				success = streamPublisher.play("mp4:"+cmd.fileName, 0, cmd.duration, true);
			}
		}
		catch (Exception e) {
			logger.error(".startPublishing ", e);

			success = false;
		}

		return success;		
	}

	public boolean stopPublishing() {
		boolean success = false;

		if (streamPublisher == null)
			return false;

		try
		{
			streamPublisher.closeAndWait();
			streamPublisher = null;
			success = true;
		}
		catch (Exception e)
		{
			success = false;
			logger.error(".stopPublishing ", e);
		}
		return success;
		
	}


}