/*
 * Copyright 2011 madvertise Mobile Advertising GmbH
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

/**
 * Container for rich media ads using the MRAID version 1.0 - standard
 */

package de.madvertise.android.sdk;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONException;
import org.json.JSONObject;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Picture;
import android.net.Uri;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.VideoView;
import de.madvertise.android.sdk.MadvertiseView.AnimationEndListener;
import de.madvertise.android.sdk.MadvertiseView.MadvertiseViewCallbackListener;

public class MadvertiseMraidView extends WebView {

    protected static final Pattern sUrlSplitter = Pattern.compile("((?:http|file):\\/\\/.*(?:\\.|_)+.*\\/)(.*\\.js)");
    private static final String TAG = MadvertiseMraidView.class.getCanonicalName();
    private static String sCachePath;
    private static final int CLOSE_BUTTON_SIZE = 50;
    protected static final int STATE_LOADING = 0;
    protected static final int STATE_HIDDEN = 1;
    protected static final int STATE_DEFAULT = 2;
    protected static final int STATE_EXPANDED = 3;
    private boolean mJavaIsReady;
    private boolean mJsIsReady;
    private int mState;
    private int mIndex;
    private boolean mOnScreen;
    private int mPlacementType;
    private FrameLayout mExpandLayout;
    private ViewGroup mOriginalParent;
    private Handler mLoadingCompletedHandler;
    private ExpandProperties mExpandProperties;
    private MadvertiseViewCallbackListener mListener;
    private AnimationEndListener mAnimationEndListener;
    private boolean mViewable;
    private Handler mHandler;


    public MadvertiseMraidView(Context context, MadvertiseViewCallbackListener listener,
            AnimationEndListener animationEndListener, Handler loadingCompletedHandler) {
        this(context);
        this.mLoadingCompletedHandler = loadingCompletedHandler;
        this.mAnimationEndListener = animationEndListener;
        this.mListener = listener;
    }

    public MadvertiseMraidView(Context context) {
        super(context);
        mHandler = new Handler();
        sCachePath = "/data/data/" + getContext().getPackageName() + "/cache/webviewCache/mraid";
        setVerticalScrollBarEnabled(false);
        setHorizontalScrollBarEnabled(false);
        setScrollBarStyle(WebView.SCROLLBARS_OUTSIDE_OVERLAY);
        setBackgroundColor(Color.TRANSPARENT);
        WebSettings settings = getSettings();
        settings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        settings.setJavaScriptEnabled(true);
        settings.setPluginsEnabled(true);
        setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                Log.d("Video", "onShow");
                super.onShowCustomView(view, callback);
                if (view instanceof FrameLayout){
                    FrameLayout frame = (FrameLayout) view;
                    if (frame.getFocusedChild() instanceof VideoView){
                        VideoView video = (VideoView) frame.getFocusedChild();
                        frame.removeView(video);
                        ((Activity)getContext()).setContentView(video);
                        video.start();
                    }
                }
            }
        });        
        addJavascriptInterface(mBridge, "mraid_bridge");
    }

    protected void loadAd(MadvertiseAd ad) {
        loadAd(ad.getBannerUrl());
    }

    protected void loadAd(String url) {
        Matcher m = sUrlSplitter.matcher(url);
        if (m.matches()) {
            final String jsFile = m.group(2);
            final String baseUrl = m.group(1);
            MadvertiseUtil.logMessage(TAG, Log.INFO, "loading javascript Ad: baseUrl=" + baseUrl + " jsFile=" + jsFile);
            loadDataWithBaseURL(baseUrl, "<html><head>" +
                        "<script type=\"text/javascript\" src=\"mraid.js\"/>" +
                        "<script type=\"text/javascript\" src=\"" + jsFile + "\"/>" +
                        "</head><body>MRAID Ad</body></html>", "text/html", "utf8", null);
        } else {
            MadvertiseUtil.logMessage(TAG, Log.INFO, "loading html Ad: "+url);
            loadUrl(url);
        }
    }

    //TODO: We can't use this since it's only available since 2.2
    @Override
    public void loadUrl(String url, Map<String, String> extraHeaders) {
        MadvertiseUtil.logMessage(TAG, Log.INFO, "loadURL "+url);
        if (!url.startsWith("javascript:")) {
            prepareMraid(url.substring(0, url.lastIndexOf("/") - 1));
        }
        //TODO: We can't use this since it's only available since 2.2
        super.loadUrl(url, extraHeaders);
    }

    @Override
    public void loadDataWithBaseURL(String baseUrl, String data, String mimeType, String encoding, String historyUrl) {
        prepareMraid(baseUrl);
        super.loadDataWithBaseURL(baseUrl, data, mimeType, encoding, historyUrl);
    }

    private void prepareMraid(String baseUrl) {
        if (baseUrl.startsWith("http:")) { // because cache hack works only for online resources
            File mraid = new File(sCachePath);
            if (!mraid.exists()) {
                try { // copy mraid.js to cache directory
                    int read;
                    byte[] buffer = new byte[1024];
                    FileOutputStream out = new FileOutputStream(mraid);
                    InputStream in = getContext().getResources().openRawResource(de.madvertise.android.sdk.R.raw.mraid);
                    while((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                } catch (Exception e) {
                    MadvertiseUtil.logMessage(TAG, Log.ERROR, e + " while copying mraid.js to cache directory");
                }
            }
            SQLiteDatabase cache = getContext().openOrCreateDatabase("webviewCache.db", SQLiteDatabase.OPEN_READWRITE, null);
            ContentValues entry = new ContentValues();
            String url = baseUrl + "mraid.js";
            entry.put("url", url);
            entry.put("filepath", "mraid");
            entry.put("mimetype", "text/javascript");
            entry.put("contentlength", mraid.length());
            cache.insert("cache", null, entry);
            cache.close();
            MadvertiseUtil.logMessage(TAG, Log.DEBUG, "prepared mraid.js for " + url);
            // TODO further long running (mraid 2.0) initialization here..
            mJavaIsReady = true;
            checkReady();
        }
        mJavaIsReady = true;
        checkReady();
    }

    private void checkReady() {
        if (mJavaIsReady && mJsIsReady && mState != STATE_DEFAULT) {
            DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
            mExpandProperties = new ExpandProperties(metrics.widthPixels, metrics.heightPixels);
            injectJs("mraid.setExpandProperties(" + mExpandProperties.toJson() + ");");
            fireEvent("ready");
            setState(STATE_DEFAULT);
            checkViewable();
            if (mLoadingCompletedHandler != null)
                mLoadingCompletedHandler.sendEmptyMessage(MadvertiseView.MAKE_VISIBLE);
        }
    }

    private void checkViewable() {
        boolean viewable;
        if (mOnScreen && getVisibility() == View.VISIBLE) {
            viewable = true;
        } else {
            viewable = false;
        }
        if (viewable != mViewable && mState == STATE_DEFAULT) {
            mViewable = viewable;
            injectJs("mraid.setViewable(" + mViewable + ");");
        }
    }

    // to be called from the Ad (js side)
    Object mBridge = new Object() {

        @SuppressWarnings("unused") // because it IS used from the js side
        public void notifyReady() {
            mJsIsReady = true;
            checkReady();
        }

        public void expand() {
            post(new Runnable() {
                @Override
                public void run() {
                    MadvertiseMraidView.this.resize(
                            mExpandProperties.width,
                            mExpandProperties.height);
                }
            });
            setState(STATE_EXPANDED);
        }

        @SuppressWarnings("unused") // because it IS used from the js side
        public void expand(String url) {
            expand();            
            loadUrl(url);
            scrollBy(mExpandProperties.scrollX, mExpandProperties.scrollY);
        }

        @SuppressWarnings("unused") // because it IS used from the js side
        public void close() {
            post(new Runnable() {
                @Override
                public void run() {
                    MadvertiseMraidView.this.close();
                }
            });
        }

		@SuppressWarnings("unused")	// because it IS used from the js side
		public void open(final String url) {
			post(new Runnable() {
				@Override
				public void run() {
					if (mListener != null) {
						mListener.onAdClicked();
					}
					final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url),
							getContext().getApplicationContext(),
							MadvertiseActivity.class);
					intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					getContext().startActivity(intent);					
				}
			});
		}

        @SuppressWarnings("unused") // because it IS used from the js side
        public void setExpandProperties(String json) {
            mExpandProperties.readJson(json);
        }
    };



    // to be called from the App (java side)
    public int getPlacementType() {
        return mPlacementType;        
    }

    public void setPlacementType(int placementType) { 
        if(placementType != MadvertiseUtil.PLACEMENT_TYPE_INTERSTITIAL && placementType != MadvertiseUtil.PLACEMENT_TYPE_INLINE) {
            MadvertiseUtil.logMessage(null, Log.WARN, "Placement type must be one of MadvertiseUtil.PLACEMENT_TYPE_INLINE or MadvertiseUtil.PLACEMENT_TYPE_INTERSTITIAL");
        } else {
            mPlacementType = placementType;
            injectJs("mraid.setPlacementType(" + mPlacementType + ");");
        }
    }

    protected void setState(int state) {
        mState = state;
        injectJs("mraid.setState('" + state + "');");
    }

    protected void fireEvent(String event) {
        injectJs("mraid.fireEvent('" + event + "');");
    }
    
    protected void fireErrorEvent(String message, String action) {
        injectJs("mraid.fireErrorEvent('" + message + "', '"+action+"');");
    }

    protected ExpandProperties getExpandProperties() {
        return mExpandProperties;
    }

    protected void injectJs(String jsCode) {
        loadUrl("javascript:" + jsCode);
    }

    private void resize(final int width, final int height) {
        final FrameLayout content = (FrameLayout)getRootView().findViewById(android.R.id.content);
        final FrameLayout.LayoutParams adParams = new FrameLayout.LayoutParams(width, height);
        final View placeholderView = new View(getContext());
        placeholderView.setLayoutParams(getLayoutParams());
        mExpandLayout = new FrameLayout(getContext());
        final FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        layoutParams.gravity = Gravity.CENTER;
        mExpandLayout.setLayoutParams(layoutParams);
        mOriginalParent = (ViewGroup)getParent();

        int index = 0;
        int count = mOriginalParent.getChildCount();
        for (index = 0; index < count; index++) {
            if (mOriginalParent.getChildAt(index) == this)
                break;
        }

        mIndex = index;

        this.setLayoutParams(adParams);
        mOriginalParent.removeView(this);
        mExpandLayout.addView(this);

        final ImageButton closeButton = new ImageButton(getContext());
        closeButton.setId(43);
        final FrameLayout.LayoutParams closeButtonParams = new FrameLayout.LayoutParams(
                CLOSE_BUTTON_SIZE, CLOSE_BUTTON_SIZE);
        closeButtonParams.gravity = Gravity.RIGHT;
        closeButton.setLayoutParams(closeButtonParams);
        closeButton.setBackgroundColor(Color.TRANSPARENT);
        closeButton.setOnClickListener(new OnClickListener() {            
            @Override
            public void onClick(View v) {
                close();
            }
        });

        if (!mExpandProperties.useCustomClose) {
            closeButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        }
        
        mExpandLayout.addView(closeButton);

        content.addView(mExpandLayout);
        mOriginalParent.addView(placeholderView, mIndex);
        mState = STATE_EXPANDED;
    }

    private void close() {
        switch (mState) {
            case STATE_EXPANDED:
                if (mExpandLayout != null) {
                    ((ViewGroup)mExpandLayout.getParent()).removeView(mExpandLayout);
                    mExpandLayout.removeView(this);
                    this.setLayoutParams(mOriginalParent.getChildAt(mIndex).getLayoutParams());
                    mOriginalParent.removeViewAt(mIndex);
                    mOriginalParent.addView(this, mIndex);
                }
                setState(STATE_DEFAULT);
                break;
            case STATE_DEFAULT:
                // Set MadvertiseView to GONE. Note: This will cause this view
                // to be GONE too.
                ((ViewGroup)getParent()).setVisibility(View.GONE);
                setState(STATE_HIDDEN);
                break;
        }
    }


    @Override
    protected void onAttachedToWindow() {
        mOnScreen = true;
        checkViewable();
    }
    
    @Override
    protected void onDetachedFromWindow() {
        mOnScreen = false;
        checkViewable();
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        checkViewable();
    }

    @Override
    protected void onAnimationEnd() {
        super.onAnimationEnd();
        if (mAnimationEndListener != null) {
            mAnimationEndListener.onAnimationEnd();
        }
    }



    public class ExpandProperties {

        private static final String WIDTH = "width";
        private static final String HEIGHT = "height";
        private static final String USE_CUSTOM_CLOSE = "useCustomClose";
        private static final String IS_MODAL = "isModal";
        private int mMaxWidth;
        private int mMaxHeight;
        public int scrollX;
        public int scrollY;
        public int width;
        public int height;
        public boolean useCustomClose;
        public boolean isModal;

        public ExpandProperties(final int width, final int height) {
            this.width = width;
            this.height = height;
            this.mMaxWidth = width;
            this.mMaxHeight = height;
        }

        public String toJson() {
            final JSONObject jsonObject = new JSONObject();
            try {
                jsonObject.put(WIDTH, width);
                jsonObject.put(HEIGHT, height);
                jsonObject.put(USE_CUSTOM_CLOSE, useCustomClose);
                jsonObject.put(IS_MODAL, isModal);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return jsonObject.toString();
        }

        public void readJson(final String json) {
            JSONObject jsonObject;
            try {
                jsonObject = new JSONObject(json);

                if (jsonObject.has(HEIGHT)) {
                    width = jsonObject.getInt(WIDTH);
                }
                if (jsonObject.has(WIDTH)) {
                    height = jsonObject.getInt(HEIGHT);
                }
                if (jsonObject.has(USE_CUSTOM_CLOSE)) {
                    useCustomClose = jsonObject.getBoolean(USE_CUSTOM_CLOSE);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            checkSizeParams();
        }

        private void checkSizeParams() {
        	scrollX = width / 2;
        	scrollY = height / 2;
        	
            if (width > mMaxWidth || height > mMaxHeight) {
                final float ratio = height / (float)width;
                // respect the ratio
                final int diffWidth = (int)((float)(width - mMaxWidth) * ratio);
                final int diffHeight = (int)((float)(height - mMaxHeight) * ratio);

                if (diffWidth > diffHeight) {
                    width = mMaxWidth;
                    height = (int)(width * ratio);
                } else {
                    height = mMaxHeight;
                    width = (int)(height / ratio);
                }
            }
        }
    }
}
