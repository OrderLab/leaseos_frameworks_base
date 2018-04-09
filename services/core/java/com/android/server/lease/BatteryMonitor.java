/*
 *  @author Yigong Hu <hyigong1@jhu.edu>
 *
 *  The LeaseOS Project
 *
 *  Copyright (c) 2018, Johns Hopkins University - Order Lab.
 *      All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.android.server.lease;


import android.content.Context;
import android.os.BatteryStats;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.util.Slog;

import com.android.internal.app.IBatteryStats;

import libcore.io.Libcore;


/**
 *
 */
public class BatteryMonitor {
    private static final String TAG = "BatterMonitor";
    private static BatteryMonitor gInstance = null;
    private static final int REFRESH_BOUND_MS = 5 * 1000; // refresh if the data is 5 seconds old

    private final Object mLock = new Object();

    private final Context mContext;

    private long mLastRefreshTime = -1;

    private IBatteryStats mService;


    public static BatteryMonitor getInstance(Context context) {
        Slog.d(TAG, "Get the instance of BatteryMonitor");
        if (gInstance == null) {
            gInstance = new BatteryMonitor(context);
        }
        return gInstance;
    }

    private BatteryMonitor(Context context) {
        mLastRefreshTime = SystemClock.elapsedRealtime();
        mContext = context;
    }


    private boolean getService() {
        if (mService == null) {
            Slog.d(TAG, "Getting IBatteryStatsService");
            mService = IBatteryStats.Stub.asInterface(
                    ServiceManager.getService(BatteryStats.SERVICE_NAME));
        }
        return mService != null;
    }

    /**
     * Tell if the battery is charging.
     *
     * @return
     */
    public boolean isCharging() {
        if (!getService()) {
            Slog.e(TAG, "Fail to get IBatteryStatsService");
            return false;
        }
        try {
            return mService.isCharging();
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to get isCharging");
            return false;
        }
    }

    /**
     * Get the CPU usage time for a UID. The usage time might be stale, but is bound by
     * REFRESH_BOUND_MS.
     *
     * @param uid
     * @return
     */
    public long getCPUTime(int uid) {
        if (!getService()) {
            Slog.e(TAG, "Fail to get IBatteryStatsService");
            return -1;
        }
        try {
            long now = SystemClock.elapsedRealtime();
            if (mLastRefreshTime < 0 || (now - mLastRefreshTime) > REFRESH_BOUND_MS) {
                mLastRefreshTime = now;
                return mService.getCPUTimeLOS(uid, true);
            } else {
                return mService.getCPUTimeLOS(uid, false);
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "Fail to getCPUTime for " + uid);
            return -1;
        }
    }


    /**
     * Get the Exception number for a UID.
     * @param uid
     * @return
     */
    public long getExceptionNumber(int uid) {
        if (!getService()) {
            Slog.e(TAG, "Fail to get IBatteryStatsService");
            return -1;
        }
        return Libcore.os.getAndClearException(uid);
    }

}




