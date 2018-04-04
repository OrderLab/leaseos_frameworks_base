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

    @Override
    public void update(long holdingTime, int frequency, Context context, int uid) {
        final BatteryMonitor bm = BatteryMonitor.getInstance(context);
        if (bm.isCharging()) {
            // if the phone is charging skill updating resource stat
            Slog.d(TAG, "Phone is charging, skip updating WakelockStat");
            return;
        }
        mHoldingTime = holdingTime;
        mAcquiringFrequency = frequency;
        mCurCPUTime = bm.getCPUTime(mUid);
        Slog.d(TAG,"The current time is " + mCurCPUTime + ", for uid " + mUid);
        mUsageTime = mCurCPUTime - mBaseCPUTime;
        Slog.d(TAG, "For process " + uid + ", the Holding time is " + mHoldingTime + ", the CPU usage time is " + mUsageTime);
        mExceptionFrequency = bm.getExceptionNumber(mUid);
        judge();
        // TODO: uncomment inserting db to make it work
        LeaseStatsRecord record = createRecord(uid);
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

    public WakelockStat(long beginTime, int uid, Context context) {
        super(beginTime);
        BatteryMonitor.getInstance(context).getExceptionNumber(mUid);
        mContext = context;
        mFrequency = 0;
        mHoldingTime = 0;
        mUid = uid;
        mBaseCPUTime = BatteryMonitor.getInstance(context).getCPUTime(mUid);
        mBehaviorType = BehaviorType.Normal;
        Slog.d(TAG,"The base time is " + mBaseCPUTime + ", for uid " + mUid);
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
        if ((float)mUsageTime/mHoldingTime < 0.1 && mHoldingTime > 100) {
            Slog.d(TAG, "For process " + mUid + ", this lease term has a LongHolding behavior");
            mBehaviorType = BehaviorType.LongHolding;
        } else if ((float)mUsageTime /(mHoldingTime * mExceptionFrequency) < 0.1 && mHoldingTime > 100) {
            Slog.d(TAG, "For process " + mUid + ", this lease term has a Low Utility behavior");
            mBehaviorType = BehaviorType.LowUtility;
        } else if ((float) mUsageTime / (mHoldingTime * mExceptionFrequency * mAcquiringFrequency) < 0.1 && mHoldingTime > 100) {
            Slog.d(TAG, "For process " + mUid + ", this lease term has a High Damage behavior");
            mBehaviorType = BehaviorType.HighDamage;
        } else {
            Slog.d(TAG, "For process " + mUid + ", this lease term has a Normal behavior");
            mBehaviorType = BehaviorType.Normal;
        }
    }
}