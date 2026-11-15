package com.windrr.hyundae.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.windrr.hyundae.data.local.entity.VehicleCargoSpaceEntity

@Dao
interface VehicleCargoDao {

    @Query("SELECT * FROM vehicle_cargo_space WHERE vehicle_id = :vehicleId")
    suspend fun spaces(vehicleId: String): List<VehicleCargoSpaceEntity>

    /** 대표 차량의 공간 */
    @Query("SELECT s.* FROM vehicle_cargo_space s INNER JOIN vehicle v ON v.vehicle_id = s.vehicle_id WHERE v.is_main = 1")
    suspend fun mainVehicleSpaces(): List<VehicleCargoSpaceEntity>
}
