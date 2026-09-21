package com.sinelynx.grindingrobot.core.model.entity

import kotlinx.serialization.Serializable

/**
 * GD32状态实体类
 * 对应协议消息 MSG_ID: 0x0105
 *
 * @param systemStatus 系统状态 0-正常 1-错误
 * @param chipModel 芯片型号
 * @param boardModel 板卡型号
 * @param firmwareVersion 固件版本
 * @param serialNumber 序列号
 * @param imuError IMU错误码
 * @param imuSensors IMU传感器信息列表
 * @author Dreamj
 */
@Serializable
data class Gd32Status(

    /**
     * 系统状态
     * 0-正常 (SYS_STATUS_NORMAL)
     * 1-错误 (SYS_STATUS_ERROR)
     */
    val systemStatus: Int = 0,

    /**
     * 芯片型号
     */
    val chipModel: String = "",

    /**
     * 板卡型号
     */
    val boardModel: String = "",

    /**
     * 固件版本
     */
    val firmwareVersion: String = "",

    /**
     * 序列号
     */
    val serialNumber: String = "",

    /**
     * IMU错误码
     * 0x00-无错误
     * 0x01-电压异常
     * 0x02-通信链路失败
     */
    val imuError: Int = 0,

    /**
     * IMU传感器信息列表
     */
    val imuSensors: List<ImuSensorInfo> = emptyList()
)

/**
 * IMU传感器信息实体类
 *
 * @param status 传感器状态 0-正常 1-通信错误
 * @param serialNumber 序列号
 * @param firmwareVersion 固件版本
 * @param location 安装位置 0-未知 1-车身 2-大臂 3-小臂 4-狗骨
 * @author Dreamj
 */
@Serializable
data class ImuSensorInfo(

    /**
     * 传感器状态
     * 0-正常 (IMU_STATUS_NORMAL)
     * 1-通信错误 (IMU_STATUS_COMM_ERROR)
     */
    val status: Int = 0,

    /**
     * 序列号
     */
    val serialNumber: String = "",

    /**
     * 固件版本
     */
    val firmwareVersion: String = "",

    /**
     * 安装位置
     * 0-未知 (IMU_LOC_UNKNOWN)
     * 1-车身 (IMU_LOC_BODY)
     * 2-大臂 (IMU_LOC_BIG_ARM)
     * 3-小臂 (IMU_LOC_SMALL_ARM)
     * 4-狗骨 (IMU_LOC_DOG_BONE)
     */
    val location: Int = 0
)
