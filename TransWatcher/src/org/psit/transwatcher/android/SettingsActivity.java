/**
 * This file is part of TransWatcher.
 *
 * TransWatcher is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * TransWatcher is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with TransWatcher.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Peter Steiger <peter.steiger74@gmail.com>
 * @since May 2nd, 2014
 */
package org.psit.transwatcher.android;

import org.psit.transwatcher.android.R;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;

public class SettingsActivity extends PreferenceActivity implements
		OnSharedPreferenceChangeListener {

	@SuppressWarnings("deprecation")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);
		
		updateKeySummary("destinationFolder");

		getPreferenceScreen().getSharedPreferences()
		.registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		updateKeySummary(key);
	}

	private void updateKeySummary(String key) {
		@SuppressWarnings("deprecation")
		Preference pref = findPreference(key);
		String summary = null;

		if (pref instanceof EditTextPreference)
			summary = ((EditTextPreference) pref).getText();
		else if (pref instanceof ListPreference)
			summary = (String) ((ListPreference) pref).getEntry();

		if(pref != null)
			pref.setSummary(summary);
	}
}