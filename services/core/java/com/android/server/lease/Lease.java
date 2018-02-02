    
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
 * The struct of lease
 */
public class Lease {
    long mLeaseid;
    long mOwnerid;
    ResourceType mType;
    LeaseStatus mStatus;
    int mLength; // in millisecond

    public Lease(int lid, int Oid, String type) {
        mLeaseid = lid;
        mOwnerid = Oid;
        this.mType.setType(type);
        mStatus.setStatus("active");
        mLength = 5;
    }

    public boolean isvalid() {
        int valid = LeaseStatus.ACTIVE.compareTo(mStatus);
        if (valid == 0) {
            return true;
        }
        return false;
    }

    public boolean expire() {
        return mStatus.setStatus("expired");
    }

    public boolean renew() {
        boolean success = mStatus.setStatus("active");
        mLength = 5;
        return true;
    }
}