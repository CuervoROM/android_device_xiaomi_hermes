/*
* Copyright (C) 2014 The OmniROM Project <http://www.omnirom.org>
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.android.internal.telephony;

import static com.android.internal.telephony.RILConstants.*;
import com.android.internal.telephony.dataconnection.ApnProfileOmh;
import com.android.internal.telephony.dataconnection.ApnSetting;
import com.android.internal.telephony.uicc.IccIoResult;

import android.content.Context;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.AsyncResult;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.telephony.SignalStrength;
import android.telephony.SmsMessage;
import android.util.Log;
import android.content.res.Resources;

import com.android.internal.telephony.cdma.CdmaInformationRecords;
import com.android.internal.telephony.dataconnection.DataCallResponse;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

import java.io.IOException;
import java.io.InputStream;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;


import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.UiccController;

public class MediaTekRIL extends RIL implements CommandsInterface {
  static final String LOG_TAG = "RILJ";
  private int dtmfRequestCount = 0;
  private final int MAXIMUM_DTMF_REQUEST = 32;

  private int getAvailableNetworkRequests = 0;

  // TODO: Support multiSIM
  // Sim IDs are 0 / 1
  int mSimId = 0;
  boolean mIsModemInitialized = false;

  static final int RIL_REQUEST_SET_PHONE_RAT_FAMILY = 131;
  static final int RIL_REQUEST_GET_PHONE_RAT_FAMILY = 130;

  static final int RIL_UNSOL_SET_PHONE_RAT_FAMILY_COMPLETE = 1042;

  static final int RIL_REQUEST_MTK_BASE = 2000;
  static final int RIL_REQUEST_DUAL_SIM_MODE_SWITCH = (RIL_REQUEST_MTK_BASE + 12);

  private static final String OEM_IDENTIFIER = "QOEMHOOK";



  public MediaTekRIL(Context context, int networkMode, int cdmaSubscription) {
    super(context, networkMode, cdmaSubscription, null);
  }

  public MediaTekRIL(Context context, int networkMode, int cdmaSubscription, Integer instanceId) {
    super(context, networkMode, cdmaSubscription, instanceId);
  }


  public static byte[] hexStringToBytes(String s) {
    byte[] ret;

    if (s == null) return null;

    int len = s.length();
    ret = new byte[len/2];

    for (int i=0 ; i <len ; i+=2) {
      ret[i/2] = (byte) ((hexCharToInt(s.charAt(i)) << 4)
      | hexCharToInt(s.charAt(i+1)));
    }

    return ret;
  }

  static int hexCharToInt(char c) {
    if (c >= '0' && c <= '9') return (c - '0');
    if (c >= 'A' && c <= 'F') return (c - 'A' + 10);
    if (c >= 'a' && c <= 'f') return (c - 'a' + 10);

    throw new RuntimeException ("invalid hex char '" + c + "'");
  }

  static String
  responseToString(int request)
  {
/*
cat libs/telephony/ril_unsol_commands.h \
| egrep "^ *{RIL_" \
| sed -re 's/\{RIL_([^,]+),[^,]+,([^}]+).+/case RIL_\1: return "\1";/'
*/
      switch(request) {
          case RIL_UNSOL_RESPONSE_RADIO_STATE_CHANGED: return "UNSOL_RESPONSE_RADIO_STATE_CHANGED";
          case RIL_UNSOL_RESPONSE_CALL_STATE_CHANGED: return "UNSOL_RESPONSE_CALL_STATE_CHANGED";
          case RIL_UNSOL_RESPONSE_VOICE_NETWORK_STATE_CHANGED: return "UNSOL_RESPONSE_VOICE_NETWORK_STATE_CHANGED";
          case RIL_UNSOL_RESPONSE_NEW_SMS: return "UNSOL_RESPONSE_NEW_SMS";
          case RIL_UNSOL_RESPONSE_NEW_SMS_STATUS_REPORT: return "UNSOL_RESPONSE_NEW_SMS_STATUS_REPORT";
          case RIL_UNSOL_RESPONSE_NEW_SMS_ON_SIM: return "UNSOL_RESPONSE_NEW_SMS_ON_SIM";
          case RIL_UNSOL_ON_USSD: return "UNSOL_ON_USSD";
          case RIL_UNSOL_ON_USSD_REQUEST: return "UNSOL_ON_USSD_REQUEST";
          case RIL_UNSOL_NITZ_TIME_RECEIVED: return "UNSOL_NITZ_TIME_RECEIVED";
          case RIL_UNSOL_SIGNAL_STRENGTH: return "UNSOL_SIGNAL_STRENGTH";
          case RIL_UNSOL_DATA_CALL_LIST_CHANGED: return "UNSOL_DATA_CALL_LIST_CHANGED";
          case RIL_UNSOL_SUPP_SVC_NOTIFICATION: return "UNSOL_SUPP_SVC_NOTIFICATION";
          case RIL_UNSOL_STK_SESSION_END: return "UNSOL_STK_SESSION_END";
          case RIL_UNSOL_STK_PROACTIVE_COMMAND: return "UNSOL_STK_PROACTIVE_COMMAND";
          case RIL_UNSOL_STK_EVENT_NOTIFY: return "UNSOL_STK_EVENT_NOTIFY";
          case RIL_UNSOL_STK_CALL_SETUP: return "UNSOL_STK_CALL_SETUP";
          case RIL_UNSOL_SIM_SMS_STORAGE_FULL: return "UNSOL_SIM_SMS_STORAGE_FULL";
          case RIL_UNSOL_SIM_REFRESH: return "UNSOL_SIM_REFRESH";
          case RIL_UNSOL_CALL_RING: return "UNSOL_CALL_RING";
          case RIL_UNSOL_RESPONSE_SIM_STATUS_CHANGED: return "UNSOL_RESPONSE_SIM_STATUS_CHANGED";
          case RIL_UNSOL_RESPONSE_CDMA_NEW_SMS: return "UNSOL_RESPONSE_CDMA_NEW_SMS";
          case RIL_UNSOL_RESPONSE_NEW_BROADCAST_SMS: return "UNSOL_RESPONSE_NEW_BROADCAST_SMS";
          case RIL_UNSOL_CDMA_RUIM_SMS_STORAGE_FULL: return "UNSOL_CDMA_RUIM_SMS_STORAGE_FULL";
          case RIL_UNSOL_RESTRICTED_STATE_CHANGED: return "UNSOL_RESTRICTED_STATE_CHANGED";
          case RIL_UNSOL_ENTER_EMERGENCY_CALLBACK_MODE: return "UNSOL_ENTER_EMERGENCY_CALLBACK_MODE";
          case RIL_UNSOL_CDMA_CALL_WAITING: return "UNSOL_CDMA_CALL_WAITING";
          case RIL_UNSOL_CDMA_OTA_PROVISION_STATUS: return "UNSOL_CDMA_OTA_PROVISION_STATUS";
          case RIL_UNSOL_CDMA_INFO_REC: return "UNSOL_CDMA_INFO_REC";
          case RIL_UNSOL_OEM_HOOK_RAW: return "UNSOL_OEM_HOOK_RAW";
          case RIL_UNSOL_RINGBACK_TONE: return "UNSOL_RINGBACK_TONE";
          case RIL_UNSOL_RESEND_INCALL_MUTE: return "UNSOL_RESEND_INCALL_MUTE";
          case RIL_UNSOL_CDMA_SUBSCRIPTION_SOURCE_CHANGED: return "CDMA_SUBSCRIPTION_SOURCE_CHANGED";
          case RIL_UNSOl_CDMA_PRL_CHANGED: return "UNSOL_CDMA_PRL_CHANGED";
          case RIL_UNSOL_EXIT_EMERGENCY_CALLBACK_MODE: return "UNSOL_EXIT_EMERGENCY_CALLBACK_MODE";
          case RIL_UNSOL_RIL_CONNECTED: return "UNSOL_RIL_CONNECTED";
          case RIL_UNSOL_VOICE_RADIO_TECH_CHANGED: return "UNSOL_VOICE_RADIO_TECH_CHANGED";
          case RIL_UNSOL_CELL_INFO_LIST: return "UNSOL_CELL_INFO_LIST";
          case RIL_UNSOL_RESPONSE_IMS_NETWORK_STATE_CHANGED:
              return "UNSOL_RESPONSE_IMS_NETWORK_STATE_CHANGED";
          case RIL_UNSOL_UICC_SUBSCRIPTION_STATUS_CHANGED:
                  return "RIL_UNSOL_UICC_SUBSCRIPTION_STATUS_CHANGED";
          case RIL_UNSOL_SRVCC_STATE_NOTIFY:
                  return "UNSOL_SRVCC_STATE_NOTIFY";
          case RIL_UNSOL_HARDWARE_CONFIG_CHANGED: return "RIL_UNSOL_HARDWARE_CONFIG_CHANGED";
          //case RIL_UNSOL_ON_SS: return "UNSOL_ON_SS";
          case RIL_UNSOL_STK_CC_ALPHA_NOTIFY: return "UNSOL_STK_CC_ALPHA_NOTIFY";
          case RIL_UNSOL_STK_SEND_SMS_RESULT: return "RIL_UNSOL_STK_SEND_SMS_RESULT";
          case RIL_UNSOL_SET_PHONE_RAT_FAMILY_COMPLETE: return "UNSOL_SET_PHONE_RAT_FAMILY_COMPLETE";
          default: return "<unknown response>";
      }
  }

  protected RILRequest
  processSolicited (Parcel p) {
    int serial, error;
    boolean found = false;

    serial = p.readInt();
    error = p.readInt();

    RILRequest rr;

    rr = findAndRemoveRequestFromList(serial);

    if (rr == null) {
      Rlog.w(RILJ_LOG_TAG, "Unexpected solicited response! sn: "
      + serial + " error: " + error);
      return null;
    }

    Object ret = null;

    if (error == 0 || p.dataAvail() > 0) {
      // either command succeeds or command fails but with data payload
      try {switch (rr.mRequest) {
        /*
        cat libs/telephony/ril_commands.h \
        | egrep "^ *{RIL_" \
        | sed -re 's/\{([^,]+),[^,]+,([^}]+).+/case \1: ret = \2(p); break;/'
        */
        case RIL_REQUEST_GET_SIM_STATUS: ret =  responseIccCardStatus(p); break;
        case RIL_REQUEST_ENTER_SIM_PIN: ret =  responseInts(p); break;
        case RIL_REQUEST_ENTER_SIM_PUK: ret =  responseInts(p); break;
        case RIL_REQUEST_ENTER_SIM_PIN2: ret =  responseInts(p); break;
        case RIL_REQUEST_ENTER_SIM_PUK2: ret =  responseInts(p); break;
        case RIL_REQUEST_CHANGE_SIM_PIN: ret =  responseInts(p); break;
        case RIL_REQUEST_CHANGE_SIM_PIN2: ret =  responseInts(p); break;
        case RIL_REQUEST_ENTER_DEPERSONALIZATION_CODE: ret =  responseInts(p); break;
        case RIL_REQUEST_GET_CURRENT_CALLS: ret =  responseCallList(p); break;
        case RIL_REQUEST_DIAL: ret =  responseVoid(p); break;
        case RIL_REQUEST_GET_IMSI: ret =  responseString(p); break;
        case RIL_REQUEST_HANGUP: ret =  responseVoid(p); break;
        case RIL_REQUEST_HANGUP_WAITING_OR_BACKGROUND: ret =  responseVoid(p); break;
        case RIL_REQUEST_HANGUP_FOREGROUND_RESUME_BACKGROUND: {
          if (mTestingEmergencyCall.getAndSet(false)) {
            if (mEmergencyCallbackModeRegistrant != null) {
              riljLog("testing emergency call, notify ECM Registrants");
              mEmergencyCallbackModeRegistrant.notifyRegistrant();
            }
          }
          ret =  responseVoid(p);
          break;
        }
        case RIL_REQUEST_SWITCH_WAITING_OR_HOLDING_AND_ACTIVE: ret =  responseVoid(p); break;
        case RIL_REQUEST_CONFERENCE: ret =  responseVoid(p); break;
        case RIL_REQUEST_UDUB: ret =  responseVoid(p); break;
        case RIL_REQUEST_LAST_CALL_FAIL_CAUSE: ret =  responseInts(p); break;
        case RIL_REQUEST_SIGNAL_STRENGTH: ret =  responseSignalStrength(p); break;
        case RIL_REQUEST_VOICE_REGISTRATION_STATE: ret =  responseStrings(p); break;
        case RIL_REQUEST_DATA_REGISTRATION_STATE: ret =  responseStrings(p); break;
        //case RIL_REQUEST_OPERATOR: ret =  responseStrings(p); break;
        case RIL_REQUEST_OPERATOR: ret =  responseOperator(p); break;
        case RIL_REQUEST_RADIO_POWER: ret =  responseVoid(p); break;
        case RIL_REQUEST_DTMF: ret =  responseVoid(p); break;
        case RIL_REQUEST_SEND_SMS: ret =  responseSMS(p); break;
        case RIL_REQUEST_SEND_SMS_EXPECT_MORE: ret =  responseSMS(p); break;
        case RIL_REQUEST_SETUP_DATA_CALL: ret =  responseSetupDataCall(p); break;
        case RIL_REQUEST_SIM_IO: ret =  responseICC_IO(p); break;
        case RIL_REQUEST_SEND_USSD: ret =  responseVoid(p); break;
        case RIL_REQUEST_CANCEL_USSD: ret =  responseVoid(p); break;
        case RIL_REQUEST_GET_CLIR: ret =  responseInts(p); break;
        case RIL_REQUEST_SET_CLIR: ret =  responseVoid(p); break;
        case RIL_REQUEST_QUERY_CALL_FORWARD_STATUS: ret =  responseCallForward(p); break;
        case RIL_REQUEST_SET_CALL_FORWARD: ret =  responseVoid(p); break;
        case RIL_REQUEST_QUERY_CALL_WAITING: ret =  responseInts(p); break;
        case RIL_REQUEST_SET_CALL_WAITING: ret =  responseVoid(p); break;
        case RIL_REQUEST_SMS_ACKNOWLEDGE: ret =  responseVoid(p); break;
        case RIL_REQUEST_GET_IMEI: ret =  responseString(p); break;
        case RIL_REQUEST_GET_IMEISV: ret =  responseString(p); break;
        case RIL_REQUEST_ANSWER: ret =  responseVoid(p); break;
        case RIL_REQUEST_DEACTIVATE_DATA_CALL: ret =  responseVoid(p); break;
        case RIL_REQUEST_QUERY_FACILITY_LOCK: ret =  responseInts(p); break;
        case RIL_REQUEST_SET_FACILITY_LOCK: ret =  responseInts(p); break;
        case RIL_REQUEST_CHANGE_BARRING_PASSWORD: ret =  responseVoid(p); break;
        case RIL_REQUEST_QUERY_NETWORK_SELECTION_MODE: ret =  responseInts(p); break;
        case RIL_REQUEST_SET_NETWORK_SELECTION_AUTOMATIC: ret =  responseVoid(p); break;
        case RIL_REQUEST_SET_NETWORK_SELECTION_MANUAL: ret =  responseVoid(p); break;
        case RIL_REQUEST_QUERY_AVAILABLE_NETWORKS : ret =  responseOperatorInfos(p); break;
        case RIL_REQUEST_DTMF_START: ret =  responseVoid(p); break;
        case RIL_REQUEST_DTMF_STOP: ret =  responseVoid(p); break;
        case RIL_REQUEST_BASEBAND_VERSION: ret =  responseString(p); break;
        case RIL_REQUEST_SEPARATE_CONNECTION: ret =  responseVoid(p); break;
        case RIL_REQUEST_SET_MUTE: ret =  responseVoid(p); break;
        case RIL_REQUEST_GET_MUTE: ret =  responseInts(p); break;
        case RIL_REQUEST_QUERY_CLIP: ret =  responseInts(p); break;
        case RIL_REQUEST_LAST_DATA_CALL_FAIL_CAUSE: ret =  responseInts(p); break;
        case RIL_REQUEST_DATA_CALL_LIST: ret =  responseDataCallList(p); break;
        case RIL_REQUEST_RESET_RADIO: ret =  responseVoid(p); break;
        case RIL_REQUEST_OEM_HOOK_RAW: ret =  responseRaw(p); break;
        case RIL_REQUEST_OEM_HOOK_STRINGS: ret =  responseStrings(p); break;
        case RIL_REQUEST_SCREEN_STATE: ret =  responseVoid(p); break;
        case RIL_REQUEST_SET_SUPP_SVC_NOTIFICATION: ret =  responseVoid(p); break;
        case RIL_REQUEST_WRITE_SMS_TO_SIM: ret =  responseInts(p); break;
        case RIL_REQUEST_DELETE_SMS_ON_SIM: ret =  responseVoid(p); break;
        case RIL_REQUEST_SET_BAND_MODE: ret =  responseVoid(p); break;
        case RIL_REQUEST_QUERY_AVAILABLE_BAND_MODE: ret =  responseInts(p); break;
        case RIL_REQUEST_STK_GET_PROFILE: ret =  responseString(p); break;
        case RIL_REQUEST_STK_SET_PROFILE: ret =  responseVoid(p); break;
        case RIL_REQUEST_STK_SEND_ENVELOPE_COMMAND: ret =  responseString(p); break;
        case RIL_REQUEST_STK_SEND_TERMINAL_RESPONSE: ret =  responseVoid(p); break;
        case RIL_REQUEST_STK_HANDLE_CALL_SETUP_REQUESTED_FROM_SIM: ret =  responseInts(p); break;
        case RIL_REQUEST_EXPLICIT_CALL_TRANSFER: ret =  responseVoid(p); break;
        case RIL_REQUEST_SET_PREFERRED_NETWORK_TYPE: ret =  responseVoid(p); break;
        case RIL_REQUEST_GET_PREFERRED_NETWORK_TYPE: ret =  responseGetPreferredNetworkType(p); break;
        case RIL_REQUEST_GET_NEIGHBORING_CELL_IDS: ret = responseCellList(p); break;
        case RIL_REQUEST_SET_LOCATION_UPDATES: ret =  responseVoid(p); break;
        case RIL_REQUEST_CDMA_SET_SUBSCRIPTION_SOURCE: ret =  responseVoid(p); break;
        case RIL_REQUEST_CDMA_SET_ROAMING_PREFERENCE: ret =  responseVoid(p); break;
        case RIL_REQUEST_CDMA_QUERY_ROAMING_PREFERENCE: ret =  responseInts(p); break;
        case RIL_REQUEST_SET_TTY_MODE: ret =  responseVoid(p); break;
        case RIL_REQUEST_QUERY_TTY_MODE: ret =  responseInts(p); break;
        case RIL_REQUEST_CDMA_SET_PREFERRED_VOICE_PRIVACY_MODE: ret =  responseVoid(p); break;
        case RIL_REQUEST_CDMA_QUERY_PREFERRED_VOICE_PRIVACY_MODE: ret =  responseInts(p); break;
        case RIL_REQUEST_CDMA_FLASH: ret =  responseVoid(p); break;
        case RIL_REQUEST_CDMA_BURST_DTMF: ret =  responseVoid(p); break;
        case RIL_REQUEST_CDMA_SEND_SMS: ret =  responseSMS(p); break;
        case RIL_REQUEST_CDMA_SMS_ACKNOWLEDGE: ret =  responseVoid(p); break;
        case RIL_REQUEST_GSM_GET_BROADCAST_CONFIG: ret =  responseGmsBroadcastConfig(p); break;
        case RIL_REQUEST_GSM_SET_BROADCAST_CONFIG: ret =  responseVoid(p); break;
        case RIL_REQUEST_GSM_BROADCAST_ACTIVATION: ret =  responseVoid(p); break;
        case RIL_REQUEST_CDMA_GET_BROADCAST_CONFIG: ret =  responseCdmaBroadcastConfig(p); break;
        case RIL_REQUEST_CDMA_SET_BROADCAST_CONFIG: ret =  responseVoid(p); break;
        case RIL_REQUEST_CDMA_BROADCAST_ACTIVATION: ret =  responseVoid(p); break;
        case RIL_REQUEST_CDMA_VALIDATE_AND_WRITE_AKEY: ret =  responseVoid(p); break;
        case RIL_REQUEST_CDMA_SUBSCRIPTION: ret =  responseStrings(p); break;
        case RIL_REQUEST_CDMA_WRITE_SMS_TO_RUIM: ret =  responseInts(p); break;
        case RIL_REQUEST_CDMA_DELETE_SMS_ON_RUIM: ret =  responseVoid(p); break;
        case RIL_REQUEST_DEVICE_IDENTITY: ret =  responseStrings(p); break;
        case RIL_REQUEST_GET_SMSC_ADDRESS: ret = responseString(p); break;
        case RIL_REQUEST_SET_SMSC_ADDRESS: ret = responseVoid(p); break;
        case RIL_REQUEST_EXIT_EMERGENCY_CALLBACK_MODE: ret = responseVoid(p); break;
        case RIL_REQUEST_REPORT_SMS_MEMORY_STATUS: ret = responseVoid(p); break;
        case RIL_REQUEST_REPORT_STK_SERVICE_IS_RUNNING: ret = responseVoid(p); break;
        case RIL_REQUEST_CDMA_GET_SUBSCRIPTION_SOURCE: ret =  responseInts(p); break;
        case RIL_REQUEST_GET_DATA_CALL_PROFILE: ret =  responseGetDataCallProfile(p); break;
        case RIL_REQUEST_ISIM_AUTHENTICATION: ret =  responseString(p); break;
        case RIL_REQUEST_ACKNOWLEDGE_INCOMING_GSM_SMS_WITH_PDU: ret = responseVoid(p); break;
        case RIL_REQUEST_STK_SEND_ENVELOPE_WITH_STATUS: ret = responseICC_IO(p); break;
        case RIL_REQUEST_VOICE_RADIO_TECH: ret = responseInts(p); break;
        case RIL_REQUEST_GET_CELL_INFO_LIST: ret = responseCellInfoList(p); break;
        case RIL_REQUEST_SET_UNSOL_CELL_INFO_LIST_RATE: ret = responseVoid(p); break;
        case RIL_REQUEST_SET_INITIAL_ATTACH_APN: ret = responseVoid(p); break;
        case RIL_REQUEST_SET_DATA_PROFILE: ret = responseVoid(p); break;
        case RIL_REQUEST_IMS_REGISTRATION_STATE: ret = responseInts(p); break;
        case RIL_REQUEST_IMS_SEND_SMS: ret =  responseSMS(p); break;
        case RIL_REQUEST_SIM_TRANSMIT_APDU_BASIC: ret =  responseICC_IO(p); break;
        case RIL_REQUEST_SIM_OPEN_CHANNEL: ret  = responseInts(p); break;
        case RIL_REQUEST_SIM_CLOSE_CHANNEL: ret  = responseVoid(p); break;
        case RIL_REQUEST_SIM_TRANSMIT_APDU_CHANNEL: ret = responseICC_IO(p); break;
        case RIL_REQUEST_SIM_GET_ATR: ret = responseString(p); break;
        case RIL_REQUEST_NV_READ_ITEM: ret = responseString(p); break;
        case RIL_REQUEST_NV_WRITE_ITEM: ret = responseVoid(p); break;
        case RIL_REQUEST_NV_WRITE_CDMA_PRL: ret = responseVoid(p); break;
        case RIL_REQUEST_NV_RESET_CONFIG: ret = responseVoid(p); break;
        case RIL_REQUEST_SET_UICC_SUBSCRIPTION: ret = responseVoid(p); break;
        case RIL_REQUEST_ALLOW_DATA: ret = responseVoid(p); break;
        case RIL_REQUEST_GET_HARDWARE_CONFIG: ret = responseHardwareConfig(p); break;
        case RIL_REQUEST_SIM_AUTHENTICATION: ret =  responseICC_IOBase64(p); break;
        case RIL_REQUEST_SHUTDOWN: ret = responseVoid(p); break;
        default:
        throw new RuntimeException("Unrecognized solicited response: " + rr.mRequest);
        //break;
      }} catch (Throwable tr) {
        // Exceptions here usually mean invalid RIL responses

        Rlog.w(RILJ_LOG_TAG, rr.serialString() + "< "
        + requestToString(rr.mRequest)
        + " exception, possible invalid RIL response", tr);

        if (rr.mResult != null) {
          AsyncResult.forMessage(rr.mResult, null, tr);
          rr.mResult.sendToTarget();
        }
        return rr;
      }
    }

    if (rr.mRequest == RIL_REQUEST_SHUTDOWN) {
      // Set RADIO_STATE to RADIO_UNAVAILABLE to continue shutdown process
      // regardless of error code to continue shutdown procedure.
      riljLog("Response to RIL_REQUEST_SHUTDOWN received. Error is " +
      error + " Setting Radio State to Unavailable regardless of error.");
      setRadioState(RadioState.RADIO_UNAVAILABLE);
    }

    // Here and below fake RIL_UNSOL_RESPONSE_SIM_STATUS_CHANGED, see b/7255789.
    // This is needed otherwise we don't automatically transition to the main lock
    // screen when the pin or puk is entered incorrectly.
    switch (rr.mRequest) {
      case RIL_REQUEST_ENTER_SIM_PUK:
      case RIL_REQUEST_ENTER_SIM_PUK2:
      if (mIccStatusChangedRegistrants != null) {
        if (RILJ_LOGD) {
          riljLog("ON enter sim puk fakeSimStatusChanged: reg count="
          + mIccStatusChangedRegistrants.size());
        }
        mIccStatusChangedRegistrants.notifyRegistrants();
      }
      break;
    }

    if (error != 0) {
      switch (rr.mRequest) {
        case RIL_REQUEST_ENTER_SIM_PIN:
        case RIL_REQUEST_ENTER_SIM_PIN2:
        case RIL_REQUEST_CHANGE_SIM_PIN:
        case RIL_REQUEST_CHANGE_SIM_PIN2:
        case RIL_REQUEST_SET_FACILITY_LOCK:
        if (mIccStatusChangedRegistrants != null) {
          if (RILJ_LOGD) {
            riljLog("ON some errors fakeSimStatusChanged: reg count="
            + mIccStatusChangedRegistrants.size());
          }
          mIccStatusChangedRegistrants.notifyRegistrants();
        }
        break;
      }

      rr.onError(error, ret);
    } else {

      if (RILJ_LOGD) riljLog(rr.serialString() + "< " + requestToString(rr.mRequest)
      + " " + retToString(rr.mRequest, ret));

      if (rr.mResult != null) {
        AsyncResult.forMessage(rr.mResult, ret, null);
        rr.mResult.sendToTarget();
      }
    }
    return rr;
  }

  protected void
  processUnsolicited (Parcel p) {
    int response;
    Object ret;

    response = p.readInt();

    try {switch(response) {
      /*
      cat libs/telephony/ril_unsol_commands.h \
      | egrep "^ *{RIL_" \
      | sed -re 's/\{([^,]+),[^,]+,([^}]+).+/case \1: \2(rr, p); break;/'
      */

      case RIL_UNSOL_RESPONSE_RADIO_STATE_CHANGED: ret =  responseVoid(p); break;
      case RIL_UNSOL_RESPONSE_CALL_STATE_CHANGED: ret =  responseVoid(p); break;
      case RIL_UNSOL_RESPONSE_VOICE_NETWORK_STATE_CHANGED: ret =  responseVoid(p); break;
      case RIL_UNSOL_RESPONSE_NEW_SMS: ret =  responseString(p); break;
      case RIL_UNSOL_RESPONSE_NEW_SMS_STATUS_REPORT: ret =  responseString(p); break;
      case RIL_UNSOL_RESPONSE_NEW_SMS_ON_SIM: ret =  responseInts(p); break;
      case RIL_UNSOL_ON_USSD: ret =  responseStrings(p); break;
      case RIL_UNSOL_NITZ_TIME_RECEIVED: ret =  responseString(p); break;
      case RIL_UNSOL_SIGNAL_STRENGTH: ret = responseSignalStrength(p); break;
      case RIL_UNSOL_DATA_CALL_LIST_CHANGED: ret = responseDataCallList(p);break;
      case RIL_UNSOL_SUPP_SVC_NOTIFICATION: ret = responseSuppServiceNotification(p); break;
      case RIL_UNSOL_STK_SESSION_END: ret = responseVoid(p); break;
      case RIL_UNSOL_STK_PROACTIVE_COMMAND: ret = responseString(p); break;
      case RIL_UNSOL_STK_EVENT_NOTIFY: ret = responseString(p); break;
      case RIL_UNSOL_STK_CALL_SETUP: ret = responseInts(p); break;
      case RIL_UNSOL_SIM_SMS_STORAGE_FULL: ret =  responseVoid(p); break;
      case RIL_UNSOL_SIM_REFRESH: ret =  responseSimRefresh(p); break;
      case RIL_UNSOL_CALL_RING: ret =  responseCallRing(p); break;
      case RIL_UNSOL_RESTRICTED_STATE_CHANGED: ret = responseInts(p); break;
      case RIL_UNSOL_RESPONSE_SIM_STATUS_CHANGED:  ret =  responseVoid(p); break;
      case RIL_UNSOL_RESPONSE_CDMA_NEW_SMS:  ret =  responseCdmaSms(p); break;
      case RIL_UNSOL_RESPONSE_NEW_BROADCAST_SMS:  ret =  responseRaw(p); break;
      case RIL_UNSOL_CDMA_RUIM_SMS_STORAGE_FULL:  ret =  responseVoid(p); break;
      case RIL_UNSOL_ENTER_EMERGENCY_CALLBACK_MODE: ret = responseVoid(p); break;
      case RIL_UNSOL_CDMA_CALL_WAITING: ret = responseCdmaCallWaiting(p); break;
      case RIL_UNSOL_CDMA_OTA_PROVISION_STATUS: ret = responseInts(p); break;
      case RIL_UNSOL_CDMA_INFO_REC: ret = responseCdmaInformationRecord(p); break;
      case RIL_UNSOL_OEM_HOOK_RAW: ret = responseRaw(p); break;
      case RIL_UNSOL_RINGBACK_TONE: ret = responseInts(p); break;
      case RIL_UNSOL_RESEND_INCALL_MUTE: ret = responseVoid(p); break;
      case RIL_UNSOL_CDMA_SUBSCRIPTION_SOURCE_CHANGED: ret = responseInts(p); break;
      case RIL_UNSOl_CDMA_PRL_CHANGED: ret = responseInts(p); break;
      case RIL_UNSOL_EXIT_EMERGENCY_CALLBACK_MODE: ret = responseVoid(p); break;
      case RIL_UNSOL_RIL_CONNECTED: ret = responseInts(p); break;
      case RIL_UNSOL_VOICE_RADIO_TECH_CHANGED: ret =  responseInts(p); break;
      case RIL_UNSOL_CELL_INFO_LIST: ret = responseCellInfoList(p); break;
      case RIL_UNSOL_RESPONSE_IMS_NETWORK_STATE_CHANGED: ret =  responseVoid(p); break;
      case RIL_UNSOL_UICC_SUBSCRIPTION_STATUS_CHANGED: ret =  responseInts(p); break;
      case RIL_UNSOL_SRVCC_STATE_NOTIFY: ret = responseInts(p); break;
      case RIL_UNSOL_HARDWARE_CONFIG_CHANGED: ret = responseHardwareConfig(p); break;
      //case RIL_UNSOL_ON_SS: ret =  responseSsData(p); break;
      case RIL_UNSOL_STK_CC_ALPHA_NOTIFY: ret =  responseString(p); break;
      case RIL_UNSOL_STK_SEND_SMS_RESULT: ret = responseInts(p); break; // Samsung STK
      case RIL_UNSOL_SET_PHONE_RAT_FAMILY_COMPLETE: ret = responseInts(p); break;

      default:
      throw new RuntimeException("Unrecognized unsol response: " + response);
      //break; (implied)
    }} catch (Throwable tr) {
      Rlog.e(RILJ_LOG_TAG, "Exception processing unsol response: " + response +
      "Exception:" + tr.toString());
      return;
    }

    switch(response) {
      case RIL_UNSOL_RESPONSE_RADIO_STATE_CHANGED:
      /* has bonus radio state int */
      RadioState newState = getRadioStateFromInt(p.readInt());
      if (RILJ_LOGD) unsljLogMore(response, newState.toString());

      switchToRadioState(newState);
      break;
      case RIL_UNSOL_RESPONSE_IMS_NETWORK_STATE_CHANGED:
      if (RILJ_LOGD) unsljLog(response);

      mImsNetworkStateChangedRegistrants
      .notifyRegistrants(new AsyncResult(null, null, null));
      break;
      case RIL_UNSOL_RESPONSE_CALL_STATE_CHANGED:
      if (RILJ_LOGD) unsljLog(response);

      mCallStateRegistrants
      .notifyRegistrants(new AsyncResult(null, null, null));
      break;
      case RIL_UNSOL_RESPONSE_VOICE_NETWORK_STATE_CHANGED:
      if (RILJ_LOGD) unsljLog(response);

      mVoiceNetworkStateRegistrants
      .notifyRegistrants(new AsyncResult(null, null, null));
      break;
      case RIL_UNSOL_RESPONSE_NEW_SMS: {
        if (RILJ_LOGD) unsljLog(response);

        // FIXME this should move up a layer
        String a[] = new String[2];

        a[1] = (String)ret;

        SmsMessage sms;

        sms = SmsMessage.newFromCMT(a);
        if (mGsmSmsRegistrant != null) {
          mGsmSmsRegistrant
          .notifyRegistrant(new AsyncResult(null, sms, null));
        }
        break;
      }
      case RIL_UNSOL_RESPONSE_NEW_SMS_STATUS_REPORT:
      if (RILJ_LOGD) unsljLogRet(response, ret);

      if (mSmsStatusRegistrant != null) {
        mSmsStatusRegistrant.notifyRegistrant(
        new AsyncResult(null, ret, null));
      }
      break;
      case RIL_UNSOL_RESPONSE_NEW_SMS_ON_SIM:
      if (RILJ_LOGD) unsljLogRet(response, ret);

      int[] smsIndex = (int[])ret;

      if(smsIndex.length == 1) {
        if (mSmsOnSimRegistrant != null) {
          mSmsOnSimRegistrant.
          notifyRegistrant(new AsyncResult(null, smsIndex, null));
        }
      } else {
        if (RILJ_LOGD) riljLog(" NEW_SMS_ON_SIM ERROR with wrong length "
        + smsIndex.length);
      }
      break;
      case RIL_UNSOL_ON_USSD:
      String[] resp = (String[])ret;

      if (resp.length < 2) {
        resp = new String[2];
        resp[0] = ((String[])ret)[0];
        resp[1] = null;
      }
      if (RILJ_LOGD) unsljLogMore(response, resp[0]);
      if (mUSSDRegistrant != null) {
        mUSSDRegistrant.notifyRegistrant(
        new AsyncResult (null, resp, null));
      }
      break;
      case RIL_UNSOL_NITZ_TIME_RECEIVED:
      if (RILJ_LOGD) unsljLogRet(response, ret);

      // has bonus long containing milliseconds since boot that the NITZ
      // time was received
      long nitzReceiveTime = p.readLong();

      Object[] result = new Object[2];

      result[0] = ret;
      result[1] = Long.valueOf(nitzReceiveTime);

      boolean ignoreNitz = SystemProperties.getBoolean(
      TelephonyProperties.PROPERTY_IGNORE_NITZ, false);

      if (ignoreNitz) {
        if (RILJ_LOGD) riljLog("ignoring UNSOL_NITZ_TIME_RECEIVED");
      } else {
        if (mNITZTimeRegistrant != null) {

          mNITZTimeRegistrant
          .notifyRegistrant(new AsyncResult (null, result, null));
        } else {
          // in case NITZ time registrant isnt registered yet
          mLastNITZTimeInfo = result;
        }
      }
      break;

      case RIL_UNSOL_SIGNAL_STRENGTH:
      // Note this is set to "verbose" because it happens
      // frequently
      if (RILJ_LOGV) unsljLogvRet(response, ret);

      if (mSignalStrengthRegistrant != null) {
        mSignalStrengthRegistrant.notifyRegistrant(
        new AsyncResult (null, ret, null));
      }
      break;
      case RIL_UNSOL_DATA_CALL_LIST_CHANGED:
      if (RILJ_LOGD) unsljLogRet(response, ret);

      boolean oldRil = needsOldRilFeature("skipbrokendatacall");
      if (oldRil && "IP".equals(((ArrayList<DataCallResponse>)ret).get(0).type))
      break;

      mDataNetworkStateRegistrants.notifyRegistrants(new AsyncResult(null, ret, null));
      break;

      case RIL_UNSOL_SUPP_SVC_NOTIFICATION:
      if (RILJ_LOGD) unsljLogRet(response, ret);

      if (mSsnRegistrant != null) {
        mSsnRegistrant.notifyRegistrant(
        new AsyncResult (null, ret, null));
      }
      break;

      case RIL_UNSOL_STK_SESSION_END:
      if (RILJ_LOGD) unsljLog(response);

      if (mCatSessionEndRegistrant != null) {
        mCatSessionEndRegistrant.notifyRegistrant(
        new AsyncResult (null, ret, null));
      }
      break;

      case RIL_UNSOL_STK_PROACTIVE_COMMAND:
      if (RILJ_LOGD) unsljLog(response);

      if (mCatProCmdRegistrant != null) {
        mCatProCmdRegistrant.notifyRegistrant(
        new AsyncResult (null, ret, null));
      }
      break;

      case RIL_UNSOL_STK_EVENT_NOTIFY:
      if (RILJ_LOGD) unsljLog(response);

      if (mCatEventRegistrant != null) {
        mCatEventRegistrant.notifyRegistrant(
        new AsyncResult (null, ret, null));
      }
      break;

      case RIL_UNSOL_STK_CALL_SETUP:
      if (RILJ_LOGD) unsljLogRet(response, ret);

      if (mCatCallSetUpRegistrant != null) {
        mCatCallSetUpRegistrant.notifyRegistrant(
        new AsyncResult (null, ret, null));
      }
      break;

      case RIL_UNSOL_SIM_SMS_STORAGE_FULL:
      if (RILJ_LOGD) unsljLog(response);

      if (mIccSmsFullRegistrant != null) {
        mIccSmsFullRegistrant.notifyRegistrant();
      }
      break;

      case RIL_UNSOL_SIM_REFRESH:
      if (RILJ_LOGD) unsljLogRet(response, ret);

      if (mIccRefreshRegistrants != null) {
        mIccRefreshRegistrants.notifyRegistrants(
        new AsyncResult (null, ret, null));
      }
      break;

      case RIL_UNSOL_CALL_RING:
      if (RILJ_LOGD) unsljLogRet(response, ret);

      if (mRingRegistrant != null) {
        mRingRegistrant.notifyRegistrant(
        new AsyncResult (null, ret, null));
      }
      break;

      case RIL_UNSOL_RESTRICTED_STATE_CHANGED:
      if (RILJ_LOGD) unsljLogvRet(response, ret);
      if (mRestrictedStateRegistrant != null) {
        mRestrictedStateRegistrant.notifyRegistrant(
        new AsyncResult (null, ret, null));
      }
      break;

      case RIL_UNSOL_RESPONSE_SIM_STATUS_CHANGED:
      if (RILJ_LOGD) unsljLog(response);

      if (mIccStatusChangedRegistrants != null) {
        mIccStatusChangedRegistrants.notifyRegistrants();
      }
      break;

      case RIL_UNSOL_RESPONSE_CDMA_NEW_SMS:
      if (RILJ_LOGD) unsljLog(response);

      SmsMessage sms = (SmsMessage) ret;

      if (mCdmaSmsRegistrant != null) {
        mCdmaSmsRegistrant
        .notifyRegistrant(new AsyncResult(null, sms, null));
      }
      break;

      case RIL_UNSOL_RESPONSE_NEW_BROADCAST_SMS:
      if (RILJ_LOGD) unsljLog(response);

      if (mGsmBroadcastSmsRegistrant != null) {
        mGsmBroadcastSmsRegistrant
        .notifyRegistrant(new AsyncResult(null, ret, null));
      }
      break;

      case RIL_UNSOL_CDMA_RUIM_SMS_STORAGE_FULL:
      if (RILJ_LOGD) unsljLog(response);

      if (mIccSmsFullRegistrant != null) {
        mIccSmsFullRegistrant.notifyRegistrant();
      }
      break;

      case RIL_UNSOL_ENTER_EMERGENCY_CALLBACK_MODE:
      if (RILJ_LOGD) unsljLog(response);

      if (mEmergencyCallbackModeRegistrant != null) {
        mEmergencyCallbackModeRegistrant.notifyRegistrant();
      }
      break;

      case RIL_UNSOL_CDMA_CALL_WAITING:
      if (RILJ_LOGD) unsljLogRet(response, ret);

      if (mCallWaitingInfoRegistrants != null) {
        mCallWaitingInfoRegistrants.notifyRegistrants(
        new AsyncResult (null, ret, null));
      }
      break;

      case RIL_UNSOL_CDMA_OTA_PROVISION_STATUS:
      if (RILJ_LOGD) unsljLogRet(response, ret);

      if (mOtaProvisionRegistrants != null) {
        mOtaProvisionRegistrants.notifyRegistrants(
        new AsyncResult (null, ret, null));
      }
      break;

      case RIL_UNSOL_CDMA_INFO_REC:
      ArrayList<CdmaInformationRecords> listInfoRecs;

      try {
        listInfoRecs = (ArrayList<CdmaInformationRecords>)ret;
      } catch (ClassCastException e) {
        Rlog.e(RILJ_LOG_TAG, "Unexpected exception casting to listInfoRecs", e);
        break;
      }

      for (CdmaInformationRecords rec : listInfoRecs) {
        if (RILJ_LOGD) unsljLogRet(response, rec);
        notifyRegistrantsCdmaInfoRec(rec);
      }
      break;

      case RIL_UNSOL_OEM_HOOK_RAW:
      if (RILJ_LOGD) unsljLogvRet(response, IccUtils.bytesToHexString((byte[]) ret));
      ByteBuffer oemHookResponse = ByteBuffer.wrap((byte[]) ret);
      oemHookResponse.order(ByteOrder.nativeOrder());
      if (isQcUnsolOemHookResp(oemHookResponse)) {
        Rlog.d(RILJ_LOG_TAG, "OEM ID check Passed");
        processUnsolOemhookResponse(oemHookResponse);
      } else if (mUnsolOemHookRawRegistrant != null) {
        Rlog.d(RILJ_LOG_TAG, "External OEM message, to be notified");
        mUnsolOemHookRawRegistrant.notifyRegistrant(new AsyncResult(null, ret, null));
      }
      break;

      case RIL_UNSOL_RINGBACK_TONE:
      if (RILJ_LOGD) unsljLogvRet(response, ret);
      if (mRingbackToneRegistrants != null) {
        boolean playtone = (((int[])ret)[0] == 1);
        mRingbackToneRegistrants.notifyRegistrants(
        new AsyncResult (null, playtone, null));
      }
      break;

      case RIL_UNSOL_RESEND_INCALL_MUTE:
      if (RILJ_LOGD) unsljLogRet(response, ret);

      if (mResendIncallMuteRegistrants != null) {
        mResendIncallMuteRegistrants.notifyRegistrants(
        new AsyncResult (null, ret, null));
      }
      break;

      case RIL_UNSOL_VOICE_RADIO_TECH_CHANGED:
      if (RILJ_LOGD) unsljLogRet(response, ret);

      if (mVoiceRadioTechChangedRegistrants != null) {
        mVoiceRadioTechChangedRegistrants.notifyRegistrants(
        new AsyncResult(null, ret, null));
      }
      break;

      case RIL_UNSOL_CDMA_SUBSCRIPTION_SOURCE_CHANGED:
      if (RILJ_LOGD) unsljLogRet(response, ret);

      if (mCdmaSubscriptionChangedRegistrants != null) {
        mCdmaSubscriptionChangedRegistrants.notifyRegistrants(
        new AsyncResult (null, ret, null));
      }
      break;

      case RIL_UNSOl_CDMA_PRL_CHANGED:
      if (RILJ_LOGD) unsljLogRet(response, ret);

      if (mCdmaPrlChangedRegistrants != null) {
        mCdmaPrlChangedRegistrants.notifyRegistrants(
        new AsyncResult (null, ret, null));
      }
      break;

      case RIL_UNSOL_EXIT_EMERGENCY_CALLBACK_MODE:
      if (RILJ_LOGD) unsljLogRet(response, ret);

      if (mExitEmergencyCallbackModeRegistrants != null) {
        mExitEmergencyCallbackModeRegistrants.notifyRegistrants(
        new AsyncResult (null, null, null));
      }
      break;

      case RIL_UNSOL_RIL_CONNECTED: {
        if (RILJ_LOGD) unsljLogRet(response, ret);

        // Initial conditions
        setRadioPower(false, null);
        setPreferredNetworkType(mPreferredNetworkType, null);
        setCdmaSubscriptionSource(mCdmaSubscription, null);
        setCellInfoListRate(Integer.MAX_VALUE, null);
        notifyRegistrantsRilConnectionChanged(((int[])ret)[0]);
        break;
      }
      case RIL_UNSOL_CELL_INFO_LIST: {
        if (RILJ_LOGD) unsljLogRet(response, ret);

        if (mRilCellInfoListRegistrants != null) {
          mRilCellInfoListRegistrants.notifyRegistrants(
          new AsyncResult (null, ret, null));
        }
        break;
      }
      case RIL_UNSOL_UICC_SUBSCRIPTION_STATUS_CHANGED: {
        if (RILJ_LOGD) unsljLogRet(response, ret);

        if (mSubscriptionStatusRegistrants != null) {
          mSubscriptionStatusRegistrants.notifyRegistrants(
          new AsyncResult (null, ret, null));
        }
        break;
      }
      case RIL_UNSOL_SRVCC_STATE_NOTIFY: {
        if (RILJ_LOGD) unsljLogRet(response, ret);

        if (mSrvccStateRegistrants != null) {
          mSrvccStateRegistrants
          .notifyRegistrants(new AsyncResult(null, ret, null));
        }
        break;
      }
      case RIL_UNSOL_HARDWARE_CONFIG_CHANGED:
      if (RILJ_LOGD) unsljLogRet(response, ret);

      if (mHardwareConfigChangeRegistrants != null) {
        mHardwareConfigChangeRegistrants.notifyRegistrants(
        new AsyncResult (null, ret, null));
      }
      break;
      /*case RIL_UNSOL_ON_SS:
      if (RILJ_LOGD) unsljLogRet(response, ret);

      if (mSsRegistrant != null) {
        mSsRegistrant.notifyRegistrant(
        new AsyncResult (null, ret, null));
      }
      break;*/
      case RIL_UNSOL_STK_CC_ALPHA_NOTIFY:
      if (RILJ_LOGD) unsljLogRet(response, ret);

      if (mCatCcAlphaRegistrant != null) {
        mCatCcAlphaRegistrant.notifyRegistrant(
        new AsyncResult (null, ret, null));
      }
      break;
      // Samsung STK
      case RIL_UNSOL_STK_SEND_SMS_RESULT:
      if (Resources.getSystem().
      getBoolean(com.android.internal.R.bool.config_samsung_stk)) {
        if (RILJ_LOGD) unsljLogRet(response, ret);

        if (mCatSendSmsResultRegistrant != null) {
          mCatSendSmsResultRegistrant.notifyRegistrant(
          new AsyncResult (null, ret, null));
        }
      }
      break;
      case RIL_UNSOL_SET_PHONE_RAT_FAMILY_COMPLETE:
      if (RILJ_LOGD) unsljLogRet(response, ret);
      break;
    }
  }

  private boolean isQcUnsolOemHookResp(ByteBuffer oemHookResponse) {

      /* Check OEM ID in UnsolOemHook response */
      if (oemHookResponse.capacity() < mHeaderSize) {
          /*
           * size of UnsolOemHook message is less than expected, considered as
           * External OEM's message
           */
          Rlog.d(RILJ_LOG_TAG,
                  "RIL_UNSOL_OEM_HOOK_RAW data size is " + oemHookResponse.capacity());
          return false;
      } else {
          byte[] oemIdBytes = new byte[OEM_IDENTIFIER.length()];
          oemHookResponse.get(oemIdBytes);
          String oemIdString = new String(oemIdBytes);
          Rlog.d(RILJ_LOG_TAG, "Oem ID in RIL_UNSOL_OEM_HOOK_RAW is " + oemIdString);
          if (!oemIdString.equals(OEM_IDENTIFIER)) {
              /* OEM ID not matched, considered as External OEM's message */
              return false;
          }
      }
      return true;
  }

  private void processUnsolOemhookResponse(ByteBuffer oemHookResponse) {
      int responseId = 0, responseSize = 0, responseVoiceId = 0;

      responseId = oemHookResponse.getInt();
      Rlog.d(RILJ_LOG_TAG, "Response ID in RIL_UNSOL_OEM_HOOK_RAW is " + responseId);

      responseSize = oemHookResponse.getInt();
      if (responseSize < 0) {
          Rlog.e(RILJ_LOG_TAG, "Response Size is Invalid " + responseSize);
          return;
      }

      byte[] responseData = new byte[responseSize];
      if (oemHookResponse.remaining() == responseSize) {
          oemHookResponse.get(responseData, 0, responseSize);
      } else {
          Rlog.e(RILJ_LOG_TAG, "Response Size(" + responseSize
                  + ") doesnot match remaining bytes(" +
                  oemHookResponse.remaining() + ") in the buffer. So, don't process further");
          return;
      }

      switch (responseId) {
          case OEMHOOK_UNSOL_WWAN_IWLAN_COEXIST:
              notifyWwanIwlanCoexist(responseData);
              break;

          case OEMHOOK_UNSOL_SIM_REFRESH:
              notifySimRefresh(responseData);
              break;

          case QCRIL_EVT_HOOK_UNSOL_MODEM_CAPABILITY:
              Rlog.d(RILJ_LOG_TAG, "QCRIL_EVT_HOOK_UNSOL_MODEM_CAPABILITY = mInstanceId"
                      + mInstanceId);
              notifyModemCap(responseData, mInstanceId);
              break;

          default:
              Rlog.d(RILJ_LOG_TAG, "Response ID " + responseId
                      + " is not served in this process.");
              break;
      }
  }

  private Object
  responseOperator(Parcel p) {
    int num;
    String response[] = null;

    response = p.readStringArray();

    if (false) {
      num = p.readInt();

      response = new String[num];
      for (int i = 0; i < num; i++) {
        response[i] = p.readString();
      }
    }

    /* ALPS00273663 handle UCS2 format name : prefix + hex string ex: "uCs2806F767C79D1" */
    if((response[0] != null) && (response[0].startsWith("uCs2") == true))
    {
      riljLog("responseOperator handling UCS2 format name");
      try{
        response[0] = new String(hexStringToBytes(response[0].substring(4)),"UTF-16");
      }catch(UnsupportedEncodingException ex){
        riljLog("responseOperatorInfos UnsupportedEncodingException");
      }
    }

    return response;
  }

  protected Object
  responseOperatorInfos(Parcel p) {
    String strings[] = (String [])responseStrings(p);
    ArrayList<OperatorInfo> ret;

    if (strings.length % 5 != 0) {
      throw new RuntimeException(
      "RIL_REQUEST_QUERY_AVAILABLE_NETWORKS: invalid response. Got "
      + strings.length + " strings, expected multible of 5");
    }

    // ALPS00353868 START
    String lacStr = SystemProperties.get("gsm.cops.lac");
    boolean lacValid = false;
    int lacIndex=0;

    Log.d(LOG_TAG, "lacStr = " + lacStr+" lacStr.length="+lacStr.length()+" strings.length="+strings.length);
    if((lacStr.length() > 0) && (lacStr.length()%4 == 0) && ((lacStr.length()/4) == (strings.length/5 ))){
      Log.d(LOG_TAG, "lacValid set to true");
      lacValid = true;
    }

    SystemProperties.set("gsm.cops.lac",""); //reset property
    // ALPS00353868 END

    ret = new ArrayList<OperatorInfo>(strings.length / 5);

    for (int i = 0 ; i < strings.length ; i += 5) {

      /* ALPS00273663 handle UCS2 format name : prefix + hex string ex: "uCs2806F767C79D1" */
      if((strings[i+0] != null) && (strings[i+0].startsWith("uCs2") == true))
      {
        riljLog("responseOperatorInfos handling UCS2 format name");

        try{
          strings[i+0] = new String(hexStringToBytes(strings[i+0].substring(4)), "UTF-16");
        }catch(UnsupportedEncodingException ex){
          riljLog("responseOperatorInfos UnsupportedEncodingException");
        }
      }

      //1 and 2 is 2g. above 2 is 3g
      String property_name = "gsm.baseband.capability";

      int basebandCapability = SystemProperties.getInt(property_name, 3); /* ALPS00352231 */
      Log.d(LOG_TAG, "property_name="+property_name+",basebandCapability=" + basebandCapability);
      if( 3 < basebandCapability){
        strings[i+0] = strings[i+0].concat(" " + strings[i+4]);
        strings[i+1] = strings[i+1].concat(" " + strings[i+4]);
      }

      ret.add (
      new OperatorInfo(
      strings[i+0],
      strings[i+1],
      strings[i+2],
      strings[i+3]));
    }

    return ret;
  }


  @Override
  public void
  setRadioPower(boolean on, Message result) {
    if (!mIsModemInitialized) {
      RILRequest rrPnt = RILRequest.obtain(
      RIL_REQUEST_SET_PREFERRED_NETWORK_TYPE, null);
      int ratOnlyStart = 100;

      rrPnt.mParcel.writeInt(1);
      rrPnt.mParcel.writeInt(mPreferredNetworkType + ratOnlyStart);
      int finalNetworkType = mPreferredNetworkType + ratOnlyStart;
      riljLog("Modem not initialized, sending "+finalNetworkType);

      if (RILJ_LOGD) riljLog(rrPnt.serialString() + "> "
      + requestToString(rrPnt.mRequest) + " : " + mPreferredNetworkType);

      send(rrPnt);
      mIsModemInitialized = true;
    }

    RILRequest rr = RILRequest.obtain(RIL_REQUEST_RADIO_POWER, result);

    rr.mParcel.writeInt(1);
    rr.mParcel.writeInt(on ? 1 : 0);

    if (RILJ_LOGD) {
      riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
      + (on ? " on" : " off"));
    }

    send(rr);
  }


  public void
  setRadioMode(int mode, Message result) {
    RILRequest rr = RILRequest.obtain(RIL_REQUEST_DUAL_SIM_MODE_SWITCH,
    result);

    if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

    rr.mParcel.writeInt(1);
    rr.mParcel.writeInt(mode);

    send(rr);
  }

  public void setPreferredNetworkType(int networkType , Message response) {
    int ratOnlyStart = 100;
    RILRequest rr = RILRequest.obtain(
    RILConstants.RIL_REQUEST_SET_PREFERRED_NETWORK_TYPE, response);

    rr.mParcel.writeInt(1);
    rr.mParcel.writeInt(networkType + ratOnlyStart);

    mPreferredNetworkType = networkType;

    if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest)
    + " : " + networkType + " network preferred = " + networkType);

    send(rr);
  }

public void setPhoneRatFamily(final int i, final Message message) {
    final RILRequest obtain = RILRequest.obtain(131, message);
    obtain.mParcel.writeInt(1);
    obtain.mParcel.writeInt(i);
    this.riljLog(obtain.serialString() + "> " + requestToString(obtain.mRequest) + ": " + i);
    this.send(obtain);
}

public void setModemPower(final boolean b, final Message message) {
    this.riljLog("Set Modem power as: " + b);
    RILRequest rilRequest;
    if (b) {
        rilRequest = RILRequest.obtain(2028, message);
    }
    else {
        rilRequest = RILRequest.obtain(2010, message);
    }
    this.riljLog(rilRequest.serialString() + "> " + requestToString(rilRequest.mRequest));
    this.send(rilRequest);
}


  //public void setTTYMode(int ttyMode, Message response) {
  //	riljLog("Not changin TTY mode");
  //	return;
  //}
  //
  //	public void setDataAllowed(boolean allowed, Message result) {
  //		if (result != null) {
  //			CommandException ex = new CommandException(
  //					CommandException.Error.REQUEST_NOT_SUPPORTED);
  //			AsyncResult.forMessage(result, null, ex);
  //			result.sendToTarget();
  //		}
  //		return;
  //	}

  @Override
  public void getModemCapability(Message response) {
    Rlog.d(RILJ_LOG_TAG, "GetModemCapability");
    if (response != null) {
      CommandException ex = new CommandException(
      CommandException.Error.REQUEST_NOT_SUPPORTED);
      AsyncResult.forMessage(response, null, ex);
      response.sendToTarget();
    }
    return;
  }

  private ArrayList<ApnSetting> responseGetDataCallProfile(Parcel p) {
    int nProfiles = p.readInt();
    if (RILJ_LOGD) riljLog("# data call profiles:" + nProfiles);

    ArrayList<ApnSetting> response = new ArrayList<ApnSetting>(nProfiles);

    int profileId = 0;
    int priority = 0;
    for (int i = 0; i < nProfiles; i++) {
      profileId = p.readInt();
      priority = p.readInt();
      ApnProfileOmh profile = new ApnProfileOmh(profileId, priority);
      if (RILJ_LOGD) {
        riljLog("responseGetDataCallProfile()" +
        profile.getProfileId() + ":" + profile.getPriority());
      }
      response.add(profile);
    }

    return response;
  }

  private Object
  responseHardwareConfig(Parcel p) {
    int num;
    ArrayList<HardwareConfig> response;
    HardwareConfig hw;

    num = p.readInt();
    response = new ArrayList<HardwareConfig>(num);

    if (RILJ_LOGV) {
      riljLog("responseHardwareConfig: num=" + num);
    }
    for (int i = 0 ; i < num ; i++) {
      int type = p.readInt();
      switch(type) {
        case HardwareConfig.DEV_HARDWARE_TYPE_MODEM: {
          hw = new HardwareConfig(type);
          hw.assignModem(p.readString(), p.readInt(), p.readInt(),
          p.readInt(), p.readInt(), p.readInt(), p.readInt());
          break;
        }
        case HardwareConfig.DEV_HARDWARE_TYPE_SIM: {
          hw = new HardwareConfig(type);
          hw.assignSim(p.readString(), p.readInt(), p.readString());
          break;
        }
        default: {
          throw new RuntimeException(
          "RIL_REQUEST_GET_HARDWARE_CONFIG invalid hardward type:" + type);
        }
      }

      response.add(hw);
    }

    return response;
  }

  private Object
  responseICC_IOBase64(Parcel p) {
    int sw1, sw2;
    Message ret;

    sw1 = p.readInt();
    sw2 = p.readInt();

    String s = p.readString();

    if (RILJ_LOGV) riljLog("< iccIO: "
    + " 0x" + Integer.toHexString(sw1)
    + " 0x" + Integer.toHexString(sw2) + " "
    + s);


    return new IccIoResult(sw1, sw2, android.util.Base64.decode(s, android.util.Base64.DEFAULT));
  }

}
