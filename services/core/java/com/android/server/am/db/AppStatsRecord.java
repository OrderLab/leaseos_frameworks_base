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
package com.android.server.am.db;

import android.content.ContentValues;

/**
 * Record of stats related to lease
 */
public class AppStatsRecord {
    public int uid;
    public String totalPowerMah;

    /**
     * Generic usage time in milliseconds.
     */
    public long usageTimeMs;

    /**
     * Generic power usage in mAh.
     */
    public String usagePowerMah;

    // Subsystem usage times.
    public long cpuTimeMs;
    public long gpsTimeMs;
    public long wifiRunningTimeMs;
    public long cpuFgTimeMs;
    public long wakeLockTimeMs;
    public long cameraTimeMs;
    public long flashlightTimeMs;
    public long bluetoothRunningTimeMs;

    // Measured in mAh (milli-ampere per hour).
    // These are included when summed.
    public String wifiPowerMah;
    public String cpuPowerMah;
    public String wakeLockPowerMah;
    public String mobileRadioPowerMah;
    public String gpsPowerMah;
    public String sensorPowerMah;
    public String cameraPowerMah;
    public String flashlightPowerMah;
    public String bluetoothPowerMah;

    public AppStatsRecord() {

    }

    public ContentValues toContentValues() {
        ContentValues values = new ContentValues();
        values.put(AppStatsRecordSchema.COLUMN_TIME, System.currentTimeMillis());
        values.put(AppStatsRecordSchema.COLUMN_UID, uid);
        values.put(AppStatsRecordSchema.COLUMN_TOTALPOWER, totalPowerMah);
        values.put(AppStatsRecordSchema.COLUMN_USAGETIME, usageTimeMs);
        values.put(AppStatsRecordSchema.COLUMN_USAGEPOWER, usagePowerMah);
        values.put(AppStatsRecordSchema.COLUMN_CPUTIME, cpuTimeMs);
        values.put(AppStatsRecordSchema.COLUMN_GPSTIME, gpsTimeMs);
        values.put(AppStatsRecordSchema.COLUMN_WIFIRUNNINGTIME, wifiRunningTimeMs);
        values.put(AppStatsRecordSchema.COLUMN_CPUFGTIME, cpuFgTimeMs);
        values.put(AppStatsRecordSchema.COLUMN_WAKELOCKTIME, wakeLockTimeMs);
        values.put(AppStatsRecordSchema.COLUMN_CAMERATIME, cameraTimeMs);
        values.put(AppStatsRecordSchema.COLUMN_FLASHLIGHTTIME, flashlightTimeMs);
        values.put(AppStatsRecordSchema.COLUMN_BLUETOOTHRUNNINGTIME, bluetoothRunningTimeMs);
        values.put(AppStatsRecordSchema.COLUMN_WIFIPOWER, wifiPowerMah);
        values.put(AppStatsRecordSchema.COLUMN_CPUPOWER, cpuPowerMah);
        values.put(AppStatsRecordSchema.COLUMN_WAKELOCKPOWER, wakeLockPowerMah);
        values.put(AppStatsRecordSchema.COLUMN_MOBILERADIOPOWER, mobileRadioPowerMah);
        values.put(AppStatsRecordSchema.COLUMN_GPSPOWER, gpsPowerMah);
        values.put(AppStatsRecordSchema.COLUMN_SENSORPOWER, sensorPowerMah);
        values.put(AppStatsRecordSchema.COLUMN_CAMERAPOWER, cameraPowerMah);
        values.put(AppStatsRecordSchema.COLUMN_FLASHLIGHTPOWER, flashlightPowerMah);
        values.put(AppStatsRecordSchema.COLUMN_BLUETOOTHPOWER, bluetoothPowerMah);
        return values;
    }
}
