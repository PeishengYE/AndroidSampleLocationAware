package com.radioyps.locationaware;

import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.support.annotation.Nullable;

import com.example.android.location.R;

/**
 * Created by yep on 10/01/18.
 */

public class SettingsActivity extends PreferenceActivity {


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preference);
    }
}
