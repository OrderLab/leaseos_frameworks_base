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
package android.lease;

import java.text.SimpleDateFormat;

/**
 * Utilities for time-related definitions and methods.
 */
public class TimeUtils {

    public static final int SECONDS_PER_MINUTE = 60;
    public static final int MINUTES_PER_HOUR = 60;
    public static final int HOURS_PER_DAY = 24;
    public static final int DAYS_PER_WEEK = 7;

    public static final int MILLIS_PER_SECOND = 1000;
    public static final int MILLIS_PER_MINUTE = MILLIS_PER_SECOND * SECONDS_PER_MINUTE;
    public static final int MILLIS_PER_HOUR = MILLIS_PER_MINUTE * MINUTES_PER_HOUR;
    public static final int MILLIS_PER_DAY = MILLIS_PER_HOUR * HOURS_PER_DAY;
    public static final int MILLIS_PER_WEEK = MILLIS_PER_DAY * DAYS_PER_WEEK;

    public static final int DURATION_PRECISIONS = 2;

    public static SimpleDateFormat MS_SDF =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

}