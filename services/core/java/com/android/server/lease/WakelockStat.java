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


import android.lease.BehaviorType;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;


/**
 *
 */
public class WakelockStat extends ResourceStat {
    protected long mHoldingTime;
    protected long mUsageTime;
    protected long mExceptionFrequency;
    protected List<Event> mEventList;
    protected int mOpenIndex;
    protected int mUid;

    protected long mBaseCPUTime;
    protected long mCurCPUTime;

    @Override
    public void update(long startTime, long leaseTerm) {
        for (Event e : mEventList) {
            if (e.acquireTime > startTime || e.releaseTime < leaseTerm) {
                if(e.acquireTime == e.releaseTime) {
                    mHoldingTime += SystemClock.elapsedRealtime() - e.acquireTime;
                }else if (e.acquireTime < startTime){
                    mHoldingTime += e.releaseTime - startTime;
                }else {
                    mHoldingTime += e.acquireTime - e.releaseTime;
                }
                mFrequency++;
            }
        }
        mCurCPUTime = BatteryMonitor.getInstance().getCPUTime(mUid);
        mUsageTime = mCurCPUTime- mBaseCPUTime;
    }

    public WakelockStat(long beginTime, int uid) {
        super(beginTime);
        mEventList = new ArrayList<>();
        mOpenIndex = -1;
        mFrequency = 0;
        mHoldingTime = 0;
        mUid = uid;
        mBaseCPUTime = BatteryMonitor.getInstance().getCPUTime(mUid);
    }

    public void noteAcquire() {
        Event e = new Event(SystemClock.elapsedRealtime());
        mEventList.add(e);
    }

    public void noteRelease() {
        if (mOpenIndex+1 >= mEventList.size())
            return;
        mOpenIndex++;
        Event e = mEventList.get(mOpenIndex);
        if (e.releaseTime > e.acquireTime)
            return;
        e.releaseTime = SystemClock.elapsedRealtime();
    }

    public void setEndTime(long endTime) {
        mEndTime = endTime;
    }

    public class Event {
        public long acquireTime;
        public long releaseTime;

        public Event(long acquireTime) {
            this.acquireTime = acquireTime;
            releaseTime = acquireTime;
        }
    }

    @Override
    public long getConsumption(){
        return mHoldingTime;
    }

    @Override
    public long getWork(){
        return mUsageTime;
    }

    @Override
    public long getEfficientRatio(){
        return mExceptionFrequency;
    }

    @Override
    public long getFrequency(){
        return 0;
    }

    //TODO: implment the getDamage method
    @Override
    public long getDamage() {
         return 0;
    }

    //TODO: implment the judge method
    public BehaviorType judge (){
        return BehaviorType.FrequencyAsking;
    }
}