package com.huaweitv.solar.data.source

import com.huaweitv.solar.data.model.AppSettings
import com.huaweitv.solar.data.model.InverterBrand

/**
 * Factory creating the appropriate [InverterDataSource] based on `inverter_brand` settings.
 */
object InverterDataSourceFactory {

    fun create(settings: AppSettings): InverterDataSource {
        if (settings.mockMode) {
            return MockDataSource(settings.inverterBrand)
        }
        return when (settings.inverterBrand) {
            InverterBrand.HUAWEI -> HuaweiDataSource(
                ipAddress = settings.ipAddress,
                port = settings.effectivePort(),
                unitId = settings.unitId
            )
            InverterBrand.FRONIUS -> FroniusDataSource(
                ipAddress = settings.ipAddress,
                port = settings.effectivePort(),
                deviceId = settings.froniusDeviceId
            )
        }
    }
}
