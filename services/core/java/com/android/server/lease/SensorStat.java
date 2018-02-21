/*
 *  @author Yigong HU <hyigong1@jhu.edu>
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

import com.android.server.lease.ResourceStat;

/**
 *
 */
public class SensorStat extends ResourceStat {

    @Override
    public void update(long startTime, long leaseTerm) {

    }

    public SensorStat(long BeginTime) {
        super(BeginTime);
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

}