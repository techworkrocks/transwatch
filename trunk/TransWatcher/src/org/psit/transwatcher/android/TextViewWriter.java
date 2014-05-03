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

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;

import android.app.Activity;
import android.widget.TextView;

public class TextViewWriter extends Writer {
	private TextView textView;
	
	public TextViewWriter(TextView textView) {
		this.textView = textView;
	}
	
	@Override
	public void close() throws IOException {}

	@Override
	public void flush() throws IOException {}

	@Override
	public void write(final char cbuf[], final int off, final int len) throws IOException {
		if(textView != null) {
			Activity activity = (Activity) textView.getContext();
			activity.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					textView.append(new String(Arrays.copyOfRange(cbuf, off, off+len)));
				} } );
		}
	}
	
	

}
