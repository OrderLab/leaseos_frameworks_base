/*
 *  @author Yigong Hu <hyigong1@cs.jhu.edu>
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

import android.provider.BaseColumns;

/**
 *
 */
public class AppStatsRecordSchema implements BaseColumns {


    public static final String TABLE_NAME = "appstats";

    public static final int ID_PATH_POSITION = 1;

    public static final String DEFAULT_SORT_ORDER = "package DESC";

    public static final String COLUMN_TIME = "time";

    public static final String COLUMN_UID = "uid";

    public static final String COLUMN_TOTALPOWER = "totalPowerMah";


    public static final String COLUMN_USAGETIME = "usageTimeMs";

    public static final String COLUMN_USAGEPOWER = "usagePowerMah";

    public static final String COLUMN_CPUTIME = "cpuTimeMs";

    public static final String COLUMN_GPSTIME = "gpsTimeMs";

    public static final String COLUMN_WIFIRUNNINGTIME = "wifiRunningTimeMs";

    public static final String COLUMN_CPUFGTIME = "cpuFgTimeMs";

    public static final String COLUMN_WAKELOCKTIME = "wakelockTimeMs";

    public static final String COLUMN_CAMERATIME = "cameraTimeMs";

    public static final String COLUMN_FLASHLIGHTTIME = "flashlightTimeMs";

    public static final String COLUMN_BLUETOOTHRUNNINGTIME = "bluetoothRunningTimeMs";

    public static final String COLUMN_WIFIPOWER = "wifiPowerMah";

    public static final String COLUMN_CPUPOWER = "cpuPowerMah";

    public static final String COLUMN_WAKELOCKPOWER = "wakeLockPowerMah";

    public static final String COLUMN_MOBILERADIOPOWER = "mobileRadioPowerMah";

    public static final String COLUMN_GPSPOWER = "gpsPowerMah";

    public static final String COLUMN_SENSORPOWER = "sensorPowerMah";

    public static final String COLUMN_CAMERAPOWER = "cameraPowerMah";

    public static final String COLUMN_FLASHLIGHTPOWER = "flashlightPowerMah";

    public static final String COLUMN_BLUETOOTHPOWER = "bluetoothPowerMah";


    public static final String[] DEFAULT_ENTRY_PROJECTION = new String[]{
            AppStatsRecordSchema._ID,
            AppStatsRecordSchema.COLUMN_TIME,
            AppStatsRecordSchema.COLUMN_UID,
            AppStatsRecordSchema.COLUMN_TOTALPOWER,
            AppStatsRecordSchema.COLUMN_USAGETIME,
            AppStatsRecordSchema.COLUMN_USAGEPOWER,
            AppStatsRecordSchema.COLUMN_CPUTIME,
            AppStatsRecordSchema.COLUMN_GPSTIME,
            AppStatsRecordSchema.COLUMN_WIFIRUNNINGTIME,
            AppStatsRecordSchema.COLUMN_CPUFGTIME,
            AppStatsRecordSchema.COLUMN_WAKELOCKTIME,
            AppStatsRecordSchema.COLUMN_CAMERATIME,
            AppStatsRecordSchema.COLUMN_FLASHLIGHTTIME,
            AppStatsRecordSchema.COLUMN_BLUETOOTHRUNNINGTIME,
            AppStatsRecordSchema.COLUMN_WIFIPOWER,
            AppStatsRecordSchema.COLUMN_CPUPOWER,
            AppStatsRecordSchema.COLUMN_WAKELOCKPOWER,
            AppStatsRecordSchema.COLUMN_MOBILERADIOPOWER,
            AppStatsRecordSchema.COLUMN_GPSPOWER,
            AppStatsRecordSchema.COLUMN_SENSORPOWER,
            AppStatsRecordSchema.COLUMN_CAMERAPOWER,
            AppStatsRecordSchema.COLUMN_FLASHLIGHTPOWER,
            AppStatsRecordSchema.COLUMN_BLUETOOTHPOWER,
    };
}

