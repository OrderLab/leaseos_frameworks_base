    
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

/**
 *
 */
abstract class ResourceStat {

    public int mStatNumber;

    // Timestamp of when the lease term  was created.
    protected long mBeginTime;

    // Timestamp of when the lease term was ended.
    protected long mEndTime;

    //The number of resource consumed in this lease term
    protected long mConsumption;

    //The number of work made in this lease term
    protected long mWork;

    //The asking frequency
    protected long mFrequency;

    //The ratio of useful work and useless work
    protected long mEfficientRatio;

    //The number of system damage caused by this lease term
    protected long mSystemdamage;


    public ResourceStat(long BeginTime, long Endtime) {
          mBeginTime = BeginTime;
          mEndTime = Endtime;

    }

    public long getBeginTime() {
        return mBeginTime;
    }

    public long getEndTime() {
        return mEndTime;
    }

    public long getLength() {
        return mEndTime - mBeginTime;
    }

    public abstract long getConsumption();

    public abstract long getWork();

    public abstract long getFrequency() ;

    public abstract long getEfficientRatio();

    public abstract long getDamage();

    public long getEfficiency() {
        return getWork() / getConsumption();
    }


    public long getUtility() {
        return getWork() / (getConsumption() * getEfficientRatio());
    }

}