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
import android.util.Slog;

import java.util.Hashtable;


/**
 *
 */
public class ResourceStatManager {
    private static final String TAG = "ResourceStatManager";

    protected Hashtable<Long, StatHistory> mStatsHistorys;


    public ResourceStatManager(){
        mStatsHistorys = new Hashtable<>();
    }

    public boolean update(long leaseId, long startTime, long endTime) {
        StatHistory statHistory = mStatsHistorys.get(leaseId);
        if (statHistory == null) {
            Slog.e(TAG, "No statHistory for the lease " + leaseId);
            return false;
        }
        ResourceStat resourceStat = statHistory.getCurrentStat();
        resourceStat.update(startTime, endTime);
        return true;
    }

    public ResourceStat getCurrentStat(long leaseId) {
        StatHistory statHistory = mStatsHistorys.get(leaseId);
        if (statHistory == null) {
            Slog.e(TAG, "No statHistory for the lease " + leaseId);
            return null;
        }
        return statHistory.getCurrentStat();
    }

    /**
     * Set a new Resource  statHistory
     * @param leaseId the related lease
     * @param statHistory the new stat
     * @return true if successfully add one lease
     */

    public void setStatsHistory(long leaseId, StatHistory statHistory) {
        mStatsHistorys.put(leaseId, statHistory);
    }

    /**
     * Set a new Resource stat into the statHistory
     * @param leaseId the related lease
     * @param rStat the new stat
     * @return true if successfully add one lease
     */
    public boolean addResourceStat(long leaseId, ResourceStat rStat) {
        //TODO: add a bound of history length
        StatHistory statHistory = mStatsHistorys.get(leaseId);
        if (statHistory == null) {
            return false;
        }
        return statHistory.addItem(rStat);
    }

    public boolean removeStatHistory(long leaseId){
        StatHistory statHistory = mStatsHistorys.get(leaseId);
        if (statHistory == null) {
            return false;
        }
        statHistory.remove();
        return mStatsHistorys.remove(leaseId, statHistory);
    }


    //TODO:This is the core part of lease manager
    public BehaviorType judge() {
        return BehaviorType.FrequencyAsking;
    }
}