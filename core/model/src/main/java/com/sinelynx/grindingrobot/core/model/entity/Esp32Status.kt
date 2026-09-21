package com.sinelynx.grindingrobot.core.model.entity

import kotlinx.serialization.Serializable

/**
 * ESP32状态实体类
 * 对应协议消息 MSG_ID: 0x0104
 *
 * @param systemStatus 系统状态 0-正常 1-错误
 * @param esp32Error ESP32错误码
 * @param chipModel 芯片型号
 * @param firmwareVersion 固件版本
 * @param serialNumber 序列号
 * @param radioModel 无线电型号
 * @param radioError 无线电错误码
 * @param gnssError GNSS错误码
 * @param antennas 天线信息列表
 * @author Dreamj
 */
@Serializable
data class Esp32Status(

    /**
     * 系统状态
     * 0-正常 (SYS_STATUS_NORMAL)
     * 1-错误 (SYS_STATUS_ERROR)
     */
    val systemStatus: Int = 0,

    /**
     * ESP32错误码
     * 0x00-无错误
     * 0x01-电压异常
     * 0x02-通信链路失败
     */
    val esp32Error: Int = 0,

    /**
     * 芯片型号
     */
    val chipModel: String = "",

    /**
     * 固件版本
     */
    val firmwareVersion: String = "",

    /**
     * 序列号
     */
    val serialNumber: String = "",

    /**
     * 无线电型号
     */
    val radioModel: String = "",

    /**
     * 无线电错误码
     * 0x00-无错误
     * 0x30-电压异常
     * 0x32-通信链路失败
     */
    val radioError: Int = 0,

    /**
     * GNSS错误码
     * 0x00-无错误
     * 0x01-电压异常
     * 0x02-模块通信失败
     */
    val gnssError: Int = 0,

    /**
     * 天线信息列表
     */
    val antennas: List<AntennaInfo> = emptyList()
)

/**
 * 天线信息实体类
 *
 * @param status 天线状态 0-正常 1-开路 2-短路
 * @param moduleName 模块名称
 * @param firmwareVersion 固件版本
 * @param location 天线位置 0-主天线 1-副天线
 * @author Dreamj
 */
@Serializable
data class AntennaInfo(

    /**
     * 天线状态
     * 0-正常 (ANT_STATUS_NORMAL)
     * 1-开路 (ANT_STATUS_OPEN)
     * 2-短路 (ANT_STATUS_SHORT)
     */
    val status: Int = 0,

    /**
     * 模块名称
     */
    val moduleName: String = "",

    /**
     * 固件版本
     */
    val firmwareVersion: String = "",

    /**
     * 天线位置
     * 0-主天线 (ANT_LOC_PRIMARY)
     * 1-副天线 (ANT_LOC_SECONDARY)
     */
    val location: Int = 0
)
