package com.bullshitman.bullshituberclone

import com.bullshitman.bullshituberclone.Model.DriverInfoModel
import java.lang.StringBuilder

object Common {
    fun buildWelcomeMessage() = StringBuilder("Welcome, ${currentUser?.firstName} ${currentUser?.lastName}")


    val DRIVER_LOCATION_REFERENCE = "DriverLocation"
    var currentUser: DriverInfoModel? = null
    val DRIVER_INFO_REFERENCE: String = "DriverInfo"
}