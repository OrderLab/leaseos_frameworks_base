/*
 *  @author Yigong Hu <hyigong1@cs.jhu.edu>
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
package com.android.server.lease.db;


import android.content.*;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.HashMap;

/**
 *
 */
public class LeaseStatsDBHelper extends SQLiteOpenHelper{
    private static final String TAG = "LeaseStatsDBHelper";
    private static final String DBNAME = "leasestats.db";
    private static final int DATABASE_VERSION = 1;
    private static final String SQL_CREATE_LEASE_STATS =
            "CREATE TABLE " + LeaseStatsRecordSchema.TABLE_NAME + " ("
                    + LeaseStatsRecordSchema._ID + " INTEGER PRIMARY KEY,"
                    + LeaseStatsRecordSchema.COLUMN_TIME + " INTEGER,"
                    + LeaseStatsRecordSchema.COLUMN_APP + " TEXT,"
                    + LeaseStatsRecordSchema.COLUMN_PACKAGE + " TEXT,"
                    + LeaseStatsRecordSchema.COLUMN_VERSION + " TEXT,"
                    + LeaseStatsRecordSchema.COLUMN_WAKELOCKTIME + " INTEGER,"
                    + LeaseStatsRecordSchema.COLUMN_WAKELOCKCOUNT + " INTEGER,"
                    + LeaseStatsRecordSchema.COLUMN_PROCESSSTARTS + " INTEGER,"
                    + LeaseStatsRecordSchema.COLUMN_PROCESSSYSTIME + " INTEGER,"
                    + LeaseStatsRecordSchema.COLUMN_PROCESSUSERTIME + " INTEGER,"
                    + LeaseStatsRecordSchema.COLUMN_BYTESRECEIVED + " INTEGER,"
                    + LeaseStatsRecordSchema.COLUMN_BYTESSENT + " INTEGER,"
                    + LeaseStatsRecordSchema.COLUMN_ALARMWAKEUPS + " INTEGER,"
                    + LeaseStatsRecordSchema.COLUMN_ALARMTIME + " INTEGER,"
                    + LeaseStatsRecordSchema.COLUMN_ALARMTOTALCOUNT + " INTEGER,"
                    + LeaseStatsRecordSchema.COLUMN_GPSTIME + " INTEGER,"
                    + LeaseStatsRecordSchema.COLUMN_SENSORTIME + " INTEGER"
                    + ");";

    private static final HashMap<String, String> sAppStatsProjectionMap;

    static {
        sAppStatsProjectionMap = new HashMap<>();
        for (String str : LeaseStatsRecordSchema.DEFAULT_ENTRY_PROJECTION) {
            sAppStatsProjectionMap.put(str, str);
        }
    }

    private static LeaseStatsDBHelper sInstance;

    public Context mContext;

    private LeaseStatsDBHelper(Context context) {
        super(context, DBNAME, null, DATABASE_VERSION);
        mContext = context;
    }

    public static synchronized LeaseStatsDBHelper getInstance(Context context) {
        if (sInstance == null) {
            return new LeaseStatsDBHelper(context);
        }
        return sInstance;
    }

    public void insert(LeaseStatsRecord record) {
        ContentValues values = record.toContentValues();
        synchronized (this) {
            final SQLiteDatabase db = getWritableDatabase();
            if (db.insert(LeaseStatsRecordSchema.TABLE_NAME, LeaseStatsRecordSchema.COLUMN_APP,
                    values) < 0) {
                Log.e(TAG, "Error inserting LeaseStatsRecord " + record);
            }
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_LEASE_STATS);
        Log.d(TAG, "LeaseStats table created");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + LeaseStatsRecordSchema.TABLE_NAME);
        onCreate(db);
        Log.d(TAG, "LeaseStats table upgraded");
    }
}
