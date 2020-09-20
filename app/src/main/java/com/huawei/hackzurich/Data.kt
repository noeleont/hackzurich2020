package com.huawei.hackzurich

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns


object TableInfo: BaseColumns{
    const val DB_NAME = "SmokingExpenses"
    const val IMAGE = "Image"
    const val TOTAL = "Total"
}
object Commands {
    const val SQL_CREATE_TABLE:String=
        "CREATE TABLE ${TableInfo.DB_NAME} (" +
                "${BaseColumns._ID} INTEGER PRIMARY KEY," +
                "${TableInfo.IMAGE} TEXT NOT NULL, " +
                "${TableInfo.TOTAL}  REAL)"

    const val SQL_DELETE_TABLE = " DROP TABLE IF EXISTS ${TableInfo.DB_NAME}"
}

class DataBaseHelper(var context: Context) : SQLiteOpenHelper(context, TableInfo.DB_NAME, null, 1){
    override fun onCreate(p0: SQLiteDatabase?) {
        p0?.execSQL(Commands.SQL_CREATE_TABLE)
    }
    override fun onUpgrade(p0: SQLiteDatabase?, p1: Int, p2: Int) {
        p0?.execSQL(Commands.SQL_DELETE_TABLE)
        onCreate(p0)
    }

}