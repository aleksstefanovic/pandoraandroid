package com.phnks.pandora;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.TextView;
import android.os.Handler;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStreamWriter;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import android.os.StrictMode;

public class Pandora extends Activity implements OnClickListener, OnInitListener {

    //variable for checking Voice Recognition support on user device
    private static final int VR_REQUEST = 999;

    //variable for checking TTS engine data on user device
    private int MY_DATA_CHECK_CODE = 0;

    //Text To Speech instance
    private TextToSpeech repeatTTS;

    //ListView for displaying suggested words
    private ListView wordList;

    //Log tag for output information
    private final String LOG_TAG = "Pandora";

    private final int SPLASH_DISPLAY_LENGTH = 1000;

    private String say = "";

    final String pandorabaseURL = "https://pandora0.herokuapp.com";

    /**
     * Create the Activity, prepare to process speech and repeat
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {

        //call superclass
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);
        //set content view
        setContentView(R.layout.pandora_main);

        //gain reference to speak button
        Button speechBtn = (Button) findViewById(R.id.speech_btn);
        //gain reference to word list
        wordList = (ListView) findViewById(R.id.word_list);

        //find out whether speech recognition is supported
        PackageManager packManager = getPackageManager();
        List<ResolveInfo> intActivities = packManager.queryIntentActivities
                (new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0);
        if (intActivities.size() != 0) {
            //speech recognition is supported - detect user button clicks
            speechBtn.setOnClickListener(this);
            //prepare the TTS to repeat chosen words
            Intent checkTTSIntent = new Intent();
            //check TTS data
            checkTTSIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
            //start the checking Intent - will retrieve result in onActivityResult
            startActivityForResult(checkTTSIntent, MY_DATA_CHECK_CODE);
        } else {
            //speech recognition not supported, disable button and output message
            speechBtn.setEnabled(false);
            Toast.makeText(this, "Speech recognition not supported", Toast.LENGTH_LONG).show();
        }
        //listenToSpeech();

        //Button talkBtn = (Button) findViewById(R.id.talk_btn);
        //detect user click of talk button
        /*talkBtn.setOnClickListener (new OnClickListener()
        {
            @Override
            public void onClick (View v)
            {
                //TODO Auto-generated method stub
                repeatTTS.speak(say, TextToSpeech.QUEUE_FLUSH, null);
            }
        });*/

    }

    /**
     * Called when the user presses the speak button
     */
    public void onClick(View v) {
        if (v.getId() == R.id.speech_btn) {
            //listen for results
            listenToSpeech();
        }
    }

    /**
     * Instruct the app to listen for user speech input
     */
    private void listenToSpeech() {

        //start the speech recognition intent passing required data
        Intent listenIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        //indicate package
        listenIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getClass().getPackage().getName());
        //message to display while listening
        listenIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak");
        //set speech model
        listenIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        //specify number of results to retrieve
        listenIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 10);

        //start listening
        startActivityForResult(listenIntent, VR_REQUEST);
    }

    /**
     * onActivityResults handles:
     * - retrieving results of speech recognition listening
     * - retrieving result of TTS data check
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        //check speech recognition result
        if (requestCode == VR_REQUEST && resultCode == RESULT_OK) {
            //store the returned word list as an ArrayList
            ArrayList<String> suggestedWords = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            say = suggestedWords.get(0);
            //set the retrieved list to display in the ListView using an ArrayAdapter
            //wordList.setAdapter(new ArrayAdapter<String> (this, R.layout.word, suggestedWords));
        }

        //returned from TTS data check
        if (requestCode == MY_DATA_CHECK_CODE) {
            //we have the data - create a TTS instance
            if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                repeatTTS = new TextToSpeech(this, this);
            }
            //data not installed, prompt the user to install it
            else {
                //intent will take user to TTS download page in Google Play
                Intent installTTSIntent = new Intent();
                installTTSIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                startActivity(installTTSIntent);
            }
        }
        if (say.length() != 0) {
            System.out.println("SAY:" + say);
            RetrieveFeedTask job = new RetrieveFeedTask();
            job.execute(pandorabaseURL+"/phrases", "{\"phrase\":\""+say+"\"}");
        } else {
            System.out.println("SAY: no words :(");
        }
        //call superclass method
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * onInit fires when TTS initializes
     */
    public void onInit(int initStatus) {
        //if successful, set locale
        if (initStatus == TextToSpeech.SUCCESS)
            repeatTTS.setLanguage(Locale.UK);//***choose your own locale here***
    }

    public static String executePost(String targetURL, String payload) {
        //StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork().penaltyLog().build());
        HttpURLConnection connection = null;

        try {
            // Create connection
            URL url = new URL(targetURL);
            connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setUseCaches(false);
            connection.setDoOutput(true);

            OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream(), "UTF-8");
            writer.write(payload);
            writer.close();

            // Get Response
            InputStream is = connection.getInputStream();
            BufferedReader rd = new BufferedReader(new InputStreamReader(is));
            StringBuilder response = new StringBuilder(); // or StringBuffer if
            // not Java 5+
            String line;
            while ((line = rd.readLine()) != null) {
                response.append(line);
                response.append('\r');
            }
            rd.close();
            return response.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    class RetrieveFeedTask extends AsyncTask<String, Void, String> {

        protected String doInBackground(String[] params) {
            // do above Server call here
            //System.out.println(params.length);
            //System.out.println(params[0]);
            //System.out.println(params[1]);
            String response = executePost(params[0], params[1]);
            return response;
        }

        protected void onPostExecute(String message) {
            //process message
            System.out.println(message);
            repeatTTS.speak(message, TextToSpeech.QUEUE_FLUSH, null);
        }
    }
}
