    
/*
 *  @author Yigong HU <hyigong1@jhu.edu>
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


import com.android.internal.app.IBatteryStats;
import com.android.internal.os.BatteryStatsImpl;

import android.os.BatteryStats;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.ArrayMap;
import android.util.Log;
import android.util.Slog;

import java.util.Map;

/**
 *
 */
public class BatteryMonitor {
    private static final String TAG = "BatterMonitor";
    private BatteryStatsImpl mInstance;

    public BatteryMonitor() {
        mInstance = null;
    }

    public BatteryStatsImpl.Uid getBatteryUid(int uid) {
        try{
            IBatteryStats om = IBatteryStats.Stub.asInterface(ServiceManager.getService(BatteryStats.SERVICE_NAME));
            byte[] data = om.getStatistics();
            Parcel parcel = Parcel.obtain();
            parcel.unmarshall(data, 0, data.length);
            parcel.setDataPosition(0);
            mInstance = BatteryStatsImpl.CREATOR.createFromParcel(parcel);
            if (mInstance == null) {
                Slog.e(TAG, "There is no BatteryStatImpl instance for the uid: " + uid);
                return null;
            }
            return mInstance.getUidStatsLocked(uid);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    public long getUserTime(int uid) {
        BatteryStatsImpl.Uid Uid;
        BatteryStatsImpl.Uid.Proc Proc;
        ArrayMap<String, ? extends BatteryStats.Uid.Proc> processStats = new ArrayMap<>();
        long userTime = 0;

        Uid = getBatteryUid(uid);
        processStats = Uid.getProcessStats();
        if (processStats.size() > 0) {
            for (Map.Entry<String, ? extends BatteryStats.Uid.Proc> ent : processStats.entrySet()) {
                Slog.d(TAG, "ProcessStat name = " + ent.getKey());
                Proc = (BatteryStatsImpl.Uid.Proc)ent.getValue();
                userTime += Proc.getUserTime(BatteryStatsImpl.STATS_CURRENT);
            }
        }
        return userTime;
    }

    public long getSystemTime(int uid) {
        BatteryStatsImpl.Uid Uid;
        BatteryStatsImpl.Uid.Proc Proc;
        ArrayMap<String, ? extends BatteryStats.Uid.Proc> processStats = new ArrayMap<>();
        long systemTime = 0;

        Uid = getBatteryUid(uid);
        processStats = Uid.getProcessStats();
        if (processStats.size() > 0) {
            for (Map.Entry<String, ? extends BatteryStats.Uid.Proc> ent : processStats.entrySet()) {
                Slog.d(TAG, "ProcessStat name = " + ent.getKey());
                Proc = (BatteryStatsImpl.Uid.Proc)ent.getValue();
                systemTime += Proc.getSystemTime(BatteryStatsImpl.STATS_CURRENT);
            }
        }
        return systemTime;
    }

    public long getCPUTime(int uid) {
        return getUserTime(uid) + getSystemTime(uid);
    }

}