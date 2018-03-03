/*
 *  @author Ryan Huang <huang@cs.jhu.edu>
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
 * Descriptor of a lease containing the lease ID and an internal object for different leases.
 */
public class LeaseDescriptor<T> {
    public final T mLeaseKey;
    public final long mLeaseId;

    public LeaseStatus mLeaseStatus; // a cached value of the lease status

    public LeaseDescriptor(T key, long id, LeaseStatus status) {
        mLeaseKey = key;
        mLeaseId = id;
        mLeaseStatus = status;
    }
}