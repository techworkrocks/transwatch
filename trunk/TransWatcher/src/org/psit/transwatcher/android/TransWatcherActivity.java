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

import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.Locale;

import org.psit.transwatcher.TransWatcher;
import org.psit.transwatcher.TransWatcher.Listener;
import org.psit.transwatcher.TransWatcher.State;

import org.psit.transwatcher.android.R;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;


public class TransWatcherActivity extends Activity implements Listener {
	private static final int OPTION_PREFERENCES = 1;

	private TransWatcher watcher;
	private PrintWriter logWriter;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.main);
		initLogWriter();
	}

	private void initLogWriter() {
		TextView logOutputTF = (TextView) findViewById(R.id.logOutput);
		logWriter = new PrintWriter(new TextViewWriter(logOutputTF));
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuItem mi0 = menu.add(Menu.NONE, OPTION_PREFERENCES,
				OPTION_PREFERENCES, getString(R.string.settingsTitle));
		mi0.setIcon(R.drawable.ic_menu_preferences);

		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case OPTION_PREFERENCES:
			startActivity(new Intent(this, SettingsActivity.class));
			return true;
		}
		return false;
	}

	@Override
	protected void onStart() {
		startWatcher();
		super.onStart();
	}

	private void startWatcher() {
		SharedPreferences sharedPrefs = PreferenceManager
				.getDefaultSharedPreferences(this);
		String destinationFolder = sharedPrefs.getString("destinationFolder",
				getString(R.string.destinationFolderDefault));
		if(!destinationFolder.endsWith("/")) {
			destinationFolder = destinationFolder + "/";
		}
		watcher = new TransWatcher(destinationFolder);
		watcher.addListener(this);
		watcher.start();
	}

	@Override
	protected void onStop() {
		stopWatcher();
		super.onStop();
	}

	private void stopWatcher() {
		this.watcher.interrupt();
	}

	@Override
	public void ping(final String msg) {
		// logWriter.append(msg);
	}

	@Override
	public void exception(Exception ex) {
		// ex.printStackTrace(logWriter);
	}

	/**
	 * Hands over the current state of the communication with the SD card to the
	 * UI.
	 */
	@Override
	public void state(final State state) {
		// logWriter.append("State changed to: "+state+"\n");
		final TextView statusText = (TextView) findViewById(R.id.statusText);
		final ImageView imageView = (ImageView) findViewById(R.id.statusIcon);
		imageView.post(new Runnable() {
			@Override
			public void run() {

				try {
					Field f = R.string.class.getField("status_"
							+ state.toString());
					int stringId = f.getInt(null);
					statusText.setText(stringId);

					Field f1 = R.drawable.class.getField("statusicon_"
							+ state.toString().toLowerCase(Locale.ENGLISH));
					int iconId = f1.getInt(null);
					imageView.setImageDrawable(getResources().getDrawable(
							iconId));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	@Override
	public void downloaded(String filePath) {
		File f = new File(filePath);
		if (f.exists()) {
			logWriter.append("Downloaded: " + f.getName() + "\n");
			BitmapFactory.Options opts = new BitmapFactory.Options();
			opts.inJustDecodeBounds = true;
			Bitmap bm = BitmapFactory.decodeFile(filePath, opts);
			
			final ImageView imageView = (ImageView) findViewById(R.id.imagePreview);			
			
			opts.inSampleSize = calculateInSampleSize(opts, Math.min(800, imageView.getWidth()),
					Math.min(600, imageView.getHeight()));
			opts.inJustDecodeBounds = false;
			final Bitmap scaledBitmap = BitmapFactory
					.decodeFile(filePath, opts);

			imageView.post(new Runnable() {
				@Override
				public void run() {
					imageView.setImageBitmap(scaledBitmap);
				}
			});
		}

	}

	/**
	 * Calculates the sample size (power of two) that is sufficient for the
	 * requested dimensions of the imagen
	 */
	public static int calculateInSampleSize(BitmapFactory.Options options,
			int reqWidth, int reqHeight) {
		// Raw height and width of image
		final int height = options.outHeight;
		final int width = options.outWidth;
		int inSampleSize = 1;

		if (height > reqHeight || width > reqWidth) {

			final int halfHeight = height / 2;
			final int halfWidth = width / 2;

			// Calculate the largest inSampleSize value that is a power of 2 and
			// keeps both
			// height and width larger than the requested height and width.
			while ((halfHeight / inSampleSize) > reqHeight
					&& (halfWidth / inSampleSize) > reqWidth) {
				inSampleSize *= 2;
			}
		}
		return inSampleSize;
	}
}