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
import com.android.internal.os.BatteryStatsHelper;
import com.android.internal.os.BatteryStatsImpl;
import com.android.internal.os.PowerProfile;


/**
 *
 */
public class BatteryMonitor {
    private static final String TAG = "BatterMonitor";
    private static BatteryMonitor gInstance = null;
    private static final int REFRESH_RATE_MS = 5 * 1000; // refresh if the data is 5 seconds old

    private final Object mLock = new Object();

    private final Context mContext;
    private IBatteryStats mService;

    public static BatteryMonitor getInstance(Context context) {
        Slog.d(TAG, "Get the instance of BatteryMonitor");
        if (gInstance == null) {
            gInstance = new BatteryMonitor(context);
        }
        return gInstance;
    }

    private BatteryMonitor(Context context) {
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

    public long getCPUTime(int uid) {
        if (!getService()) {
            Slog.e(TAG, "Fail to get IBatteryStatsService");
            return -1;
        }
        try {
            return mService.getCPUTimeLOS(uid);
        } catch (RemoteException e) {
            Slog.e(TAG, "Fail to getCPUTime for " + uid);
            return -1;
        }
    }

}

