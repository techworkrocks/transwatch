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

package org.psit.transwatcher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

/**
 * This runnable is working in an infinite loop to get connection to the Transcend Wifi SD card
 * and downloading images that are shot immediately into the specified local fileDestination.
 * 
 * The Listener class is used to notify about any events to the using classes.
 */
public class TransWatcher extends Thread {
	private Set<Listener> listeners;
	private Thread watchDogThread;
	private Thread downLoadQueue;
	private BlockingQueue<String> queue = new LinkedBlockingQueue<String>();
	private String fileDestinationPrefix;
	public enum State { NO_WIFI, SEARCHING_CARD, LISTENING, DOWNLOADING };
	private State state = State.NO_WIFI;
	
	public TransWatcher(String fileDestinationPrefix) {
		this.fileDestinationPrefix = fileDestinationPrefix;
		this.listeners = new HashSet<TransWatcher.Listener>();
	}

	@Override
	public void run() {

		try {
			while (true) {

				String cardIP = connectAndGetCardIP();
				if (cardIP != null) {
					notifyMessage("Found SD card, IP: " + cardIP);

					// handshake successful, open permanent TCP connection
					// to listen to new images
					Socket newImageListenerSocket = null;
					try {
						newImageListenerSocket = new Socket(cardIP, 5566);
						newImageListenerSocket.setKeepAlive(true);
						InputStream is = newImageListenerSocket
								.getInputStream();
						byte[] c = new byte[1];
						byte[] singleMessage = new byte[255];
						int msgPointer = 0;

						startConnectionKeepAliveWatchDog(newImageListenerSocket);

						startImageDownloaderQueue(cardIP);

						setState(State.LISTENING);
						
						// loop to wait for new images
						while (newImageListenerSocket.isConnected()) {
							if (is.read(c) == 1) {
								if (0x3E == c[0]) { // >
									// start of filename
									msgPointer = 0;
								} else if (0x00 == c[0]) {
									// end of filename
									String msg = new String(Arrays.copyOfRange(
											singleMessage, 0, msgPointer));
									notifyMessage("Image shot: " + msg);
									
									// add to download queue
									queue.add(msg);
								} else {
									// single byte. add to buffer
									singleMessage[msgPointer++] = c[0];
								}
							}
						}
						setState(State.SEARCHING_CARD);
					} catch (IOException e) {
						notifyMessage("Error during image notification connection!");
					} finally {
						try {
							newImageListenerSocket.close();
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				} else {
					notifyMessage("No card found, retrying.");
				}
				Thread.sleep(2000);
			}
		} catch (InterruptedException e) {
			stopImageDownLoaderQueue();
			notifyMessage("Connection abandoned.");
		}

	}

	private void stopImageDownLoaderQueue() {
		if(downLoadQueue != null && downLoadQueue.isAlive())
			downLoadQueue.interrupt();
	}

	private void startImageDownloaderQueue(final String cardIP) {
		if(downLoadQueue != null)
			downLoadQueue.interrupt();
		
		downLoadQueue = new Thread(new Runnable() {

			@Override
			public void run() {
				queue = new LinkedBlockingQueue<String>();
				InputStream input = null;
				FileOutputStream output= null;
				try {
					while (true) {
						String fileName = queue.take();
						setState(State.DOWNLOADING);
						File file = new File(fileName);
						String req = "/cgi-bin/wifi_download?fn="
								+ file.getName() + "&fd=" + file.getParent()
								+ "/";
						HttpClient httpclient = new DefaultHttpClient();
						HttpGet httpGet = new HttpGet("http://" + cardIP + req);
						HttpResponse response = httpclient.execute(httpGet);

						input = response.getEntity().getContent();
						output = new FileOutputStream(
								fileDestinationPrefix+file.getName() );
						byte[] buffer = new byte[1024];

						for (int length; (length = input.read(buffer)) > 0;) {
							output.write(buffer, 0, length);
						}
						notifyDownload(fileDestinationPrefix+file.getName());
						notifyMessage(file.getName()+" downloaded");
						setState(State.LISTENING);
					}
				} catch (InterruptedException ex) {
					notifyMessage("Downloadqueue stopped.");
				} catch (ClientProtocolException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					if (output != null) try { output.close(); } catch (IOException logOrIgnore) {}
		            if (input != null) try { input.close(); } catch (IOException logOrIgnore) {}
				}
			}

		}, "ImageDownloaderQueue");
		
		downLoadQueue.start();
	}

	private void startConnectionKeepAliveWatchDog(
			final Socket newImageListenerSocket) {
		watchDogThread = new Thread(new Runnable() {
			public void run() {
				try {
					while (true) {
						Thread.sleep(5000);
						InetSocketAddress addr = new InetSocketAddress(
								newImageListenerSocket.getInetAddress(), 80);
						Socket s = new Socket();
						s.connect(addr, 1000);
						s.close();
						notifyMessage("WatchDog ping.");
					}
				} catch (InterruptedException e) {
					notifyMessage("WatchDog interrupted");
				} catch (IOException e) {
					notifyMessage("WatchDog: Connection to card lost.");
					try {
						newImageListenerSocket.close();
					} catch (IOException e1) {
						e1.printStackTrace();
					}
					e.printStackTrace();
				}

			};
		}, "ConnectionWatchDog");
		watchDogThread.start();
	}

	@Override
	public void interrupt() {
		if (watchDogThread != null && watchDogThread.isAlive())
			watchDogThread.interrupt();
		
		if (downLoadQueue != null && downLoadQueue.isAlive())
			downLoadQueue.interrupt();

		super.interrupt();
	}

	private String connectAndGetCardIP() {
		DatagramSocket clientSocket = null, serverSocket = null;

		try {
			String broadcastIP = getBroadcastIP();
			setState(broadcastIP == null ? State.NO_WIFI
					: State.SEARCHING_CARD);
			notifyMessage("BroadcastIP: " + broadcastIP);

			// send out broadcast
			clientSocket = new DatagramSocket(58255);
			InetAddress IPAddress = InetAddress.getByName(broadcastIP);
			byte[] sendData = "".getBytes();
			DatagramPacket sendPacket = new DatagramPacket(sendData,
					sendData.length, IPAddress, 55777);
			clientSocket.send(sendPacket);
			clientSocket.close();

			serverSocket = new DatagramSocket(58255);
			byte[] receiveData = new byte[256];
			DatagramPacket receivePacket = new DatagramPacket(receiveData,
					receiveData.length);
			serverSocket.setSoTimeout(3000);
			serverSocket.receive(receivePacket);
			serverSocket.close();

			notifyMessage("Packet received: "+new String(receiveData));
			if (new String(receivePacket.getData()).indexOf("Transcend WiFiSD") >= 0)
				return receivePacket.getAddress().getHostAddress();
		} catch (Exception ex) {
			notifyMessage("Card handshake unsuccessful. ");
			notifyException(ex);
		} finally {
			if (clientSocket != null)
				clientSocket.close();
			if (serverSocket != null)
				serverSocket.close();
		}
		return null;
	}

	private void setState(State state) {
		this.state = state;
		notifyState();
	}

	private String getBroadcastIP() {
		String myIP = null;

		try {
			Enumeration<NetworkInterface> interfaces = NetworkInterface
					.getNetworkInterfaces();
			while (interfaces.hasMoreElements() && myIP == null) {
				NetworkInterface current = interfaces.nextElement();
				notifyMessage(current.toString());
				notifyMessage("Name: "+current.getName());
				if (!current.isUp() || current.isLoopback()
						|| current.isVirtual()
						|| !current.getName().startsWith("wl"))
					continue;
				Enumeration<InetAddress> addresses = current.getInetAddresses();
				while (addresses.hasMoreElements()) {
					InetAddress current_addr = addresses.nextElement();
					if (current_addr.isLoopbackAddress())
						continue;
					if (current_addr instanceof Inet4Address) {
						myIP = current_addr.getHostAddress();
						break;
					}
				}
			}
		} catch (Exception exc) {
			notifyMessage("Error determining network interfaces:\n");
		}

		if (myIP != null) {
			// broadcast for IPv4
			StringTokenizer st = new StringTokenizer(myIP, ".");
			StringBuffer broadcastIP = new StringBuffer();
			// hate that archaic string puzzle
			broadcastIP.append(st.nextElement());
			broadcastIP.append(".");
			broadcastIP.append(st.nextElement());
			broadcastIP.append(".");
			broadcastIP.append(st.nextElement());
			broadcastIP.append(".");
			broadcastIP.append("255");
			return broadcastIP.toString();
		}

		return null;
	}

	// ----------------- Listener stuff

	public void addListener(Listener l) {
		listeners.add(l);
	}

	public void removeListener(Listener l) {
		listeners.remove(l);
	}

	public interface Listener {
		public void ping(String msg);
		public void exception(Exception ex);
		public void state(State state);
		public void downloaded(String filePath);
	}

	private void notifyMessage(String msg) {
		for (Listener listener : listeners)
			listener.ping(msg + "\n");
	}

	private void notifyException(Exception ex) {
		for (Listener listener : listeners)
			listener.exception(ex);
	}
	
	private void notifyState() {
		for (Listener listener : listeners)
			listener.state(this.state);		
	}

	private void notifyDownload(String canonicalPath) {
		for (Listener listener : listeners)
			listener.downloaded(canonicalPath);			
	}
}
