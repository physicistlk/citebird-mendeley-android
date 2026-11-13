package com.refmanager.tablet.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Document::class, Annotation::class, Collection::class, DocumentCollection::class],
    version = 6,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun annotationDao(): AnnotationDao
    abstract fun collectionDao(): CollectionDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** v1 libraries get the annotation table without losing their documents. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `annotations` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`documentId` INTEGER NOT NULL, " +
                        "`page` INTEGER NOT NULL, " +
                        "`kind` TEXT NOT NULL, " +
                        "`color` INTEGER NOT NULL, " +
                        "`rectsData` TEXT NOT NULL, " +
                        "`note` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`documentId`) REFERENCES `documents`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_annotations_documentId` " +
                        "ON `annotations` (`documentId`)"
                )
            }
        }

        /** v2 → v3: trashed flag + collections (folders/labels). */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `documents` ADD COLUMN `trashed` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS `collections` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`type` TEXT NOT NULL, " +
                    "`color` INTEGER, " +
                    "`createdAt` INTEGER NOT NULL)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `document_collections` (" +
                        "`documentId` INTEGER NOT NULL, " +
                        "`collectionId` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`documentId`, `collectionId`))"
                )
            }
        }

        /** v3 → v4: pdf restricted flag + size. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `documents` ADD COLUMN `pdfRestricted` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `documents` ADD COLUMN `pdfSize` TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v4 → v5: docType + abstractText. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `documents` ADD COLUMN `docType` TEXT NOT NULL DEFAULT 'Journal Article'")
                db.execSQL("ALTER TABLE `documents` ADD COLUMN `abstractText` TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v5 → v6: authored (My Publications). */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `documents` ADD COLUMN `authored` INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "library.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                    .also { instance = it }
            }
    }
}
