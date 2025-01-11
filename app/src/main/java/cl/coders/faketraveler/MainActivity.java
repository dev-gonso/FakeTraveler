package cl.coders.faketraveler;

import static cl.coders.faketraveler.MainActivity.SourceChange.CHANGE_FROM_EDITTEXT;
import static cl.coders.faketraveler.MainActivity.SourceChange.CHANGE_FROM_MAP;
import static cl.coders.faketraveler.MainActivity.SourceChange.NONE;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.os.ConfigurationCompat;

import com.google.android.material.button.MaterialButton;

import java.util.Locale;


public class MainActivity extends AppCompatActivity {

    static final String sharedPrefKey = "cl.coders.mockposition.sharedpreferences";
    static final int KEEP_GOING = 0;
    final static private int SCHEDULE_REQUEST_CODE = 1;
    public static Intent serviceIntent;
    public static PendingIntent pendingIntent;
    public static AlarmManager alarmManager;
    static MaterialButton button0;
    static MaterialButton button1;
    static WebView webView;
    static EditText editTextLat;
    static EditText editTextLng;
    static Context context;
    static SharedPreferences sharedPref;
    static SharedPreferences.Editor editor;
    static Double lat;
    static Double lng;
    static int timeInterval;
    static int howManyTimes;
    static long endTime;
    static int currentVersion;
    private static MockLocationProvider mockNetwork;
    private static MockLocationProvider mockGps;

    WebAppInterface webAppInterface;

    public enum SourceChange {
        NONE, CHANGE_FROM_EDITTEXT, CHANGE_FROM_MAP
    }

    static SourceChange srcChange = NONE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        context = getApplicationContext();
        webView = findViewById(R.id.webView0);
        webAppInterface = new WebAppInterface(this, this);
        alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        sharedPref = context.getSharedPreferences(sharedPrefKey, Context.MODE_PRIVATE);
        editor = sharedPref.edit();

        button0 = findViewById(R.id.button0);
        button1 = findViewById(R.id.button1);
        editTextLat = findViewById(R.id.editText0);
        editTextLng = findViewById(R.id.editText1);

        button0.setOnClickListener(arg0 -> applyLocation());

        button1.setOnClickListener(arg0 -> {
            Intent myIntent = new Intent(getBaseContext(), MoreActivity.class);
            startActivity(myIntent);
        });

        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebChromeClient(new WebChromeClient());
        webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        webView.addJavascriptInterface(webAppInterface, "Android");
        webView.loadUrl("file:///android_asset/map.html");

        try {
            PackageInfo pInfo = this.getPackageManager().getPackageInfo(getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                currentVersion = (int) (pInfo.getLongVersionCode() >> 32);
            } else {
                currentVersion = pInfo.versionCode;
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(MainActivity.class.toString(), e.toString());
        }

        checkSharedPrefs();

        howManyTimes = Integer.parseInt(sharedPref.getString("howManyTimes", "1"));
        timeInterval = Integer.parseInt(sharedPref.getString("timeInterval", "10"));

        try {
            lat = Double.parseDouble(sharedPref.getString("lat", ""));
            lng = Double.parseDouble(sharedPref.getString("lng", ""));
            Locale locale = ConfigurationCompat.getLocales(getResources().getConfiguration()).get(0);
            if (locale != null) {
                editTextLat.setText(String.format(locale, "%f", lat));
                editTextLng.setText(String.format(locale, "%f", lng));
            }
        } catch (NumberFormatException e) {
            Log.e(MainActivity.class.toString(), e.toString());
        }

        editTextLat.addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable s) {
                if (!editTextLat.getText().toString().isEmpty() && !editTextLat.getText().toString().equals("-")) {
                    if (srcChange != CHANGE_FROM_MAP) {
                        lat = Double.parseDouble((editTextLat.getText().toString()));

                        if (lng == null)
                            return;

                        setLatLng(editTextLat.getText().toString(), lng.toString(), CHANGE_FROM_EDITTEXT);
                    }
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start,
                                          int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start,
                                      int before, int count) {
            }
        });

        editTextLng.addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable s) {
                if (!editTextLng.getText().toString().isEmpty() && !editTextLng.getText().toString().equals("-")) {
                    if (srcChange != CHANGE_FROM_MAP) {
                        lng = Double.parseDouble((editTextLng.getText().toString()));

                        if (lat == null)
                            return;

                        setLatLng(lat.toString(), editTextLng.getText().toString(), CHANGE_FROM_EDITTEXT);
                    }
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        endTime = sharedPref.getLong("endTime", 0);

        if (pendingIntent != null && endTime > System.currentTimeMillis()) {
            changeButtonToStop();
        } else {
            endTime = 0;
            editor.putLong("endTime", 0);
            editor.apply();
        }

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        toast(context.getResources().getString(R.string.ApplyMockBroadRec_Closed));
        stopMockingLocation();
    }

    /**
     * Check and reinitialize shared preferences in case of problem.
     */
    static void checkSharedPrefs() {
        int version = sharedPref.getInt("version", 0);
        String lat = sharedPref.getString("lat", "N/A");
        String lng = sharedPref.getString("lng", "N/A");
        String howManyTimes = sharedPref.getString("howManyTimes", "N/A");
        String timeInterval = sharedPref.getString("timeInterval", "N/A");
        String dMockLon = sharedPref.getString("DMockLon", "0");
        String dMockLat = sharedPref.getString("DMockLat", "0");
        Long endTime = sharedPref.getLong("endTime", 0);

        if (version != currentVersion) {
            editor.putInt("version", currentVersion);
            editor.apply();
        }

        try {
            Double.parseDouble(lat);
            Double.parseDouble(lng);
            Double.parseDouble(howManyTimes);
            Double.parseDouble(timeInterval);
            Double.parseDouble(dMockLon);
            Double.parseDouble(dMockLat);
        } catch (NumberFormatException e) {
            editor.clear();
            editor.putString("lat", lat);
            editor.putString("lng", lng);
            editor.putInt("version", currentVersion);
            editor.putString("howManyTimes", "1");
            editor.putString("timeInterval", "10");
            editor.putString("DMockLon", "0");
            editor.putString("DMockLat", "0");
            editor.putLong("endTime", 0);
            editor.apply();
            Log.e(MainActivity.class.toString(), e.toString());
        }

    }

    /**
     * Apply a mocked location, and start an alarm to keep doing it if howManyTimes is > 1
     * This method is called when "Apply" button is pressed.
     */
    protected static void applyLocation() {
        if (latIsEmpty() || lngIsEmpty()) {
            toast(context.getResources().getString(R.string.MainActivity_NoLatLong));
            return;
        }

        lat = Double.parseDouble(editTextLat.getText().toString());
        lng = Double.parseDouble(editTextLng.getText().toString());

        toast(context.getResources().getString(R.string.MainActivity_MockApplied));
        endTime = System.currentTimeMillis() + (howManyTimes - 1L) * timeInterval * 1000L;
        editor.putLong("endTime", endTime);
        editor.apply();

        changeButtonToStop();

        try {
            mockNetwork = new MockLocationProvider(LocationManager.NETWORK_PROVIDER, context);
            mockGps = new MockLocationProvider(LocationManager.GPS_PROVIDER, context);
        } catch (SecurityException e) {
            Log.e(MainActivity.class.toString(), e.toString());
            MainActivity.toast(context.getResources().getString(R.string.ApplyMockBroadRec_MockNotApplied));
            stopMockingLocation();
            return;
        }

        exec(lat, lng);

        if (!hasEnded()) {
            toast(context.getResources().getString(R.string.MainActivity_MockLocRunning));
            setAlarm(timeInterval);
        } else {
            stopMockingLocation();
        }
    }

    /**
     * Set a mocked location.
     *
     * @param lat latitude
     * @param lng longitude
     */
    static void exec(double lat, double lng) {
        try {
            //MockLocationProvider mockNetwork = new MockLocationProvider(LocationManager.NETWORK_PROVIDER, context);
            mockNetwork.pushLocation(lat, lng);
            //MockLocationProvider mockGps = new MockLocationProvider(LocationManager.GPS_PROVIDER, context);
            mockGps.pushLocation(lat, lng);
        } catch (Exception e) {
            toast(context.getResources().getString(R.string.MainActivity_MockNotApplied));
            changeButtonToApply();
            Log.e(MainActivity.class.toString(), e.toString());
        }
    }

    /**
     * Check if mocking location should be stopped
     *
     * @return true if it has ended
     */
    static boolean hasEnded() {
        if (howManyTimes == KEEP_GOING) {
            return false;
        } else return System.currentTimeMillis() > endTime;
    }

    /**
     * Sets the next alarm accordingly to <seconds>
     *
     * @param seconds number of seconds
     */
    static void setAlarm(int seconds) {
        serviceIntent = new Intent(context, ApplyMockBroadcastReceiver.class);
        pendingIntent = PendingIntent.getBroadcast(context, SCHEDULE_REQUEST_CODE, serviceIntent,
                PendingIntent.FLAG_IMMUTABLE);  // creating with same request code will get rid of previous

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC, System.currentTimeMillis() + seconds * 1000L, pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC, System.currentTimeMillis() + timeInterval * 1000L, pendingIntent);
            }

        } catch (SecurityException e) {
            Log.e(MainActivity.class.toString(), e.toString());
        }
    }

    /**
     * Shows a toast
     */
    static void toast(String str) {
        Toast.makeText(context, str, Toast.LENGTH_LONG).show();
    }

    /**
     * Returns true editTextLat has no text
     */
    static boolean latIsEmpty() {
        return editTextLat.getText().toString().isEmpty();
    }

    /**
     * Returns true editTextLng has no text
     */
    static boolean lngIsEmpty() {
        return editTextLng.getText().toString().isEmpty();
    }

    /**
     * Stops mocking the location.
     */
    protected static void stopMockingLocation() {
        changeButtonToApply();
        editor.putLong("endTime", System.currentTimeMillis() - 1);
        editor.apply();

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
            toast(context.getResources().getString(R.string.MainActivity_MockStopped));
        }

        if (mockNetwork != null)
            mockNetwork.shutdown();
        if (mockGps != null)
            mockGps.shutdown();
    }

    /**
     * Changes the button to Apply, and its behavior.
     */
    static void changeButtonToApply() {
        button0.setText(context.getResources().getString(R.string.ActivityMain_Apply));
        button0.setOnClickListener(arg0 -> applyLocation());
    }

    /**
     * Changes the button to Stop, and its behavior.
     */
    static void changeButtonToStop() {
        button0.setText(context.getResources().getString(R.string.ActivityMain_Stop));
        button0.setOnClickListener(arg0 -> stopMockingLocation());
    }

    /**
     * Sets latitude and longitude
     *
     * @param mLat      latitude
     * @param mLng      longitude
     * @param srcChange CHANGE_FROM_EDITTEXT or CHANGE_FROM_MAP, indicates from where comes the change
     */
    static void setLatLng(String mLat, String mLng, SourceChange srcChange) {
        lat = Double.parseDouble(mLat);
        lng = Double.parseDouble(mLng);

        if (srcChange == CHANGE_FROM_EDITTEXT) {
            webView.loadUrl("javascript:setOnMap(" + lat + "," + lng + ");");
        } else if (srcChange == CHANGE_FROM_MAP) {
            MainActivity.srcChange = CHANGE_FROM_MAP;
            editTextLat.setText(mLat);
            editTextLng.setText(mLng);
            MainActivity.srcChange = NONE;
        }

        editor.putString("lat", mLat);
        editor.putString("lng", mLng);
        editor.apply();
    }

    /**
     * returns latitude
     *
     * @return latitude
     */
    static String getLat() {
        return editTextLat.getText().toString();
    }

    /**
     * returns latitude
     *
     * @return latitude
     */
    static String getLng() {
        return editTextLng.getText().toString();
    }
}