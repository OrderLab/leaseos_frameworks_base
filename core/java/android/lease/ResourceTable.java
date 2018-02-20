    
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
package android.lease;

import java.util.*;

/**
 *
 */
public class ResourceTable<T> {
    private Map<T, ResourceUsage> mStats;

    public ResourceTable() {
        mStats = new HashMap<T, ResourceUsage>();
    }

    /**
     * Notify the table a resource is allocated so the usage stats can be
     * updated accordingly.
     *
     * @param resource
     * @param timeStamp
     */
    public void updateAllocUsage(T resource, long timeStamp) {
        ResourceUsage usage = mStats.get(resource);
        if (usage == null) {
            usage = newUsage(resource, timeStamp);
        }
        usage.open(timeStamp);
    }



    /**
     * Notify the table a resource is released so the usage stats can be
     * updated accordingly.
     *
     * @param resource
     * @param timeStamp
     */
    public void updateReleaseUsage(T resource, long timeStamp) {
        ResourceUsage usage = mStats.get(resource);
        if (usage == null) {
            return;
        }
        usage.close(timeStamp);
    }

    /**
     * Get the usage stats for the given resource
     *
     * @param resource
     * @return The resource usage stats
     */
    public ResourceUsage getUsage(T resource) {
        return mStats.get(resource);
    }

    public ResourceUsage newUsage(T resource, long timeStamp) {
        ResourceUsage usage = new ResourceUsage(timeStamp);
        mStats.put(resource, usage);
        return usage;
    }

    /**
     * Remove the lock usage stats from table
     * @param resource
     */
    public void removeUsage(T resource) {
        mStats.remove(resource);
    }

    /**
     * Clear all resource usages
     */
    public int clearUsage() {
        int size = mStats.size();
        mStats.clear();
        return size;
    }
}