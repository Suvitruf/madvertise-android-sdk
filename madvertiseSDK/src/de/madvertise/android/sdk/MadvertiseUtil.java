/*
 * Copyright 2012 madvertise Mobile Advertising GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.madvertise.android.sdk;

import de.madvertise.android.sdk.MadvertiseView.MadvertiseViewCallbackListener;

import org.apache.http.Header;
import org.apache.http.NameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings.Secure;
import android.util.Log;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Utility class for the madvertise android SDK.
 */
public class MadvertiseUtil {

	static final String LOG = "MAD_LOG";
	static final String ENCODING = "UTF-8";
	static final int SECONDS_TO_REFRESH_LOCATION = 900;
	static final int SECONDS_TO_REFRESH_AD_DEFAULT = 45;
	static final boolean PRINT_LOG = true;
	static final String MAD_SERVER = "http://ad.madvertise.de";
	static final int CONNECTION_TIMEOUT = 5000;
	static final int TEXT_COLOR_DEFAULT = 0xffffffff;
	static final int BACKGROUND_COLOR_DEFAULT = 0x000000;
	static final String BANNER_TYPE_MEDIUM_RECTANGLE = "medium_rectangle";

	/** 320x53 */
	public static final String BANNER_TYPE_MMA = "mma";

	/** 728x90 */
	public static final String BANNER_TYPE_LEADERBOARD = "leaderboard";

	/** 768x768 */
	public static final String BANNER_TYPE_FULLSCREEN = "fullscreen";

	/** 766x66 */
	public static final String BANNER_TYPE_PORTRAIT = "portrait";

	/** 1024x66 */
	public static final String BANNER_TYPE_LANDSCAPE = "landscape";

	public static final String BANNER_TYPE_ALL = "all";
	public static final String BANNER_TYPE_RICH_MEDIA = "rich_media";
	public static final String BANNER_TYPE_RICH_MEDIA_SHORT = "rm";
	public static final String BANNER_TYPE_DEFAULT = BANNER_TYPE_MMA;
	static final String ANIMATION_TYPE_LEFT_TO_RIGHT = "left_to_right";
	static final String ANIMATION_TYPE_TOP_DOWN = "top_down";
	static final String ANIMATION_TYPE_FADE = "fade";
	static final String ANIMATION_TYPE_DEFAULT = ANIMATION_TYPE_FADE;
	static final boolean DELIVER_ONLY_TEXT_DEFAULT = false;
	static final int TEXT_SIZE_DEFAULT = 18;
	static final String AD_PROVIDER_TEXT = "ad by madvertise";
	static final int TEXT_SIZE_PROVIDER = 10;
	static final int LANDSCAPE_BANNER_WIDTH = 1024;
	static final int PORTRAIT_BANNER_WIDTH = 766;
	static final int LEADERBOARD_BANNER_WIDTH = 728;
	static final int FULLSCREEN_BANNER_WIDTH = 768;
	static final int MMA_BANNER_WIDTH = 320;
	static final int MEDIUM_RECTANGLE_BANNER_WIDTH = 300;
	static final int BANNER_WIDTH_DEFAULT = MMA_BANNER_WIDTH;
	static final int LANDSCAPE_BANNER_HEIGHT = 66;
	static final int PORTRAIT_BANNER_HEIGHT = 66;
	static final int LEADERBOARD_BANNER_HEIGHT = 90;
	static final int FULLSCREEN_BANNER_HEIGHT = 768;
	static final int MMA_BANNER_HEIGHT = 53;
	static final int MEDIUM_RECTANGLE_BANNER_HEIGHT = 250;
	static final int BANNER_HEIGHT_DEFAULT = MMA_BANNER_HEIGHT;
	public static final int PLACEMENT_TYPE_INLINE = 0;
	public static final int PLACEMENT_TYPE_INTERSTITIAL = 1;
	private static final String MADVERTISE_SITE_TOKEN = "madvertise_site_token";
	private static String sUA;
	private static long sLocationUpdateTimestamp = 0;
	private static Location sCurrentLocation = null;

	public enum HashType {
		MD5, SHA1
	}

	public static String getHashedAndroidID(Context context, HashType hashType) {
		String id = Secure.getString(context.getApplicationContext()
				.getContentResolver(), Secure.ANDROID_ID);
		if (id == null) {
			id = "";
		} else {
			id = MadvertiseUtil.getHash(id, hashType);
		}
		return id;
	}

	public static String getHashedMacAddress(Context context, HashType hashType) {
		String mac = null;

		if (MadvertiseUtil.checkPermissionGranted(
				android.Manifest.permission.ACCESS_WIFI_STATE, context)) {
			WifiManager wm = (WifiManager) context
					.getSystemService(Context.WIFI_SERVICE);
			mac = wm.getConnectionInfo().getMacAddress();
		}

		if (mac == null) {
			mac = "";
		} else {
			mac = MadvertiseUtil.getHash(mac, hashType);
		}

		return mac;
	}

	/**
	 * Returns the madvertise token
	 * 
	 * @param context
	 *            application context
	 * @return madvertise_token from AndroidManifest.xml or null
	 */
	public static String getToken(final Context context,
			MadvertiseViewCallbackListener listener) {
		String madvertiseToken = null;

		PackageManager packageManager = context.getPackageManager();
		try {
			ApplicationInfo applicationInfo = packageManager
					.getApplicationInfo(context.getPackageName(),
							PackageManager.GET_META_DATA);
			madvertiseToken = applicationInfo.metaData
					.getString(MADVERTISE_SITE_TOKEN);
		} catch (Exception e) {
			e.printStackTrace();
		}

		if (madvertiseToken == null) {
			MadvertiseUtil
					.logMessage(null, Log.DEBUG,
							"Could not fetch \"madvertise_site_token\" from AndroidManifest.xml");
			if (listener != null) {
				listener.onError(new IllegalArgumentException(
						"Could not fetch \"madvertise_site_token\" from AndroidManifest.xml"));
			}
		}

		return madvertiseToken;
	}

	public static String getApplicationName(final Context context) {
		String appName = "";

		PackageManager packageManager = context.getPackageManager();
		try {
			ApplicationInfo applicationInfo = packageManager
					.getApplicationInfo(context.getPackageName(),
							PackageManager.GET_META_DATA);
			appName = packageManager.getApplicationLabel(applicationInfo)
					.toString();
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}

		return appName;
	}

	public static String getApplicationVersion(final Context context) {
		String appVersion = "";

		PackageManager packageManager = context.getPackageManager();
		try {
			PackageInfo packageInfo = packageManager.getPackageInfo(
					context.getPackageName(), 0);
			appVersion = packageInfo.versionName;
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}

		return appVersion;
	}

	/**
	 * Fetch the address of the enabled interface
	 * 
	 * @return ip address as string
	 */
	public static String getLocalIpAddress(
			MadvertiseViewCallbackListener listener) {
		try {
			for (Enumeration<NetworkInterface> en = NetworkInterface
					.getNetworkInterfaces(); en.hasMoreElements();) {
				NetworkInterface intf = en.nextElement();
				for (Enumeration<InetAddress> enumIpAddr = intf
						.getInetAddresses(); enumIpAddr.hasMoreElements();) {
					InetAddress inetAddress = enumIpAddr.nextElement();
					if (!inetAddress.isLoopbackAddress()) {
						if (inetAddress instanceof Inet4Address) {
							return inetAddress.getHostAddress().toString();
						}
					}
				}
			}
		} catch (SocketException e) {
			if (listener != null) {
				listener.onError(e);
			}
			e.printStackTrace();
		}

		if (listener != null) {
			listener.onError(new IllegalArgumentException(
					"Couldn't obtain the local ip address"));
		}
		return "";
	}

	/**
	 * Returns the MD5 hash for a string.
	 * 
	 * @param input
	 * @return md5 hash
	 */
	public synchronized static String getHash(final String input,
			HashType hashType) {
		MessageDigest messageDigest = null;

		try {
			messageDigest = MessageDigest.getInstance(hashType.toString());
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			MadvertiseUtil.logMessage(null, Log.DEBUG,
					"Could not create hash value");
			return "";
		}
		messageDigest.update(input.getBytes());
		String temp = null;
		byte[] digest = messageDigest.digest();
		StringBuffer hexString = new StringBuffer();
		for (int i = 0; i < digest.length; i++) {
			temp = Integer.toHexString(0xFF & digest[i]);
			if (temp.length() < 2) {
				temp = "0" + temp;
			}
			hexString.append(temp);
		}

		return hexString.toString();
	}

	/**
	 * Print all header parameters, just for logging purpose
	 * 
	 * @param headers
	 *            header object
	 * @return all headers concatenated
	 */
	public static String getAllHeadersAsString(final Header[] headers) {
		String returnString = "";
		for (int i = 0; i < headers.length; i++) {
			returnString += "<< " + headers[i].getName() + " : "
					+ headers[i].getValue() + " >>";
		}
		return returnString;
	}

	/**
	 * converts a stream to a string
	 * 
	 * @param inputStream
	 *            stream from the http connection with the ad server
	 * @return json string from the ad server
	 */
	public static String convertStreamToString(final InputStream inputStream) {
		BufferedReader bufferedReader = new BufferedReader(
				new InputStreamReader(inputStream));
		StringBuilder stringBuilder = new StringBuilder();
		String line = null;
		try {
			while ((line = bufferedReader.readLine()) != null) {
				stringBuilder.append(line + "\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				inputStream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return stringBuilder.toString();
	}

	public static Location getLocation() {
		return sCurrentLocation;
	}

	/**
	 * Try to update current location. Non blocking call.
	 * 
	 * @param context
	 *            application context
	 */
	public static void refreshCoordinates(final Context context) {
		MadvertiseUtil
				.logMessage(null, Log.DEBUG, "Trying to refresh location");

		if (context == null) {
			MadvertiseUtil.logMessage(null, Log.DEBUG,
					"Context not set - quit location refresh");
			return;
		}

		// check if we need a regular update
		if ((sLocationUpdateTimestamp + MadvertiseUtil.SECONDS_TO_REFRESH_LOCATION * 1000) > System
				.currentTimeMillis()) {
			MadvertiseUtil.logMessage(null, Log.DEBUG,
					"It's not time yet for refreshing the location");
			return;
		}

		synchronized (context) {
			// recheck, if location was updated by another thread while we
			// paused
			if ((sLocationUpdateTimestamp + MadvertiseUtil.SECONDS_TO_REFRESH_LOCATION * 1000) > System
					.currentTimeMillis()) {
				MadvertiseUtil.logMessage(null, Log.DEBUG,
						"Another thread updated the loation already");
				return;
			}

			boolean permissionCoarseLocation = MadvertiseUtil
					.checkPermissionGranted(
							android.Manifest.permission.ACCESS_COARSE_LOCATION,
							context);
			boolean permissionFineLocation = MadvertiseUtil
					.checkPermissionGranted(
							android.Manifest.permission.ACCESS_FINE_LOCATION,
							context);

			// return (null) if we do not have any permissions
			if (!permissionCoarseLocation && !permissionFineLocation) {
				MadvertiseUtil.logMessage(null, Log.DEBUG,
						"No permissions for requesting the location");
				return;
			}

			// return (null) if we can't get a location manager
			LocationManager locationManager = (LocationManager) context
					.getSystemService(Context.LOCATION_SERVICE);
			if (locationManager == null) {
				MadvertiseUtil.logMessage(null, Log.DEBUG,
						"Unable to fetch a location manger");
				return;
			}

			String provider = null;
			Criteria criteria = new Criteria();
			criteria.setCostAllowed(false);

			// try to get coarse location first
			if (permissionCoarseLocation) {
				criteria.setAccuracy(Criteria.ACCURACY_COARSE);
				provider = locationManager.getBestProvider(criteria, true);
			}

			// try to get gps location if coarse locatio did not work
			if (provider == null && permissionFineLocation) {
				criteria.setAccuracy(Criteria.ACCURACY_FINE);
				provider = locationManager.getBestProvider(criteria, true);
			}

			// still no provider, return (null)
			if (provider == null) {
				MadvertiseUtil.logMessage(null, Log.DEBUG,
						"Unable to fetch a location provider");
				return;
			}

			// create a finalized reference to the location manager, in order to
			// access it in the inner class
			final LocationManager finalizedLocationManager = locationManager;
			sLocationUpdateTimestamp = System.currentTimeMillis();
			locationManager.requestLocationUpdates(provider, 0, 0,
					new LocationListener() {
						@Override
						public void onLocationChanged(Location location) {
							MadvertiseUtil.logMessage(null, Log.DEBUG,
									"Refreshing location");
							sCurrentLocation = location;
							sLocationUpdateTimestamp = System
									.currentTimeMillis();
							// stop draining battery life
							finalizedLocationManager.removeUpdates(this);
						}

						// not used yet
						@Override
						public void onProviderDisabled(String provider) {
						}

						@Override
						public void onProviderEnabled(String provider) {
						}

						@Override
						public void onStatusChanged(String provider,
								int status, Bundle extras) {
						}
					}, context.getMainLooper());
		}
	}

	public static String printRequestParameters(
			final List<NameValuePair> parameterList) {
		StringBuilder stringBuilder = new StringBuilder();
		Iterator<NameValuePair> nameValueIterator = parameterList.iterator();
		while (nameValueIterator.hasNext()) {
			NameValuePair pair = nameValueIterator.next();
			stringBuilder.append(pair.getName() + "=" + pair.getValue() + "\n");
		}

		return stringBuilder.toString();
	}

	/**
	 * Generate a User-Agent used in HTTP request to pick an ad. Source used
	 * from Android source code
	 * "frameworks/base/core/java/android/webkit/WebSettings.java"
	 * 
	 * @return
	 */
	public static String getUA() {
		if (sUA != null)
			return sUA;

		StringBuffer arg = new StringBuffer();

		final String version = Build.VERSION.RELEASE;
		if (version.length() > 0) {
			arg.append(version);
		} else {
			arg.append("1.0");
		}
		arg.append("; ");

		final Locale l = Locale.getDefault();
		final String language = l.getLanguage();
		if (language != null) {
			arg.append(language.toLowerCase());
			final String country = l.getCountry();
			if (country != null) {
				arg.append("-");
				arg.append(country.toLowerCase());
			}
		} else {
			arg.append("de");
		}
		final String model = Build.MODEL;
		if (model.length() > 0) {
			arg.append("; ");
			arg.append(model);
		}
		final String id = Build.ID;
		if (id.length() > 0) {
			arg.append(" Build/");
			arg.append(id);
		}

		// TODO: add version detection for AppleWebKit, Version and Safari
		final String rawUA = "Mozilla/5.0 (Linux; U; Android %s) AppleWebKit/525.10+ (KHTML, like Gecko) Version/3.0.4 Mobile Safari/523.12.2";
		sUA = String.format(rawUA, arg);

		return sUA;
	}

	public static boolean checkForBrowserDeclaration(final Context context) {
		PackageManager pm = context.getPackageManager();
		Intent mainIntent = new Intent(context, MadvertiseActivity.class);
		List<ResolveInfo> returnList = pm.queryIntentActivities(mainIntent, 0);
		return returnList.size() > 0;
	}

	/**
	 * Simple logging helper to prevent producing duplicate code blocks.
	 * Log-Message is only printed to LogCat if logging is enabled in MadUtils
	 * and message is logable with specified tag and level.
	 * 
	 * @param tag
	 *            use a given tag for logging or use default tag if nil. Default
	 *            tag can be defined in MadUtil class.
	 * @param level
	 *            log level from {@link android.util.Log}
	 * @param message
	 * @see android.util.Log
	 */
	public static void logMessage(final String tag, final int level,
			String message) {
		StackTraceElement stackTraceElement = Thread.currentThread()
				.getStackTrace()[3];
		logMessage(tag, level, message, null, stackTraceElement);
	}

	/**
	 * Simple logging helper to prevent producing duplicate code blocks.
	 * Log-Message is only printed to LogCat if logging is enabled in MadUtils
	 * and message is logable with specified tag and level.
	 * 
	 * @param tag
	 *            use a given tag for logging or use default tag if nil. Default
	 *            tag can be defined in MadUtil class.
	 * @param level
	 *            log level from {@link android.util.Log}
	 * @param message
	 *            message to log
	 * @param throwable
	 *            exception to log
	 * @see android.util.Log
	 */
	public static void logMessage(final String tag, final int level,
			String message, Throwable throwable) {
		StackTraceElement stackTraceElement = Thread.currentThread()
				.getStackTrace()[3];
		logMessage(tag, level, message, throwable, stackTraceElement);
	}

	static void logMessage(final String tag, final int level, String message,
			Throwable throwable, StackTraceElement stackTraceElement) {
		String logTag = tag;
		if (!PRINT_LOG) {
			return;
		}

		if (tag == null) {
			logTag = MadvertiseUtil.LOG;
		}

		if (message == null) {
			message = "";
		}

		String fullClassName = stackTraceElement.getClassName();
		String className = fullClassName.substring(fullClassName
				.lastIndexOf(".") + 1);
		int lineNumber = stackTraceElement.getLineNumber();

		String logMessage = "(" + className + ":" + lineNumber + ") : "
				+ message;
		if (throwable != null) {
			logMessage += '\n' + Log.getStackTraceString(throwable);
		}
		Log.println(level, logTag, logMessage);
	}

	public static boolean checkPermissionGranted(String p, Context c) {
		return c.checkCallingOrSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
	}

	public static String getJSONValue(JSONObject json, String key)
			throws JSONException {
		MadvertiseUtil.checkEmptyJson(json, key);
		return json.has(key) ? json.getString(key) : "";
	}

	public static JSONArray getJSONArray(JSONObject json, String key)
			throws JSONException {
		MadvertiseUtil.checkEmptyJson(json, key);
		return json.has(key) ? json.getJSONArray(key) : new JSONArray();
	}

	public static JSONObject getJSONObject(JSONObject json, String key)
			throws JSONException {
		MadvertiseUtil.checkEmptyJson(json, key);
		return json.has(key) ? json.getJSONObject(key) : null;
	}

	public static boolean getJSONBoolean(JSONObject json, String key)
			throws JSONException {
		MadvertiseUtil.checkEmptyJson(json, key);
		return json.has(key) ? json.getBoolean(key) : false;
	}

	private static void checkEmptyJson(JSONObject json, String key)
			throws JSONException {
		if (json == null || key.equals("")) {
			throw new JSONException("Empty JSON or key");
		}
	}

	// code for getting app names
	// taken from
	// http://www.androidsnippets.com/get-installed-applications-with-name-package-name-version-and-icon
	// thx guys :-)
	static class PInfo {
		private String appname = "";
		private String pname = "";
		private String versionName = "";
		private int versionCode = 0;
		@SuppressWarnings("unused")
		private Drawable icon;

		private void prettyPrint() {
			MadvertiseUtil.logMessage(null, Log.DEBUG, appname + "\t " + pname
					+ "\t " + versionName + "\t " + versionCode);
		}
	}

	public static ArrayList<PInfo> getPackages(Context c) {
		ArrayList<PInfo> apps = MadvertiseUtil.getInstalledApps(false, c);
		final int max = apps.size();
		for (int i = 0; i < max; i++) {
			apps.get(i).prettyPrint();
		}
		return apps;
	}

	private static ArrayList<PInfo> getInstalledApps(boolean getSysPackages,
			Context c) {
		ArrayList<PInfo> res = new ArrayList<PInfo>();
		List<PackageInfo> packs = c.getPackageManager().getInstalledPackages(0);
		for (int i = 0; i < packs.size(); i++) {
			PackageInfo p = packs.get(i);
			if ((!getSysPackages) && (p.versionName == null)) {
				continue;
			}
			PInfo newInfo = new PInfo();
			newInfo.appname = p.applicationInfo
					.loadLabel(c.getPackageManager()).toString();
			newInfo.pname = p.packageName;
			newInfo.versionName = p.versionName;
			newInfo.versionCode = p.versionCode;
			newInfo.icon = p.applicationInfo.loadIcon(c.getPackageManager());
			res.add(newInfo);
		}
		return res;
	}

	public static boolean isConnectionAvailable() {
		class NetworkCheckTask extends AsyncTask<Void, Void, Boolean> {

			@Override
			protected Boolean doInBackground(Void... params) {
				try {
					URL url = new URL(MAD_SERVER + "/sync.html");
					HttpURLConnection conn = null;
					conn = (HttpURLConnection) url.openConnection();
					conn.setConnectTimeout(999);
					conn.setRequestMethod("GET");
					conn.setDoInput(true);
					conn.connect();
					if (conn.getResponseCode() == 200)
						return true;
					else
						return false;
				} catch (Exception e) {
					return false;
				}
			}

		}

		try {
			NetworkCheckTask async = new NetworkCheckTask();
			async.execute();
			return async.get();
		} catch (Exception e) {
			return false;
		}
	}

}
