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

package de.madvertise.test;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import de.madvertise.android.sdk.MadvertiseTracker;
import de.madvertise.android.sdk.MadvertiseView;
import de.madvertise.android.sdk.MadvertiseView.MadvertiseViewCallbackListener;

/**
 * BannerActivity.java Example activity that shows how the madvertise SDK can be
 * integrated. It shows a list view containing some countries and the madvertise
 * banner at the top. The integration of the madvertise is done in the layout
 * xml file.
 */
public class OverlayActivity extends Activity implements MadvertiseViewCallbackListener {

    private MadvertiseTracker mTracker;

    private MadvertiseView mMadView;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // set gender and age of your user.
        MadvertiseView.setAge("20-26");
        MadvertiseView.setGender(MadvertiseView.GENDER_FEMALE);

        // set the layout
        setContentView(R.layout.main);

        // set the callback listener, to receive a message when an ad was loaded
        mMadView = (MadvertiseView) findViewById(R.id.madad);
        mMadView.setMadvertiseViewCallbackListener(this);

        // Retrieve a new instance of the a MadvertiseTracker
         mTracker = MadvertiseTracker.getInstance(this);
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Report that the application becomes active
        mTracker.reportActive();
    }

    @Override
    protected void onResume() {
    	super .onResume();
    	
    	// Report that the application was downloaded
    	mTracker.reportDownload(getIntent(), "mad-TestTokn");
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Report that the application is being ended
        mTracker.reportStop();
    }

    @Override
    protected void onStop() {
        super.onStop();

        // Report that the application becomes inactive
        mTracker.reportInactive();
    }

    /**
     * Notifies the listener on success or failure
     * 
     * @param succeed true, if an ad could be loading, else false
     * @param madView specified view
     */
    public void onLoaded(final boolean succeed, final MadvertiseView madView) {
        if (succeed) {
            // ad loaded
            Log.d("YOUR_LOG_TAG", "Ad successfully loaded");
        } else {
            // ad could not be loaded
            Log.w("YOUR_LOG_TAG", "Ad could not be loaded");
        }
    }

    @Override
    public void onError(final Exception exception) {
        // This method is most times called when there is a
        // SocketTimeoutException.
        // Do something with the exception
    }

    @Override
    public void onIllegalHttpStatusCode(final int statusCode, final String message) {
        // called when the madvertise-server did not answer with a HTTP-okay
        // statuscode (200).
    }

    @Override
    public void onAdClicked() {
        Log.d("YOUR_LOG_TAG", "Ad clicked");
        // stop loading new ads and "remove" the view from the layout
        // mMadView.setFetchingAdsEnabled(false);
        // mMadView.setVisibility(View.GONE);
    }

    @Override
    public void onApplicationPause() {
        // called when a rich media ad is expanded. Should then call onPause().
        this.onPause();
    }

    @Override
    public void onApplicationResume() {
        // called when a rich media ad is close. Should then call onResume().
        this.onResume();
    }
}
