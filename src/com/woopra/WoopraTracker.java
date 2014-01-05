package com.woopra;

import java.net.URLEncoder;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import android.content.Context;
import android.util.Log;

/**
 * @author Woopra on 1/26/2013
 * 
 */
public class WoopraTracker {

	public static String LOG_TAG = "WoopraTracker";
	private static final String W_EVENT_ENDPOINT = "http://www.woopra.com/track/ce/";
	private static WoopraTracker gSingleton = null;
	private ExecutorService executor = null;
	private String domain = null;
	// default timeout value for Woopra service
	private int idleTimeout = 30;
	// ping
	private boolean pingEnabled = false;

	//
	private String referer = null;

	private WoopraVisitor visitor = null;
	private WoopraPing ping = null;

	private WoopraTracker() {
	}

	public static synchronized WoopraTracker getInstance() {
		if (gSingleton == null) {
			gSingleton = new WoopraTracker();
			gSingleton.executor = Executors.newFixedThreadPool(1);
			gSingleton.setVisitor(WoopraVisitor.getAnonymousVisitor());
		}
		return gSingleton;
	}

	// public void resetVisitorByUniqueId(String uniqueId) {
	// gSingleton.setVisitor(WoopraVisitor.getVisitorByString(uniqueId));
	// }

	public void resetVisitorContext(Context context) {
		gSingleton.setVisitor(WoopraVisitor.getVisitorByContent(context));
	}

	public boolean trackEvent(WoopraEvent event) {
		if (getDomain() == null) {
			Log.i(LOG_TAG,
					"WTracker.domain property must be set before [WTracker trackEvent:] invocation. Ex.: tracker.domain = mywebsite.com");
			return false;
		}
		if (getVisitor() == null) {
			Log.i(LOG_TAG,
					"WTracker.visitor property must be set before [WTracker trackEvent:] invocation");
			return false;
		}
		// stop ping
		if (ping != null) {
			ping.stopPing();
		}
		EventRunner runner = new EventRunner(event);
		try {
			executor.execute(runner);
		} catch (Exception e) {
			return false;
		}
		return true;
	}

	private boolean trackEventIntra(WoopraEvent event) {
		// generate request url
		StringBuilder urlBuilder = new StringBuilder();
		urlBuilder.append(W_EVENT_ENDPOINT).append("?host=")
				.append(getDomain()).append("&cookie=")
				.append(getVisitor().getCookie())
				.append("&response=xml&os=android&browser=app&timeout=").append(idleTimeout);
		if (referer != null) {
			urlBuilder.append("&referer=").append(encodeUriComponent(referer));
		}
		//
		// Add visitors properties
		for (Entry<String, String> entry : visitor.getProperties().entrySet()) {
			urlBuilder.append("&cv_").append(encodeUriComponent(entry.getKey()))
					.append("=")
					.append(encodeUriComponent(entry.getValue()));
		}
		// Add Event properties
		for (Entry<String, String> entry : event.getProperties().entrySet()) {
			urlBuilder.append("&ce_").append(encodeUriComponent(entry.getKey()))
					.append("=")
					.append(encodeUriComponent(entry.getValue()));
		}
		Log.d(LOG_TAG, "Final url:" + urlBuilder.toString());

		HttpClient httpClient = new DefaultHttpClient();
		HttpGet httpGet = new HttpGet(urlBuilder.toString());
		try {
			HttpResponse response = httpClient.execute(httpGet);
			Log.d(LOG_TAG,
					"Response:" + EntityUtils.toString(response.getEntity()));
		} catch (Exception e) {
			Log.e(LOG_TAG, "Got error!", e);
			return false;
		}
		// reset ping
		// if (pingEnabled) {
		// resetPing(domain, getVisitor().getCookie(), idleTimeout);
		// }
		return true;
	}

	public String getDomain() {
		return domain;
	}

	public void setup(String domain) {
		this.domain = domain;
	}

	public int getIdleTimeout() {
		return idleTimeout;
	}

	public void setIdleTimeout(int idleTimeout) {
		this.idleTimeout = idleTimeout;
	}

	public boolean isPingEnabled() {
		return pingEnabled;
	}

	public void setPingEnabled(boolean newPingEnabled) {
		this.pingEnabled = newPingEnabled;
		if (newPingEnabled == false && ping != null) {
			ping.stopPing();
		}
	}
	
	public static String encodeUriComponent(String param) {
		try {
			return URLEncoder.encode(param, "utf-8");
		} catch (Exception e) {
			// will not throw an exception since utf-8 is supported.
		}
		return param;
	}

	/**
	 * This method must called after resetVisitorByContext and setIdleTimeout
	 * 
	 * @param newPingEnabled
	 */
	public void resetPing(String domain, String cookie, int idleTimeout) {
		if (ping != null) {
			ping.stopPing();
		}
		ping = new WoopraPing(domain, cookie, idleTimeout);
		ping.ping();
	}

	public WoopraVisitor getVisitor() {
		return visitor;
	}

	public void setVisitor(WoopraVisitor visitor) {
		this.visitor = visitor;
	}

	public String getReferer() {
		return referer;
	}

	public void setReferer(String referer) {
		this.referer = referer;
	}

	public void addVisitorProperty(String key, String value) {
	  if (value != null) {
	    getVisitor().addProperty(key, value);
	  }
	}

	public void addVisitorProperties(Map<String,String> newProperties) {
		getVisitor().addProperties(newProperties);
	}

	class EventRunner implements Runnable {
		WoopraEvent event = null;

		public EventRunner(WoopraEvent event) {
			this.event = event;
		}

		@Override
		public void run() {
			// send track event
			trackEventIntra(event);
			// send ping events
			if (pingEnabled) {
				ping = new WoopraPing(domain, getVisitor().getCookie(),
						idleTimeout);
				ping.ping();
			}
		}
	}
}
