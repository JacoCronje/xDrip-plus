package com.eveningoutpost.dexdrip;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.DrawerLayout;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.eveningoutpost.dexdrip.ImportedLibraries.dexcom.Constants;
import com.eveningoutpost.dexdrip.Models.ActiveBluetoothDevice;
import com.eveningoutpost.dexdrip.Models.BgReading;
import com.eveningoutpost.dexdrip.Models.Calibration;
import com.eveningoutpost.dexdrip.Models.JoH;
import com.eveningoutpost.dexdrip.Models.Treatments;
import com.eveningoutpost.dexdrip.Models.UserError;
import com.eveningoutpost.dexdrip.Services.PlusSyncService;
import com.eveningoutpost.dexdrip.Services.WixelReader;
import com.eveningoutpost.dexdrip.UtilityModels.BgGraphBuilder;
import com.eveningoutpost.dexdrip.UtilityModels.CollectionServiceStarter;
import com.eveningoutpost.dexdrip.UtilityModels.Intents;
import com.eveningoutpost.dexdrip.UtilityModels.Notifications;
import com.eveningoutpost.dexdrip.UtilityModels.SendFeedBack;
import com.eveningoutpost.dexdrip.UtilityModels.UpdateActivity;
import com.eveningoutpost.dexdrip.utils.ActivityWithMenu;
import com.eveningoutpost.dexdrip.utils.DatabaseUtil;
import com.eveningoutpost.dexdrip.utils.DisplayQRCode;
import com.eveningoutpost.dexdrip.utils.SdcardImportExport;
import com.google.gson.Gson;
import com.nispok.snackbar.Snackbar;
import com.nispok.snackbar.SnackbarManager;
import com.nispok.snackbar.enums.SnackbarType;
import com.nispok.snackbar.listeners.ActionClickListener;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import lecho.lib.hellocharts.gesture.ZoomType;
import lecho.lib.hellocharts.listener.ViewportChangeListener;
import lecho.lib.hellocharts.model.Viewport;
import lecho.lib.hellocharts.view.LineChartView;
import lecho.lib.hellocharts.view.PreviewLineChartView;




public class Home extends ActivityWithMenu {
    static String TAG = "jamorham: " + Home.class.getSimpleName();
    public static String menu_name = "xDrip";
    public static boolean activityVisible = false;
    public static boolean invalidateMenu = false;
    public static Context staticContext;
    public static boolean is_follower = false;
    private boolean updateStuff;
    private boolean updatingPreviewViewport = false;
    private boolean updatingChartViewport = false;
    private BgGraphBuilder bgGraphBuilder;
    private SharedPreferences prefs;
    private Viewport tempViewport = new Viewport();
    private Viewport holdViewport = new Viewport();
    private boolean isBTShare;
    private BroadcastReceiver _broadcastReceiver;
    private BroadcastReceiver newDataReceiver;
    private LineChartView            chart;
    private ImageButton btnSpeak;
    private ImageButton btnApprove;
    private ImageButton btnCancel;
    private ImageButton btnCarbohydrates;
    private ImageButton btnBloodGlucose;
    private ImageButton btnInsulinDose;
    private ImageButton btnTime;
    private TextView voiceRecognitionText;
    private TextView textCarbohydrates;
    private TextView textBloodGlucose;
    private TextView textInsulinDose;
    private TextView textTime;
    private final int REQ_CODE_SPEECH_INPUT = 1994;
    private static double last_speech_time = 0;
    private PreviewLineChartView previewChart;
    private TextView dexbridgeBattery;
    private TextView currentBgValueText;
    private TextView notificationText;
    private boolean alreadyDisplayedBgInfoCommon = false;
    private boolean recognitionRunning = false;
    double thisnumber = -1;
    double thisglucosenumber = 0;
    double thiscarbsnumber = 0;
    double thisinsulinnumber = 0;
    double thistimeoffset = 0;
    String thisword = "";
    private static String nexttoast;
    boolean carbsset = false;
    boolean insulinset = false;
    boolean glucoseset = false;
    boolean timeset = false;
    private wordDataWrapper searchWords = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        staticContext = getApplicationContext();
        set_is_follower();

        prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        checkEula();
        setContentView(R.layout.activity_home);

        this.dexbridgeBattery = (TextView) findViewById(R.id.textBridgeBattery);
        this.currentBgValueText = (TextView) findViewById(R.id.currentBgValueRealTime);
        if (BgGraphBuilder.isXLargeTablet(getApplicationContext())) {
            this.currentBgValueText.setTextSize(100);
        }
        this.notificationText = (TextView) findViewById(R.id.notices);
        if (BgGraphBuilder.isXLargeTablet(getApplicationContext())) {
            this.notificationText.setTextSize(40);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent();
            String packageName = getPackageName();
            Log.d(TAG, "Maybe ignoring battery optimization");
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(packageName) &&
                    !prefs.getBoolean("requested_ignore_battery_optimizations", false)) {
                Log.d(TAG, "Requesting ignore battery optimization");

                prefs.edit().putBoolean("requested_ignore_battery_optimizations", true).apply();
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                startActivity(intent);
            }
        }


        // jamorham voice input
        this.voiceRecognitionText = (TextView) findViewById(R.id.treatmentTextView);
        this.textBloodGlucose = (TextView) findViewById(R.id.textBloodGlucose);
        this.textCarbohydrates = (TextView) findViewById(R.id.textCarbohydrate);
        this.textInsulinDose = (TextView) findViewById(R.id.textInsulinUnits);
        this.textTime = (TextView) findViewById(R.id.textTimeButton);
        this.btnBloodGlucose = (ImageButton) findViewById(R.id.bloodTestButton);
        this.btnCarbohydrates = (ImageButton) findViewById(R.id.buttonCarbs);
        this.btnInsulinDose = (ImageButton) findViewById(R.id.buttonInsulin);
        this.btnCancel = (ImageButton) findViewById(R.id.cancelTreatment);
        this.btnApprove = (ImageButton) findViewById(R.id.approveTreatment);
        this.btnTime = (ImageButton) findViewById(R.id.timeButton);

        hideAllTreatmentButtons();

        if (searchWords == null) {
            initializeSearchWords("");
        }

        this.btnSpeak = (ImageButton) findViewById(R.id.btnSpeak);
        btnSpeak.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                promptSpeechInput();
            }
        });
        btnSpeak.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                promptTextInput();
                return true;
            }
        });


        btnCancel.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                hideAllTreatmentButtons();
            }
        });

        btnApprove.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                // proccess and approve all treatments
                // TODO Handle BG Tests here also
                Treatments.create(thiscarbsnumber, thisinsulinnumber, Treatments.getTimeStampWithOffset(thistimeoffset));
                hideAllTreatmentButtons();
                if (hideTreatmentButtonsIfAllDone()) {
                    updateCurrentBgInfo("approve button");
                }
            }
        });

        btnInsulinDose.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                // proccess and approve treatment
                textInsulinDose.setVisibility(View.INVISIBLE);
                btnInsulinDose.setVisibility(View.INVISIBLE);
                Treatments.create(0, thisinsulinnumber, Treatments.getTimeStampWithOffset(thistimeoffset));
                if (hideTreatmentButtonsIfAllDone()) {
                    updateCurrentBgInfo("insulin button");
                }
            }
        });
        btnCarbohydrates.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                // proccess and approve treatment
                textCarbohydrates.setVisibility(View.INVISIBLE);
                btnCarbohydrates.setVisibility(View.INVISIBLE);
                Treatments.create(thiscarbsnumber, 0, Treatments.getTimeStampWithOffset(thistimeoffset));
                if (hideTreatmentButtonsIfAllDone()) {
                    updateCurrentBgInfo("carbs button");
                }
            }
        });

        btnBloodGlucose.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                // TODO BG Tests to be possible without being calibrations
                // TODO Offer Choice? Reject calibrations under various circumstances
                // This should be wrapped up in a generic method
                Intent calintent = new Intent();
                calintent.setClassName("com.eveningoutpost.dexdrip", "com.eveningoutpost.dexdrip.AddCalibration");
                calintent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                calintent.putExtra("bg_string", JoH.qs(thisglucosenumber));
                calintent.putExtra("bg_age", "0");
                getApplicationContext().startActivity(calintent);

                textBloodGlucose.setVisibility(View.INVISIBLE);
                btnBloodGlucose.setVisibility(View.INVISIBLE);
                if (hideTreatmentButtonsIfAllDone()) {
                    updateCurrentBgInfo("bg button");
                }
            }
        });

        btnTime.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                // clears time if clicked
                textTime.setVisibility(View.INVISIBLE);
                btnTime.setVisibility(View.INVISIBLE);
                if (hideTreatmentButtonsIfAllDone()) {
                    updateCurrentBgInfo("time button");
                }
            }
        });

        activityVisible = true;
        PlusSyncService.startSyncService(getApplicationContext(),"HomeOnCreate");
        ParakeetHelper.notifyOnNextCheckin(false);
    }

    private boolean hideTreatmentButtonsIfAllDone() {
        if ((btnBloodGlucose.getVisibility() == View.INVISIBLE) &&
                (btnCarbohydrates.getVisibility() == View.INVISIBLE) &&
                (btnInsulinDose.getVisibility() == View.INVISIBLE)) {
            hideAllTreatmentButtons(); // we clear values here also
            return true;
        } else {
            return false;
        }
    }

    private void hideAllTreatmentButtons() {
        textBloodGlucose.setVisibility(View.INVISIBLE);
        textCarbohydrates.setVisibility(View.INVISIBLE);
        btnApprove.setVisibility(View.INVISIBLE);
        btnCancel.setVisibility(View.INVISIBLE);
        btnCarbohydrates.setVisibility(View.INVISIBLE);
        textInsulinDose.setVisibility(View.INVISIBLE);
        btnInsulinDose.setVisibility(View.INVISIBLE);
        btnBloodGlucose.setVisibility(View.INVISIBLE);
        voiceRecognitionText.setVisibility(View.INVISIBLE);
        textTime.setVisibility(View.INVISIBLE);
        btnTime.setVisibility(View.INVISIBLE);

        thiscarbsnumber = 0;
        thisinsulinnumber = 0;
        thistimeoffset = 0;
        if (chart != null) {
            chart.setAlpha((float) 1);
        }
    }
    // jamorham voiceinput methods

    public String readTextFile(InputStream inputStream) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        byte buf[] = new byte[1024];
        int len;
        try {
            while ((len = inputStream.read(buf)) != -1) {
                outputStream.write(buf, 0, len);
            }
            outputStream.close();
            inputStream.close();
        } catch (IOException e) {

        }
        return outputStream.toString();
    }

    private void initializeSearchWords(String jstring) {
        Log.d(TAG, "Initialize Search words");
        wordDataWrapper lcs = new wordDataWrapper();
        try {

            Resources res = getResources();
            InputStream in_s = res.openRawResource(R.raw.initiallexicon);

            String input = readTextFile(in_s);

            Gson gson = new Gson();
            lcs = gson.fromJson(input, wordDataWrapper.class);

        } catch (Exception e) {
            e.printStackTrace();

            Log.d(TAG, "Got exception during search word load: " + e.toString());
            Toast.makeText(getApplicationContext(),
                    "Problem loading speech lexicon!",
                    Toast.LENGTH_LONG).show();
        }
        Log.d(TAG, "Loaded Words: " + Integer.toString(lcs.entries.size()));
        searchWords = lcs;
    }

    private void promptTextInput() {

        if (recognitionRunning) return;
        recognitionRunning = true;

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Type treatment\neg: units x.x");
// Set up the input
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);
// Set up the buttons
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                voiceRecognitionText.setText(input.getText().toString());
                voiceRecognitionText.setVisibility(View.VISIBLE);
                last_speech_time = JoH.ts();
                naturalLanguageRecognition(input.getText().toString());

            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        final AlertDialog dialog = builder.create();
        input.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    if (dialog != null)
                        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                }
            }
        });
        dialog.show();
        recognitionRunning = false;
    }

    /**
     * Showing google speech input dialog
     */
    private void promptSpeechInput() {


        if (recognitionRunning) return;
        recognitionRunning = true;

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        // intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US"); // debug voice
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                "Speak your treatment eg:\nx.x units insulin / xx grams carbs");

        try {
            startActivityForResult(intent, REQ_CODE_SPEECH_INPUT);
        } catch (ActivityNotFoundException a) {
            Toast.makeText(getApplicationContext(),
                    "Speech recognition is not supported",
                    Toast.LENGTH_LONG).show();
        }

    }

    private String classifyWord(String word) {
        // convert fuzzy recognised word to our keyword from lexicon
        for (wordData thislex : searchWords.entries) {
            if (thislex.matchWords.contains(word)) {
                Log.d(TAG, "Matched spoken word: " + word + " => " + thislex.lexicon);
                return thislex.lexicon;
            }
        }
        Log.d(TAG, "Could not match spoken word: " + word);
        return null; // if cannot match
    }

    private void naturalLanguageRecognition(String allWords) {
        if (searchWords == null) {

            Toast.makeText(getApplicationContext(),
                    "Word lexicon not loaded!",
                    Toast.LENGTH_LONG).show();
            return;
        }

        allWords = allWords.replaceAll(":", "."); // fix real times
        allWords = allWords.replaceAll("(\\d)([a-zA-Z])", "$1 $2"); // fix like 22mm
        allWords = allWords.replaceAll("([0-9].[0-9])([0-9][0-9])", "$1 $2"); // fix multi number order like blood 3.622 grams
        allWords = allWords.toLowerCase();

        Log.d(TAG, "Processing speech input: " + allWords);

        if (allWords.contentEquals("delete last treatment")
                || allWords.contentEquals("cancel last treatment")
                || allWords.contentEquals("erase last treatment")) {
            Treatments.delete_last(true);
            updateCurrentBgInfo("delete last treatment");
        }

        if ((allWords.contentEquals("delete all treatments"))
                || (allWords.contentEquals("delete all treatment"))) {
            Treatments.delete_all(true);
            updateCurrentBgInfo("delete all treatment");
        }

        // reset parameters for new speech
        glucoseset = false;
        insulinset = false;
        carbsset = false;
        timeset = false;
        thisnumber = -1;
        thistimeoffset = 0;
        thisword = "";

        String[] wordsArray = allWords.split(" ");
        for (int i = 0; i < wordsArray.length; i++) {
            // per word in input stream
            try {
                double thisdouble = Double.parseDouble(wordsArray[i]);
                thisnumber = thisdouble; // if no exception
                handleWordPair();
            } catch (NumberFormatException nfe) {
                // detection of number or not
                String result = classifyWord(wordsArray[i]);
                if (result != null)
                    thisword = result;
                handleWordPair();
            }
        }
    }

    private void handleWordPair() {
        boolean preserve = false;
        if ((thisnumber == -1) || (thisword == "")) return;

        Log.d(TAG, "GOT WORD PAIR: " + thisnumber + " = " + thisword);

        switch (thisword) {

            case "rapid":
                if ((insulinset == false) && (thisnumber > 0)) {
                    thisinsulinnumber = thisnumber;
                    textInsulinDose.setText(Double.toString(thisnumber) + " units");
                    Log.d(TAG, "Rapid dose: " + Double.toString(thisnumber));
                    insulinset = true;
                    btnInsulinDose.setVisibility(View.VISIBLE);
                    textInsulinDose.setVisibility(View.VISIBLE);
                } else {
                    Log.d(TAG, "Rapid dose already set");
                    preserve = true;
                }
                break;

            case "carbs":
                if ((carbsset == false) && (thisnumber > 0)) {
                    thiscarbsnumber = thisnumber;
                    textCarbohydrates.setText(Integer.toString((int) thisnumber) + " carbs");
                    carbsset = true;
                    Log.d(TAG, "Carbs eaten: " + Double.toString(thisnumber));
                    btnCarbohydrates.setVisibility(View.VISIBLE);
                    textCarbohydrates.setVisibility(View.VISIBLE);
                } else {
                    Log.d(TAG, "Carbs already set");
                    preserve = true;
                }
                break;

            case "blood":
                if ((glucoseset == false) && (thisnumber > 0)) {
                    thisglucosenumber = thisnumber;
                    if (bgGraphBuilder.doMgdl) {
                        textBloodGlucose.setText(Double.toString(thisnumber) + " mg/dl");
                    } else {
                        textBloodGlucose.setText(Double.toString(thisnumber) + " mmol/l");
                    }

                    Log.d(TAG, "Blood test: " + Double.toString(thisnumber));
                    glucoseset = true;
                    btnBloodGlucose.setVisibility(View.VISIBLE);
                    textBloodGlucose.setVisibility(View.VISIBLE);

                } else {
                    Log.d(TAG, "Blood glucose already set");
                    preserve = true;
                }
                break;

            case "time":
                Log.d(TAG, "processing time keyword");
                if ((timeset == false) && (thisnumber > 0)) {

                    DecimalFormat df = new DecimalFormat("#");
                    df.setMinimumIntegerDigits(2);
                    df.setMinimumFractionDigits(2);
                    df.setMaximumFractionDigits(2);
                    df.setMaximumIntegerDigits(2);

                    Calendar c = Calendar.getInstance();

                    SimpleDateFormat simpleDateFormat1 =
                            new SimpleDateFormat("dd/M/yyyy ");
                    SimpleDateFormat simpleDateFormat2 =
                            new SimpleDateFormat("dd/M/yyyy hh.mm"); // TODO double check 24 hour 12.00 etc
                    String datenew = simpleDateFormat1.format(c.getTime()) + df.format(thisnumber);

                    Log.d(TAG, "Time Timing data datenew: " + datenew);

                    Date datethen;
                    Date datenow = new Date();

                    try {
                        datethen = simpleDateFormat2.parse(datenew);
                        double difference = datenow.getTime() - datethen.getTime();
                        // is it more than 1 hour in the future? If so it must be yesterday
                        if (difference < -(1000 * 60 * 60)) {
                            difference = difference + (86400 * 1000);
                        } else {
                            // - midnight feast pre-bolus nom nom
                            if (difference > (60 * 60 * 23 * 1000))
                                difference = difference - (86400 * 1000);
                        }

                        Log.d(TAG, "Time Timing data: " + df.format(thisnumber) + " = difference ms: " + JoH.qs(difference));
                        textTime.setText(df.format(thisnumber));
                        timeset = true;
                        thistimeoffset = difference;
                        btnTime.setVisibility(View.VISIBLE);
                        textTime.setVisibility(View.VISIBLE);
                    } catch (ParseException e) {
                        // toast to explain?
                        Log.d(TAG, "Got exception parsing date time");
                    }
                } else {
                    Log.d(TAG, "Time data already set");
                    preserve = true;
                }
                break;
        } // end switch

        if (preserve == false) {
            Log.d(TAG, "Clearing speech values");
            thisnumber = -1;
            thisword = "";
        } else {
            Log.d(TAG, "Preserving speech values");
        }

        // don't show approve/cancel if we only have time
        if (insulinset || glucoseset || carbsset) {
            btnApprove.setVisibility(View.VISIBLE);
            btnCancel.setVisibility(View.VISIBLE);
        }

        if (insulinset || glucoseset || carbsset || timeset) {
            if (chart != null) {
                chart.setAlpha((float) 0.10);
            }
        }

    }

    public static void toaststatic(String msg)
    {
        nexttoast=msg;
        staticRefreshBGCharts();
    }
    public static void toaststaticnext(String msg)
    {
        nexttoast=msg;
    }

    public void toast(final String msg) {
        try {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(staticContext, msg, Toast.LENGTH_LONG).show();
                }
            });
            Log.d(TAG, "toast: " + msg);
        } catch (Exception e) {
            Log.d(TAG, "Couldn't display toast: " + msg + " / " + e.toString());
        }
    }

    /**
     * Receiving speech input
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQ_CODE_SPEECH_INPUT: {
                if (resultCode == RESULT_OK && null != data) {
                    ArrayList<String> result = data
                            .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    voiceRecognitionText.setText(result.get(0));
                    voiceRecognitionText.setVisibility(View.VISIBLE);
                    last_speech_time = JoH.ts();
                    naturalLanguageRecognition(result.get(0));
                }
                recognitionRunning = false;
                break;
            }

        }
    }

    class wordDataWrapper {
        public ArrayList<wordData> entries;

        wordDataWrapper() {
            entries = new ArrayList<wordData>();

        }
    }

    class wordData {
        public String lexicon;
        public ArrayList<String> matchWords;
    }

    /// jamorham end voiceinput methods

    @Override
    public String getMenuName() {
        return menu_name;
    }

    private void checkEula() {
        boolean IUnderstand = prefs.getBoolean("I_understand", false);
        if (!IUnderstand) {
            Intent intent = new Intent(getApplicationContext(), LicenseAgreementActivity.class);
            startActivity(intent);
            finish();
        }
    }

    public static void staticRefreshBGCharts() {
        if (activityVisible) {
            Intent updateIntent = new Intent(Intents.ACTION_NEW_BG_ESTIMATE_NO_DATA);
            staticContext.sendBroadcast(updateIntent);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkEula();
        set_is_follower();
        _broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (intent.getAction().compareTo(Intent.ACTION_TIME_TICK) == 0) {
                    updateCurrentBgInfo("time tick");
                }
            }
        };
        newDataReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                holdViewport.set(0, 0, 0, 0);
                updateCurrentBgInfo("new data");
            }
        };
        registerReceiver(_broadcastReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));
        registerReceiver(newDataReceiver, new IntentFilter(Intents.ACTION_NEW_BG_ESTIMATE_NO_DATA));
        holdViewport.set(0, 0, 0, 0);

        if (invalidateMenu)
        {
            invalidateOptionsMenu();
            invalidateMenu=false;
        }
        activityVisible = true;
        updateCurrentBgInfo("generic on resume");

    }

    private void setupCharts() {
        bgGraphBuilder = new BgGraphBuilder(this);
        updateStuff = false;
        chart = (LineChartView) findViewById(R.id.chart);

        if(BgGraphBuilder.isXLargeTablet(getApplicationContext())) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) chart.getLayoutParams();
            params.topMargin = 130;
            chart.setLayoutParams(params);
        }

        chart.setZoomType(ZoomType.HORIZONTAL);

        //Transmitter Battery Level
        final Sensor sensor = Sensor.currentSensor();
        if (sensor != null && sensor.latest_battery_level != 0 && sensor.latest_battery_level <= Constants.TRANSMITTER_BATTERY_LOW && ! prefs.getBoolean("disable_battery_warning", false)) {
            Drawable background = new Drawable() {

                @Override
                public void draw(Canvas canvas) {

                    DisplayMetrics metrics = getApplicationContext().getResources().getDisplayMetrics();
                    int px = (int) (30 * (metrics.densityDpi / 160f));
                    Paint paint = new Paint();
                    paint.setTextSize(px);
                    paint.setAntiAlias(true);
                    paint.setColor(Color.parseColor("#FFFFAA"));
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setAlpha(100);
                    canvas.drawText("transmitter battery", 10, chart.getHeight() / 3 - (int) (1.2 * px), paint);
                    if(sensor.latest_battery_level <= Constants.TRANSMITTER_BATTERY_EMPTY){
                        paint.setTextSize((int)(px*1.5));
                        canvas.drawText("VERY LOW", 10, chart.getHeight() / 3, paint);
                    } else {
                        canvas.drawText("low", 10, chart.getHeight() / 3, paint);
                    }
                }

                @Override
                public void setAlpha(int alpha) {}
                @Override
                public void setColorFilter(ColorFilter cf) {}
                @Override
                public int getOpacity() {return 0;}
            };
            chart.setBackground(background);
        }
        previewChart = (PreviewLineChartView) findViewById(R.id.chart_preview);
        previewChart.setZoomType(ZoomType.HORIZONTAL);

        chart.setLineChartData(bgGraphBuilder.lineData());
        chart.setOnValueTouchListener(bgGraphBuilder.getOnValueSelectTooltipListener());
        previewChart.setLineChartData(bgGraphBuilder.previewLineData(chart.getLineChartData()));
        updateStuff = true;

        previewChart.setViewportCalculationEnabled(true);
        chart.setViewportCalculationEnabled(true);
        previewChart.setViewportChangeListener(new ViewportListener());
        chart.setViewportChangeListener(new ChartViewPortListener());
        setViewport();
    }

    public void setViewport() {
        if (tempViewport.left == 0.0 || holdViewport.left == 0.0 || holdViewport.right >= (new Date().getTime())) {
            previewChart.setCurrentViewport(bgGraphBuilder.advanceViewport(chart, previewChart));
        } else {
            previewChart.setCurrentViewport(holdViewport);
        }
    }

    @Override
    public void onPause() {
        activityVisible = false;
        super.onPause();
        if (_broadcastReceiver != null ) {
            try {
                unregisterReceiver(_broadcastReceiver);
            } catch (IllegalArgumentException e) {
                UserError.Log.e(TAG, "_broadcast_receiver not registered", e);
            }
        }
        if (newDataReceiver != null) {
            try {
                unregisterReceiver(newDataReceiver);
            } catch (IllegalArgumentException e) {
                UserError.Log.e(TAG, "newDataReceiver not registered", e);
            }
        }
    }

    public static void set_is_follower()
    {
        is_follower = PreferenceManager.getDefaultSharedPreferences(xdrip.getAppContext()).getString("dex_collection_method", "").equals("Follower");
    }

    private void updateCurrentBgInfo(String source) {
        Log.d(TAG,"updateCurrentBgInfo from: "+source);
        if (!activityVisible)
        {
            Log.d(TAG,"Display not visible - not updating chart");
            return;
        }
        setupCharts();
        final TextView notificationText = (TextView) findViewById(R.id.notices);
        if(BgGraphBuilder.isXLargeTablet(getApplicationContext())) {
            notificationText.setTextSize(40);
        }
        notificationText.setText("");
        notificationText.setTextColor(Color.RED);
        boolean isBTWixel = CollectionServiceStarter.isBTWixel(getApplicationContext());
        boolean isDexbridgeWixel = CollectionServiceStarter.isDexbridgeWixel(getApplicationContext());
        boolean isWifiBluetoothWixel = CollectionServiceStarter.isWifiandBTWixel(getApplicationContext());
        isBTShare = CollectionServiceStarter.isBTShare(getApplicationContext());
        boolean isWifiWixel = CollectionServiceStarter.isWifiWixel(getApplicationContext());
        alreadyDisplayedBgInfoCommon = false; // reset flag
        if (isBTShare) {
            updateCurrentBgInfoForBtShare(notificationText);
        }
        if (isBTWixel || isDexbridgeWixel ||  isWifiBluetoothWixel) {
            updateCurrentBgInfoForBtBasedWixel(notificationText);
        }
        if (isWifiWixel || isWifiBluetoothWixel) {
            updateCurrentBgInfoForWifiWixel(notificationText);
        } else if (is_follower)
        {
            displayCurrentInfo();
            getApplicationContext().startService(new Intent(getApplicationContext(), Notifications.class));
        }
        if (prefs.getLong("alerts_disabled_until", 0) > new Date().getTime()) {
            notificationText.append("\n ALL ALERTS CURRENTLY DISABLED");
        } else if (prefs.getLong("low_alerts_disabled_until", 0) > new Date().getTime()
			&&
			prefs.getLong("high_alerts_disabled_until", 0) > new Date().getTime()) {
            notificationText.append("\n LOW AND HIGH ALERTS CURRENTLY DISABLED");
        } else if (prefs.getLong("low_alerts_disabled_until", 0) > new Date().getTime()) {
            notificationText.append("\n LOW ALERTS CURRENTLY DISABLED");
        } else if (prefs.getLong("high_alerts_disabled_until", 0) > new Date().getTime()) {
            notificationText.append("\n HIGH ALERTS CURRENTLY DISABLED");
        }
        NavigationDrawerFragment navigationDrawerFragment = (NavigationDrawerFragment) getFragmentManager().findFragmentById(R.id.navigation_drawer);
        navigationDrawerFragment.setUp(R.id.navigation_drawer, (DrawerLayout) findViewById(R.id.drawer_layout), menu_name, this);

        if (nexttoast!=null)
        {
            toast(nexttoast);
            nexttoast=null;
        }

        // hide the treatment recognition text after some seconds
        if ((last_speech_time >0)&&((JoH.ts()- last_speech_time)>20000))
        {
            voiceRecognitionText.setVisibility(View.INVISIBLE);
            last_speech_time =0;
        }
    }

    private void updateCurrentBgInfoForWifiWixel(TextView notificationText) {
        if (!WixelReader.IsConfigured(getApplicationContext())) {
            notificationText.setText("First configure your wifi wixel reader ip addresses");
            return;
        }

        updateCurrentBgInfoCommon(notificationText);

    }

    private void updateCurrentBgInfoForBtBasedWixel(TextView notificationText) {
        if ((android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN_MR2)) {
            notificationText.setText("Unfortunately your android version does not support Bluetooth Low Energy");
            return;
        }

        if (ActiveBluetoothDevice.first() == null) {
            notificationText.setText("First pair with your BT device!");
            return;
        }
        updateCurrentBgInfoCommon(notificationText);
    }

    private void updateCurrentBgInfoCommon(TextView notificationText) {
        if (alreadyDisplayedBgInfoCommon) return; // with bluetooth and wifi, skip second time
        alreadyDisplayedBgInfoCommon = true;

        final boolean isSensorActive = Sensor.isActive();
        if(!isSensorActive){
            notificationText.setText("Now start your sensor");
            return;
        }

        final long now = System.currentTimeMillis();
        if (Sensor.currentSensor().started_at + 60000 * 60 * 2 >= now) {
            double waitTime = (Sensor.currentSensor().started_at + 60000 * 60 * 2 - now) / 60000.0;
            notificationText.setText("Please wait while sensor warms up! (" + String.format("%.2f", waitTime) + " minutes)");
            return;
        }

        if (BgReading.latest(2).size() > 1) {
            List<Calibration> calibrations = Calibration.latest(2);
            if (calibrations.size() > 1) {
                if (calibrations.get(0).possible_bad != null && calibrations.get(0).possible_bad == true && calibrations.get(1).possible_bad != null && calibrations.get(1).possible_bad != true) {
                    notificationText.setText("Possible bad calibration slope, please have a glass of water, wash hands, then recalibrate in a few!");
                }
                displayCurrentInfo();
            } else {
                notificationText.setText("Please enter two calibrations to get started!");
            }
        } else {
            if (BgReading.latestUnCalculated(2).size() < 2) {
                notificationText.setText("Please wait, need 2 readings from transmitter first.");
            } else {
                List<Calibration> calibrations = Calibration.latest(2);
                if (calibrations.size() < 2) {
                    notificationText.setText("Please enter two calibrations to get started!");
                }
            }
        }
    }

    private void updateCurrentBgInfoForBtShare(TextView notificationText) {
        if ((android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN_MR2)) {
            notificationText.setText("Unfortunately your android version does not support Bluetooth Low Energy");
            return;
        }

        String receiverSn = prefs.getString("share_key", "SM00000000").toUpperCase();
        if (receiverSn.compareTo("SM00000000") == 0 || receiverSn.length() == 0) {
            notificationText.setText("Please set your Dex Receiver Serial Number in App Settings");
            return;
        }

        if (receiverSn.length() < 10) {
            notificationText.setText("Double Check Dex Receiver Serial Number, should be 10 characters, don't forget the letters");
            return;
        }

        if (ActiveBluetoothDevice.first() == null) {
            notificationText.setText("Now pair with your Dexcom Share");
            return;
        }

        if (!Sensor.isActive()) {
            notificationText.setText("Now choose start your sensor in your settings");
            return;
        }

        displayCurrentInfo();
    }

    private void displayCurrentInfo() {
        DecimalFormat df = new DecimalFormat("#");
        df.setMaximumFractionDigits(0);

        boolean isDexbridge = CollectionServiceStarter.isDexbridgeWixel(getApplicationContext());
        boolean isWifiWixel = CollectionServiceStarter.isWifiandBTWixel(getApplicationContext()) | CollectionServiceStarter.isWifiWixel(getApplicationContext());
        if (isDexbridge) {
            int bridgeBattery = prefs.getInt("bridge_battery", 0);

            if (bridgeBattery == 0) {
                dexbridgeBattery.setText("Waiting for packet");
            } else {
                dexbridgeBattery.setText("Bridge Battery: " + bridgeBattery + "%");
            }
            if (bridgeBattery < 50) dexbridgeBattery.setTextColor(Color.YELLOW);
            if (bridgeBattery < 25) dexbridgeBattery.setTextColor(Color.RED);
            else dexbridgeBattery.setTextColor(Color.GREEN);
            dexbridgeBattery.setVisibility(View.VISIBLE);
        } else if (CollectionServiceStarter.isWifiWixel(getApplicationContext())
                || CollectionServiceStarter.isWifiandBTWixel(getApplicationContext())) {
            int bridgeBattery = prefs.getInt("parakeet_battery", 0);
            if (bridgeBattery > 0) {
                // reuse dexbridge battery text. If we end up running dexbridge and parakeet then this will need a rethink
              // only show it when it gets low
                if (bridgeBattery < 50) {
                    dexbridgeBattery.setText("Parakeet Battery: " + bridgeBattery + "%");

                    if (bridgeBattery < 40) {
                        dexbridgeBattery.setTextColor(Color.RED);
                    } else {
                        dexbridgeBattery.setTextColor(Color.YELLOW);
                    }
                    dexbridgeBattery.setVisibility(View.VISIBLE);
                } else {
                   dexbridgeBattery.setVisibility(View.INVISIBLE);
                }


            }
        } else {
            dexbridgeBattery.setVisibility(View.INVISIBLE);
        }

        if ((currentBgValueText.getPaintFlags() & Paint.STRIKE_THRU_TEXT_FLAG) > 0) {
            currentBgValueText.setPaintFlags(currentBgValueText.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            dexbridgeBattery.setPaintFlags(dexbridgeBattery.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
        }
        BgReading lastBgReading = BgReading.lastNoSenssor();
        boolean predictive = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("predictive_bg", false);
        if (isBTShare) {
            predictive = false;
        }
        if (lastBgReading != null) {
            displayCurrentInfoFromReading(lastBgReading, predictive);
        }
    }

    private void displayCurrentInfoFromReading(BgReading lastBgReading, boolean predictive) {
        double estimate = 0;
        if ((new Date().getTime()) - (60000 * 11) - lastBgReading.timestamp > 0) {
            notificationText.setText("Signal Missed");
            if (!predictive) {
                estimate = lastBgReading.calculated_value;
            } else {
                estimate = BgReading.estimated_bg(lastBgReading.timestamp + (6000 * 7));
            }
            currentBgValueText.setText(bgGraphBuilder.unitized_string(estimate));
            currentBgValueText.setPaintFlags(currentBgValueText.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            dexbridgeBattery.setPaintFlags(dexbridgeBattery.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            if (notificationText.getText().length() == 0) {
                notificationText.setTextColor(Color.WHITE);
            }
            final boolean bg_from_filtered = prefs.getBoolean("bg_from_filtered", false);
            if (!predictive) {
                if (bg_from_filtered) {
                    currentBgValueText.setPaintFlags(currentBgValueText.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
                    estimate = lastBgReading.filtered_calculated_value;
                } else {
                    estimate = lastBgReading.calculated_value;
                }
                String stringEstimate = bgGraphBuilder.unitized_string(estimate);
                String slope_arrow = lastBgReading.slopeArrow();
                if ((lastBgReading.hide_slope) || (bg_from_filtered)) {
                    slope_arrow = "";
                }
                currentBgValueText.setText(stringEstimate + " " + slope_arrow);
            } else {
                estimate = BgReading.activePrediction();
                String stringEstimate = bgGraphBuilder.unitized_string(estimate);
                currentBgValueText.setText(stringEstimate + " " + BgReading.activeSlopeArrow());
            }
        }
        int minutes = (int) (System.currentTimeMillis() - lastBgReading.timestamp) / (60 * 1000);
        notificationText.append("\n" + minutes + ((minutes == 1) ? " Minute ago" : " Minutes ago"));

        // do we actually need to do this query here if we again do it in unitizedDeltaString
        List<BgReading> bgReadingList = BgReading.latest(2, is_follower);
        if (bgReadingList != null && bgReadingList.size() == 2) {
            // same logic as in xDripWidget (refactor that to BGReadings to avoid redundancy / later inconsistencies)?
            if (BgGraphBuilder.isXLargeTablet(getApplicationContext())) {
                notificationText.append("  ");
            } else {
                notificationText.append("\n");
            }

            notificationText.append(
                    bgGraphBuilder.unitizedDeltaString(true, true, is_follower));
        }
        if (bgGraphBuilder.unitized(estimate) <= bgGraphBuilder.lowMark) {
            currentBgValueText.setTextColor(Color.parseColor("#C30909"));
        } else if (bgGraphBuilder.unitized(estimate) >= bgGraphBuilder.highMark) {
            currentBgValueText.setTextColor(Color.parseColor("#FFBB33"));
        } else {
            currentBgValueText.setTextColor(Color.WHITE);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_home, menu);
        MenuItem menuItem =  menu.findItem(R.id.action_toggle_speakreadings);
        if(prefs.getBoolean("bg_to_speech_shortcut", false)){
            menuItem.setVisible(true);
            if(prefs.getBoolean("bg_to_speech", false)){
                menuItem.setChecked(true);
            } else {
                menuItem.setChecked(false);
            }
        } else {
            menuItem.setVisible(false);
        }

        menu.findItem(R.id.showmap).setVisible(prefs.getBoolean("plus_extra_features", false));
        menu.findItem(R.id.parakeetsetup).setVisible(prefs.getBoolean("plus_extra_features", false));
        return super.onCreateOptionsMenu(menu);
    }

    public void shareMyConfig(MenuItem myitem) {
        startActivity(new Intent(getApplicationContext(), DisplayQRCode.class));
    }

    public void settingsSDcardExport(MenuItem myitem)
    {
        startActivity(new Intent(getApplicationContext(), SdcardImportExport.class));
    }

    public void showMapFromMenu(MenuItem myitem)
    {
        startActivity(new Intent(getApplicationContext(), MapsActivity.class));
    }

    public void parakeetSetupMode(MenuItem myitem)
    {
       ParakeetHelper.parakeetSetupMode(getApplicationContext());
    }

    public void doBackFillBroadcast(MenuItem myitem) {
        DisplayQRCode.mContext = getApplicationContext();
        GcmActivity.syncBGTable2();
        toast("Starting sync to other devices");
    }

    public void deleteAllBG(MenuItem myitem) {
        BgReading.deleteALL();
        toast("Deleting ALL BG readings!");
        staticRefreshBGCharts();
    }

    public void checkForUpdate(MenuItem myitem)
    {
        toast("Checking for update..");
        UpdateActivity.checkForAnUpdate(getApplicationContext());
    }

    public void sendFeedback(MenuItem myitem)
    {
        startActivity(new Intent(getApplicationContext(), SendFeedBack.class));
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_export_database) {
            new AsyncTask<Void, Void, String>() {
                @Override
                protected String doInBackground(Void... params) {
                    int permissionCheck = ContextCompat.checkSelfPermission(Home.this,
                            android.Manifest.permission.READ_EXTERNAL_STORAGE);
                    if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(Home.this,
                                new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},
                                0);
                        return null;
                    } else {
                        return DatabaseUtil.saveSql(getBaseContext());
                    }

                }

                @Override
                protected void onPostExecute(String filename) {
                    super.onPostExecute(filename);
                    if (filename != null) {
                        SnackbarManager.show(
                                Snackbar.with(Home.this)
                                        .type(SnackbarType.MULTI_LINE)
                                        .duration(4000)
                                        .text("Exported to " + filename) // text to display
                                        .actionLabel("Share") // action button label
                                        .actionListener(new SnackbarUriListener(Uri.fromFile(new File(filename)))),
                                Home.this);
                    } else {
                        Toast.makeText(Home.this, "Could not export Database :(", Toast.LENGTH_LONG).show();
                    }
                }
            }.execute();

            return true;
        }

        if (item.getItemId() == R.id.action_import_db) {
            startActivity(new Intent(this, ImportDatabaseActivity.class));
            return true;
        }

        // jamorham additions
        if (item.getItemId() == R.id.synctreatments) {
            startActivity(new Intent(this,GoogleDriveInterface.class));
            return true;

        }
        ///


        if (item.getItemId() == R.id.action_export_csv_sidiary) {
          new AsyncTask<Void, Void, String>() {
                @Override
                protected String doInBackground(Void... params) {
                    return DatabaseUtil.saveCSV(getBaseContext());
                }

                @Override
                protected void onPostExecute(String filename) {
                    super.onPostExecute(filename);
                    if (filename != null) {
                        SnackbarManager.show(
                                Snackbar.with(Home.this)
                                        .type(SnackbarType.MULTI_LINE)
                                        .duration(4000)
                                        .text("Exported to " + filename) // text to display
                                        .actionLabel("Share") // action button label
                                        .actionListener(new SnackbarUriListener(Uri.fromFile(new File(filename)))),
                                Home.this);
                    } else {
                        Toast.makeText(Home.this, "Could not export CSV :(", Toast.LENGTH_LONG).show();
                    }
                }
            }.execute();

            return true;
        }

        if (item.getItemId() == R.id.action_toggle_speakreadings) {
            prefs.edit().putBoolean("bg_to_speech", !prefs.getBoolean("bg_to_speech", false)).commit();
            invalidateOptionsMenu();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private class ChartViewPortListener implements ViewportChangeListener {
        @Override
        public void onViewportChanged(Viewport newViewport) {
            if (!updatingPreviewViewport) {
                updatingChartViewport = true;
                previewChart.setZoomType(ZoomType.HORIZONTAL);
                previewChart.setCurrentViewport(newViewport);
                updatingChartViewport = false;
            }
        }
    }

    private class ViewportListener implements ViewportChangeListener {
        @Override
        public void onViewportChanged(Viewport newViewport) {
            if (!updatingChartViewport) {
                updatingPreviewViewport = true;
                chart.setZoomType(ZoomType.HORIZONTAL);
                chart.setCurrentViewport(newViewport);
                tempViewport = newViewport;
                updatingPreviewViewport = false;
            }
            if (updateStuff) {
                holdViewport.set(newViewport.left, newViewport.top, newViewport.right, newViewport.bottom);
            }
        }

    }

    class SnackbarUriListener implements ActionClickListener {
        Uri uri;

        SnackbarUriListener(Uri uri) {
            this.uri = uri;
        }

        @Override
        public void onActionClicked(Snackbar snackbar) {
            Intent shareIntent = new Intent();
            shareIntent.setAction(Intent.ACTION_SEND);
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.setType("application/octet-stream");
            startActivity(Intent.createChooser(shareIntent, "Share database..."));
        }
    }
}
