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


import android.content.ContentValues;
import android.content.Context;
import android.lease.BehaviorType;
import android.util.Slog;

import com.android.server.lease.db.LeaseStatsRecord;
import com.android.server.lease.db.LeaseStatsRecordSchema;
import com.android.server.lease.db.LeaseStatsStorage;

import java.sql.Timestamp;
import java.util.Date;


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

    public LeaseStatsStorage mLeaseStatsStorage;

    @Override
    public void update(long holdingTime, int frequency, Context context, int uid) {
        mHoldingTime = holdingTime;
        mFrequency = frequency;
        mCurCPUTime = BatteryMonitor.getInstance().getCPUTime(mUid);
        mUsageTime = mCurCPUTime - mBaseCPUTime;
        mLeaseStatsStorage = new LeaseStatsStorage(context);
        Slog.d(TAG, "For process " + uid + ", the Holding time is " + mHoldingTime + ", the CPU usage time is " + mUsageTime);
        LeaseStatsRecord record = createRecord(uid);
        writeRecords(context, record);
    }

    public LeaseStatsRecord createRecord(int uid) {
        LeaseStatsRecord record = new LeaseStatsRecord();
        record.wakelockCount = mFrequency;
        record.wakelockTime = mHoldingTime;
        record.processStarts = 0;
        record.processUserTime = mUsageTime;
        record.processSysTime = mUsageTime;
        record.alarmRunningTime = 0;
        record.alarmTotalCount = 0;
        record.alarmWakeups = 0;
        record.bytesReceived = 0;
        record.bytesSent = 0;
        record.gpsTime = 0;
        record.sensorTime = 0;
        record.uid = uid;
        return record;
    }

    public void writeRecords(Context context, LeaseStatsRecord record) {
        ContentValues values = new ContentValues();
        Date date = new Date();
        Timestamp timestamp = new Timestamp(date.getTime());
        values.put(LeaseStatsRecordSchema.COLUMN_TIME, String.valueOf(timestamp));
        values.put(LeaseStatsRecordSchema.COLUMN_APP, record.uid);
        values.put(LeaseStatsRecordSchema.COLUMN_WAKELOCKTIME, record.wakelockTime);
        values.put(LeaseStatsRecordSchema.COLUMN_WAKELOCKCOUNT, record.wakelockCount);
        values.put(LeaseStatsRecordSchema.COLUMN_PROCESSSTARTS, record.processStarts);
        values.put(LeaseStatsRecordSchema.COLUMN_PROCESSUSERTIME, record.processUserTime);
        values.put(LeaseStatsRecordSchema.COLUMN_PROCESSSYSTIME, record.processSysTime);
        values.put(LeaseStatsRecordSchema.COLUMN_BYTESRECEIVED, record.bytesReceived);
        values.put(LeaseStatsRecordSchema.COLUMN_BYTESSENT, record.bytesSent);
        values.put(LeaseStatsRecordSchema.COLUMN_ALARMWAKEUPS, record.alarmWakeups);
        values.put(LeaseStatsRecordSchema.COLUMN_ALARMTIME, record.alarmRunningTime);
        values.put(LeaseStatsRecordSchema.COLUMN_ALARMTOTALCOUNT, record.alarmTotalCount);
        values.put(LeaseStatsRecordSchema.COLUMN_GPSTIME, record.gpsTime);
        values.put(LeaseStatsRecordSchema.COLUMN_SENSORTIME, record.sensorTime);
        mLeaseStatsStorage.insert( values);
    }

    public WakelockStat(long beginTime, int uid) {
        super(beginTime);
        mFrequency = 0;
        mHoldingTime = 0;
        mUid = uid;
        mBaseCPUTime = BatteryMonitor.getInstance().getCPUTime(mUid);
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
    public BehaviorType judge() {
        return BehaviorType.FrequencyAsking;
    }
}