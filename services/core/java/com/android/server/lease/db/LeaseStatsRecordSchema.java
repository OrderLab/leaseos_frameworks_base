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
package com.android.server.lease.db;

import android.provider.BaseColumns;

/**
 *
 */
public class LeaseStatsRecordSchema implements BaseColumns {


    public static final String TABLE_NAME = "leasestats";

    public static final int ID_PATH_POSITION = 1;

    public static final String DEFAULT_SORT_ORDER = "package DESC";

    public static final String COLUMN_TIME = "time";

    public static final String COLUMN_APP = "app";

    public static final String COLUMN_PACKAGE = "package";

    public static final String COLUMN_VERSION = "version";

    public static final String COLUMN_WAKELOCKTIME = "wakelockTime";

    public static final String COLUMN_WAKELOCKCOUNT = "wakelockCount";

    public static final String COLUMN_PROCESSSTARTS = "processStarts";

    public static final String COLUMN_PROCESSUSERTIME = "processUserTime";

    public static final String COLUMN_PROCESSSYSTIME = "processSysTime";

    public static final String COLUMN_BYTESRECEIVED = "bytesRecv";

    public static final String COLUMN_BYTESSENT = "bytesSent";

    public static final String COLUMN_ALARMWAKEUPS = "alarmWakeups";

    public static final String COLUMN_ALARMTIME = "alarmTime";

    public static final String COLUMN_ALARMTOTALCOUNT = "alarmTotalCount";

    public static final String COLUMN_GPSTIME = "gpsTime";

    public static final String COLUMN_SENSORTIME = "sensorTime";


    public static final String[] DEFAULT_ENTRY_PROJECTION = new String[]{
            LeaseStatsRecordSchema._ID,
            LeaseStatsRecordSchema.COLUMN_TIME,
            LeaseStatsRecordSchema.COLUMN_APP,
            LeaseStatsRecordSchema.COLUMN_PACKAGE,
            LeaseStatsRecordSchema.COLUMN_VERSION,
            LeaseStatsRecordSchema.COLUMN_WAKELOCKTIME,
            LeaseStatsRecordSchema.COLUMN_WAKELOCKCOUNT,
            LeaseStatsRecordSchema.COLUMN_PROCESSSTARTS,
            LeaseStatsRecordSchema.COLUMN_PROCESSUSERTIME,
            LeaseStatsRecordSchema.COLUMN_PROCESSSYSTIME,
            LeaseStatsRecordSchema.COLUMN_BYTESRECEIVED,
            LeaseStatsRecordSchema.COLUMN_BYTESSENT,
            LeaseStatsRecordSchema.COLUMN_ALARMWAKEUPS,
            LeaseStatsRecordSchema.COLUMN_ALARMTIME,
            LeaseStatsRecordSchema.COLUMN_ALARMTOTALCOUNT,
            LeaseStatsRecordSchema.COLUMN_GPSTIME,
            LeaseStatsRecordSchema.COLUMN_SENSORTIME,
    };
}

