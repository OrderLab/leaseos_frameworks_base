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

/**
 * The status of lease
 */
public enum LeaseStatus {
    INVALID("invalid"), //the lease is not activated
    ACTIVE("active"), // the lease is in its lease term
    EXPIRED("expired"), // the lease is expired
    ABORTED("aborted");// the lease is expired and can not be renewed

    final String status;

    LeaseStatus(String status) {
        this.status = status;
    }

    /**
     * Return the mStatus of the lease
     */
    public String toString() {
        return status;
    }
}