    
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
 * The mStatus of lease
 */
public enum LeaseStatus {
    ACTIVE("active"), // the lease is in its lease term
    EXPIRED("expired"), // the lease is expired
    INVALID("invalid"); // the lease is expired and can not be renewed

    String status;

    private LeaseStatus(String status) {
        this.status = status;
    }

    /**
     * Return the mStatus of the lease
     * @return
     */
    public String toString() {
        return status;
    }

    /**
     * Set the mStatus of the lease
     * @param status
     * @return
     */
    public boolean setStatus(String status) {
        if (status == "active" || status == "expired" || status == "invalid")
            this.status = status;
        else
            return false;
        return true;
    }

}