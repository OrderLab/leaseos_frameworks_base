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
package com.android.server.lease;

import android.lease.BehaviorType;
import android.os.DeadSystemException;

/**
 * Decide important policy about lease such as whether to renew a lease.
 *
 */
public class LeasePolicyRuler {
    public enum Decision {
        EXPIRE,
        RENEW,
        DELAY,
        FREEZE
    }

    /**
     * Classify the app's lease usage behavior type based on the stat history.
     *
     * @param history
     * @return
     */
    public static BehaviorType classify(StatHistory history) {
        return BehaviorType.FrequencyAsking;
    }

    /**
     * Judge what to do with a lease when it expires.
     *
     * @param lease
     * @return
     */
    public static Decision behaviorJudge(Lease lease) {
        // TODO: judge based on the lease's current resource stat or entire stat history.
        BehaviorType behavior = classify(lease.getStatHistory());
        if (behavior == BehaviorType.Normal)
            return Decision.RENEW; // TODO: probably should just let it expire
        return Decision.DELAY;
    }

}