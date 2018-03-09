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

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Slog;

/**
 * Worker thread in lease manager service to perform certain types of work such as expiring leases
 *
 */
public class LeaseWorkerHandler extends Handler {
    private static final int MSG_EXPIRE_LEASE = 1;
    protected final String mName;

    public LeaseWorkerHandler(String name, Looper looper) {
        super(looper, null, true /*async*/);
        mName = name;
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_EXPIRE_LEASE:
                break;
            default:
                Slog.wtf(mName, "Unknown lease worker message");
        }
    }
}