package org.surfsite.gexporter;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;

import org.surfsite.gexporter.WebServer;
import org.tracks.exporter.R;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    @Nullable
    private WebServer server = null;
    private TextView mTextView;
    private Spinner mSpeedUnit;
    private ArrayAdapter<CharSequence> mSpeedUnitAdapter;
    private EditText mSpeed;
    private CheckBox mForceSpeed;
    private CheckBox mUse3DDistance;
    private CheckBox mInjectCoursePoints;
    private CheckBox mUseWalkingGrade;
    private CheckBox mReducePoints;
    private EditText mMaxPoints;
    private GpxToFitOptions mGpxToFitOptions = null;
    private NumberFormat mNumberFormat = NumberFormat.getInstance(Locale.getDefault());

    private final static int MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 300;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mSpeedUnit = (Spinner) findViewById(R.id.SPspeedUnits);
        mSpeedUnitAdapter = ArrayAdapter.createFromResource(this,
                R.array.speedunits, android.R.layout.simple_spinner_item);
        mSpeedUnitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpeedUnit.setAdapter(mSpeedUnitAdapter);
        mSpeedUnit.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long selId) {
                if (mGpxToFitOptions != null)
                    mGpxToFitOptions.setSpeedUnit(pos);
                setSpeedText(pos);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                if (mGpxToFitOptions != null)
                    mGpxToFitOptions.setSpeedUnit(0);
            }
        });

        mSpeed = (EditText) findViewById(R.id.editSpeed);
        char separator = DecimalFormatSymbols.getInstance().getDecimalSeparator();
        mSpeed.setKeyListener(DigitsKeyListener.getInstance("0123456789" + separator));
        mSpeed.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (getCurrentFocus() != mSpeed) {
                        return;
                }

                if (editable.length() > 0 && mGpxToFitOptions != null) {
                    double speed;
                    try {
                        speed = mNumberFormat.parse(editable.toString()).doubleValue();
                    } catch (ParseException e) {
                        speed = .0;
                    }
                    if (speed > .0) {
                        //System.err.println("Speed: " + speed);
                        //System.err.println("Unit: " + mGpxToFitOptions.getSpeedUnit());
                        switch (mGpxToFitOptions.getSpeedUnit()) {
                            case 0:
                                speed = 1000.0 / 60.0 / speed;
                                break;
                            case 1:
                                speed = speed * 1000.0 / 3600.0;
                                break;
                            case 2:
                                speed = 1609.344 / 60.0 / speed;
                                break;
                            case 3:
                                speed = speed * 1609.344 / 3600.0;
                        }
                        //System.err.println("ResSpeed: " + speed);
                    }
                    mGpxToFitOptions.setSpeed(speed);
                }
            }
        });

        mForceSpeed = (CheckBox) findViewById(R.id.CBforceSpeed);
        mForceSpeed.setOnClickListener(this);

        mUse3DDistance = (CheckBox) findViewById(R.id.CBuse3D);
        mUse3DDistance.setOnClickListener(this);

        mInjectCoursePoints = (CheckBox) findViewById(R.id.CBinject);
        mInjectCoursePoints.setOnClickListener(this);

        mUseWalkingGrade = (CheckBox) findViewById(R.id.CBuseWalkingGrade);
        mUseWalkingGrade.setOnClickListener(this);

        mReducePoints = (CheckBox) findViewById(R.id.CBreducePoints);
        mReducePoints.setOnClickListener(this);

        mMaxPoints = (EditText) findViewById(R.id.editPointNumber);
        mMaxPoints.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (editable.length() > 0 && mGpxToFitOptions != null && mReducePoints.isChecked())
                    mGpxToFitOptions.setMaxPoints(Integer.valueOf(editable.toString()));
            }
        });

        mTextView = (TextView) findViewById(R.id.textv);
    }

    private void setSpeedText(int pos) {
        double speed = mGpxToFitOptions.getSpeed();
        //System.err.println("xx Speed: " + speed);
        //System.err.println("xx Unit: " + pos);

        switch (pos) {
            case 0:
                speed = 1000.0 / speed / 60.0;
                break;
            case 1:
                speed = speed / 1000.0 * 3600.0;
                break;
            case 2:
                speed = 1609.344 / speed / 60.0;
                break;
            case 3:
                speed = speed / 1609.344 * 3600.0;
        }
        if (mSpeed != null)
            mSpeed.setText(String.format(Locale.getDefault(), "%.2f", speed));
        //System.err.println("xx Speed: " + speed);
    }

    @Override
    public void onClick(View v) {
        if (mGpxToFitOptions == null)
            return;

        switch (v.getId()) {
            case R.id.CBforceSpeed:
                mGpxToFitOptions.setForceSpeed(mForceSpeed.isChecked());
                break;
            case R.id.CBinject:
                mGpxToFitOptions.setInjectCoursePoints(mInjectCoursePoints.isChecked());
                break;
            case R.id.CBreducePoints:
                if (mReducePoints.isChecked()) {
                    String t = mMaxPoints.getText().toString();
                    if (t.length() > 0)
                        mGpxToFitOptions.setMaxPoints(Integer.decode(t));
                } else
                    mGpxToFitOptions.setMaxPoints(0);
                break;
            case R.id.CBuse3D:
                mGpxToFitOptions.setUse3dDistance(mUse3DDistance.isChecked());
                break;
            case R.id.CBuseWalkingGrade:
                mGpxToFitOptions.setWalkingGrade(mUseWalkingGrade.isChecked());
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @Nullable String permissions[], @Nullable int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults != null && grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    serveFiles();

                } else {
                    mTextView.setText(R.string.no_permission);
                }
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    public void save(GpxToFitOptions options) {
        Application app = getApplication();
        SharedPreferences mPrefs=app.getSharedPreferences(app.getApplicationInfo().name, Context.MODE_PRIVATE);
        SharedPreferences.Editor ed=mPrefs.edit();
        Gson gson = new Gson();
        ed.putString(options.getClass().getName(), gson.toJson(options));
        ed.apply();
    }

    public GpxToFitOptions load() {
        Application app = getApplication();
        Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().create();
        JsonParser parser=new JsonParser();
        SharedPreferences mPrefs=app.getSharedPreferences(app.getApplicationInfo().name, Context.MODE_PRIVATE);
        String json = mPrefs.getString(GpxToFitOptions.class.getName(), null);
        GpxToFitOptions opts = null;
        if (json != null && json.length() > 0)
            opts = gson.fromJson(parser.parse(json).getAsJsonObject(), GpxToFitOptions.class);
        if (opts != null)
            return opts;
        else
            return new GpxToFitOptions();
    }


    @Override
    public void onPause() {
        super.onPause();
        save(mGpxToFitOptions);
    }

    @Override
    public void onResume() {
        super.onResume();  // Always call the superclass method first

        mGpxToFitOptions = load();
        double speed = mGpxToFitOptions.getSpeed();
        if (Double.isNaN(speed))
            speed = 10.0;
        setSpeedText(mGpxToFitOptions.getSpeedUnit());

        mForceSpeed.setChecked(mGpxToFitOptions.isForceSpeed());
        mUse3DDistance.setChecked(mGpxToFitOptions.isUse3dDistance());
        mInjectCoursePoints.setChecked(mGpxToFitOptions.isInjectCoursePoints());
        mUseWalkingGrade.setChecked(mGpxToFitOptions.isWalkingGrade());

        mReducePoints.setChecked(mGpxToFitOptions.getMaxPoints() > 0);
        int maxp = mGpxToFitOptions.getMaxPoints();
        if (maxp == 0)
            maxp = 1000;
        mMaxPoints.setText(Integer.toString(maxp));

        mSpeedUnit.setSelection(mGpxToFitOptions.getSpeedUnit());

        if (server == null) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
            } else {
                serveFiles();
            }
        }
    }

    protected void serveFiles() {
        try {
/*
            InputStream keystoreStream = getResources().openRawResource(R.raw.keystore);
            KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
            keystore.load(keystoreStream, "123456".toCharArray());
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keystore, "123456".toCharArray());

            server = new WebServer(keystore, keyManagerFactory, getCacheDir(), 22222);
*/
            String rootdir = Environment.getExternalStorageDirectory().getAbsolutePath() +
                    "/Download/";

            server = new WebServer(new File(rootdir), getCacheDir(), 22222, mGpxToFitOptions);
            server.start();
            Log.w("Httpd", "Web server initialized.");
        } catch (IOException | NoSuchAlgorithmException e) {
            Log.w("Httpd", "The server could not start. " + e.getLocalizedMessage());
            mTextView.setText(R.string.no_server);
        }

        String rootdir = Environment.getExternalStorageDirectory().getAbsolutePath() +
                "/Download/";
        FilenameFilter filenameFilter = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                if (name.endsWith(".fit") || name.endsWith(".FIT") || name.endsWith(".gpx") || name.endsWith(".GPX")) {
                    return true;
                }
                return false;
            }
        };
        String[] filelist = new File(rootdir).list(filenameFilter);

        if (filelist == null) {
            mTextView.setText(R.string.no_permission);
        } else {
            Arrays.sort(filelist);
            mTextView.setText(String.format(getResources().getString(R.string.serving_from), rootdir, TextUtils.join("\n", filelist)));
        }
    }
}
