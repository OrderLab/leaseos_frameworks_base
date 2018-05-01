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

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Slog;

/**
 * Worker thread in lease manager service to perform certain types of work such as expiring leases
 *
 */
public class LeaseWorkerHandler extends Handler {
    public static final int MSG_GET_CPU = 1;
    public final static String ACTION_PREFIX = "com.android.server.lease";
    public final static String NOTE_GET_CPU = ACTION_PREFIX + ".NOTE_GET_CPU";
    protected final String mName;

    protected long mBaseCPUTime;

    private Context mContext;

    public LeaseWorkerHandler(String name, Looper looper, Context context) {
        super(looper, null, true /*async*/);
        mName = name;
        mContext = context;
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_GET_CPU:
                break;
            default:
                Slog.wtf(mName, "Unknown lease worker message");
        }
    }
}