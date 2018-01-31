/*
 *  @author Yiong Hu <hyigong1@jhu.edu>
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

import java.util.Hashtable;

/**
 * The central lease manager service
 */
public class LeaseManagerService {

    Hashtable mLeaseMap = new Hashtable();

    public int createlease() {
        Lease lease = new Lease(1);
        mLeaseMap.put(1, lease);
        return lease.leaseid;
    }

    /**
     *
     * @param lid
     * @return
     */
    public boolean check(int lid) {
        return findLease(lid).isvaild();
    }

    /**
     * Find a lease object given a UID
     * @param lid
     * @return
     */
    private Lease findLease(int lid) {
        return (Lease) mLeaseMap.get(lid);
    }

    public boolean expire(int lid) {
        return findLease(lid).expire();
    }

    public boolean renew(int lid) {
        return findLease(lid).renew();
    }

    public boolean remove(int lid) {
        findLease(lid).expire();
        mLeaseMap.remove(lid);
        return true;
    }

    public void setMap(Hashtable map) {
        this.mLeaseMap = map;
    }


}