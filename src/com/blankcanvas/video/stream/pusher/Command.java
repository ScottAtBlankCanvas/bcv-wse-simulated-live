package com.blankcanvas.video.stream.pusher;

import com.wowza.wms.application.*;

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