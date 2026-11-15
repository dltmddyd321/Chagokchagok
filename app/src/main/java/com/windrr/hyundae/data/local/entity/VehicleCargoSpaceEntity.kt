package com.windrr.hyundae.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * 차량 적재 공간 실측 (빠른 확인: 이 물건이 지금 차에 들어가는지 앱에서 바로 판정).
 * 공간은 직육면체로 본다. 가로 = 좌우, 세로 = 앞뒤(트렁크 입구에서 안쪽), 높이 = 위아래.
 * 입구(opening)가 있으면 짐이 입구를 통과해야 한다 (가로·높이 단면).
 */
@Entity(tableName = "vehicle_cargo_space", primaryKeys = ["vehicle_id", "space_id"])
data class VehicleCargoSpaceEntity(
    @ColumnInfo(name = "vehicle_id")
    val vehicleId: String,
    /** 예: TRUNK, REAR_FOLDED */
    @ColumnInfo(name = "space_id")
    val spaceId: String,
    /** 화면 표시용 이름 */
    val name: String,
    @ColumnInfo(name = "width_cm")
    val widthCm: Int,
    @ColumnInfo(name = "depth_cm")
    val depthCm: Int,
    @ColumnInfo(name = "height_cm")
    val heightCm: Int,
    @ColumnInfo(name = "opening_width_cm")
    val openingWidthCm: Int?,
    @ColumnInfo(name = "opening_height_cm")
    val openingHeightCm: Int?,
    /** 2열을 접어야 쓸 수 있는 공간 */
    @ColumnInfo(name = "requires_fold")
    val requiresFold: Boolean,
)
