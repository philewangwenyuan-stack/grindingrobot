package com.sinelynx.grindingrobot.core.database
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS project (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "projectCode TEXT NOT NULL, " +
                "name TEXT NOT NULL, " +
                "coordData TEXT, " +
                "createTime TEXT, " +
                "updateTime TEXT, " +
                "drawings TEXT NOT NULL, " +
                "tasks TEXT NOT NULL, " +
                "points TEXT NOT NULL" +
            ")"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_project_projectCode ON project(projectCode)")
    }
}
