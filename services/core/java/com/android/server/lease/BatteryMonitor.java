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
import android.os.Handler;
import android.os.HandlerThread;
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
    private static final int REFRESH_RATE_MS = 5 * 1000; // refresh if the data is 5 seconds old

    private BatteryStatsImpl mStats;
    private final Object mLock = new Object();
    private final Context mContext;
    private long mLastRefreshTime;

    private Runnable mRefresher = new Runnable() {
        @Override
        public void run() {
            synchronized (mLock) {
                refreshLocked();
            }
        }
    };

    public static BatteryMonitor getInstance(Context context) {
        Slog.d (TAG, "Get the instance of BatteryMonitor");
        if (gInstance == null) {
            gInstance = new BatteryMonitor(context);
        }
        return gInstance;
    }

    private BatteryMonitor(Context context) {
        mStats = null;
        mLastRefreshTime = -1;
        mContext = context;
    }

    private void refreshLocked() {
        Slog.d(TAG, "Refreshing data of BatteryStatsImpl");
        mStats.addHistoryEventLocked(
                SystemClock.elapsedRealtime(),
                SystemClock.uptimeMillis(),
                BatteryStats.HistoryItem.EVENT_COLLECT_EXTERNAL_STATS,
                "get-stats", 0);
        mStats.updateCpuTimeLocked();
        mStats.updateKernelWakelocksLocked();
        mLastRefreshTime = SystemClock.elapsedRealtime();
    }

    private BatteryStatsImpl getStatsLocked() {
        if (mStats == null) {
            IBatteryStats batteryInfo = IBatteryStats.Stub.asInterface(
                    ServiceManager.getService(BatteryStats.SERVICE_NAME));
            if (batteryInfo == null) {
                Slog.d(TAG, "BatteryInfo is not ready yet");
                return null;
            }
            // The BatteryStatsHelper's way of getting statistics from the IBatteryStats is to
            // use getStatisticsStream() instead of getStatistics(), which writing the data to
            // a memory mapped file that can then be read instead of trying to pass the data
            // through rpc that may run into size limits. So we directly leverage this helper function.
            mStats = BatteryStatsHelper.getStats(batteryInfo);
            Slog.d(TAG, "Obtained instance of BatteryStatsImpl, set a power profile");
            mStats.setPowerProfile(new PowerProfile(mContext));
        }
        return mStats;
    }

    public long getCPUTime(int uid) {
        synchronized (mLock) {
            if (getStatsLocked() == null) {
                return -1;
            }
            long now = SystemClock.elapsedRealtime();
            if ((now - mLastRefreshTime) > REFRESH_RATE_MS) {
                refreshLocked();
            }
            long totalTime = 0;
            BatteryStatsImpl.Uid u = mStats.getUidStatsLocked(uid);
            SparseArray<? extends BatteryStats.Uid> uidStats = mStats.getUidStats();
            Slog.d(TAG,"The size of uidstats is " + uidStats.size());
            ArrayMap<String, ? extends BatteryStats.Uid.Proc> processStats = u.getProcessStats();
            int NP = processStats.size();
            Slog.d (TAG, "the processStat size is " + NP + ", for uid " + u.getUid());
            for (int ip=0; ip<NP; ip++) {
                BatteryStatsImpl.Uid.Proc ps = (BatteryStatsImpl.Uid.Proc) processStats.valueAt(ip);
                totalTime += ps.getUserTime(BatteryStatsImpl.STATS_CURRENT);
                totalTime += ps.getSystemTime(BatteryStatsImpl.STATS_CURRENT);
                Slog.d(TAG, "ProcessStat name = " + processStats.keyAt(ip) + ", CPU time = " + totalTime);
            }
            return totalTime;
        }
    }
}