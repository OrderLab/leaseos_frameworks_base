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
import android.lease.LeaseStatus;
import android.util.Slog;

import com.android.server.lease.db.LeaseStatsDBHelper;
import com.android.server.lease.db.LeaseStatsRecord;


/**
 *
 */
public class WakelockStat extends ResourceStat {
    public static final String TAG = "WakelockStat";

    protected long mHoldingTime;
    protected long mUsageTime;
    protected long mExceptionFrequency;
    protected int mUid;

    protected long mBaseCPUTime;
    protected long mCurCPUTime;

    protected Context mContext;

    protected LeaseStatus mStatus;

    @Override
    public LeaseStatus update(long holdingTime, int frequency, Context context, int uid) {
        if (BatteryMonitor.getInstance(context).isCharging()) {
            mStatus = LeaseStatus.CHARGING;
            return mStatus;
        }
        mHoldingTime = holdingTime;
        mFrequency = frequency;
        mCurCPUTime = BatteryMonitor.getInstance(context).getCPUTime(mUid);
        Slog.d(TAG,"The current time is " + mCurCPUTime + ", for uid " + mUid);
        mUsageTime = mCurCPUTime - mBaseCPUTime;
        Slog.d(TAG, "For process " + uid + ", the Holding time is " + mHoldingTime + ", the CPU usage time is " + mUsageTime);
        LeaseStatsRecord record = createRecord(uid);
        LeaseStatsDBHelper.getInstance(context).insert(record);
        return null;
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

    public WakelockStat(long beginTime, int uid, Context context) {
        super(beginTime);
        mContext = context;
        mFrequency = 0;
        mHoldingTime = 0;
        mUid = uid;
        if (BatteryMonitor.getInstance(context).isCharging()) {
            mStatus = LeaseStatus.CHARGING;
        } else {
            mStatus = null;
        }
        mBaseCPUTime = BatteryMonitor.getInstance(context).getCPUTime(mUid);
        Slog.d(TAG,"The base time is " + mBaseCPUTime + ", for uid " + mUid);
    }
    public void setEndTime(long endTime) {
        mEndTime = endTime;
    }

    public String getStatusStr() {
        return mStatus.toString();
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
    public BehaviorType judge() {
        return BehaviorType.FrequencyAsking;
    }
}