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

import android.text.TextUtils;
import android.util.Slog;

import java.util.HashSet;
import java.util.Set;

/**
 * White list to exempt important apps from Lease mechanism
 */
public class LeaseWhiteList {
    public static final String WHITELIST_DELIM = ",";
    public static final String WHITELIST_DEFAULT = "android,com.android.phone,com.quicin.trepn";

    private static final String TAG = "LeaseWhiteList";
    private Set<String> mVIPs;
    private String mStrVIPs;

    public LeaseWhiteList() {
        mVIPs = new HashSet<>();
        mStrVIPs = "";
    }

    public LeaseWhiteList(String strVIPs) {
        mVIPs = new HashSet<>();
        mStrVIPs = "";
        reset(strVIPs);
    }

    public void reset(String strVIPs) {
        if (TextUtils.isEmpty(mStrVIPs)) {
            if (TextUtils.isEmpty(strVIPs)) {
                Slog.d(TAG, "Both VIPs are empty");
                return;
            }
            mStrVIPs = ""; // in case strvips are set to null
        }
        if (mStrVIPs.equals(strVIPs)) {
            Slog.d(TAG, "VIPs are the same");
            return;
        }
        Slog.d(TAG, "Defense white list changed from " + mStrVIPs + " to " + strVIPs);
        mStrVIPs = strVIPs;
        mVIPs.clear();
        String[] vips = mStrVIPs.split(WHITELIST_DELIM);
        for (String vip:vips) {
            mVIPs.add(vip.trim());
        }
    }

    public void add(String vip) {
        mVIPs.add(vip.trim());
    }

    public void remove(String vip) {
        mVIPs.remove(vip.trim());
    }

    public boolean isVIP(String owner) {
        return mVIPs.contains(owner);
    }
}