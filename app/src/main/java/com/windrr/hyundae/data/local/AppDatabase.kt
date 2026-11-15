package com.windrr.hyundae.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.windrr.hyundae.data.local.converter.Converters
import com.windrr.hyundae.data.local.dao.CalculationDao
import com.windrr.hyundae.data.local.dao.PlanDao
import com.windrr.hyundae.data.local.dao.SavedItemDao
import com.windrr.hyundae.data.local.dao.VehicleCargoDao
import com.windrr.hyundae.data.local.dao.VehicleDao
import com.windrr.hyundae.data.local.entity.ArrangementEntity
import com.windrr.hyundae.data.local.entity.CalculationEntity
import com.windrr.hyundae.data.local.entity.PackingSpaceEntity
import com.windrr.hyundae.data.local.entity.PlacementEntity
import com.windrr.hyundae.data.local.entity.PlanCarSeatEntity
import com.windrr.hyundae.data.local.entity.PlanEntity
import com.windrr.hyundae.data.local.entity.PlanItemEntity
import com.windrr.hyundae.data.local.entity.SavedItemEntity
import com.windrr.hyundae.data.local.entity.VehicleCargoSpaceEntity
import com.windrr.hyundae.data.local.entity.VehicleEntity
import androidx.room.migration.Migration

@Database(
    entities = [
        VehicleEntity::class,
        PlanEntity::class,
        PlanItemEntity::class,
        PlanCarSeatEntity::class,
        CalculationEntity::class,
        ArrangementEntity::class,
        PackingSpaceEntity::class,
        PlacementEntity::class,
        SavedItemEntity::class,
        VehicleCargoSpaceEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun vehicleDao(): VehicleDao

    abstract fun planDao(): PlanDao

    abstract fun calculationDao(): CalculationDao

    abstract fun savedItemDao(): SavedItemDao

    abstract fun vehicleCargoDao(): VehicleCargoDao

    companion object {
        /** 스키마 버전. 출시 전이라 올리면 기존 로컬 데이터는 지워진다 */
        const val VERSION = 8

        private const val NAME = "load_plan.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                    .addCallback(SeedVehicles)
                    // 7 → 8: 차량 적재 공간 실측 테이블 추가 (기존 계획·짐은 그대로 둔다)
                    .addMigrations(MIGRATION_7_8)
                    // 출시 전이라 스키마가 바뀌면 기존 로컬 데이터는 버리고 새로 만든다
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}

/** 7 → 8: vehicle_cargo_space 추가. 실측 값은 열 때마다 [SeedVehicles]가 최신으로 채운다 */
private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `vehicle_cargo_space` (" +
                "`vehicle_id` TEXT NOT NULL, `space_id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`width_cm` INTEGER NOT NULL, `depth_cm` INTEGER NOT NULL, `height_cm` INTEGER NOT NULL, " +
                "`opening_width_cm` INTEGER, `opening_height_cm` INTEGER, `requires_fold` INTEGER NOT NULL, " +
                "PRIMARY KEY(`vehicle_id`, `space_id`))",
        )
    }
}

/**
 * 디 올 뉴 투싼 하이브리드 실측 (2026-11-14 실측 시트, docs/VEHICLE_TUCSON_HYBRID.md).
 * 휠하우스처럼 튀어나온 곳은 좁은 쪽 값을 써서 실제로 안 들어가는 걸 들어간다고 하지 않게 한다.
 */
object DefaultCargoSpaces {
    private val vehicleId get() = DefaultVehicles.AVANTE.vehicleId

    val ALL: List<VehicleCargoSpaceEntity>
        get() = listOf(
            // 트렁크(바닥판 위, 2열 세운 상태): 폭 = 휠하우스 사이 123, 앞뒤 100, 높이 80 / 입구 116 × 80
            VehicleCargoSpaceEntity(vehicleId, "TRUNK", "트렁크", 123, 100, 80, 116, 80, requiresFold = false),
            // 2열 접은 상태: 앞뒤 = 트렁크 입구 턱 ~ 접힌 등받이 끝 160, 높이 = 접힌 등받이 위 여유 70
            // 폭은 접힌 등받이 폭이 미측정이라 트렁크 폭(123)으로 둔다
            VehicleCargoSpaceEntity(vehicleId, "REAR_FOLDED", "2열 접은 트렁크", 123, 160, 70, 116, 80, requiresFold = true),
        )
}

/**
 * 1차 프로토타입은 이 차량 하나만 쓴다.
 * vehicle_id는 기존 계획이 참조하는 내부 키라 그대로 두고 표시 이름만 바꾼다.
 */
object DefaultVehicles {
    val AVANTE = VehicleEntity(
        vehicleId = "avante_cn8",
        isMain = true,
        name = "디 올 뉴 투싼 하이브리드",
        desc = "5인승 SUV",
    )
    val ALL = listOf(AVANTE)
}

/** DB 최초 생성 시 기본 차량을 넣고, 열 때마다 기본 차량 이름·설명을 최신 값으로 맞춘다 */
private object SeedVehicles : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        DefaultVehicles.ALL.forEach {
            db.execSQL(
                "INSERT OR IGNORE INTO vehicle (vehicle_id, is_main, name, description) VALUES (?, ?, ?, ?)",
                arrayOf(it.vehicleId, if (it.isMain) 1 else 0, it.name, it.desc),
            )
        }
    }

    /** 이미 설치된 앱도 기본 차량 표시값·적재 공간 실측이 바뀌면 따라가게 (대표 차량 여부는 그대로) */
    override fun onOpen(db: SupportSQLiteDatabase) {
        DefaultVehicles.ALL.forEach {
            db.execSQL(
                "UPDATE vehicle SET name = ?, description = ? WHERE vehicle_id = ?",
                arrayOf(it.name, it.desc, it.vehicleId),
            )
        }
        DefaultCargoSpaces.ALL.forEach {
            db.execSQL(
                "INSERT OR REPLACE INTO vehicle_cargo_space " +
                    "(vehicle_id, space_id, name, width_cm, depth_cm, height_cm, opening_width_cm, opening_height_cm, requires_fold) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf(
                    it.vehicleId, it.spaceId, it.name, it.widthCm, it.depthCm, it.heightCm,
                    it.openingWidthCm, it.openingHeightCm, if (it.requiresFold) 1 else 0,
                ),
            )
        }
    }
}
