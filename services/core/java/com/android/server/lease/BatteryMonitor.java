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


import android.os.BatteryStats;
import android.os.ServiceManager;
import android.util.ArrayMap;
import android.util.Slog;

import com.android.internal.app.IBatteryStats;
import com.android.internal.os.BatteryStatsHelper;
import com.android.internal.os.BatteryStatsImpl;

/**
 *
 */
public class BatteryMonitor {
    private static final String TAG = "BatterMonitor";
    private static BatteryMonitor gInstance = null;

    private BatteryStatsImpl mStats;

    public static BatteryMonitor getInstance() {
        Slog.d (TAG, "Get the instance of BatteryMonitor");
        if (gInstance == null) {
            gInstance = new BatteryMonitor();
        }
        return gInstance;
    }

    private BatteryMonitor() {
        mStats = null;
    }

    public BatteryStatsImpl getStats() {
        if (mStats == null) {
            IBatteryStats batteryInfo = IBatteryStats.Stub.asInterface(
                    ServiceManager.getService(BatteryStats.SERVICE_NAME));

            // The BatteryStatsHelper's way of getting statistics from the IBatteryStats is to
            // use getStatisticsStream() instead of getStatistics(), which writing the data to
            // a memory mapped file that can then be read instead of trying to pass the data
            // through rpc that may run into size limits. So we directly leverage this helper function.
            mStats = BatteryStatsHelper.getStats(batteryInfo);
        }
        return mStats;
    }

    public long getCPUTime(int uid) {
        long totalTime = 0;
        BatteryStatsImpl.Uid u = getStats().getUidStatsLocked(uid);
        ArrayMap<String, ? extends BatteryStats.Uid.Proc> processStats = u.getProcessStats();
        int NP = processStats.size();
        Slog.d (TAG, "the processStat size is " + NP);
        for (int ip=0; ip<NP; ip++) {
            Slog.d(TAG, "ProcessStat name = " + processStats.keyAt(ip));
            BatteryStatsImpl.Uid.Proc ps = (BatteryStatsImpl.Uid.Proc) processStats.valueAt(ip);
            totalTime += ps.getUserTime(BatteryStatsImpl.STATS_CURRENT);
            totalTime += ps.getSystemTime(BatteryStatsImpl.STATS_CURRENT);
        }
        return totalTime;
    }

}