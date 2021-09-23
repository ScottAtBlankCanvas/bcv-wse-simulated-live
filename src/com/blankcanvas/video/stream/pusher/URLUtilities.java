package com.blankcanvas.video.stream.pusher;

import java.util.*;

import com.wowza.util.*;

public class URLUtilities {

	// TODO: Move to BCV utility module
		public static Map<String, String> parseQueryString(String url0) {
			Map<String, String> ret = new LinkedHashMap<String, String>();
			Map qsm = URLUtils.parseQueryStr(url0, false);
			Set keys = qsm.keySet();
			for (Object k : keys) {
				List val = (List)qsm.get(k);
				ret.put(k.toString(), val.get(0).toString());
	//			System.out.println(ret);
			}
			return ret;
		}

}
