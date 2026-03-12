package com.example.msp_app.`test-fixtures`

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.data.local.AppDatabase
import org.junit.After
import org.junit.Before

abstract class RoomTestBase : RobolectricTestBase() {

    protected lateinit var db: AppDatabase

    @Before
    fun setUpDatabase() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        AppDatabase.setInstanceForTesting(db)
    }

    @After
    fun tearDownDatabase() {
        AppDatabase.clearInstance()
    }
}
