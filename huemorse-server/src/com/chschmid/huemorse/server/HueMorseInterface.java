/**
 * Copyright (C) 2014 Christian M. Schmid
 *
 * This file is part of the huemorse.
 * 
 * huemorse is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

 package com.chschmid.huemorse.server;

import java.util.List;

import com.philips.lighting.hue.sdk.PHAccessPoint;
import com.philips.lighting.hue.sdk.PHBridgeSearchManager;
import com.philips.lighting.hue.sdk.PHHueSDK;
import com.philips.lighting.hue.sdk.PHSDKListener;
import com.philips.lighting.model.PHBridge;
import com.philips.lighting.model.PHBridgeResourcesCache;
import com.philips.lighting.model.PHHueParsingError;
import com.philips.lighting.model.PHLight;
import com.philips.lighting.model.PHLightState;

public class HueMorseInterface {
	private PHHueSDK phHueSDK;
	private PHSDKListener listener;
	
	PHBridgeSearchManager sm;
	
	private String username = "";
    private String bridgeIP = "";
    
    private PHBridge bridge;
    
    private List<PHAccessPoint> bridges;
    
    private Object sync = new Object();
    
    public static final int MORSE_BUFFER_SIZE = 524288;
	public static final int MORSE_TIME_UNIT   =    300;
	
	public static final int DEFAULT_LIGHT_ID  =      1;
	
	public static final int STATUS_STARTUP    =   -100;
	public static final int STATUS_IDLE       =      0;
	public static final int STATUS_MORSE      =      1;
	public static final int STATUS_EXIT       =     -3;
	
	private MorseBuffer morseBuffer;
	private MorseThread morseThread;
	private int morseStatus = STATUS_STARTUP;
	private int lightID     = DEFAULT_LIGHT_ID;
	
	public HueMorseInterface() {
		phHueSDK = PHHueSDK.getInstance();
		listener = new HueMorsePHSDKListener();
		phHueSDK.getNotificationManager().registerSDKListener(listener);
		morseBuffer = new MorseBuffer();
	}
	
	public void connect() {
		PHAccessPoint accessPoint = new PHAccessPoint();
		accessPoint.setIpAddress(bridgeIP);
		accessPoint.setUsername(username);
		phHueSDK.connect(accessPoint);
		synchronized (sync) {
	    	try {
	    		sync.wait(60*1000);
			} catch (InterruptedException e) {
			}
		}
		bridge = PHHueSDK.getInstance().getSelectedBridge();
		phHueSDK.enableHeartbeat(bridge, PHHueSDK.HB_INTERVAL);
		morseBuffer.initBuffer(MORSE_BUFFER_SIZE);
		morseThread = new MorseThread();
		morseThread.start();
	}
	
	public void connect(String bridgeIP, String username) {
		this.bridgeIP = bridgeIP;
		this.username = username;
		connect();
	}
		
	public List<PHAccessPoint> listBridges() {
		bridges = null;
		sm = (PHBridgeSearchManager) phHueSDK.getSDKService(PHHueSDK.SEARCH_BRIDGE);
	    sm.search(true, true);
	    
	    synchronized (sync) {
	    	try {
	    		sync.wait(60*1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	    return bridges;
	}
	
	public List<PHLight> listLights() {
		PHBridgeResourcesCache cache = bridge.getResourceCache();
        return cache.getAllLights();
	}
			
	public void close() {
		if (morseThread != null){
			synchronized (morseThread) {
				morseStatus = STATUS_EXIT;
				morseThread.notify();
			}
		}
			
		phHueSDK.disableAllHeartbeat();
		phHueSDK.destroySDK();
	}
	
	public void switchLight(int lightID, boolean on) {
		PHLight light = getLight(lightID);
        PHLightState state = new PHLightState();
        state.setOn(on);
        if (on) state.setBrightness(254);
        state.setTransitionTime(0);
        bridge.updateLightState(light, state);
	}
	
	public void addMorseCode(String morseCode) {
		synchronized (morseThread) {
			if(morseBuffer.getAvailableBufferLength() > morseCode.length()) {
				for (int k1 = 0; k1 < morseCode.length(); k1++) morseBuffer.addLast(morseCode.charAt(k1));
				morseThread.notify();
			}
        }
	}
	
	private PHLight getLight(int lightID) {
		PHBridgeResourcesCache cache = bridge.getResourceCache();
        PHLight light = null;
        for (PHLight temp : cache.getAllLights()) {
        	if (Integer.parseInt(temp.getIdentifier()) == lightID) light = temp;
        }
        return light;
	}
	
	private class HueMorsePHSDKListener implements PHSDKListener {

		@Override
		public void onAccessPointsFound(List<PHAccessPoint> result) {
			bridges = result;
			synchronized (sync) { sync.notify(); }
		}

		@Override
		public void onAuthenticationRequired(PHAccessPoint arg0) {}

		@Override
		public void onBridgeConnected(PHBridge arg0) {
			if (HueMorse.DEBUG) System.out.println("Connection esablished");
			synchronized (sync) { sync.notify(); }
		}

		@Override
		public void onCacheUpdated(List<Integer> arg0, PHBridge arg1) {
			synchronized (sync) { sync.notify(); }
		}

		@Override
		public void onConnectionLost(PHAccessPoint arg0) {}

		@Override
		public void onConnectionResumed(PHBridge arg0) {}

		@Override
		public void onError(int arg0, String arg1) {}

		@Override
		public void onParsingErrors(List<PHHueParsingError> arg0) {}
	}
	
	// Setters & Getters
	public void setUsername(String username) { this.username = username; }
	public void setBridgeIP(String bridgeIP) { this.bridgeIP = bridgeIP; }
	public void setLightID(int lightID)      { this.lightID = lightID; }
	
	public String getUsername() { return username; }
	public String getBridgeIP() { return bridgeIP; }
	public int getLightID()     { return lightID; }
	
	private class MorseThread extends Thread {
		public void run() {
			PHLightState backup = new PHLightState(getLight(lightID).getLastKnownLightState());
			morseStatus = STATUS_IDLE;
			
			while (morseStatus != STATUS_EXIT) {
				if (morseBuffer.getAvailableDataLength() > 0) {
					if (morseStatus == STATUS_IDLE) {
						morseStatus = STATUS_MORSE;
						backup = new PHLightState(getLight(lightID).getLastKnownLightState());
						switchLight(lightID, false);
						mySleep(1000);
					}
						
					char c = morseBuffer.readFirst();
					if (morseStatus == STATUS_MORSE){
						if (c == '.') dit();
						else if (c == '-') dah();
						else if (c == ' ') space();
					}
				} else {
					morseStatus = STATUS_IDLE;
					mySleep(1000);
					bridge.updateLightState(getLight(lightID), backup);
					synchronized (morseThread) {
						if (morseBuffer.getAvailableDataLength() == 0) {
							try {
								if (morseBuffer.getAvailableDataLength() == 0) morseThread.wait();
							} catch ( InterruptedException e ) { }
						}
					}
				}
			}
		}
		
		private void dit() {
			switchLight(lightID, true);
			mySleep(MORSE_TIME_UNIT);
			switchLight(lightID, false);
			mySleep(MORSE_TIME_UNIT);
		}
		
		private void dah() {
			switchLight(lightID, true);
			mySleep(3*MORSE_TIME_UNIT);
			switchLight(lightID, false);
			mySleep(MORSE_TIME_UNIT);
		}
		
		private void space() {
			mySleep(2*MORSE_TIME_UNIT);
		}
		
		private void mySleep(int time) {
			try {
				sleep(time);
			} catch (InterruptedException e) {}
		}
	}
	
	public static class MorseBuffer {
		public static final int DEFAULT_SIZE = 1024;
		
		private char[] buffer;
		private int size;
		private int start, end;
		
		int availableBufferSize;
		
		public MorseBuffer() {
			initBuffer(DEFAULT_SIZE);
		}
		
		public MorseBuffer(int size) {
			initBuffer(size);
		}
		
		private void initBuffer(int size) {
			start = 0;
			end   = 0;
			this.size = size;
			buffer = new char[size];
			availableBufferSize = size;
		}
		
		public void addLast(char c) {
			if (availableBufferSize == 0) return;
			buffer[end] = c;
			availableBufferSize = availableBufferSize - 1;
			end = normalizePosition(end + 1);
		}
		
		public void addFirst(char c) {
			if (availableBufferSize == 0) return;
			start = normalizePosition(start - 1);
			buffer[start] = c;
			availableBufferSize = availableBufferSize - 1;
		}
		
		public char readFirst() {
			char c = 0;
			if (availableBufferSize == size) return c;
			c = buffer[start];
			availableBufferSize = availableBufferSize + 1;
			start = normalizePosition(start + 1);
			return c;
		}
		
		public char readLast() {
			char c = 0;
			if (availableBufferSize == size) return c;
			end = normalizePosition(end - 1);
			c = buffer[end];
			availableBufferSize = availableBufferSize + 1;
			return c;
		}
		
		private int normalizePosition(int position) {
			if (position < 0)     position = size-position;
			if (position >= size) position = position - size;
			return position;
		}
		
		public int getAvailableDataLength() {return size - availableBufferSize;}
		public int getAvailableBufferLength() {return size;}
		public int getSize() {return size;}
	}
}
