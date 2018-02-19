    
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
package android.lease;

import com.android.compatibility.common.util.Stat;
import com.android.server.lease.BehaviorType;
import com.android.server.lease.Lease;

import java.util.Hashtable;


/**
 *
 */
public class ResourceStatManager {

    protected Hashtable<Lease, StatHistory> mStatsHistorys;


    public ResourceStatManager(){
        mStatsHistorys = new Hashtable<>();
    }

    /**
     * Set a new Resource  statHistory
     * @param lease the related lease
     * @param statHistory the new stat
     * @return true if successfully add one lease
     */

    public void setStatsHistory(Lease lease, StatHistory statHistory) {
        mStatsHistorys.put(lease, statHistory);
    }

    /**
     * Set a new Resource stat into the statHistory
     * @param lease the related lease
     * @param rStat the new stat
     * @return true if successfully add one lease
     */
    public boolean setResourceStat(Lease lease, ResourceStat rStat) {
        StatHistory statHistory = mStatsHistorys.get(lease);
        if (statHistory == null) {
            return false;
        }
        return statHistory.addItem(rStat);
    }

    public boolean removeStatHistory(Lease lease){
        StatHistory statHistory = mStatsHistorys.get(lease);
        if (statHistory == null) {
            return false;
        }
        statHistory.remove();
        return mStatsHistorys.remove(lease, statHistory);
    }


    //TODO:This is the core part of lease manager
    public BehaviorType judge() {
        return BehaviorType.FrequencyAsking;
    }


}