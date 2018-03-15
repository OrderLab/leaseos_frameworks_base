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

import android.lease.LeaseSettings;

/**
 * Callback interface for lease manager service to notify the proxy to carry out certain action, such
 * as blocking.
 *
 * {@hide}
 */
oneway interface ILeaseProxy {
    /* perform the actual expiration for a specific type of lease */
    void onExpire(long leaseId);

    /* perform the actual renewal for a specific type of lease */
    void onRenew(long leaseId);

    /* Reject any new lease request through this proxy for a given UID */
    void onReject(int uid);

    /* Freeze a given UID from any new lease request through this proxy for a given period */
    void onFreeze(int uid, long freezeDuration, int freeCount);

    void startLease(in LeaseSettings settings);

    void stopLease();

    void settingsChanged(in LeaseSettings settings);
}
