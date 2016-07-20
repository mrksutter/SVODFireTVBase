package com.example.android.tvleanback;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.math.BigInteger;


/**
 * Created by mark on 12/6/15.
 */
public class LinkActivity extends Activity{


    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SecureRandom random = new SecureRandom();

        setContentView(R.layout.link_activity);

        Intent intent = getIntent();

        Button button = (Button) findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });

        String pin = new BigInteger(130, random).toString(32).substring(0, 5);

        new setPin().execute("https://www.mstarvid.com/set_starlight_pin.php", pin);

        TextView pinView = (TextView) findViewById(R.id.textView2);
        pinView.setText("Your linking code: " + pin);


    }


    /** Called when the activity is about to become visible. */
    @Override
    protected void onStart() {
        super.onStart();
        //Log.d(msg, "The onStart() event");


    }

    /** Called when the activity has become visible. */
    @Override
    protected void onResume() {
        super.onResume();
        // Log.d(msg, "The onResume() event");
    }

    /** Called when another activity is taking focus. */
    @Override
    protected void onPause() {
        super.onPause();
        //  Log.d(msg, "The onPause() event");
        //app.stopUpdating();

    }

    /** Called when the activity is no longer visible. */
    @Override
    protected void onStop() {
        super.onStop();
        //  Log.d(msg, "The onStop() event");
        //app.stopUpdating();
        //finish();

    }

    /** Called just before the activity is destroyed. */
    @Override
    public void onDestroy() {
        super.onDestroy();
        // Log.d(msg, "The onDestroy() event");


    }


    public String readIt(InputStream stream, int len) throws IOException, UnsupportedEncodingException {
        Reader reader = null;
        reader = new InputStreamReader(stream, "UTF-8");
        char[] buffer = new char[len];
        reader.read(buffer);
        return new String(buffer);
    }

    private String downloadUrl(String myurl, String newPin) throws IOException {
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
                    .appendQueryParameter("newPin", newPin);
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

    private class setPin extends AsyncTask<String, Void, String> {
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

                Context context = getApplicationContext();
                SharedPreferences sharedPref = context.getSharedPreferences("pin_store", Context.MODE_PRIVATE);

                String new_pin = result.replace(cs, "");
                Log.i("12345----Starlight", new_pin);

                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putString("pin", new_pin);
                editor.commit();




            }

            Log.d("STARLIGHT SYSTEM NETWORK", result);
        }
    }



}
