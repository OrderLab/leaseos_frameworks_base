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

import com.android.server.lease.db.LeaseStatsRecord;

/**
 * GPS related stats
 */
public class LocationStat extends ResourceStat {
    public static final String TAG = "LocationStat";

    protected int mUid;

    protected long mHoldingTime;
    protected boolean isLeak;
    protected boolean isWeak;

    protected long mBaseCPUTime;
    protected long mCurCPUTime;
    protected long mAcquiringFrequency;
    protected long mUsageTime;
    protected Context mContext;
    protected LeaseWorkerHandler mHandler;



    public LocationStat(long beginTime, int uid, Context context, LeaseManagerService leaseManagerService, LeaseWorkerHandler handler) {
        super(beginTime);
        mContext = context;
        mUid = uid;
        mFrequency = 0;
        mHoldingTime = 0;
        mHandler = handler;
        mBaseCPUTime = BatteryMonitor.getInstance(mContext).getCPUTime(mUid);
        mLeaseManagerService = leaseManagerService;
        isLeak = false;
        isWeak = false;
        mIsMatch = true;
        Slog.d(TAG, "The base time is " + mBaseCPUTime + ", for uid " + mUid);
    }

    @Override
    public void update(long holdingTime, int frequency, Context context, double lastUtility) {
        final BatteryMonitor bm = BatteryMonitor.getInstance(context);
        if (bm.isCharging()) {
            // if the phone is charging skill updating resource stat
            Slog.d(TAG, "Phone is charging, skip updating WakelockStat");
            return;
        }
        mHoldingTime = holdingTime;
        mFrequency = frequency;
        mCurCPUTime = bm.getCPUTime(mUid);
        //Slog.d(TAG,"The current time is " + mCurCPUTime + ", for uid " + mUid);
        mUsageTime = mCurCPUTime - mBaseCPUTime;
        //Slog.d(TAG, "The number of exceptions are " + exceptions + " for process " + mOwnerId);
       /* Slog.d(TAG, "For process " + mUid + ", the Holding time is " + mHoldingTime
                + ", the CPU usage time is " + mUsageTime + ", the utility is " + mUtility);*/
        judge();
        // TODO: uncomment inserting db to make it work
       // LeaseStatsRecord record = createRecord(mUid);
       // LeaseStatsDBHelper.getInstance(context).insert(record);
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

    public void setLocationLeak() {
        Slog.d(TAG, "set the location leak");
        isLeak = true;
    }

    public void setLocationWeak() {
        Slog.d(TAG, "set the location Weak");
        isWeak = true;
    }


    @Override
    public long getConsumption() {
        return 0;
    }

    @Override
    public long getWork() {
        return 0;
    }

    @Override
    public long getFrequency() {
        return 0;
    }

    @Override
    public long getEfficientRatio() {
        return 0;
    }

    @Override
    public long getDamage() {
        return 0;
    }

    @Override
    public void judge() {

        if (isLeak) {
            Slog.d(TAG, "For process " + mUid + ", this lease term has a LongHolding behavior");
            mBehaviorType = BehaviorType.LongHolding;
            return;
        }

        if (isWeak) {
            Slog.d(TAG, "For process " + mUid + ", this lease term has a FrequencyAsking behavior");
            mBehaviorType = BehaviorType.FrequencyAsking;
            return;
        }

        if(!mIsMatch) {
            Slog.d(TAG, "For process " + mUid + ", this lease term has a Low Utility behavior");
            mBehaviorType = BehaviorType.LowUtility;
            return;
        }

        if(mFrequency > 2) {
            Slog.d(TAG, "For process " + mUid + ", this lease term has a High Damage behavior");
            mBehaviorType = BehaviorType.HighDamage;
            return;
        }

        mBehaviorType = BehaviorType.Normal;
    }
}