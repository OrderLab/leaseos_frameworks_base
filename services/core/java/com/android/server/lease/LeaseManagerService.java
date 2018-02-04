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

import java.util.ArrayList;

import com.android.server.lease.*;


/**
 * The central lease manager service
 */
public class LeaseManagerService {

    //Operation failed
    public static final int FAILED = -1;

    // Table of all leases acquired by services.
    private final ArrayList<Lease> mLeases = new ArrayList<Lease>();

    //The identifier of lease
    private long mleaseid = 0;

    /**
     * Create a new lease
     *
     * @param RType The resource type of the lease
     * @param uid   the identifier of caller
     * @return the lease id
     */
    public long newLease(String RType, int uid) {
        if (!validateTypeParameters(RType)) {
            return FAILED;
        }
        Lease lease = new Lease(mleaseid, uid, RType);
        mLeases.add(lease);
        lease.createLease();
        mleaseid++;
        return lease.mLeaseid;
    }

    /**
     * Verify the type parameter is vaild
     *
     * @param RType The resource type of the lease
     * @return true if the type is valid
     */
    public static boolean validateTypeParameters(String RType) {
        for (ResourceType type : ResourceType.values()) {
            if (type.toString() == RType) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find the lease index by the Leaseid
     *
     * @param Leaseid The identifier of lease
     * @return The lease index or -1 if can not find the lease
     */
    private int findLeaseIndex(int Leaseid) {
        final int count = mLeases.size();
        for (int i = 0; i < count; i++) {
            if (mLeases.get(i).mLeaseid == Leaseid) {
                return i;
            }
        }
        return FAILED;
    }

    /**
     * Check the validation of the lease
     *
     * @param Leaseid The identifier of lease
     * @return True if the lease is valid
     * @throws Exception can not find a lease by the leaseid
     */
    public boolean check(int Leaseid) throws Exception {
        int index = findLeaseIndex(Leaseid);
        if (index >= 0) {
            Lease lease = mLeases.get(index);
            return lease.isvalid();
        } else {
            throw new Exception("No lease");
        }
    }


    /**
     * Expire the lease
     *
     * @param Leaseid The identifier of lease
     * @return Ture if the lease expire
     * @throws Exception can not find a lease by the leaseid
     */
    public boolean expire(int Leaseid) throws Exception {
        int index = findLeaseIndex(Leaseid);
        if (index >= 0) {
            Lease lease = mLeases.get(index);
            return lease.expire();
        } else {
            throw new Exception("No lease");
        }
    }

    /**
     * Renew the lease
     *
     * @param Leaseid The identifier of lease
     * @return Ture if the lease is renewed
     * @throws Exception can not find a lease by the leaseid
     */
    public boolean renew(int Leaseid) throws Exception {
        int index = findLeaseIndex(Leaseid);
        if (index >= 0) {
            Lease lease = mLeases.get(index);
            return lease.expire();
        } else {
            throw new Exception("No lease");
        }
    }

    /**
     * Remove the lease
     *
     * @param Leaseid The identifier of lease
     * @return Ture if the lease is removed from lease table
     * @throws Exception can not find a lease by the leaseid
     */
    public boolean remove(int Leaseid) throws Exception {
        int index = findLeaseIndex(Leaseid);
        if (index >= 0) {
            Lease lease = mLeases.get(index);
            lease.expire();
            mLeases.remove(index);
        } else {
            throw new Exception("No lease");
        }
        return true;
    }
}