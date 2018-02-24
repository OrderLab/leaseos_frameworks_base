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

/**
 *
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
}
