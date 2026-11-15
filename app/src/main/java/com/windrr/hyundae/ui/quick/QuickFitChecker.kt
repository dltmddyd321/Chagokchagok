package com.windrr.hyundae.ui.quick

import com.windrr.hyundae.data.local.entity.VehicleCargoSpaceEntity

/** 빠른 확인 판정 */
enum class QuickFit {
    /** 지금 상태(2열 세움) 트렁크에 들어감 */
    FITS,

    /** 2열을 접어야 들어감 */
    FITS_WHEN_FOLDED,

    /** 어디에도 안 들어감 */
    NOT_FIT,
}

/**
 * 물건이 차에 들어가는지 (서버 배치 계산 없이 실측 치수로만).
 * 물건을 6방향으로 돌려 보며, 공간 안에 들어가고(가로·앞뒤·높이) 입구를 통과하면(가로·높이) 들어간다고 본다.
 * 빠듯해도 수치상 들어가면 들어간다고 한다 (여유분은 따로 두지 않음).
 */
object QuickFitChecker {

    fun check(widthCm: Double, depthCm: Double, heightCm: Double, spaces: List<VehicleCargoSpaceEntity>): QuickFit {
        val fits = spaces.filter { fits(widthCm, depthCm, heightCm, it) }
        return when {
            fits.any { !it.requiresFold } -> QuickFit.FITS
            fits.isNotEmpty() -> QuickFit.FITS_WHEN_FOLDED
            else -> QuickFit.NOT_FIT
        }
    }

    fun fits(w: Double, d: Double, h: Double, space: VehicleCargoSpaceEntity): Boolean =
        orientations(w, d, h).any { (x, y, z) ->
            x <= space.widthCm && y <= space.depthCm && z <= space.heightCm &&
                (space.openingWidthCm == null || x <= space.openingWidthCm) &&
                (space.openingHeightCm == null || z <= space.openingHeightCm)
        }

    private fun orientations(a: Double, b: Double, c: Double) = listOf(
        Triple(a, b, c), Triple(a, c, b), Triple(b, a, c),
        Triple(b, c, a), Triple(c, a, b), Triple(c, b, a),
    )
}
