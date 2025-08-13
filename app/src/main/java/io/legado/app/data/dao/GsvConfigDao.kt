package io.legado.app.data.dao

import androidx.room.*
import io.legado.app.data.entities.GsvConfig
import kotlinx.coroutines.flow.Flow

@Dao
interface GsvConfigDao {

    @Query("SELECT * FROM gsv_config WHERE id = 1")
    fun observeConfig(): Flow<GsvConfig?>

    @Query("SELECT * FROM gsv_config WHERE id = 1")
    suspend fun getConfig(): GsvConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: GsvConfig)

    @Update
    suspend fun update(config: GsvConfig)

    @Query("UPDATE gsv_config SET url = :url WHERE id = 1")
    suspend fun updateUrl(url: String)

    @Query("DELETE FROM gsv_config")
    suspend fun deleteAll()
}