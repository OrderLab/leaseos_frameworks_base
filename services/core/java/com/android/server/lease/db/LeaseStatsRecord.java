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
package com.android.server.lease.db;

import android.content.ContentValues;

/**
 * Record of stats related to lease
 */
public class LeaseStatsRecord {
    public int uid;

    public int wakelockCount;
    public long wakelockTime;

    public int processStarts;
    public long processUserTime;
    public long processSysTime;

    public long bytesSent;
    public long bytesReceived;

    public long alarmWakeups;
    public long alarmRunningTime;
    public long alarmTotalCount;

    public long gpsTime;
    public long sensorTime;


    public LeaseStatsRecord() {

    }

    public ContentValues toContentValues() {
        ContentValues values = new ContentValues();
        values.put(LeaseStatsRecordSchema.COLUMN_TIME, System.currentTimeMillis());
        values.put(LeaseStatsRecordSchema.COLUMN_APP, uid);
        values.put(LeaseStatsRecordSchema.COLUMN_WAKELOCKTIME, wakelockTime);
        values.put(LeaseStatsRecordSchema.COLUMN_WAKELOCKCOUNT, wakelockCount);
        values.put(LeaseStatsRecordSchema.COLUMN_PROCESSSTARTS, processStarts);
        values.put(LeaseStatsRecordSchema.COLUMN_PROCESSUSERTIME, processUserTime);
        values.put(LeaseStatsRecordSchema.COLUMN_PROCESSSYSTIME, processSysTime);
        values.put(LeaseStatsRecordSchema.COLUMN_BYTESRECEIVED, bytesReceived);
        values.put(LeaseStatsRecordSchema.COLUMN_BYTESSENT, bytesSent);
        values.put(LeaseStatsRecordSchema.COLUMN_ALARMWAKEUPS, alarmWakeups);
        values.put(LeaseStatsRecordSchema.COLUMN_ALARMTIME, alarmRunningTime);
        values.put(LeaseStatsRecordSchema.COLUMN_ALARMTOTALCOUNT, alarmTotalCount);
        values.put(LeaseStatsRecordSchema.COLUMN_GPSTIME, gpsTime);
        values.put(LeaseStatsRecordSchema.COLUMN_SENSORTIME, sensorTime);
        return values;
    }
}
