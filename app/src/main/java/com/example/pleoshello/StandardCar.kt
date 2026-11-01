package com.example.pleoshello

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context

// 안드로이드 표준 차량 속성으로 기어·속도 읽기 (지원 종료 예정인 플레오스 기어 기능 대신 사용)
class StandardCar(
    context: Context,
    private val onGear: (Int) -> Unit,       // 기어 값 (P=4, R=2, N=1, D=8, 알 수 없음=0)
    private val onSpeed: (Float) -> Unit,    // 속도 (m/s)
    private val onStatus: (String) -> Unit,  // 연결 결과 안내
    private val onIgnition: (Int) -> Unit = {},  // 시동 상태 (잠금=1, 꺼짐=2, 액세서리=3, 켜짐=4, 시동 중=5)
) {
    private val car: Car = Car.createCar(context)
    private val cpm = car.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            when (value.propertyId) {
                VehiclePropertyIds.GEAR_SELECTION -> (value.value as? Int)?.let(onGear)
                VehiclePropertyIds.PERF_VEHICLE_SPEED -> (value.value as? Float)?.let(onSpeed)
                VehiclePropertyIds.IGNITION_STATE -> (value.value as? Int)?.let(onIgnition)
            }
        }

        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            onStatus("오류 (속성 $propertyId)")
        }
    }

    fun listenGear(): Boolean = listen(VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE, "기어")
    fun listenIgnition(): Boolean = listen(VehiclePropertyIds.IGNITION_STATE, CarPropertyManager.SENSOR_RATE_ONCHANGE, "시동")
    fun listenSpeed(): Boolean =listen(VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL, "속도")

    // 지원 여부 확인 → 알림 등록. 결과를 문장으로 알려주고 성공 여부를 돌려줌 (조용한 실패 방지)
    private fun listen(propertyId: Int, rate: Float, label: String): Boolean {
        val (ok, message) = runCatching {
            when {
                cpm.getCarPropertyConfig(propertyId) == null -> false to "$label 미지원 또는 권한 없음"
                cpm.registerCallback(callback, propertyId, rate) -> true to "$label 연결됨"
                else -> false to "$label 연결 실패"
            }
        }.getOrElse { false to "$label 오류 (${it.javaClass.simpleName})" }
        onStatus(message)
        return ok
    }

    fun release() {
        runCatching { cpm.unregisterCallback(callback) }
        car.disconnect()
    }
}