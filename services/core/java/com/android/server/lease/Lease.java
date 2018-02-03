    
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

import android.os.IBinder;

/**
 * The struct of lease
 */
public class Lease {
    protected long mLeaseid;
    protected long mOwnerid;
    protected IBinder mToken;
    protected ResourceType mType;
    protected LeaseStatus mStatus;
    protected int mLength; // in millisecond

    public Lease(long lid, long Oid, String type) {
        mLeaseid = lid;
        mOwnerid = Oid;
        mType.setType(type);
        mStatus.setStatus("active");
        mLength = 5;
    }

    public boolean isvalid() {
        for (LeaseStatus status : LeaseStatus.values()) {
            if (mStatus == status) {
                return true;
            }
        }
        return false;
    }

    public long getLength() {
        return mLength;
    }

    public long getLease() {
        return mLeaseid;
    }

    public long getOwner() {
        return mOwnerid;
    }

    public String getLeaseType() {
        return mType.toString();
    }

    public String getLeaseStatus() {
        return mStatus.toString();
    }


    public boolean expire() {
        return false;
    }

    public boolean renew() {
        return false;
    }

}