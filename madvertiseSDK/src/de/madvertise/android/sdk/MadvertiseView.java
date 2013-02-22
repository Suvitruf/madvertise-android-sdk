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

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.FrameLayout;

public class MadvertiseView extends FrameLayout {

    private static final boolean IS_TESTMODE_DEFAULT = false;

    // parameters for shine effect of the textview banner
    private final int GRADIENT_TOP_ALPHA = (int) (255 * 0.50);

    private final double GRADIENT_STOP = 0.7375;

    private MadvertiseAd mCurrentAd;

    private static BitmapDrawable sTextBannerBackground;

    // parameters of the mad view
    private int mTextColor = MadvertiseUtil.TEXT_COLOR_DEFAULT;

    private int mBackgroundColor = MadvertiseUtil.BACKGROUND_COLOR_DEFAULT;

    private int mSecondsToRefreshAd = MadvertiseUtil.SECONDS_TO_REFRESH_AD_DEFAULT;

    private boolean mTestMode = IS_TESTMODE_DEFAULT;

    private String mBannerType = MadvertiseUtil.BANNER_TYPE_DEFAULT;

    private String mAnimationType = MadvertiseUtil.ANIMATION_TYPE_DEFAULT;

    private boolean mDeliverOnlyText = MadvertiseUtil.DELIVER_ONLY_TEXT_DEFAULT;

    private int mTextSize = MadvertiseUtil.TEXT_SIZE_DEFAULT;

    // Banner height and width in pixel, needed for this view
    private int mBannerHeight = MadvertiseUtil.BANNER_HEIGHT_DEFAULT;

    private int mBannerWidth = MadvertiseUtil.BANNER_WIDTH_DEFAULT;

    // Banner height and width in dp, needed for the rendering of the banners
    private int mBannerHeightDp = MadvertiseUtil.BANNER_HEIGHT_DEFAULT;

    private int mBannerWidthDp = MadvertiseUtil.BANNER_WIDTH_DEFAULT;

    private int mParentWidth = 0;

    private int mParentHeight = 0;

    private MadvertiseViewCallbackListener mCallbackListener = null;

    private Timer mAdTimer = null;

    public static final int MAKE_VISIBLE = View.VISIBLE;

    private static final int ANIMATION_COMPLETE = 2;

    private static String sGender = "";

    private static String sAge = "";

    private final Handler mHandler = new Handler() {
        public void handleMessage(android.os.Message msg) {
            switch (msg.what) {
                case MAKE_VISIBLE:
                	setVisibility(View.VISIBLE);
                    break;
                case ANIMATION_COMPLETE:
                	removeOldViews();
                    break;
            }
        }
    };

    private Drawable mInitialBackground = null;

    private Thread mRequestThread;

    // The value of a density-independent pixel
    private float mDp;

    private List<View> mOldViews = new ArrayList<View>();

    private final AnimationEndListener mAnimationListener = new AnimationEndListener() {
		public void onAnimationEnd() {
            mHandler.sendEmptyMessage(ANIMATION_COMPLETE);
        }
    };

    private MadvertiseMraidView mMraidView;

    private boolean mFetchAdsEnabled = true;

    private int mPlacementType = MadvertiseUtil.PLACEMENT_TYPE_INLINE;

    private boolean mIsMraid;

    private static boolean reportLauch = true;

    /**
     * The gender-type of male users
     */
    public static final String GENDER_MALE = "M";

    /**
     * The gender-type of female users
     */
    public static final String GENDER_FEMALE = "F";

    /**
     * Constructor
     *
     * @param context
     */
    public MadvertiseView(final Context context) {
        this(context, null);
    }

    /**
     * Constructor
     *
     * @param context
     * @param attrs
     */
    public MadvertiseView(final Context context, final AttributeSet attrs) {
        super(context, attrs);

        MadvertiseUtil.logMessage(null, Log.DEBUG, "** Constructor for mad view called **");

        // We use GONE instead of INVISIBLE because GONE doesn't allocate space
        // in
        // the layout.
        setVisibility(GONE);

        if (!MadvertiseUtil.checkPermissionGranted(android.Manifest.permission.INTERNET, context)) {
            MadvertiseUtil.logMessage(null, Log.DEBUG, " *** ----------------------------- *** ");
            MadvertiseUtil.logMessage(null, Log.DEBUG, " *** Missing internet permissions! *** ");
            MadvertiseUtil.logMessage(null, Log.DEBUG, " *** ----------------------------- *** ");
            throw new IllegalArgumentException("Missing internet permission!");
        }

        if (!MadvertiseUtil.checkForBrowserDeclaration(getContext())) {
            MadvertiseUtil.logMessage(null, Log.DEBUG, " *** ----------------------------- *** ");
            MadvertiseUtil
                    .logMessage(
                            null,
                            Log.DEBUG,
                            " *** You must declare the activity de.madvertise.android.sdk.MadvertiseActivity in your manifest! *** ");
            MadvertiseUtil.logMessage(null, Log.DEBUG, " *** ----------------------------- *** ");
            throw new IllegalArgumentException("Missing Activity declaration!");
        }

        initParameters(attrs);

        final DisplayMetrics displayMetrics = context.getApplicationContext().getResources()
                .getDisplayMetrics();
        MadvertiseUtil.logMessage(null, Log.DEBUG, "Display values: Width = "
                + displayMetrics.widthPixels + " ; Height = " + displayMetrics.heightPixels);

        mInitialBackground = this.getBackground();
        if (sTextBannerBackground == null) {
        	Rect r = new Rect(0, 0, 1, displayMetrics.heightPixels);
            sTextBannerBackground = generateBackgroundDrawable(r, mBackgroundColor, 0xffffff);
        }

        setClickable(true);
        setFocusable(true);
        setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);

        if (mRequestThread == null || !mRequestThread.isAlive()) {
            requestNewAd(false);
        }
        
        // no reloads for ads with banner_type = rich_media
        if (mBannerType.contains(MadvertiseUtil.BANNER_TYPE_RICH_MEDIA) || mBannerType.contains(MadvertiseUtil.BANNER_TYPE_RICH_MEDIA_SHORT) ) {
        	this.setFetchingAdsEnabled(false);
        	MadvertiseUtil.logMessage(null, Log.DEBUG, "No ad reloading, since banner_type=[rich_media|rm] was requested");
        }
        
        // just some testing
        // MadvertiseUtil.getPackages(context.getApplicationContext());
        
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        MadvertiseUtil.logMessage(null, Log.DEBUG, "onTouchEvent(MotionEvent event) fired");
        if (mCurrentAd != null)
            mCurrentAd.handleClick();
        return true;
    }

    private void refreshView() {
        setBackgroundDrawable(mInitialBackground);

        if (mCurrentAd != null) {
            if (mCurrentAd.hasBanner() && !mDeliverOnlyText) {
                if (mCurrentAd.isMraid()) {
                    showMraidView();
                } else {
                    showImageView();
                }
            } else {
                showTextView();
                // show the MadvertiseView immediately so this doesn't need to
                // be done
                // programmatically. The image banner will be shown after its
                // contents have been loaded.
                setVisibility(View.VISIBLE);
            }
            notifyListener(true);
        } else {
            removeAllViews();
            notifyListener(false);
            setVisibility(View.GONE);
        }
    }

    private void showMraidView() {
        MadvertiseUtil.logMessage(null, Log.DEBUG, "Add rich media banner");

        mMraidView = new MadvertiseMraidView(getContext().getApplicationContext(),
                mCallbackListener, mAnimationListener, mHandler, this);
        mMraidView.setPlacementType(mPlacementType);
        mMraidView.loadAd(mCurrentAd);

        // animate the old views
        animateOldViews();

        addView(mMraidView);

        final Animation animation = createAnimation(false);
        if (animation != null) {
            mMraidView.startAnimation(animation);
        }
    }

    private void showImageView() {
        MadvertiseUtil.logMessage(null, Log.DEBUG, "Add image banner");

        MadvertiseImageView imageView = new MadvertiseImageView(getContext()
                .getApplicationContext(), mBannerWidthDp, mBannerHeightDp, mCurrentAd, mHandler,
                mAnimationListener);

        // animate the old views
        animateOldViews();

        addView(imageView);

        final Animation animation = createAnimation(false);
        if (animation != null) {
            imageView.startAnimation(animation);
        }
    }

    private void showTextView() {
        MadvertiseUtil.logMessage(null, Log.DEBUG, "Add text banner");

        setBackgroundDrawable(sTextBannerBackground);
        MadvertiseTextView textBanner = new MadvertiseTextView(
                getContext().getApplicationContext(), mCurrentAd.getText(), mTextSize, mTextColor,
                mAnimationListener);

        // animate the old views
        animateOldViews();

        addView(textBanner);

        final Animation animation = createAnimation(false);
        if (animation != null) {
            textBanner.startAnimation(animation);
        }
    }

    private void removeOldViews() {
        for (View view : mOldViews) {
            removeView(view);
        }
    }

    private void animateOldViews() {
        final int childViewCounter = getChildCount();
        if (childViewCounter > 0) {
            final Animation animation = createAnimation(true);
            for (int i = 0; i < childViewCounter; i++) {
                if (animation != null && getChildAt(i) != null) {
                    getChildAt(i).setAnimation(animation);
                    mOldViews.add(getChildAt(i));
                }
            }
        }
    }

    private Animation createAnimation(final boolean isOutAnimation) {
        Animation animation = null;

        if (isOutAnimation) {
            if (mAnimationType != null && mAnimationType.equals(MadvertiseUtil.ANIMATION_TYPE_FADE)) {
                animation = new AlphaAnimation(1.0f, 0.0f);
                animation.setDuration(700);
            } else if (mAnimationType != null
                    && mAnimationType.equals(MadvertiseUtil.ANIMATION_TYPE_LEFT_TO_RIGHT)) {
                animation = new TranslateAnimation(Animation.RELATIVE_TO_PARENT, 0.0f,
                        Animation.RELATIVE_TO_PARENT, 1.0f, Animation.RELATIVE_TO_PARENT, 0.0f,
                        Animation.RELATIVE_TO_PARENT, 0.0f);
                animation.setDuration(900);
                animation.setInterpolator(new AccelerateInterpolator());
            } else if (mAnimationType != null
                    && mAnimationType.equals(MadvertiseUtil.ANIMATION_TYPE_TOP_DOWN)) {
                animation = new TranslateAnimation(Animation.RELATIVE_TO_PARENT, 0.0f,
                        Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.0f,
                        Animation.RELATIVE_TO_PARENT, 1.0f);
                animation.setDuration(900);
                animation.setInterpolator(new AccelerateInterpolator());
            }
        } else {
            if (mAnimationType != null && mAnimationType.equals(MadvertiseUtil.ANIMATION_TYPE_FADE)) {
                animation = new AlphaAnimation(0.0f, 1.0f);
                animation.setDuration(1200);
            } else if (mAnimationType != null
                    && mAnimationType.equals(MadvertiseUtil.ANIMATION_TYPE_LEFT_TO_RIGHT)) {
                animation = new TranslateAnimation(Animation.RELATIVE_TO_PARENT, -1.0f,
                        Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.0f,
                        Animation.RELATIVE_TO_PARENT, 0.0f);
                animation.setDuration(900);
                animation.setInterpolator(new AccelerateInterpolator());
            } else if (mAnimationType != null
                    && mAnimationType.equals(MadvertiseUtil.ANIMATION_TYPE_TOP_DOWN)) {
                animation = new TranslateAnimation(Animation.RELATIVE_TO_PARENT, 0.0f,
                        Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, -1.0f,
                        Animation.RELATIVE_TO_PARENT, 0.0f);
                animation.setDuration(900);
                animation.setInterpolator(new AccelerateInterpolator());
            }
        }

        // create a no-animation animation
        // if (animation == null) {
        // animation = new AlphaAnimation(1.0f, 1.0f);
        // }

        return animation;
    }

    /**
     * Convenience method to notify the callback listener
     *
     * @param succeed
     */
    private void notifyListener(final boolean succeed) {
        if (mCallbackListener != null) {
            mCallbackListener.onLoaded(succeed, this);
        } else {
            MadvertiseUtil.logMessage(null, Log.DEBUG, "Callback Listener not set");
        }
    }

    /**
     * Set the visibility state of this view.
     *
     * @param visibility - set the visibility with <code>VISIBLE</code>,
     *            <code>INVISIBLE</code> or <code>GONE</code>.
     */
    @Override
    public void setVisibility(int visibility) {
        int originVisibility = super.getVisibility();

        if (originVisibility != visibility) {
            synchronized (this) {
                int childViewCounter = getChildCount();

                for (int i = 0; i < childViewCounter; i++) {
                    View child = getChildAt(i);
                    child.setVisibility(visibility);
                }
                super.setVisibility(visibility);
            }
        }
    }

    /**
     * Reads all parameters, not needed for a request to the ad server (colors,
     * refresh timeout, ...)
     *
     * @param attrs attribute set for the view
     */
    private void initParameters(final AttributeSet attrs) {
        mDp = getContext().getApplicationContext().getResources().getDisplayMetrics().density;

        if (attrs != null) {
            String packageName = "http://schemas.android.com/apk/res/"
                    + getContext().getApplicationContext().getPackageName();
            if (packageName != null) {
                MadvertiseUtil.logMessage(null, Log.DEBUG, "namespace = " + packageName);
            }

            mTestMode = attrs.getAttributeBooleanValue(packageName, "isTestMode",
                    IS_TESTMODE_DEFAULT);

            mTextColor = attrs.getAttributeIntValue(packageName, "textColor",
                    MadvertiseUtil.TEXT_COLOR_DEFAULT);

            mBackgroundColor = attrs.getAttributeIntValue(packageName, "backgroundColor",
                    MadvertiseUtil.BACKGROUND_COLOR_DEFAULT);

            mSecondsToRefreshAd = attrs.getAttributeIntValue(packageName, "secondsToRefresh",
                    MadvertiseUtil.SECONDS_TO_REFRESH_AD_DEFAULT);

            if (attrs.getAttributeValue(packageName, "bannerType") != null) {
                mBannerType = attrs.getAttributeValue(packageName, "bannerType");
            }

            if (attrs.getAttributeValue(packageName, "animation") != null) {
                mAnimationType = attrs.getAttributeValue(packageName, "animation");
            }

            mDeliverOnlyText = attrs.getAttributeBooleanValue(packageName, "deliverOnlyText",
                    MadvertiseUtil.DELIVER_ONLY_TEXT_DEFAULT);

            if (!mBannerType.equals(MadvertiseUtil.BANNER_TYPE_MMA) && mDeliverOnlyText) {
                MadvertiseUtil
                        .logMessage(null, Log.DEBUG,
                                "Only banners in mma-format can show text. Setting deliferOnlyText to false.");
                mDeliverOnlyText = false;
            }

            mTextSize = attrs.getAttributeIntValue(packageName, "textSize",
                    MadvertiseUtil.TEXT_SIZE_DEFAULT);

            if (mTextSize > 20) {
                MadvertiseUtil.logMessage(null, Log.DEBUG,
                        "The text size must be set to 20 at maximum.");
                mTextSize = 20;
            } else if (mTextSize < 10) {
                MadvertiseUtil.logMessage(null, Log.DEBUG,
                        "The text size must be set to 10 at minimum.");
                mTextSize = 10;
            }

            mIsMraid = attrs.getAttributeBooleanValue(packageName, "mraid", true);

            final String placementTypeStr = attrs.getAttributeValue(packageName, "placement_type");
            if (placementTypeStr != null && placementTypeStr.equalsIgnoreCase("inline")) {
                mPlacementType = MadvertiseUtil.PLACEMENT_TYPE_INLINE;
            } else if (placementTypeStr != null
                    && placementTypeStr.equalsIgnoreCase("interstitial")) {
                mPlacementType = MadvertiseUtil.PLACEMENT_TYPE_INTERSTITIAL;
            }
        } else {
            MadvertiseUtil.logMessage(null, Log.DEBUG,
                    "AttributeSet is null. Using default parameters");
        }

        if (mSecondsToRefreshAd != 0 && mSecondsToRefreshAd < 30) {
            mSecondsToRefreshAd = MadvertiseUtil.SECONDS_TO_REFRESH_AD_DEFAULT;
            MadvertiseUtil.logMessage(null, Log.DEBUG, "Refresh intervall must be higher than 60");
        }

        calculateBannerDimensions();

        MadvertiseUtil.logMessage(null, Log.DEBUG, "Using following attributes values:");
        MadvertiseUtil.logMessage(null, Log.DEBUG, " testMode = " + mTestMode);
        MadvertiseUtil.logMessage(null, Log.DEBUG, " textColor = " + mTextColor);
        MadvertiseUtil.logMessage(null, Log.DEBUG, " backgroundColor = " + mBackgroundColor);
        MadvertiseUtil.logMessage(null, Log.DEBUG, " secondsToRefreshAd = " + mSecondsToRefreshAd);
        MadvertiseUtil.logMessage(null, Log.DEBUG, " bannerType = " + mBannerType);
        MadvertiseUtil.logMessage(null, Log.DEBUG, " animationType = " + mAnimationType);
        MadvertiseUtil.logMessage(null, Log.DEBUG, " deliverOnlyText = " + mDeliverOnlyText);
        MadvertiseUtil.logMessage(null, Log.DEBUG, " textSize = " + mTextSize);
        MadvertiseUtil.logMessage(null, Log.DEBUG, " isMraid = " + mIsMraid);
        MadvertiseUtil.logMessage(null, Log.DEBUG, " placementType = " + mPlacementType);
        MadvertiseUtil.logMessage(null, Log.DEBUG, " bannerWidth = " + mBannerWidth);
        MadvertiseUtil.logMessage(null, Log.DEBUG, " bannerHeight = " + mBannerHeight);
        MadvertiseUtil.logMessage(null, Log.DEBUG, " bannerWidthDp = " + mBannerWidthDp);
        MadvertiseUtil.logMessage(null, Log.DEBUG, " bannerHeightDp = " + mBannerHeightDp);
    }

    /**
     * Starts a background thread to fetch a new ad. Method is called from the
     * refresh timer task
     */
    private void requestNewAd(final boolean isTimerRequest) {
        if (!mFetchAdsEnabled) {
            MadvertiseUtil.logMessage(null, Log.DEBUG, "Fetching ads is disabled");
            return;
        }

        MadvertiseUtil.logMessage(null, Log.DEBUG, "Trying to fetch a new ad");

        mRequestThread = new Thread(new Runnable() {
            public void run() {
                // read all parameters, that we need for the request
                // get site token from manifest xml file
                String siteToken = MadvertiseUtil.getToken(
                        getContext().getApplicationContext(), mCallbackListener);
                if (siteToken == null) {
                    siteToken = "";
                    MadvertiseUtil.logMessage(null, Log.DEBUG,
                            "Cannot show ads, since the appID ist null");
                } else {
                    MadvertiseUtil.logMessage(null, Log.DEBUG, "appID = " + siteToken);
                }

                // create post request
                HttpPost postRequest = new HttpPost(MadvertiseUtil.MAD_SERVER + "/site/" + siteToken);
                postRequest.setHeader("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
                // new ad response version, that supports rich media
                postRequest.addHeader("Accept", "application/vnd.madad+json; version=3");
                List<NameValuePair> parameterList = new ArrayList<NameValuePair>();
                parameterList.add(new BasicNameValuePair("ua", MadvertiseUtil.getUA()));
                parameterList.add(new BasicNameValuePair("app", "true"));
                parameterList.add(new BasicNameValuePair("debug", Boolean.toString(mTestMode)));
                parameterList.add(new BasicNameValuePair("ip", MadvertiseUtil.getLocalIpAddress(mCallbackListener)));
                parameterList.add(new BasicNameValuePair("format", "json"));
                parameterList.add(new BasicNameValuePair("requester", "android_sdk"));
                parameterList.add(new BasicNameValuePair("version", "3.1.3"));
                parameterList.add(new BasicNameValuePair("banner_type", mBannerType));
                parameterList.add(new BasicNameValuePair("deliver_only_text", Boolean
                        .toString(mDeliverOnlyText)));
                if (sAge != null && !sAge.equals("")) {
                    parameterList.add(new BasicNameValuePair("age", sAge));
                }

                parameterList.add(new BasicNameValuePair("mraid", Boolean.toString(mIsMraid)));

                if (sGender != null && !sGender.equals("")) {
                    parameterList.add(new BasicNameValuePair("gender", sGender));
                }
                final Display display = ((WindowManager) getContext().getApplicationContext()
                        .getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
                String orientation;
                if (display.getWidth() > display.getHeight()) {
                    orientation = "landscape";
                } else {
                    orientation = "portrait";
                }

                parameterList.add(new BasicNameValuePair("device_height", Integer.toString(display.getHeight())));
                parameterList.add(new BasicNameValuePair("device_width", Integer.toString(display.getWidth())));
                
                // When the View is first created, the parent does not exist
                // when this call is made. Hence, we assume that the parent
                // size is equal the screen size for the first call.
                if (mParentWidth == 0 && mParentHeight == 0) {
                    mParentWidth = display.getWidth();
                    mParentHeight = display.getHeight();
                }

                parameterList.add(new BasicNameValuePair("parent_height", Integer.toString(mParentHeight)));
                parameterList.add(new BasicNameValuePair("parent_width", Integer.toString(mParentWidth)));

                parameterList.add(new BasicNameValuePair("device_orientation", orientation));
                MadvertiseUtil.refreshCoordinates(getContext().getApplicationContext());
                if (MadvertiseUtil.getLocation() != null) {
                    parameterList.add(new BasicNameValuePair("lat", Double
                            .toString(MadvertiseUtil.getLocation().getLatitude())));
                    parameterList.add(new BasicNameValuePair("lng", Double
                            .toString(MadvertiseUtil.getLocation().getLongitude())));
                }
                
                
                parameterList.add(new BasicNameValuePair("app_name", MadvertiseUtil.getApplicationName(getContext().getApplicationContext())));
                parameterList.add(new BasicNameValuePair("app_version", MadvertiseUtil.getApplicationVersion(getContext().getApplicationContext())));
                
                parameterList.add(new BasicNameValuePair("udid_md5", MadvertiseUtil.getHashedAndroidID(getContext(), MadvertiseUtil.HashType.MD5)));
                parameterList.add(new BasicNameValuePair("udid_sha1", MadvertiseUtil.getHashedAndroidID(getContext(), MadvertiseUtil.HashType.SHA1)));
                
                parameterList.add(new BasicNameValuePair("mac_md5", MadvertiseUtil.getHashedMacAddress(getContext(), MadvertiseUtil.HashType.MD5)));
                parameterList.add(new BasicNameValuePair("mac_sha1", MadvertiseUtil.getHashedMacAddress(getContext(), MadvertiseUtil.HashType.SHA1)));

                UrlEncodedFormEntity urlEncodedEntity = null;
                try {
                    urlEncodedEntity = new UrlEncodedFormEntity(parameterList);
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }

                postRequest.setEntity(urlEncodedEntity);

                MadvertiseUtil.logMessage(null, Log.DEBUG, "Post request created");
                MadvertiseUtil.logMessage(null, Log.DEBUG, "Uri : "
                        + postRequest.getURI().toASCIIString());
                MadvertiseUtil.logMessage(
                        null,
                        Log.DEBUG,
                        "All headers : "
                                + MadvertiseUtil.getAllHeadersAsString(postRequest
                                        .getAllHeaders()));
                MadvertiseUtil.logMessage(null, Log.DEBUG, "All request parameters :"
                        + MadvertiseUtil.printRequestParameters(parameterList));

                synchronized (this) {
                    // send blocking request to ad server
                    HttpClient httpClient = new DefaultHttpClient();
                    HttpResponse httpResponse = null;
                    InputStream inputStream = null;
                    JSONObject json = null;

                    try {
                        HttpParams clientParams = httpClient.getParams();
                        HttpConnectionParams.setConnectionTimeout(clientParams,
                                MadvertiseUtil.CONNECTION_TIMEOUT);
                        HttpConnectionParams.setSoTimeout(clientParams,
                                MadvertiseUtil.CONNECTION_TIMEOUT);

                        MadvertiseUtil.logMessage(null, Log.DEBUG, "Sending request");
                        httpResponse = httpClient.execute(postRequest);

                        MadvertiseUtil.logMessage(null, Log.DEBUG, "Response Code => "
                                + httpResponse.getStatusLine().getStatusCode());

                        String message = "";
                        if (httpResponse.getLastHeader("X-Madvertise-Debug") != null) {
                            message = httpResponse.getLastHeader("X-Madvertise-Debug")
                                    .toString();
                        }

                        if (mTestMode) {
                            MadvertiseUtil.logMessage(null, Log.DEBUG,
                                    "Madvertise Debug Response: " + message);
                        }

                        int responseCode = httpResponse.getStatusLine().getStatusCode();

                        HttpEntity entity = httpResponse.getEntity();

                        if (responseCode == 200 && entity != null) {
                            inputStream = entity.getContent();
                            String resultString = MadvertiseUtil
                                    .convertStreamToString(inputStream);
                            MadvertiseUtil.logMessage(null, Log.DEBUG, "Response => "
                                    + resultString);
                            json = new JSONObject(resultString);
                            
                            // create ad
                            mCurrentAd = new MadvertiseAd(getContext().getApplicationContext(),
                                    json, mCallbackListener);

                            calculateBannerDimensions();
                            
                            mHandler.post(mUpdateResults);
                        } else {
                            if (mCallbackListener != null) {
                                mCallbackListener
                                        .onIllegalHttpStatusCode(responseCode, message);
                            }
                        }
                    } catch (ClientProtocolException e) {
                        MadvertiseUtil.logMessage(null, Log.DEBUG,
                                "Error in HTTP request / protocol", e);
                        if (mCallbackListener != null) {
                            mCallbackListener.onError(e);
                        }
                    } catch (IOException e) {
                        MadvertiseUtil.logMessage(null, Log.DEBUG,
                                "Could not receive a http response on an ad request", e);
                        if (mCallbackListener != null) {
                            mCallbackListener.onError(e);
                        }
                    } catch (JSONException e) {
                        MadvertiseUtil.logMessage(null, Log.DEBUG,
                                "Could not parse json object", e);
                        if (mCallbackListener != null) {
                            mCallbackListener.onError(e);
                        }
                    } catch (Exception e) {
                        MadvertiseUtil.logMessage(null, Log.DEBUG,
                                "Could not receive a http response on an ad request", e);
                        if (mCallbackListener != null) {
                            mCallbackListener.onError(e);
                        }
                    } finally {
                        if (inputStream != null) {
                            try {
                                inputStream.close();
                            } catch (IOException e) {
                                if (mCallbackListener != null) {
                                    mCallbackListener.onError(e);
                                }
                            }
                        }
                    }
                }
            }
        }, "MadvertiseRequestThread");
        mRequestThread.start();
    }

    // used for execution in the ui thread
    private final Runnable mUpdateResults = new Runnable() {
        public void run() {
            refreshView();
        }
    };

    private void calculateBannerDimensions() {
    	if (mCurrentAd == null || mCurrentAd.getBannerType() == null) {
    		return;
    	}
    	
    	String bannerType = mCurrentAd.getBannerType();
    	
        // set the banner width and height
        if (bannerType.equals(MadvertiseUtil.BANNER_TYPE_MEDIUM_RECTANGLE)) {
            mBannerHeight = (int) (mDp * MadvertiseUtil.MEDIUM_RECTANGLE_BANNER_HEIGHT + 0.5f);
            mBannerWidth = (int) (mDp * MadvertiseUtil.MEDIUM_RECTANGLE_BANNER_WIDTH + 0.5f);
            mBannerHeightDp = MadvertiseUtil.MEDIUM_RECTANGLE_BANNER_HEIGHT;
            mBannerWidthDp = MadvertiseUtil.MEDIUM_RECTANGLE_BANNER_WIDTH;
        } else if (bannerType.equals(MadvertiseUtil.BANNER_TYPE_MMA)) {
            mBannerHeight = (int) (mDp * MadvertiseUtil.MMA_BANNER_HEIGHT + 0.5f);
            mBannerWidth = (int) (mDp * MadvertiseUtil.MMA_BANNER_WIDTH + 0.5f);
            mBannerHeightDp = MadvertiseUtil.MMA_BANNER_HEIGHT;
            mBannerWidthDp = MadvertiseUtil.MMA_BANNER_WIDTH;
        } else if (bannerType.equals(MadvertiseUtil.BANNER_TYPE_FULLSCREEN)) {
            mBannerHeight = (int) (mDp * MadvertiseUtil.FULLSCREEN_BANNER_HEIGHT + 0.5f);
            mBannerWidth = (int) (mDp * MadvertiseUtil.FULLSCREEN_BANNER_WIDTH + 0.5f);
            mBannerHeightDp = MadvertiseUtil.FULLSCREEN_BANNER_HEIGHT;
            mBannerWidthDp = MadvertiseUtil.FULLSCREEN_BANNER_WIDTH;
        } else if (bannerType.equals(MadvertiseUtil.BANNER_TYPE_LANDSCAPE)) {
            mBannerHeight = (int) (mDp * MadvertiseUtil.LANDSCAPE_BANNER_HEIGHT + 0.5f);
            mBannerWidth = (int) (mDp * MadvertiseUtil.LANDSCAPE_BANNER_WIDTH + 0.5f);
            mBannerHeightDp = MadvertiseUtil.LANDSCAPE_BANNER_HEIGHT;
            mBannerWidthDp = MadvertiseUtil.LANDSCAPE_BANNER_WIDTH;
        } else if (bannerType.equals(MadvertiseUtil.BANNER_TYPE_LEADERBOARD)) {
            mBannerHeight = (int) (mDp * MadvertiseUtil.LEADERBOARD_BANNER_HEIGHT + 0.5f);
            mBannerWidth = (int) (mDp * MadvertiseUtil.LEADERBOARD_BANNER_WIDTH + 0.5f);
            mBannerHeightDp = MadvertiseUtil.LEADERBOARD_BANNER_HEIGHT;
            mBannerWidthDp = MadvertiseUtil.LEADERBOARD_BANNER_WIDTH;
        } else if (bannerType.equals(MadvertiseUtil.BANNER_TYPE_PORTRAIT)) {
            mBannerHeight = (int) (mDp * MadvertiseUtil.PORTRAIT_BANNER_HEIGHT + 0.5f);
            mBannerWidth = (int) (mDp * MadvertiseUtil.PORTRAIT_BANNER_WIDTH + 0.5f);
            mBannerHeightDp = MadvertiseUtil.PORTRAIT_BANNER_HEIGHT;
            mBannerWidthDp = MadvertiseUtil.PORTRAIT_BANNER_WIDTH;
        } else if (bannerType.equals(MadvertiseUtil.BANNER_TYPE_RICH_MEDIA) && mCurrentAd.isMraid()) {
        	// If we had chained banner types with mraid=true and actually get a rich media ad, we don't know how big it should be in default mode.
        	// So we need to check the parsed ad-response from the server.
        	mBannerHeightDp = mCurrentAd.getBannerHeight();
        	mBannerWidthDp = mCurrentAd.getBannerWidth();
        	mBannerHeight = (int) (mDp * mBannerHeightDp + 0.5f);
            mBannerWidth = (int) (mDp * mBannerWidthDp + 0.5f);
        } else if (bannerType.equals(MadvertiseUtil.BANNER_TYPE_RICH_MEDIA) && mCurrentAd != null && !mCurrentAd.isMraid()) {
        	// banner type = rich_media, but no mraid, take fullscreen
        	// actually same logic as for mraid
        	mBannerHeightDp = mCurrentAd.getBannerHeight();
        	mBannerWidthDp = mCurrentAd.getBannerWidth();
        	mBannerHeight = (int) (mDp * mBannerHeightDp + 0.5f);
            mBannerWidth = (int) (mDp * mBannerWidthDp + 0.5f);
        }
	        
        // adjust width and height to fit the screen
        final DisplayMetrics displayMetrics = getContext()
                .getApplicationContext().getResources().getDisplayMetrics();
        int tempHeight = mBannerHeight;
        int tempWidth = mBannerWidth;

        if (displayMetrics.heightPixels < mBannerHeight) {
            tempHeight = displayMetrics.heightPixels;
        }
        if (displayMetrics.widthPixels < mBannerWidth) {
            tempWidth = displayMetrics.widthPixels;
        }

        // fullscreen is a square so we have special treatment here
        if (bannerType.equals(MadvertiseUtil.BANNER_TYPE_FULLSCREEN)) {
            if (tempHeight < tempWidth) {
                mBannerWidth = tempHeight;
                mBannerHeight = tempHeight;
                mBannerWidthDp = (int) (tempHeight / mDp);
                mBannerHeightDp = (int) (tempHeight / mDp);
            } else {
                mBannerWidth = tempWidth;
                mBannerHeight = tempWidth;
                mBannerWidthDp = (int) (tempWidth / mDp);
                mBannerHeightDp = (int) (tempWidth / mDp);
            }
        } else {
            float heightRatio = mBannerHeight / (float) tempHeight;
            float widthRatio = mBannerWidth / (float) tempWidth;

            if (heightRatio > widthRatio) {
                mBannerWidth = (int) (mBannerWidth / heightRatio);
                mBannerHeight = tempHeight;
                mBannerWidthDp = (int) (mBannerWidth / mDp);
                mBannerHeightDp = (int) (tempHeight / mDp);
            } else {
                mBannerWidth = tempWidth;
                mBannerHeight = (int) (mBannerHeight / widthRatio);
                mBannerWidthDp = (int) (tempWidth / mDp);
                mBannerHeightDp = (int) (mBannerHeight / mDp);
            }
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        MadvertiseUtil.logMessage(null, Log.DEBUG, "#### onWindowFocusChanged fired ####");
        onViewCallback(hasWindowFocus);
        super.onWindowFocusChanged(hasWindowFocus);
        this.getParent();
    }

    @Override
    protected void onAttachedToWindow() {
        MadvertiseUtil.logMessage(null, Log.DEBUG, "#### onAttachedToWindow fired ####");
        onViewCallback(true);
        super.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        MadvertiseUtil.logMessage(null, Log.DEBUG, "#### onDetachedFromWindow fired ####");
        onViewCallback(false);
        super.onDetachedFromWindow();
    }

    /**
     * Convenience method for a shiny background for text ads
     *
     * @param rect
     * @param backgroundColor
     * @param mTextColor
     * @return
     */
    private BitmapDrawable generateBackgroundDrawable(final Rect rect, final int backgroundColor,
            final int shineColor) {
        try {
            Bitmap bitmap = Bitmap.createBitmap(rect.width(), rect.height(),
                    Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawTextBannerBackground(canvas, rect, backgroundColor, shineColor);
            return new BitmapDrawable(bitmap);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Draw the ad background for a text banner
     *
     * @param canvas
     * @param rectangle
     * @param backgroundColor
     * @param mTextColor
     */
    private void drawTextBannerBackground(final Canvas canvas, final Rect rectangle,
            final int backgroundColor, int shineColor) {
        Paint paint = new Paint();
        paint.setColor(backgroundColor);
        paint.setAntiAlias(true);
        canvas.drawRect(rectangle, paint);

        int upperColor = Color.argb(GRADIENT_TOP_ALPHA, Color.red(shineColor),
                Color.green(shineColor), Color.blue(shineColor));
        int[] gradientColors = {
                upperColor, shineColor
        };
        GradientDrawable gradientDrawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, gradientColors);

        int stop = (int) (rectangle.height() * GRADIENT_STOP) + rectangle.top;
        gradientDrawable.setBounds(rectangle.left, rectangle.top, rectangle.right, stop);
        gradientDrawable.draw(canvas);

        Rect shadowRect = new Rect(rectangle.left, stop, rectangle.right, rectangle.bottom);
        Paint shadowPaint = new Paint();
        shadowPaint.setColor(shineColor);
        canvas.drawRect(shadowRect, shadowPaint);
    }

    public void stopRequestThread() {
        if (mRequestThread != null && mRequestThread.isAlive()) {
            mRequestThread.interrupt();
        }
    }

    /**
     * Handles the refresh timer, initiates the stopping of the request thread
     * and caching of ads.
     *
     * @param starting
     */
    private void onViewCallback(final boolean starting) {
        synchronized (this) {
            if (starting) {
                if (mAdTimer == null) {
                    mAdTimer = new Timer();
                    mAdTimer.schedule(new TimerTask() {
                        public void run() {
                            MadvertiseUtil.logMessage(null, Log.DEBUG, "Refreshing ad ...");
                            requestNewAd(true);
                        }
                    }, (long) mSecondsToRefreshAd * 1000, (long) mSecondsToRefreshAd * 1000);
                }
            } else {
                if (mAdTimer != null) {
                    MadvertiseUtil.logMessage(null, Log.DEBUG, "Stopping refresh timer ...");
                    mAdTimer.cancel();
                    mAdTimer = null;

                    // When there is no timer needed, the current request can be
                    // stopped
                    stopRequestThread();
                }
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        mParentWidth = MeasureSpec.getSize(widthMeasureSpec);
        mParentHeight = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(mBannerWidth, mBannerHeight);
    }

    /**
     * Sets the placement type of a rich media ad.
     *
     * @param placementType the placement type, either
     *            <code>MadvertiseUtil.PLACEMENT_TYPE_INTERSTITIAL</code> or
     *            <code>MadvertiseUtil.PLACEMENT_TYPE_INLINE<code/>.
     */
    public void setPlacementType(int placementType) {
        if (mMraidView != null) {
            mMraidView.setPlacementType(placementType);
        }
    }
    
    /**
     * Gets the current banner type.
     * 
     * @return See {@link MadvertiseUtil} constants for valid values
     */
    public String getBannerType() {
        return mBannerType;
    }

    /**
     * Sets the banner type.
     * 
     * @param bannerType
     *            See {@link MadvertiseUtil} constants for valid values
     */
    public void setBannerType(String bannerType) {
        mBannerType = bannerType;
    }

    /**
     * Set the gender of your app's user.
     *
     * @param the gender, either <code>GENDER_MALE</code> or
     *            <code>GENDER_FEMALE</code>
     */
    public static void setGender(String gender) {
        if (gender.equals("F") || gender.equals("M")) {
            sGender = gender;
        } else {
            sGender = "";
        }
    }

    /**
     * Set the age or age range of your app's user.
     *
     * @param the age, e.g. '26' or '20-30'
     */
    public static void setAge(String age) {
        sAge = age;
    }

    /**
     * Listener to be informed when animations on a view end
     */
    public interface AnimationEndListener {
        void onAnimationEnd();
    }

    /**
     * Enable/disable the loading of new ads. Default is true.
     *
     * @param isEnabled
     */
    public void setFetchingAdsEnabled(final boolean isEnabled) {
        mFetchAdsEnabled = isEnabled;

        MadvertiseUtil.logMessage(null, Log.DEBUG, "Set Fetching Ads to " + isEnabled);

        if (!isEnabled) {
            stopRequestThread();
            if (mAdTimer != null) {
                mAdTimer.cancel();
                mAdTimer = null;
            }
        } else {
            onViewCallback(true);
        }
    }

    /**
     * Removes the current listener that receives notifications about the ad
     * loading process
     */
    public void removeMadViewCallbackListener() {
        mCallbackListener = null;
    }

    /**
     * Returns the current listener that receives notifications about the ad
     * loading process
     *
     * @return
     */
    MadvertiseViewCallbackListener getCallbackListener() {
        return mCallbackListener;
    }

    /**
     * Sets a listener that receives notifications about the ad loading process
     *
     * @param listener
     */
    public void setMadvertiseViewCallbackListener(MadvertiseViewCallbackListener listener) {
        mCallbackListener = listener;
    }

    /**
     * Interface to receive diverse callbacks
     */
    public interface MadvertiseViewCallbackListener {
        /**
         * Notifies the listener on success or failure
         *
         * @param succeed true, if an ad could be loaded, else false
         * @param madView specified view
         */
        public void onLoaded(final boolean succeed, final MadvertiseView madView);

        /**
         * Notifies the listener when exceptions are thrown
         *
         * @param the thrown exception
         */
        public void onError(final Exception exception);

        /**
         * Notifies the listener when an illegal HTTP status code was received.
         * This method is not called when the status code is okay (200)
         *
         * @param statusCode the HTTP status code
         * @param message a message with a reason of the problem
         */
        public void onIllegalHttpStatusCode(final int statusCode, final String message);

        /**
         * Notifies the listener when an ad is clicked
         */
        public void onAdClicked();

        /**
         * Notifies the listener when a rich media ad is expanded so that the
         * application can be paused.
         */
        public void onApplicationPause();

        /**
         * Notifies the listener when a rich media ad is closed so that the
         * application can resume.
         */
        public void onApplicationResume();
    }
}
