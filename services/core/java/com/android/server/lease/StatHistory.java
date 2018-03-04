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

import android.content.Context;
import android.lease.BehaviorType;
import android.lease.LeaseStatus;
import android.os.SystemClock;
import android.util.Slog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;

/**
 * History of resource stats
 */
public class StatHistory {
    public static final String TAG = "StatHistory";
    protected LinkedList<ResourceStat> mStats;
    protected LinkedList<Event> mEventList;
    protected int mOpenIndex;
    //TODO: remove the old stats
    // Number of sessions (resource request OPEN, CLOSE pairs)
    public int frequencyCount;

    public StatHistory() {
        mStats = new LinkedList<>();
        mEventList = new LinkedList<>();
        mOpenIndex = -1;
        frequencyCount = 0;
    }

    public ResourceStat getCurrentStat() {
        if (mStats.size() == 0) {
            return null;
        }
        return mStats.get(mStats.size() - 1);
    }

    public void update(long startTime, long endTime, Context context, int uid) {
        long holdingTime = 0;
        int frequency = 0;
        ArrayList<Integer> staleEventsIndex = new ArrayList<>();
        int index = 0;
        for (Event e : mEventList) {
            if (e.acquireTime > startTime || e.releaseTime < endTime) {
                if (e.acquireTime == e.releaseTime) {
                    holdingTime += endTime - e.acquireTime;
                    Slog.d(TAG, "The lease has not been released yet. For process " + uid + ", the Holding time is " + holdingTime );
                    frequency++;
                } else if (e.acquireTime < startTime) {
                    staleEventsIndex.add(index);
                    if (index <= mOpenIndex) {
                        mOpenIndex--;
                    }
                    holdingTime += e.releaseTime - startTime;
                    Slog.d(TAG, "The lease has been acquired before this lease term. For process " + uid + ", the Holding time is " + holdingTime );
                } else {
                    staleEventsIndex.add(index);
                    if (index <= mOpenIndex) {
                        mOpenIndex--;
                    }
                    holdingTime += e.releaseTime - e.acquireTime;
                    Slog.d(TAG, "The lease has been released. For process " + uid + ", the Holding time is " + holdingTime );
                    frequency++;
                }
            }
            index++;
        }
        for (int i = staleEventsIndex.size() - 1; i >= 0; i--) {
            int idx = staleEventsIndex.get(i);
            mEventList.remove(idx);
        }
        ResourceStat resourceStat = getCurrentStat();
        resourceStat.update(holdingTime, frequency, context, uid);
    }

    public boolean hasActivateEvent () {
        return !mEventList.isEmpty();
    }

    public boolean addItem(ResourceStat resourceStat) {
        return mStats.add(resourceStat);
    }

    public void clear() {
        mStats.clear();
    }

    //TODO:need to implement the judge behavior part
    public BehaviorType judgeHistory() {
        return BehaviorType.FrequencyAsking;
    }

    public void noteAcquire() {
        Event e = new Event(SystemClock.elapsedRealtime());
        mEventList.add(e);
    }

    public void noteRelease() {
        if (mOpenIndex + 1 >= mEventList.size()) {
            return;
        }
        mOpenIndex++;
        Event e = mEventList.get(mOpenIndex);
        if (e.releaseTime > e.acquireTime) {
            return;
        }
        e.releaseTime = SystemClock.elapsedRealtime();
        frequencyCount ++;
    }

    public class Event {
        public long acquireTime;
        public long releaseTime;

        public Event(long acquireTime) {
            this.acquireTime = acquireTime;
            releaseTime = acquireTime;
        }
    }

}