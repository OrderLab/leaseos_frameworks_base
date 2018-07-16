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
package com.android.server.am.db;


import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.HashMap;

/**
 *
 */
public class AppStatsDBHelper extends SQLiteOpenHelper{
    private static final String TAG = "AppStatsDBHelper";
    private static final String DBNAME = "appstats.db";
    private static final int DATABASE_VERSION = 1;
    private static final String SQL_CREATE_APP_STATS =
            "CREATE TABLE " + AppStatsRecordSchema.TABLE_NAME + " ("
                    + AppStatsRecordSchema._ID + " INTEGER PRIMARY KEY,"
                    + AppStatsRecordSchema.COLUMN_TIME + " INTEGER,"
                    + AppStatsRecordSchema.COLUMN_UID + " INTEGER,"
                    + AppStatsRecordSchema.COLUMN_PACKAGENAME + " TEXT,"
                    + AppStatsRecordSchema.COLUMN_TOTALPOWER + " TEXT,"
                    + AppStatsRecordSchema.COLUMN_USAGETIME + " LONG,"
                    + AppStatsRecordSchema.COLUMN_USAGEPOWER + " TEXT, "
                    + AppStatsRecordSchema.COLUMN_CPUTIME + " LONG,"
                    + AppStatsRecordSchema.COLUMN_GPSTIME + " LONG,"
                    + AppStatsRecordSchema.COLUMN_WIFIRUNNINGTIME + " LONG,"
                    + AppStatsRecordSchema.COLUMN_CPUFGTIME + " LONG,"
                    + AppStatsRecordSchema.COLUMN_WAKELOCKTIME + " LONG,"
                    + AppStatsRecordSchema.COLUMN_CAMERATIME + " LONG,"
                    + AppStatsRecordSchema.COLUMN_FLASHLIGHTTIME + " LONG,"
                    + AppStatsRecordSchema.COLUMN_BLUETOOTHRUNNINGTIME + " LONG,"
                    + AppStatsRecordSchema.COLUMN_WIFIPOWER + " TEXT,"
                    + AppStatsRecordSchema.COLUMN_CPUPOWER + " TEXT,"
                    + AppStatsRecordSchema.COLUMN_WAKELOCKPOWER + " TEXT,"
                    + AppStatsRecordSchema.COLUMN_MOBILERADIOPOWER + " TEXT,"
                    + AppStatsRecordSchema.COLUMN_GPSPOWER + " TEXT,"
                    + AppStatsRecordSchema.COLUMN_SENSORPOWER + " TEXT,"
                    + AppStatsRecordSchema.COLUMN_CAMERAPOWER + " TEXT,"
                    + AppStatsRecordSchema.COLUMN_FLASHLIGHTPOWER + " TEXT,"
                    + AppStatsRecordSchema.COLUMN_BLUETOOTHPOWER + " TEXT"
                    + ");";


    private static final HashMap<String, String> sAppStatsProjectionMap;

    static {
        sAppStatsProjectionMap = new HashMap<>();
        for (String str : AppStatsRecordSchema.DEFAULT_ENTRY_PROJECTION) {
            sAppStatsProjectionMap.put(str, str);
        }
    }

    private static AppStatsDBHelper sInstance;

    public Context mContext;

    private AppStatsDBHelper(Context context) {
        super(context, DBNAME, null, DATABASE_VERSION);
        mContext = context;
    }

    public static synchronized AppStatsDBHelper getInstance(Context context) {
        if (sInstance == null) {
            return new AppStatsDBHelper(context);
        }
        return sInstance;
    }

    public void insert(AppStatsRecord record) {
        ContentValues values = record.toContentValues();
        synchronized (this) {
            final SQLiteDatabase db = getWritableDatabase();
            if (db.insert(AppStatsRecordSchema.TABLE_NAME, null,
                    values) < 0) {
                Log.e(TAG, "Error inserting AppStatsRecord " + record);
            }
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_APP_STATS);
        Log.d(TAG, "AppStats table created");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + AppStatsRecordSchema.TABLE_NAME);
        onCreate(db);
        Log.d(TAG, "AppStats table upgraded");
    }


}
