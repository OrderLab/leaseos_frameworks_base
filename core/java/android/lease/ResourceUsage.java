/*
 *  @author Yigong HU <hyigong1@jhu.edu>
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
 * Basic accounting class for generic resource usage that has
 * OPEN, CLOSE patterns.
 *
 */
public class ResourceUsage {
    //TODO keep session length history

    // Timestamp of when the resource usage entry was created. Used for garbage collecting
    // obsolete resource usage entry.
    public long birthTime;

    // Timestamp of last OPEN resource request. -1 indicating the resource is closed
    public long openTime;

    // Number of sessions (resource request OPEN, CLOSE pairs)
    public int frequencyCount;

    // time the resource is held.
    public long holdingTime;

    //time the resource is used
    public long usageTime;

    // aggregate time of close to open
    public long aggregateTime;

    // Number of times this resource was triggered (for alarm).
    public int triggerCount;

    /**
     * Constructor
     *
     * @param timeStamp the timestamp when this entry was created.
     */
    public ResourceUsage(long timeStamp) {
        reset(timeStamp);
    }

    /**
     * Indicating an OPEN resource request was made.
     * Currently, we only update the birthtime, session count.
     * But later on, we may want to keep the request usage history.
     *
     * @param timeStamp the timestamp when the resource OPEN request was made.
     */
    public void open(long timeStamp) {
        openTime = timeStamp;
        frequencyCount++;
    }

    /**
     * Indicating an CLOSE resource request was made.
     * Currently, we only update the birthtime, session count.
     * But later on, we may want to keep the request usage history.
     *
     * @param timeStamp the timestamp when the resource CLOSE request was made.
     */
    public void close(long timeStamp) {
        if (openTime > 0 && timeStamp > openTime) {
            aggregateTime += (timeStamp - openTime);
        }
        openTime = -1;
    }

    /**
     * Reset the stats
     * @param timeStamp
     */
    public void reset(long timeStamp) {
        birthTime = timeStamp;
        openTime = -1;  // not born yet
        aggregateTime = 0; // not used yet
        frequencyCount = 0;
        triggerCount = 0;
    }
}