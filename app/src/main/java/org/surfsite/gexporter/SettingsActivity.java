package org.surfsite.gexporter;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.widget.ArrayAdapter;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

/** The GPX to FIT conversion options; saved when the screen is left. */
public class SettingsActivity extends AppCompatActivity {
    private static final Logger Log = LoggerFactory.getLogger(SettingsActivity.class);
    private static final int DEFAULT_MAX_POINTS = 1000;

    private final NumberFormat mNumberFormat = NumberFormat.getInstance(Locale.getDefault());
    private Gpx2FitOptions mOptions;
    private String[] mSpeedUnits;

    private MaterialSwitch mForceSpeed;
    private TextInputLayout mSpeedLayout;
    private TextInputEditText mSpeed;
    private MaterialAutoCompleteTextView mSpeedUnit;
    private MaterialSwitch mUse3DDistance;
    private MaterialSwitch mInjectCoursePoints;
    private MaterialSwitch mUseWalkingGrade;
    private MaterialSwitch mReducePoints;
    private TextInputLayout mMaxPointsLayout;
    private TextInputEditText mMaxPoints;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mOptions = OptionsStore.load(this);
        mSpeedUnits = getResources().getStringArray(R.array.speedunits);

        mForceSpeed = findViewById(R.id.set_force_speed);
        mSpeedLayout = findViewById(R.id.set_speed_layout);
        mSpeed = findViewById(R.id.set_speed);
        mSpeedUnit = findViewById(R.id.set_speed_unit);
        mUse3DDistance = findViewById(R.id.set_use_3d);
        mInjectCoursePoints = findViewById(R.id.set_inject);
        mUseWalkingGrade = findViewById(R.id.set_walking_grade);
        mReducePoints = findViewById(R.id.set_reduce_points);
        mMaxPointsLayout = findViewById(R.id.set_max_points_layout);
        mMaxPoints = findViewById(R.id.set_max_points);

        mSpeedUnit.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, mSpeedUnits));
        int unit = Math.max(0, Math.min(mOptions.getSpeedUnit(), mSpeedUnits.length - 1));
        mSpeedUnit.setText(mSpeedUnits[unit], false);
        mSpeedUnit.setOnItemClickListener((parent, view, position, id) -> {
            mOptions.setSpeedUnit(position);
            showSpeed();
        });

        char separator = DecimalFormatSymbols.getInstance().getDecimalSeparator();
        mSpeed.setKeyListener(DigitsKeyListener.getInstance("0123456789:" + separator));
        showSpeed();
        mSpeed.addTextChangedListener(new AfterTextChanged(this::onSpeedEdited));

        mForceSpeed.setChecked(mOptions.isForceSpeed());
        mForceSpeed.setOnCheckedChangeListener((b, checked) -> mOptions.setForceSpeed(checked));
        mUse3DDistance.setChecked(mOptions.isUse3dDistance());
        mUse3DDistance.setOnCheckedChangeListener((b, checked) -> mOptions.setUse3dDistance(checked));
        mInjectCoursePoints.setChecked(mOptions.isInjectCoursePoints());
        mInjectCoursePoints.setOnCheckedChangeListener((b, checked) -> mOptions.setInjectCoursePoints(checked));
        mUseWalkingGrade.setChecked(mOptions.isWalkingGrade());
        mUseWalkingGrade.setOnCheckedChangeListener((b, checked) -> mOptions.setWalkingGrade(checked));

        int maxPoints = mOptions.getMaxPoints();
        mReducePoints.setChecked(maxPoints > 0);
        mMaxPoints.setText(String.format(Locale.getDefault(), "%d", maxPoints > 0 ? maxPoints : DEFAULT_MAX_POINTS));
        mMaxPointsLayout.setEnabled(maxPoints > 0);
        mReducePoints.setOnCheckedChangeListener((b, checked) -> {
            mMaxPointsLayout.setEnabled(checked);
            mOptions.setMaxPoints(checked ? parseMaxPoints() : 0);
        });
        mMaxPoints.addTextChangedListener(new AfterTextChanged(() -> {
            if (mReducePoints.isChecked()) {
                mOptions.setMaxPoints(parseMaxPoints());
            }
        }));
    }

    @Override
    protected void onPause() {
        super.onPause();
        OptionsStore.save(this, mOptions);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private int parseMaxPoints() {
        try {
            return Integer.parseInt(String.valueOf(mMaxPoints.getText()));
        } catch (NumberFormatException e) {
            Log.error("Invalid max points value");
            return DEFAULT_MAX_POINTS;
        }
    }

    /** Show the stored speed (m/s) in the selected unit. */
    private void showSpeed() {
        int pos = mOptions.getSpeedUnit();
        double speed = mOptions.getSpeed();
        if (Double.isNaN(speed)) {
            speed = 10.0;
        }

        String val = null;
        switch (pos) {
            case 0: // min/km
                val = formatPace(1000.0 / speed / 60.0);
                break;
            case 1: // km/h
                val = String.format(Locale.getDefault(), "%.2f", speed / 1000.0 * 3600.0);
                break;
            case 2: // min/mi
                val = formatPace(1609.344 / speed / 60.0);
                break;
            case 3: // mph
                val = String.format(Locale.getDefault(), "%.2f", speed / 1609.344 * 3600.0);
                break;
        }
        if (val != null) {
            mSpeed.setText(val);
        }
        mSpeedLayout.setSuffixText(mSpeedUnits[Math.max(0, Math.min(pos, mSpeedUnits.length - 1))]);
    }

    private static String formatPace(double minutes) {
        if (minutes < 60) {
            return String.format(Locale.getDefault(), "%d:%02d", (int) minutes, ((int) (minutes * 60.0 + 0.5) % 60));
        }
        return String.format(Locale.getDefault(), "%d:%02d:%02d", ((int) minutes) / 60,
                ((int) minutes % 60), ((int) (minutes * 60.0 + 0.5) % 60));
    }

    /** Parse the typed speed ("h:mm:ss", "mm:ss" or a number) back to m/s. */
    private void onSpeedEdited() {
        // only react to typing, not to showSpeed() filling in the value
        if (getCurrentFocus() != mSpeed) {
            return;
        }
        Editable editable = mSpeed.getText();
        if (editable == null || editable.length() == 0) {
            return;
        }

        double speed;
        try {
            String[] as = editable.toString().split(":");
            Collections.reverse(Arrays.asList(as));

            speed = parse(as[0]);
            if (as.length > 1) {
                speed = parse(as[1]) + speed / 60.0;
            }
            if (as.length > 2) {
                speed = parse(as[2]) * 60.0 + speed;
            }
        } catch (ParseException e) {
            speed = 0.0;
        }

        if (speed > 0.0) {
            switch (mOptions.getSpeedUnit()) {
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
                    break;
            }
        }
        mOptions.setSpeed(speed);
    }

    private double parse(String s) throws ParseException {
        Number n = mNumberFormat.parse(s);
        if (n == null) {
            throw new ParseException("Cannot parse speed value", 0);
        }
        return n.doubleValue();
    }

    private static final class AfterTextChanged implements TextWatcher {
        private final Runnable mAction;

        AfterTextChanged(Runnable action) {
            mAction = action;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable editable) {
            mAction.run();
        }
    }
}
