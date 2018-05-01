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
import android.os.HandlerThread;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.LongSparseArray;
import android.util.Slog;

import com.android.internal.app.IBatteryStats;

import java.io.BufferedReader;
import java.io.FileReader;


/**
 *
 */
public class BatteryMonitor {
    private static final String TAG = "BatterMonitor";
    private static BatteryMonitor gInstance = null;
    private static final int REFRESH_BOUND_MS = 10 * 1000; // refresh if the data is 5 seconds old

    private final Object mLock = new Object();

    private final Context mContext;

    private long mLastRefreshTime = -1;

    private IBatteryStats mService;

    private LeaseWorkerHandler mHandler;
    private LongSparseArray<Long> mcpuTable = new LongSparseArray<>();
    private static final String sProcFile = "/proc/uid_cputime/show_uid_stat";

    public static BatteryMonitor getInstance(Context context) {
        //Slog.d(TAG, "Get the instance of BatteryMonitor");
        if (gInstance == null) {
            gInstance = new BatteryMonitor(context);
        }
        return gInstance;
    }

    private BatteryMonitor(Context context) {
        mLastRefreshTime = SystemClock.elapsedRealtime();
        mContext = context;
        String workerName = "battery-monitor";
        HandlerThread hthread = new HandlerThread(workerName);
        hthread.start();
        LeaseWorkerHandler handler = new LeaseWorkerHandler(workerName, hthread.getLooper(), mContext);
        mHandler = handler;
        mHandler.postDelayed(mGetCPURunnable, 2000);
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
     */
    public long getCPUTime(int uid) {
        if (!getService()) {
            Slog.e(TAG, "Fail to get IBatteryStatsService");
            return -1;
        }
        long now = SystemClock.elapsedRealtime();
        if (mLastRefreshTime < 0 || (now - mLastRefreshTime) > REFRESH_BOUND_MS) {
            mLastRefreshTime = now;
            return getCPUTimeLOS(uid);
        } else {
            return getCPUTimeLOS(uid);
        }

    }


    private long getCPUTimeLOS(int uid) {
        if (mcpuTable.get(uid) == null) {
            ReadCPUTime();
        }
        return mcpuTable.get(uid)/1000;
    }

    private void ReadCPUTime() {
        long userTimeUs = 0;
        long systemTimeUs = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(sProcFile))) {
            TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(' ');
            String line;
            while ((line = reader.readLine()) != null) {
                splitter.setString(line);
                String uidStr = splitter.next();
                int uid = Integer.parseInt(uidStr.substring(0, uidStr.length() - 1), 10);
                if (uid > android.os.Process.FIRST_APPLICATION_UID
                        && uid < android.os.Process.LAST_APPLICATION_UID) {
                    userTimeUs = Long.parseLong(splitter.next(), 10);
                    systemTimeUs = Long.parseLong(splitter.next(), 10);
                    mcpuTable.put(uid, userTimeUs + systemTimeUs);
                }
            }
        } catch (Exception e) {
        }
    }

    private Runnable mGetCPURunnable = new Runnable() {
        @Override
        public void run() {
            long userTimeUs = 0;
            long systemTimeUs = 0;
            try (BufferedReader reader = new BufferedReader(new FileReader(sProcFile))) {
                TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(' ');
                String line;
                while ((line = reader.readLine()) != null) {
                    splitter.setString(line);
                    String uidStr = splitter.next();
                    int uid = Integer.parseInt(uidStr.substring(0, uidStr.length() - 1), 10);
                    if (uid > android.os.Process.FIRST_APPLICATION_UID
                            && uid < android.os.Process.LAST_APPLICATION_UID) {
                        userTimeUs = Long.parseLong(splitter.next(), 10);
                        systemTimeUs = Long.parseLong(splitter.next(), 10);
                        mcpuTable.put(uid, userTimeUs + systemTimeUs);
                    }
                }
            } catch (Exception e) {
            }
            mHandler.postDelayed(mGetCPURunnable, 2000);
        }
    };

    public void getStat() {
        if (!getService()) {
            Slog.e(TAG, "Fail to get IBatteryStatsService");
            return;
        }
        try {
            mService.refreshStatic();
        } catch (RemoteException e) {
            Slog.e(TAG, "Fail to refreshStatic");
            return;
        }
    }
}




