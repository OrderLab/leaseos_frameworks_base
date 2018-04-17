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
import android.os.SystemClock;
import android.util.Slog;

import java.util.ArrayList;
import java.util.Hashtable;
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
    protected static final int MAX_HISTORY_STATS = 10;
    // Number of sessions (resource request OPEN, CLOSE pairs)
    public int frequencyCount;
    public LinkedList<Long> mHoldTimes;

    public StatHistory() {
        mStats = new LinkedList<>();
        mEventList = new LinkedList<>();
        mHoldTimes = new LinkedList<>();
        mOpenIndex = -1;
        frequencyCount = 0;
    }

    public ResourceStat getCurrentStat() {
        if (mStats.size() == 0) {
            return null;
        }
        return mStats.get(mStats.size() - 1);
    }

    public ResourceStat getLastStat() {
        if (mStats.size() == 0 || mStats.size() == 1) {
            return null;
        }
        return mStats.get(mStats.size() - 2);
    }

    public void update(long startTime, long endTime, Context context, int uid, double utility) {
        long holdingTime = 0;
        int frequency = 0;
        ArrayList<Integer> staleEventsIndex = new ArrayList<>();
        int index = 0;
        int stale = 0;
        for (Event e : mEventList) {
            if (e.acquireTime < e.releaseTime && e.acquireTime < startTime
                    && e.releaseTime < startTime) {
                staleEventsIndex.add(index);
                Slog.d(TAG, "Stale wakelock");
                index++;
                stale++;
            } else if (e.acquireTime < e.releaseTime && e.acquireTime >= startTime
                    && e.releaseTime <= endTime) {
                staleEventsIndex.add(index);
                holdingTime += e.releaseTime - e.acquireTime;
                Slog.d(TAG, "The wakelock has been released. For process " + uid
                        + ", the Holding time is " + holdingTime);
                frequency++;
                index++;
                stale++;
            } else if (e.acquireTime < e.releaseTime && e.acquireTime < startTime
                    && e.releaseTime <= endTime) {
                staleEventsIndex.add(index);
                holdingTime += e.releaseTime - startTime;
                Slog.d(TAG,
                        "The wakelock has been released but is not acquired in this lease term. "
                                + "For process "
                                + uid
                                + ", the Holding time is " + holdingTime);
                frequency++;
                index++;
                stale++;
            } else if (e.acquireTime == e.releaseTime && e.acquireTime >= startTime) {
                holdingTime += endTime - e.acquireTime;
                Slog.d(TAG,
                        "The wakelock has not been released yet but is acquired in this lease "
                                + "term. For process "
                                + uid
                                + ", the Holding time is " + holdingTime);
                frequency++;
                index++;
            } else if (e.acquireTime == e.releaseTime && e.acquireTime <= startTime) {
                holdingTime += endTime - startTime;
                Slog.d(TAG, "The wakelock has not been released yet. For process " + uid
                        + ", the Holding time is " + holdingTime);
                frequency++;
                index++;
            } else {
                Slog.d(TAG, "UnKnow type");
            }
        }

        for (int i = staleEventsIndex.size() - 1; i >= 0; i--) {
            int idx = staleEventsIndex.get(i);
            mEventList.remove(idx);
        }
        mOpenIndex -= stale;
        ResourceStat resourceStat = getCurrentStat();
        ResourceStat lastResourceStat = getLastStat();
        double lastUtility;
        if (lastResourceStat == null) {
            lastUtility = 0;
        } else {
            lastUtility = lastResourceStat.mUtility;
        }
        resourceStat.update(holdingTime, frequency, context, uid, utility, lastUtility);
    }

    public boolean hasActivateEvent() {
        return !mEventList.isEmpty();
    }

    public boolean addItem(ResourceStat resourceStat) {
        if (mStats.size() >= MAX_HISTORY_STATS) {
            ResourceStat stat = mStats.getFirst();
            mStats.remove(stat);
        }
        return mStats.add(resourceStat);
    }

    public void clear() {
        mStats.clear();
    }

    //TODO:need to implement the judge behavior part
    public BehaviorType judgeHistory() {
        Hashtable<BehaviorType, Integer> behaviorFrequency = new Hashtable<>();
        //BehaviorType leaseBehaviorType = BehaviorType.Normal;
        int maxCount = 0;
        /*
        for (ResourceStat resourceStat : mStats) {
            if (behaviorFrequency.get(resourceStat.mBehaviorType) == null) {
                behaviorFrequency.put(resourceStat.mBehaviorType, 1);
            } else {
                int count = behaviorFrequency.get(resourceStat.mBehaviorType);
                count++;
                behaviorFrequency.put(resourceStat.mBehaviorType, count);
            }
        }
        for (BehaviorType behaviorType : BehaviorType.values()) {
            if (behaviorFrequency.get(behaviorType) == null) {
                continue;
            } else {
                int frequency = behaviorFrequency.get(behaviorType);
                if (frequency > maxCount) {
                    leaseBehaviorType = behaviorType;
                    maxCount = frequency;
                }
            }
        }*/
        return getCurrentStat().mBehaviorType;
    }

    public void noteAcquire() {
        Event e = new Event(SystemClock.elapsedRealtime());
        mEventList.add(e);
        Slog.d(TAG, "Add the acquire event at " + e.acquireTime);
    }

    public void noteRelease() {
        if (mOpenIndex + 1 >= mEventList.size()) {
            Slog.d(TAG, "There is no associated event for this release");
            return;
        }
        mOpenIndex++;
        Event e = mEventList.get(mOpenIndex);
        if (e.releaseTime > e.acquireTime) {
            return;
        }
        e.releaseTime = SystemClock.elapsedRealtime();
        if(mHoldTimes.size() < 10) {
            mHoldTimes.addLast(e.releaseTime-e.acquireTime);
        } else {
            mHoldTimes.removeFirst();
            mHoldTimes.addLast(e.releaseTime-e.acquireTime);
        }
        Slog.d(TAG, "Add the release event at " + e.releaseTime);
        frequencyCount++;
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