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

import android.lease.ResourceType;
import android.lease.LeaseEvent;
import android.lease.ILeaseProxy;

/**
 * Lease manager interfaces
 *
 * {@hide}
 */
interface ILeaseManager {

    /* create a lease */
    long create(in ResourceType rtype, int uid);

    /* check whether the lease is active or not */
    boolean check(long leaseId);

    /*  expire the lease */
    boolean expire(long leaseId);

    /* renew the lease */
    boolean renew(long leaseId);

    /* remove the lease */
    boolean remove(long leaseId);

    /* report an event about lease with id leaseId */
    void noteEvent(long leaseId, in LeaseEvent event);

    /* register a lease proxy with the lease manager service */
    boolean registerProxy(int type, String name, ILeaseProxy proxy);

    /* unregister a lease proxy with the lease manager service */
    boolean unregisterProxy(ILeaseProxy proxy);

    void noteException(int uid);

    void noteTouchEvent(int uid);

    void noteLocationEvent(long leaseId,in LeaseEvent event, String activityName);

    void noteStopEvent(String activityName);

    void noteStartEvent(String activityName);
}
