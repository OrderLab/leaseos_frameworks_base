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
import android.util.ArrayMap;
import android.util.Slog;
import android.util.SparseArray;

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

    private BatteryStatsImpl mStats;
    private final Object mLock = new Object();

    protected Context mContext;

    public static BatteryMonitor getInstance(Context context) {
        Slog.d(TAG, "Get the instance of BatteryMonitor");
        if (gInstance == null) {
            gInstance = new BatteryMonitor(context);
        }
        return gInstance;
    }

    private BatteryMonitor(Context context) {
        mContext = context;
        mStats = null;
    }

    public BatteryStatsImpl getStats(boolean force) {
        if (mStats == null || force) {
            IBatteryStats batteryInfo = IBatteryStats.Stub.asInterface(
                    ServiceManager.getService(BatteryStats.SERVICE_NAME));

            // The BatteryStatsHelper's way of getting statistics from the IBatteryStats is to
            // use getStatisticsStream() instead of getStatistics(), which writing the data to
            // a memory mapped file that can then be read instead of trying to pass the data
            // through rpc that may run into size limits. So we directly leverage this helper function.
            Slog.i(TAG, "Starting get BatteryStats");
            mStats = BatteryStatsHelper.getStats(batteryInfo);
            mStats.setPowerProfile(new PowerProfile(mContext));

        }
        return mStats;
    }

    public long getCPUTime(int uid) {
        long totalTime = 0;
        try{
            IBatteryStats batteryInfo = IBatteryStats.Stub.asInterface(
                    ServiceManager.getService(BatteryStats.SERVICE_NAME));
            totalTime = batteryInfo.getCPUTime(uid);
            return totalTime;
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to get CPU time: " + e);
        }
       return 0;

    }

}
/*
    public long getCPUTime(int uid, boolean force) {
        long totalTime = 0;

        synchronized (mLock) {
            BatteryStatsImpl stats = getStats(force);
            stats.TAG = "BatteryMonitor-BatteryStatImpl";
            stats.addHistoryEventLocked(
                    SystemClock.elapsedRealtime(),
                    SystemClock.uptimeMillis(),
                    BatteryStats.HistoryItem.EVENT_COLLECT_EXTERNAL_STATS,
                    "get-stats", 0);
            stats.m
            stats.updateCpuTimeLocked();
            stats.updateKernelWakelocksLocked();

        BatteryStatsImpl.Uid u  = stats.getUidStatsLocked(uid);
        ArrayMap<String, ? extends BatteryStats.Uid.Proc> processStats = u.getProcessStats();
        int NP = processStats.size();
        Slog.d (TAG, "the processStat size is " + NP + ", for uid " + u.getUid());
        if (NP == 0) {
            stats = getStats(true);
            u = stats.getUidStatsLocked(uid);
            processStats = u.getProcessStats();
            NP = processStats.size();
        }
        Slog.d (TAG, "the processStat size is " + NP + ", for uid " + u.getUid());
        for (int ip=0; ip<NP; ip++) {
            Slog.d(TAG, "ProcessStat name = " + processStats.keyAt(ip));
            BatteryStatsImpl.Uid.Proc ps = (BatteryStatsImpl.Uid.Proc) processStats.valueAt(ip);
            totalTime += ps.getUserTime(BatteryStatsImpl.STATS_ABSOLUTE);
            totalTime += ps.getSystemTime(BatteryStatsImpl.STATS_ABSOLUTE);
        }
        return totalTime;
    }
    }
}*/