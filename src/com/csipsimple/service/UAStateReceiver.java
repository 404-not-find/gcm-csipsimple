/**
 * Copyright (C) 2010 Regis Montoya (aka r3gis - www.r3gis.fr)
 * This file is part of CSipSimple.
 *
 *  CSipSimple is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  CSipSimple is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with CSipSimple.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.csipsimple.service;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.pjsip.pjsua.Callback;
import org.pjsip.pjsua.SWIGTYPE_p_p_pjmedia_port;
import org.pjsip.pjsua.SWIGTYPE_p_pjmedia_session;
import org.pjsip.pjsua.SWIGTYPE_p_pjsip_rx_data;
import org.pjsip.pjsua.SWIGTYPE_p_pjsip_tx_data;
import org.pjsip.pjsua.pj_str_t;
import org.pjsip.pjsua.pjsip_event;
import org.pjsip.pjsua.pjsip_inv_state;
import org.pjsip.pjsua.pjsip_status_code;
import org.pjsip.pjsua.pjsua;
import org.pjsip.pjsua.pjsuaConstants;
import org.pjsip.pjsua.pjsua_call_info;
import org.pjsip.pjsua.pjsua_call_media_status;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.text.format.DateFormat;

import com.csipsimple.R;
import com.csipsimple.db.DBAdapter;
import com.csipsimple.models.Account;
import com.csipsimple.models.CallInfo;
import com.csipsimple.models.CallInfo.UnavailableException;
import com.csipsimple.utils.CallLogHelper;
import com.csipsimple.utils.Compatibility;
import com.csipsimple.utils.Log;
import com.csipsimple.utils.PreferencesWrapper;

public class UAStateReceiver extends Callback {
	static String THIS_FILE = "SIP UA Receiver";

	final static String ACTION_PHONE_STATE_CHANGED = "android.intent.action.PHONE_STATE";
	
	private boolean autoAcceptCurrent = false;

	private SipNotifications notificationManager;
	private SipService service;
//	private ComponentName remoteControlResponder;


	
	@Override
	public void on_incoming_call(int acc_id, final int callId, SWIGTYPE_p_pjsip_rx_data rdata) {
		CallInfo callInfo = getCallInfo(callId);
		Log.d(THIS_FILE, "Incoming call <<");
		treatIncomingCall(acc_id, callInfo);
		msgHandler.sendMessage(msgHandler.obtainMessage(ON_INCOMING_CALL, callInfo));
		Log.d(THIS_FILE, "Incoming call >>");
	}
	
	
	@Override
	public void on_call_state(int callId, pjsip_event e) {
		Log.d(THIS_FILE, "Call state <<");
		//Get current infos
		CallInfo callInfo = getCallInfo(callId);
		pjsip_inv_state callState = callInfo.getCallState();
		if (callState.equals(pjsip_inv_state.PJSIP_INV_STATE_DISCONNECTED)) {
			if(SipService.mediaManager != null) {
				SipService.mediaManager.stopAnnoucing();
				SipService.mediaManager.resetSettings();
			}
			if(incomingCallLock != null && incomingCallLock.isHeld()) {
				incomingCallLock.release();
			}
			// Call is now ended
			service.stopDialtoneGenerator();
			//TODO : should be stopped only if it's the current call.
			stopRecording();
		}
		msgHandler.sendMessage(msgHandler.obtainMessage(ON_CALL_STATE, callInfo));
		Log.d(THIS_FILE, "Call state >>");
	}

	@Override
	public void on_buddy_state(int buddy_id)
	{
		Log.e(THIS_FILE, "On buddy state");
		// buddy_info = pjsua.buddy_get_info(buddy_id, new pjsua_buddy_info());
	}

	@Override
	public void on_pager(int call_id, pj_str_t from, pj_str_t to, pj_str_t contact, pj_str_t mime_type, pj_str_t body)
	{
		//String phone = StringUtilits.deleteBrackets(from.getPtr());
		//smsDAO.addNewSMS(phone, body.getPtr(), 1, service.getDb());
		String msg = "on_pager NEW MESSAGE FROM: "
				+ from.getPtr() + " "
				+ "TEXT: " + body.getPtr();
		//msgHandler.sendMessage(msgHandler.obtainMessage(ON_PAGER, msg));
		//service.showNotificationForMessage(StringUtilits.parseSMSNumber(from.getPtr()),body.getPtr());
		Log.e(THIS_FILE, msg);
	}

	@Override
	public void on_pager2(int call_id, pj_str_t from, pj_str_t to, pj_str_t contact, pj_str_t mime_type, pj_str_t body, SWIGTYPE_p_pjsip_rx_data rdata)
	{
		String msg = "on_pager2 NEW MESSAGE FROM: "
				+ from.getPtr() + " "
				+ "TEXT: " + body.getPtr();
		Log.e(THIS_FILE, msg);
		//String msg = "new message";
		//msgHandler.sendMessage(msgHandler.obtainMessage(ON_PAGER2, msg));
	}

	@Override
	public void on_pager_status(int call_id, pj_str_t to, pj_str_t body, pjsip_status_code status, pj_str_t reason)
	{
		Log.e(THIS_FILE, "Message in on pager status");
	}
	
	@Override
	public void on_pager_status2(int call_id, pj_str_t to, pj_str_t body, pjsip_status_code status, pj_str_t reason, SWIGTYPE_p_pjsip_tx_data tdata, SWIGTYPE_p_pjsip_rx_data rdata)
	{
		Log.e(THIS_FILE, "Message in on pager status2");
	}

	@Override
	public void on_reg_state(int accountId) {
		Log.d(THIS_FILE, "New reg state for : " + accountId);
		msgHandler.sendMessage(msgHandler.obtainMessage(ON_REGISTRATION_STATE, accountId));
	}

	@Override
	public void on_stream_created(int call_id, SWIGTYPE_p_pjmedia_session sess, long stream_idx, SWIGTYPE_p_p_pjmedia_port p_port) {
		Log.d(THIS_FILE, "Stream created");
	}
	
	@Override
	public void on_stream_destroyed(int callId, SWIGTYPE_p_pjmedia_session sess, long streamIdx) {
		Log.d(THIS_FILE, "Stream destroyed");
	}

	@Override
	public void on_call_media_state(int callId) {
		if(SipService.mediaManager != null) {
			SipService.mediaManager.stopRing();
		}
		if(incomingCallLock != null && incomingCallLock.isHeld()) {
			incomingCallLock.release();
		}
		
		pjsua_call_info info = new pjsua_call_info();
		pjsua.call_get_info(callId, info);
		CallInfo callInfo = new CallInfo(info);
		if (callInfo.getMediaStatus().equals(pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE)) {
			pjsua.conf_connect(info.getConf_slot(), 0);
			pjsua.conf_connect(0, info.getConf_slot());
			pjsua.conf_adjust_tx_level(0, service.prefsWrapper.getSpeakerLevel());
			float micLevel = service.prefsWrapper.getMicLevel();
			if(SipService.mediaManager != null && SipService.mediaManager.isUserWantMicrophoneMute()) {
				micLevel = 0;
			}
			pjsua.conf_adjust_rx_level(0, micLevel);
			
			
		}
		
		msgHandler.sendMessage(msgHandler.obtainMessage(ON_MEDIA_STATE, callInfo));
	}
	
	
	
	// -------
	// Current call management -- assume for now one unique call is managed
	// -------
	private HashMap<Integer, CallInfo> callsList = new HashMap<Integer, CallInfo>();
	//private long currentCallStart = 0;
	
	public CallInfo getCallInfo(Integer callId) {
		Log.d(THIS_FILE, "Get call info");
		CallInfo callInfo = callsList.get(callId);
		if(callInfo == null) {
			try {
				callInfo = new CallInfo(callId);
				callsList.put(callId, callInfo);
			} catch (UnavailableException e) {
				//TODO : treat error
			}
		} else {
			//Update from pjsip
			try {
				callInfo.updateFromPj();
			} catch (UnavailableException e) {
				//TODO : treat error
			}
		}
		
		return callInfo;
	}
	
	public CallInfo[] getCalls() {
		if(callsList != null ) {
			
			CallInfo[] callsInfos = new CallInfo[callsList.size()];
			int i = 0;
			for( Entry<Integer, CallInfo> entry : callsList.entrySet()) {
				callsInfos[i] = entry.getValue();
				i++;
			}
			return callsInfos;
		}
		return null;
	}

	

	private WorkerHandler msgHandler;
	private HandlerThread handlerThread;
	private WakeLock incomingCallLock;

	private static final int ON_INCOMING_CALL = 1;
	private static final int ON_CALL_STATE = 2;
	private static final int ON_MEDIA_STATE = 3;
	private static final int ON_REGISTRATION_STATE = 4;
	private static final int ON_PAGER = 5;


    
	private class WorkerHandler extends Handler {

		public WorkerHandler(Looper looper) {
            super(looper);
			Log.d(THIS_FILE, "Create async worker !!!");
        }
			
		public void handleMessage(Message msg) {
			Log.e(THIS_FILE, "handleMessage: " + msg.toString());
			switch (msg.what) {
			case ON_INCOMING_CALL:{
				//CallInfo callInfo = (CallInfo) msg.obj;
				
				
				break;
			}
			
			case ON_CALL_STATE:{
				CallInfo callInfo = (CallInfo) msg.obj;
				pjsip_inv_state callState = callInfo.getCallState();
				
				if (callState.equals(pjsip_inv_state.PJSIP_INV_STATE_INCOMING) || 
						callState.equals(pjsip_inv_state.PJSIP_INV_STATE_CALLING)) {
					notificationManager.showNotificationForCall(callInfo);
					launchCallHandler(callInfo);
					broadCastAndroidCallState("RINGING", callInfo.getRemoteContact());
				} else if (callState.equals(pjsip_inv_state.PJSIP_INV_STATE_EARLY)) {
					broadCastAndroidCallState("OFFHOOK", callInfo.getRemoteContact());
				} else if(callState.equals(pjsip_inv_state.PJSIP_INV_STATE_CONFIRMED)) {
					broadCastAndroidCallState("OFFHOOK", callInfo.getRemoteContact());
					callInfo.callStart = System.currentTimeMillis();
				}else if (callState.equals(pjsip_inv_state.PJSIP_INV_STATE_DISCONNECTED)) {
					//TODO : should manage multiple calls
					if(getActiveCallInProgress() == null) {
						notificationManager.cancelCalls();
					}
					Log.d(THIS_FILE, "Finish call2");
					
					//CallLog
					ContentValues cv = CallLogHelper.logValuesForCall(service, callInfo, callInfo.callStart);
					
					//Fill our own database
					DBAdapter database = new DBAdapter(service);
					database.open();
					database.insertCallLog(cv);
					database.close();
					Integer isNew = cv.getAsInteger(CallLog.Calls.NEW);
					if(isNew != null && isNew == 1) {
						notificationManager.showNotificationForMissedCall(cv);
					}
					
					//If needed fill native database
					if(service.prefsWrapper.useIntegrateCallLogs()) {
						//Don't add with new flag
						cv.put(CallLog.Calls.NEW, false);
						
						//Reformat number for callogs
						Pattern p = Pattern.compile("^(?:\")?([^<\"]*)(?:\")?[ ]*(?:<)?sip(?:s)?:([^@]*)@[^>]*(?:>)?");
						Matcher m = p.matcher(cv.getAsString(Calls.NUMBER));
						if (m.matches()) {
							
						//	remoteContact = m.group(1);
							String phoneNumber =  m.group(2);
							if(!TextUtils.isEmpty(phoneNumber)) {
								cv.put(Calls.NUMBER, phoneNumber);
								// For log in call logs => don't add as new calls... we manage it ourselves.
								cv.put(Calls.NEW, false);
								CallLogHelper.addCallLog(service, cv);
							}
						}
					}
					callInfo.setIncoming(false);
					callInfo.callStart = 0;
					
					
					broadCastAndroidCallState("IDLE", callInfo.getRemoteContact());
				}
				onBroadcastCallState(callInfo);
				break;
			}
			case ON_MEDIA_STATE:{
				CallInfo mediaCallInfo = (CallInfo) msg.obj;
				CallInfo callInfo = callsList.get(mediaCallInfo.getCallId());
				callInfo.setMediaState(mediaCallInfo.getMediaStatus());
				onBroadcastCallState(callInfo);
				break;
			}
			case ON_REGISTRATION_STATE:{
				Log.d(THIS_FILE, "In reg state");
				// Update sip service (for notifications
				((SipService) service).updateRegistrationsState();
				// Send a broadcast message that for an account
				// registration state has changed
				Intent regStateChangedIntent = new Intent(SipService.ACTION_SIP_REGISTRATION_CHANGED);
				service.sendBroadcast(regStateChangedIntent);
				break;
			}
				case ON_PAGER: {
					//startSMSRing();
					//String message = (String) msg.obj;
					//service.showMessage(message);
					Log.e(THIS_FILE, "yana you in CASE ON_PAGER");
					//stopRing();
					break;
				}
			}
		}
	};
	
	
	private void treatIncomingCall(int accountId, CallInfo callInfo) {
		int callId = callInfo.getCallId();
		
		//Get lock while ringing to be sure notification is well done !
		PowerManager pman = (PowerManager) service.getSystemService(Context.POWER_SERVICE);
		if (incomingCallLock == null) {
			incomingCallLock = pman.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "com.csipsimple.incomingCallLock");
			incomingCallLock.setReferenceCounted(false);
		}
		//Extra check if set reference counted is false ???
		if(!incomingCallLock.isHeld()) {
			incomingCallLock.acquire();
		}
		service.getSystemService(Context.POWER_SERVICE);
		
		String remContact = callInfo.getRemoteContact();
		callInfo.setIncoming(true);
		notificationManager.showNotificationForCall(callInfo);

		//Auto answer feature
		boolean shouldAutoAnswer = false;
		//In account
		Account acc = service.getAccountForPjsipId(accountId);
		if(acc != null) {
			Pattern p = Pattern.compile("^(?:\")?([^<\"]*)(?:\")?[ ]*(?:<)?sip(?:s)?:([^@]*@[^>]*)(?:>)?");
			Matcher m = p.matcher(remContact);
			String number = remContact;
			if (m.matches()) {
				number = m.group(2);
			}
			Log.w(THIS_FILE, "Search if should auto answer : "+number);
			shouldAutoAnswer = acc.isAutoAnswerNumber(number, service.db);
		}
		//Or by api
		if (autoAcceptCurrent || shouldAutoAnswer) {
			// Automatically answer incoming calls with 200/OK
			service.callAnswer(callId, 200);
			autoAcceptCurrent = false;
		} else {

			// Automatically answer incoming calls with 180/RINGING
			service.callAnswer(callId, 180);
			
			if(service.getGSMCallState() == TelephonyManager.CALL_STATE_IDLE) {
				if(SipService.mediaManager != null) {
					SipService.mediaManager.startRing(remContact);
				}
				broadCastAndroidCallState("RINGING", remContact);
			}
			
			
			launchCallHandler(callInfo);
		}
	}
	
	// -------
	// Public configuration for receiver
	// -------
	public void setAutoAnswerNext(boolean auto_response) {
		autoAcceptCurrent = auto_response;
	}
	

	public void initService(SipService srv) {
		service = srv;

		notificationManager = new SipNotifications(srv);
		
		if(handlerThread == null) {
			handlerThread = new HandlerThread("UAStateAsyncWorker");
			handlerThread.start();
		}
		if(msgHandler == null) {
			msgHandler = new WorkerHandler(handlerThread.getLooper());
		}
	}
	

	public void stopService() {
		if(handlerThread != null) {
			boolean fails = true;
			
			if(Compatibility.isCompatible(5)) {
				try {
					Method method = handlerThread.getClass().getDeclaredMethod("quit");
					method.invoke(handlerThread);
					fails = false;
				} catch (Exception e) {
					Log.d(THIS_FILE, "Something is wrong with api level declared use fallback method");
				}
			}
			if (fails && handlerThread.isAlive()) {
				try {
					//This is needed for android 4 and lower
					handlerThread.join(500);
					/*
					if (handlerThread.isAlive()) {
						handlerThread.
					}
					*/
				} catch (Exception e) {
					Log.e(THIS_FILE, "Can t finish handler thread....", e);
				}
			}
			handlerThread = null;
		}
		
		msgHandler = null;
		
	}

	// --------
	// Private methods
	// --------
	

	private void onBroadcastCallState(final CallInfo callInfo) {
		//Internal event
		Intent callStateChangedIntent = new Intent(SipService.ACTION_SIP_CALL_CHANGED);
		callStateChangedIntent.putExtra(SipService.EXTRA_CALL_INFO, callInfo);
		service.sendBroadcast(callStateChangedIntent);
		
		
	}

	private void broadCastAndroidCallState(String state, String number) {
		//Android normalized event
		Intent intent = new Intent(ACTION_PHONE_STATE_CHANGED);
		intent.putExtra(TelephonyManager.EXTRA_STATE, state);
		if (number != null) {
			intent.putExtra(TelephonyManager.EXTRA_INCOMING_NUMBER, number);
		}
		intent.putExtra(service.getString(R.string.app_name), true);
		service.sendBroadcast(intent, android.Manifest.permission.READ_PHONE_STATE);
	}
	
	/**
	 * 
	 * @param currentCallInfo2 
	 * @param callInfo
	 */
	private void launchCallHandler(CallInfo currentCallInfo2) {
		
		// Launch activity to choose what to do with this call
		Intent callHandlerIntent = new Intent(SipService.ACTION_SIP_CALL_UI); //new Intent(service, getInCallClass());
		callHandlerIntent.putExtra(SipService.EXTRA_CALL_INFO, currentCallInfo2);
		callHandlerIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

		Log.d(THIS_FILE, "Anounce call activity");
		service.startActivity(callHandlerIntent);

	}

	
	/**
	 * Check if any of call infos indicate there is an active
	 * call in progress.
	 */
	public CallInfo getActiveCallInProgress() {
		//Log.d(THIS_FILE, "isActiveCallInProgress(), number of calls: " + callsList.keySet().size());
		
		//
		// Go through the whole list of calls and check if
		// any call is in an active state.
		//
		for (Integer i : callsList.keySet()) { 
			CallInfo callInfo = getCallInfo(i);
			if (callInfo.isActive()) {
				return callInfo;
			}
		}
		return null;
	}
	
	
	/**
	 * Broadcast the Headset button press event internally if
	 * there is any call in progress.
	 */
	public boolean handleHeadsetButton() {
		CallInfo callInfo = getActiveCallInProgress();
		if (callInfo != null) {
			// Headset button has been pressed by user. If there is an
			// incoming call ringing the button will be used to answer the
			// call. If there is an ongoing call in progress the button will
			// be used to hangup the call or mute the microphone.
    		pjsip_inv_state state = callInfo.getCallState();
    		if (callInfo.isIncoming() && 
    				(state == pjsip_inv_state.PJSIP_INV_STATE_INCOMING || 
    				state == pjsip_inv_state.PJSIP_INV_STATE_EARLY)) {
    			service.callAnswer(callInfo.getCallId(), pjsip_status_code.PJSIP_SC_OK.swigValue());
    			return true;
    		}else if(state == pjsip_inv_state.PJSIP_INV_STATE_INCOMING || 
    				state == pjsip_inv_state.PJSIP_INV_STATE_EARLY ||
    				state == pjsip_inv_state.PJSIP_INV_STATE_CALLING ||
    				state == pjsip_inv_state.PJSIP_INV_STATE_CONFIRMED ||
    				state == pjsip_inv_state.PJSIP_INV_STATE_CONNECTING){
    			//
				// In the Android phone app using the media button during
				// a call mutes the microphone instead of terminating the call.
				// We check here if this should be the behavior here or if
				// the call should be cleared.
				//
				switch(service.prefsWrapper.getHeadsetAction()) {
				//TODO : add hold -
				case PreferencesWrapper.HEADSET_ACTION_CLEAR_CALL:
					service.callHangup(callInfo.getCallId(), 0);
					break;
				case PreferencesWrapper.HEADSET_ACTION_MUTE:
					SipService.mediaManager.toggleMute();
					break;
				}
				return true;
    		}
		}
		return false;
	}
	
	// Recorder
	private static int INVALID_RECORD = -1; 
	private int recordedCall = INVALID_RECORD;
	private int recPort = -1;
	private int recorderId = -1;
	private int recordedConfPort = -1;
	public void startRecording(int callId) {
		// Ensure nothing is recording actually
		if (recordedCall == INVALID_RECORD) {
			CallInfo callInfo = getCallInfo(callId);
			if(callInfo == null || !callInfo.getMediaStatus().equals(pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE)) {
				return;
			}
			
		    File mp3File = getRecordFile(callInfo.getRemoteContact());
		    if (mp3File != null){
				int[] recId = new int[1];
				pj_str_t filename = pjsua.pj_str_copy(mp3File.getAbsolutePath());
				int status = pjsua.recorder_create(filename, 0, (byte[]) null, 0, 0, recId);
				if(status == pjsuaConstants.PJ_SUCCESS) {
					recorderId = recId[0];
					Log.d(THIS_FILE, "Record started : " + recorderId);
					recordedConfPort = callInfo.getConfPort();
					recPort = pjsua.recorder_get_conf_port(recorderId);
					pjsua.conf_connect(recordedConfPort, recPort);
					pjsua.conf_connect(0, recPort);
					recordedCall = callId;
				}
		    }else {
		    	//TODO: toaster
		    	Log.w(THIS_FILE, "Impossible to write file");
		    }
		}
	}
	
	public void stopRecording() {
		Log.d(THIS_FILE, "Stop recording " + recordedCall+" et "+ recorderId);
		if (recorderId != -1) {
			pjsua.recorder_destroy(recorderId);
			recorderId = -1;
		}
		recordedCall = INVALID_RECORD;
	}
	
	public boolean canRecord(int callId) {
		if (recordedCall == INVALID_RECORD) {
			CallInfo callInfo = getCallInfo(callId);
			if(callInfo == null || !callInfo.getMediaStatus().equals(pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE)) {
				return false;
			}
			return true;
		}
		return false;
	}
	
	public int getRecordedCall() {
		return recordedCall; 
	}
	
	private File getRecordFile(String remoteContact) {
		File root = Environment.getExternalStorageDirectory();
	    if (root.canWrite()){
			File dir = new File(root.getAbsolutePath() + File.separator + "CSipSimple");
			dir.mkdirs();
			Log.d(THIS_FILE, "Create directory " + dir.getAbsolutePath());
			Date d = new Date();
			
			File file = new File(dir.getAbsoluteFile() + File.separator + sanitizeForFile(remoteContact)+ "_"+DateFormat.format("MM-dd-yy_kkmmss", d)+".wav");
			Log.d(THIS_FILE, "Out dir " + file.getAbsolutePath());
			return file;
	    }
	    return null;
	}


	private String sanitizeForFile(String remoteContact) {
		String fileName = remoteContact;
		fileName = fileName.replaceAll("[\\.\\\\<>:; \"\'\\*]", "_");
		return fileName;
	}
}
