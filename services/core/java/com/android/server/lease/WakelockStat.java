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
import android.lease.BehaviorType;
import android.util.Slog;

import com.android.server.lease.db.LeaseStatsDBHelper;
import com.android.server.lease.db.LeaseStatsRecord;


/**
 * Wake lock stat
 */
public class WakelockStat extends ResourceStat {
    public static final String TAG = "WakelockStat";

    protected long mHoldingTime;
    protected long mUsageTime;
    protected long mExceptionFrequency;
    protected long mAcquiringFrequency;
    protected int mUid;

    protected long mBaseCPUTime;
    protected long mCurCPUTime;

    protected Context mContext;
    protected LeaseWorkerHandler mHandler;



    public WakelockStat(long beginTime, int uid, Context context, LeaseManagerService leaseManagerService, LeaseWorkerHandler handler) {
        super(beginTime);
        mContext = context;
        mFrequency = 0;
        mHoldingTime = 0;
        mUid = uid;
        mHandler = handler;
       // long baseTime = SystemClock.elapsedRealtimeNanos();
       // Slog.d(TAG, "Begin to get CPU time " + baseTime);
        mBaseCPUTime = BatteryMonitor.getInstance(mContext).getCPUTime(mUid);
       // long currtime = SystemClock.elapsedRealtimeNanos();
       // Slog.d(TAG, "The time to update lease is " + (currtime - baseTime)/1000);
        mLeaseManagerService = leaseManagerService;
        Slog.d(TAG, "The base time is " + mBaseCPUTime + ", for uid " + mUid);
    }

    @Override
    public void update(long holdingTime, int frequency, Context context, double lastUtility) {
        final BatteryMonitor bm = BatteryMonitor.getInstance(context);
        if (bm.isCharging()) {
            // if the phone is charging skill updating resource stat
          //  Slog.d(TAG, "Phone is charging, skip updating WakelockStat");
            return;
        }
        int exceptions = mLeaseManagerService.getAndCleanException(mUid);
        //Slog.d(TAG, "The number of exceptions are " + exceptions + " for process " + mOwnerId);
        int touchEvent = mLeaseManagerService.getAndCleanTouchEvent(mUid);
        //Slog.d(TAG, "The number of touch events are " + touchEvent + " for process " + mOwnerId);
        double utility = exceptions - touchEvent;
        mHoldingTime = holdingTime;
        mFrequency = frequency;
        mCurCPUTime = bm.getCPUTime(mUid);
        Slog.d(TAG,"The current time is " + mCurCPUTime + ", for uid " + mUid);
        mUsageTime = mCurCPUTime - mBaseCPUTime;
        if (lastUtility == 0 && utility == 0) {
            mUtility = 0;
        } else {
            mUtility = lastUtility + 0.1 - utility;
        }
        Slog.d(TAG, "For process " + mUid + ", the Holding time is " + mHoldingTime
                + ", the CPU usage time is " + mUsageTime + ", the utility is " + mUtility);
        judge();
        // TODO: uncomment inserting db to make it work
        LeaseStatsRecord record = createRecord(mUid);
        LeaseStatsDBHelper.getInstance(context).insert(record);
    }

    public LeaseStatsRecord createRecord(int uid) {
        LeaseStatsRecord record = new LeaseStatsRecord();
        record.wakelockCount = mFrequency;
        record.wakelockTime = mHoldingTime;
        record.processUserTime = mUsageTime;
        record.processSysTime = mUsageTime;
        record.uid = uid;
        return record;
    }

    public void setEndTime(long endTime) {
        mEndTime = endTime;
    }

    @Override
    public long getConsumption() {
        return mHoldingTime;
    }

    @Override
    public long getWork() {
        return mUsageTime;
    }

    @Override
    public long getEfficientRatio() {
        return mExceptionFrequency;
    }

    @Override
    public long getFrequency() {
        return 0;
    }

    //TODO: implment the getDamage method
    @Override
    public long getDamage() {
        return 0;
    }

    //TODO: implment the judge method
    @Override
    public void judge() {
        if(mUtility <= -2) {
            Slog.d(TAG, "For process " + mUid + ", this lease term has a Low Utility behavior");
            mBehaviorType = BehaviorType.LowUtility;
            return;
        }
        if ((float) mUsageTime / mHoldingTime < 0.1 && mHoldingTime > 100) {
            Slog.d(TAG, "For process " + mUid + ", this lease term has a LongHolding behavior");
            mBehaviorType = BehaviorType.LongHolding;
        } else if ((float) mUsageTime / (mHoldingTime * mFrequency)
                < 0.1 && mHoldingTime > 100) {
            Slog.d(TAG, "For process " + mUid + ", this lease term has a High Damage behavior");
            mBehaviorType = BehaviorType.HighDamage;
        } else {
            Slog.d(TAG, "For process " + mUid + ", this lease term has a Normal behavior");
            mBehaviorType = BehaviorType.Normal;
        }
    }
}