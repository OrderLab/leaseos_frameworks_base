/*
 *  @author Yigong Hu <hyigong1@jhu.edu>
 *
 *  Copyright (c) 2017, The LeaseOS Project
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

import android.os.SystemClock;

import java.util.HashMap;
import java.util.Map;

/**
 * Handy class to deny requests for certain owners for a while (because they have been naughty)
 * and allow requests again after the temporary ban.
 */
public class RequestFreezer<T> {
    public static final long DEFAULT_FREEZE_DURATION = 60 * 1000;
            // freeze for one minute, may change later
    public static final int DEFAULT_FREEZE_COUNT = 200;

    protected Map<T, FreezeStats> mFreezeStats;
    protected long mFreezeDuration;
    protected int mFreezeCount;

    public RequestFreezer() {
        this(DEFAULT_FREEZE_DURATION, DEFAULT_FREEZE_COUNT);
    }

    public RequestFreezer(long freezeDuration, int freeCount) {
        mFreezeDuration = freezeDuration;
        mFreezeCount = freeCount;
        mFreezeStats = new HashMap<T, FreezeStats>();
    }

    /**
     * Add a naughty owner to the freezer. If the owner is already frozen,
     * do nothing.
     */
    public boolean addToFreezer(T owner) {
        FreezeStats stats = mFreezeStats.get(owner);
        if (stats != null) {
            return false;
        }
        long time;
        int cnt;
        if (mFreezeDuration > 0) {
            time = SystemClock.elapsedRealtime();
        } else {
            time = -1;
        }
        if (mFreezeCount > 0) {
            cnt = 0;
        } else {
            cnt = -1;
        }
        mFreezeStats.put(owner, new FreezeStats(time, cnt));
        return true;
    }

    /**
     * Remove a naughty owner from the freezer.
     */
    private void removeFromFreezer(T owner) {
        mFreezeStats.remove(owner);
    }

    /**
     * Test if the requests of an owner should be denied or not. If there is a freeze stats
     * for the owner and the freeze stats expired, heat the owner and return not deny,
     * If the freeze stats are still active, update the stats and return deny.
     */
    public boolean freeze(T owner) {
        FreezeStats stats = mFreezeStats.get(owner);
        // This owner is not put to the freezer!
        if (stats == null) {
            return false;
        }
        if (stats.expired()) {
            heat(owner);
            return false;
        }
        stats.increment();
        // This request should be denied
        return true;
    }

    /**
     * Defrost of a frozen owner...
     */
    public boolean heat(T owner) {
        removeFromFreezer(owner);
        // TODO additional actions
        return true;
    }

    /**
     * @hide
     */
    public class FreezeStats {
        public long freezeTime;
        public int freezeCount;

        public FreezeStats(long time, int count) {
            freezeTime = time;
            freezeCount = count;
        }

        /**
         * @hide
         */
        public boolean expired() {
            if (mFreezeCount > 0 && freezeCount >= mFreezeCount) {
                return true;
            }
            if (mFreezeDuration > 0
                    && (SystemClock.elapsedRealtime() - freezeTime) >= mFreezeDuration) {
                return true;
            }
            return false;
        }

        /**
         * @hide
         */
        public void increment() {
            if (mFreezeCount > 0) {
                mFreezeCount++;
            }
        }
    }

}