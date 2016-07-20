/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.example.android.tvleanback.ui;

import android.app.AlertDialog;
import android.app.LoaderManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v17.leanback.app.BackgroundManager;
import android.support.v17.leanback.app.BrowseFragment;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ImageCardView;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.OnItemViewSelectedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.PresenterSelector;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.support.v4.app.ActivityOptionsCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.example.android.tvleanback.LinkActivity;
import com.example.android.tvleanback.R;
import com.example.android.tvleanback.data.VideoItemLoader;
import com.example.android.tvleanback.data.VideoProvider;
import com.example.android.tvleanback.exe.FileDownloader;
import com.example.android.tvleanback.exe.ShellExecuter;
import com.example.android.tvleanback.model.Movie;
import com.example.android.tvleanback.player.DemoPlayer;
import com.example.android.tvleanback.presenter.CardPresenter;
import com.example.android.tvleanback.presenter.GridItemPresenter;
import com.example.android.tvleanback.presenter.IconHeaderItemPresenter;
import com.example.android.tvleanback.recommendation.BootupActivity;
import com.example.android.tvleanback.recommendation.UpdateRecommendationsService;
import com.example.android.tvleanback.utils.IabBroadcastListener;
import com.example.android.tvleanback.utils.IabBroadcastReceiver;
import com.example.android.tvleanback.utils.IabHelper;
import com.example.android.tvleanback.utils.IabResult;
import com.example.android.tvleanback.utils.Inventory;
import com.example.android.tvleanback.utils.Purchase;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/*
 * Main class to show BrowseFragment with header and rows of videos
 */
public class MainFragment extends BrowseFragment implements
        LoaderManager.LoaderCallbacks<HashMap<String, List<Movie>>>, DialogInterface.OnClickListener {
    private static final String TAG = "MainFragment";
    private Context mContext;
    private static int BACKGROUND_UPDATE_DELAY = 0/*changed this from 300*/;
    private static String mVideosUrl;
    private final Handler mHandler = new Handler();
    private ArrayObjectAdapter mRowsAdapter;
    private Drawable mDefaultBackground;
    private DisplayMetrics mMetrics;
    private Timer mBackgroundTimer;
    private URI mBackgroundURI;
    private BackgroundManager mBackgroundManager;

    IabHelper billingHelper = null;
    IabBroadcastReceiver mBroadcastReceiver;
    public boolean subscribed = false;

    public String get1stub(){

        return "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAnJKCPEBqdBHNFIMh9PRQKUcnGGJR3qH4bGaPUqTc3vz2OqC25i4";

    }

    public String get2stub(){

        return getActivity().getString(R.string.old_api_key);

    }

    public String get3stub(){

        return DemoPlayer.stub3;

    }

    public String get4stub(){

        return BootupActivity.stub4;

    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onActivityCreated(savedInstanceState);
        mContext = getActivity();
        loadVideoData();

        prepareBackgroundManager();
        setupUIElements();
        setupEventListeners();



    }

    @Override
    public void onDestroy() {
        if (null != mBackgroundTimer) {
            Log.d(TAG, "onDestroy: " + mBackgroundTimer.toString());
            mBackgroundTimer.cancel();
            mBackgroundTimer = null;
        }
        mBackgroundManager = null;
        super.onDestroy();

        if (billingHelper != null) billingHelper.dispose();
        billingHelper = null;

    }

    @Override
    public void onStop() {
        mBackgroundManager.release();
        super.onStop();
    }

    @Override
    public void onResume(){

        super.onResume();

        if(billingHelper != null){

            try{
                billingHelper.checkSetupDone("Done");
                //billingHelper.queryInventoryAsync(mQueryFinishedListener);
                Context context = getActivity();
                SharedPreferences sharedPref = context.getSharedPreferences("pin_store", Context.MODE_PRIVATE);
                new checkPin().execute("https://www.mstarvid.com/check_starlight_pin.php", sharedPref.getString("pin","bad"));

            }catch (IllegalStateException e){
                Log.d("MstarlightE","billing not set up yet");
            }

        }

    }

    private void prepareBackgroundManager() {
        mBackgroundManager = BackgroundManager.getInstance(getActivity());
        mBackgroundManager.attach(getActivity().getWindow());
        mDefaultBackground = getResources().getDrawable(R.drawable.default_background);
        mMetrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
    }

    private void setupUIElements() {
        setBadgeDrawable(getActivity().getResources().getDrawable(R.drawable.msvglobe));
        setTitle(getString(R.string.browse_title)); // Badge, when set, takes precedent over title
        setHeadersState(HEADERS_ENABLED);
        setHeadersTransitionOnBackEnabled(true);
        // set fastLane (or headers) background color
        setBrandColor(getResources().getColor(R.color.fastlane_background));
        // set search icon color
        setSearchAffordanceColor(getResources().getColor(R.color.search_opaque));

        setHeaderPresenterSelector(new PresenterSelector() {
            @Override
            public Presenter getPresenter(Object o) {
                return new IconHeaderItemPresenter();
            }
        });
    }

    private void loadVideoData() {
        VideoProvider.setContext(getActivity());
        mVideosUrl = getActivity().getResources().getString(R.string.catalog_url);
        getLoaderManager().initLoader(0, null, this);
    }

    private void setupEventListeners() {
        setOnSearchClickedListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getActivity(), SearchActivity.class);
                startActivity(intent);
            }
        });

        setOnItemViewClickedListener(new ItemViewClickedListener());
        setOnItemViewSelectedListener(new ItemViewSelectedListener());
    }

    /*
     * (non-Javadoc)
     * @see android.support.v4.app.LoaderManager.LoaderCallbacks#onCreateLoader(int,
     * android.os.Bundle)
     */
    @Override
    public Loader<HashMap<String, List<Movie>>> onCreateLoader(int arg0, Bundle arg1) {
        Log.d(TAG, "VideoItemLoader created ");
        return new VideoItemLoader(getActivity(), mVideosUrl);
    }

    /*
     * (non-Javadoc)
     * @see android.support.v4.app.LoaderManager.LoaderCallbacks#onLoadFinished(android
     * .support.v4.content.Loader, java.lang.Object)
     */
    @Override
    public void onLoadFinished(Loader<HashMap<String, List<Movie>>> arg0,
                               HashMap<String, List<Movie>> data) {

        mRowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());
        CardPresenter cardPresenter = new CardPresenter();

        int index = 0;

        if (null != data) {

            //start Mark's hack
            for (Map.Entry<String, List<Movie>> entry : data.entrySet()) {

                ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(cardPresenter);
                List<Movie> list = entry.getValue();

                for (int j = 0; j < list.size(); j++) {
                    listRowAdapter.add(list.get(j));
                }
                HeaderItem header = new HeaderItem(index, entry.getKey());

                if(entry.getKey().equals("MorningStar Picks")) {

                    index++;
                    mRowsAdapter.add(new ListRow(header, listRowAdapter));

                }
            }

            //end Mark's hack

            for (Map.Entry<String, List<Movie>> entry : data.entrySet()) {
                ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(cardPresenter);
                List<Movie> list = entry.getValue();

                for (int j = 0; j < list.size(); j++) {
                    listRowAdapter.add(list.get(j));
                }
                HeaderItem header = new HeaderItem(index, entry.getKey());
                index++;
                if(!entry.getKey().equals("MorningStar Picks")) {
                    mRowsAdapter.add(new ListRow(header, listRowAdapter));
                }
            }
        } else {
            Log.e(TAG, "An error occurred fetching videos");
        }

        HeaderItem gridHeader = new HeaderItem(index, getString(R.string.more_samples));

        GridItemPresenter gridPresenter = new GridItemPresenter(this);
        ArrayObjectAdapter gridRowAdapter = new ArrayObjectAdapter(gridPresenter);
        gridRowAdapter.add(getString(R.string.grid_view));
        //gridRowAdapter.add(getString(R.string.guidedstep_first_title));
        //gridRowAdapter.add(getString(R.string.error_fragment));
        gridRowAdapter.add(getString(R.string.personal_settings));
        mRowsAdapter.add(new ListRow(gridHeader, gridRowAdapter));

        setAdapter(mRowsAdapter);

        updateRecommendations();
    }

    @Override
    public void onLoaderReset(Loader<HashMap<String, List<Movie>>> arg0) {
        mRowsAdapter.clear();
    }

    protected void updateBackground(String uri) {
        int width = mMetrics.widthPixels;
        int height = mMetrics.heightPixels;
        Glide.with(getActivity())
                .load(uri)
                .asBitmap()
                .centerCrop()
                .error(mDefaultBackground)
                .into(new SimpleTarget<Bitmap>(width, height) {
                    @Override
                    public void onResourceReady(Bitmap resource,
                                                GlideAnimation<? super Bitmap>
                                                        glideAnimation) {
                        mBackgroundManager.setBitmap(resource);
                    }
                });
        mBackgroundTimer.cancel();
    }

    private void startBackgroundTimer() {
        if (null != mBackgroundTimer) {
            mBackgroundTimer.cancel();
        }
        mBackgroundTimer = new Timer();
        mBackgroundTimer.schedule(new UpdateBackgroundTask(), BACKGROUND_UPDATE_DELAY);
    }

    private void updateRecommendations() {
        Intent recommendationIntent = new Intent(getActivity(), UpdateRecommendationsService.class);
        getActivity().startService(recommendationIntent);
    }

    private class UpdateBackgroundTask extends TimerTask {

        @Override
        public void run() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mBackgroundURI != null) {
                        updateBackground(mBackgroundURI.toString());
                    }
                }
            });
        }
    }

    private final class ItemViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                  RowPresenter.ViewHolder rowViewHolder, Row row) {

            if (item instanceof Movie) {
                Movie movie = (Movie) item;
                Log.d(TAG, "Movie: " + movie.toString());
                Intent intent = new Intent(getActivity(), MovieDetailsActivity.class);
                intent.putExtra(MovieDetailsActivity.MOVIE, movie);

                Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                        getActivity(),
                        ((ImageCardView) itemViewHolder.view).getMainImageView(),
                        MovieDetailsActivity.SHARED_ELEMENT_NAME).toBundle();
                getActivity().startActivity(intent, bundle);
            } else if (item instanceof String) {
                if (((String) item).indexOf(getString(R.string.grid_view)) >= 0) {
                    Intent intent = new Intent(getActivity(), VerticalGridActivity.class);
                    startActivity(intent);
                } else if (((String) item)
                        .indexOf(getString(R.string.guidedstep_first_title)) >= 0) {
                    Intent intent = new Intent(getActivity(), GuidedStepActivity.class);
                    startActivity(intent);
                } else if (((String) item).indexOf(getString(R.string.error_fragment)) >= 0) {
                    Intent intent = new Intent(getActivity(), BrowseErrorActivity.class);
                    startActivity(intent);
                } else {

                    new DownloadFile().execute("https://s3.amazonaws.com/starlightappstore/org.pbskids.video-23.apk", "org.pbskids.video-23.apk");

                }
            }
        }
    }

    private final class ItemViewSelectedListener implements OnItemViewSelectedListener {
        @Override
        public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
                                   RowPresenter.ViewHolder rowViewHolder, Row row) {
            if (item instanceof Movie) {
                mBackgroundURI = ((Movie) item).getBackgroundImageURI();
                startBackgroundTimer();
            }

        }
    }

    private class DownloadFile extends AsyncTask<String, Void, Void>{

        Context here;

        @Override
        protected Void doInBackground(String... strings) {
            String fileUrl = strings[0];
            String fileName = strings[1];
            here = mContext;
            FileDownloader.downloadFile(fileUrl, fileName, here);
            ShellExecuter exe = new ShellExecuter();
            String dir = here.getFilesDir().getPath();
            Log.i("MSV******************", "Trying to install app....................................");
            String res = exe.Executer("pm install -r "+dir+"/org.pbskids.video-23.apk");
            Log.i("MSV******************", res);
            return null;
        }

        protected void onPostExecute(Long result) {



        }

    }

    @Override
    public void onClick(DialogInterface dialog, int id) {


    }

    public String readIt(InputStream stream, int len) throws IOException, UnsupportedEncodingException {
        Reader reader = null;
        reader = new InputStreamReader(stream, "UTF-8");
        char[] buffer = new char[len];
        reader.read(buffer);
        return new String(buffer);
    }

    private String downloadUrl(String myurl, String curPin) throws IOException {
        InputStream is = null;
        // Only display the first 500 characters of the retrieved
        // web page content.
        int len = 500;

        try {
            URL url = new URL(myurl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(10000 /* milliseconds */);
            conn.setConnectTimeout(15000 /* milliseconds */);
            conn.setRequestMethod("POST");
            conn.setDoInput(true);

            //main checks -- ping server periodically and look for updates
            Uri.Builder builder = new Uri.Builder()
                    .appendQueryParameter("apiKey",getString(R.string.api_key))
                    .appendQueryParameter("newPin", curPin);
            String query = builder.build().getEncodedQuery();
            Log.d("STARLIGHT QUERY=",query);
            OutputStream os = conn.getOutputStream();
            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(os, "UTF-8"));
            writer.write(query);
            writer.flush();
            writer.close();
            os.close();

            // Starts the query
            conn.connect();
            int response = conn.getResponseCode();
            //Log.d(DEBUG_TAG, "The response is: " + response);
            is = conn.getInputStream();

            // Convert the InputStream into a string
            String contentAsString = readIt(is, len);
            return contentAsString.trim();

            // Makes sure that the InputStream is closed after the app is
            // finished using it.
        } finally {
            if (is != null) {
                is.close();
            }
        }
    }

    private class checkPin extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls) {

            // params comes from the execute() call: params[0] is the url.
            try {
                return downloadUrl(urls[0],urls[1]);
            } catch (IOException e) {
                return "Unable to retrieve web page. URL may be invalid.";
            }
        }
        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(String result) {

            Log.i("12345----Starlight", result);
            CharSequence cs = "----starlight----done";
            //textView.setText(result);
            if(result.contains(cs)){

               /* Context context = getContext();
                SharedPreferences sharedPref = context.getSharedPreferences("pin_store", Context.MODE_PRIVATE);

                String new_pin = result.replace(cs, "");
                Log.i("12345----Starlight", new_pin);

                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putString("pin", new_pin);
                editor.commit();*/
                Log.d("MSTARLIGHT&&: ", "Linked with website");

            }

            Log.d("STARLIGHT SYSTEM NETWORK", result);
        }
    }

}

