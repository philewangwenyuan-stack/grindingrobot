package com.sinelynx.grindingrobot.core.model.gnss

import android.util.Log
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.LinkedHashMap
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * NMEA 数据解析器。
 *
 * 解析层只输出数值 DTO，不负责展示字符串拼装。
 */
object NmeaParser {

    sealed interface ParsedSentence {
        data class MainGga(val data: GgaData) : ParsedSentence
        data class AuxGga(val data: GgaData) : ParsedSentence
        data class Rmc(val data: RmcData) : ParsedSentence
        data class Ths(val data: ThsData) : ParsedSentence
    }

    data class GgaData(
        val rawSentence: String,
        val talkerAndType: String,
        val utcTimeRaw: String?,
        val utcHour: Int?,
        val utcMinute: Int?,
        val utcSecond: Double?,
        val utcTimeSecondsOfDay: Double?,
        val utcTimeKeyMs: Long?,
        val latitudeRaw: String?,
        val latitudeHemisphere: Char?,
        val latitudeDegrees: Double?,
        val longitudeRaw: String?,
        val longitudeHemisphere: Char?,
        val longitudeDegrees: Double?,
        val fixQuality: Int?,
        val satelliteCount: Int?,
        val hdop: Double?,
        val altitude: Double?,
        val altitudeUnit: String?,
        val geoidSeparation: Double?,
        val geoidSeparationUnit: String?,
        val dgpsAgeSeconds: Double?,
        val dgpsStationId: String?,
        val checksumProvided: String?,
        val checksumCalculated: String?,
        val checksumValid: Boolean,
    )

    data class RmcData(
        val rawSentence: String,
        val utcTimeRaw: String?,
        val dateRaw: String?,
        val valid: Boolean?,
        val speedKnots: Double?,
        val courseDegrees: Double?,
        val utcTimeSecondsOfDay: Double?,
        val utcTimeKeyMs: Long?,
        val utcEpochSeconds: Double?,
        val checksumProvided: String?,
        val checksumCalculated: String?,
        val checksumValid: Boolean,
    )

    data class ThsData(
        val rawSentence: String,
        val headingDeg: Double?,
        val valid: Boolean?,
        val checksumProvided: String?,
        val checksumCalculated: String?,
        val checksumValid: Boolean,
    )

    fun parseSentence(nmeaSentence: String, strictChecksum: Boolean = true): ParsedSentence? {
        if (nmeaSentence.isBlank()) return null
        val trimmed = nmeaSentence.trim()
        return when {
            trimmed.startsWith("\$GNGGAH,") -> ParsedSentence.AuxGga(parseGgaData(trimmed, strictChecksum))
            trimmed.startsWith("\$GNGGA,") || trimmed.startsWith("\$GPGGA,") ->
                ParsedSentence.MainGga(parseGgaData(trimmed, strictChecksum))
            trimmed.startsWith("\$GPRMC,") || trimmed.startsWith("\$GNRMC,") ->
                ParsedSentence.Rmc(parseRmcData(trimmed, strictChecksum))
            trimmed.startsWith("\$GPTHS,") || trimmed.startsWith("\$GNTHS,") ->
                ParsedSentence.Ths(parseThsData(trimmed, strictChecksum))
            else -> null
        }
    }

    fun parseEpochs(raw: String, arrivalMonotonicS: Double = -1.0): List<GnssFrame> {
        if (raw.isBlank()) return emptyList()
        val assembler = NmeaEpochAssembler()
        return raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.startsWith("$") }
            .flatMap { assembler.onSentence(it, arrivalMonotonicS).asSequence() }
            .toList()
    }

    fun parseGgaData(sentence: String, strictChecksum: Boolean = true): GgaData {
        require(sentence.isNotBlank()) { "NMEA sentence is blank." }
        val trimmed = sentence.trim()
        require(trimmed.startsWith("$")) { "NMEA sentence must start with '$'." }

        val (payload, checksumProvided, checksumCalculated, checksumValid) = splitAndValidateChecksum(trimmed)
        if (strictChecksum) {
            require(checksumValid) {
                "Checksum mismatch: provided=$checksumProvided, calculated=$checksumCalculated"
            }
        }

        val fields = payload.split(",")
        require(fields.isNotEmpty()) { "Empty NMEA payload." }
        val type = fields[0]
        require(type.endsWith("GNGGA") || type.endsWith("GPGGA") || type.endsWith("GNGGAH")) {
            "Not a GGA/GGAH sentence: $type"
        }

        val utcTimeRaw = fields.getOrNull(1)?.takeIf { it.isNotBlank() }
        val latRaw = fields.getOrNull(2)?.takeIf { it.isNotBlank() }
        val latHemisphere = fields.getOrNull(3)?.takeIf { it.isNotBlank() }?.firstOrNull()?.uppercaseChar()
        val lonRaw = fields.getOrNull(4)?.takeIf { it.isNotBlank() }
        val lonHemisphere = fields.getOrNull(5)?.takeIf { it.isNotBlank() }?.firstOrNull()?.uppercaseChar()
        val fixQuality = fields.getOrNull(6)?.toIntOrNull()
        val satelliteCount = fields.getOrNull(7)?.toIntOrNull()
        val hdop = fields.getOrNull(8)?.toDoubleOrNull()
        val altitude = fields.getOrNull(9)?.toDoubleOrNull()
        val altitudeUnit = fields.getOrNull(10)?.takeIf { it.isNotBlank() }
        val geoidSeparation = fields.getOrNull(11)?.toDoubleOrNull()
        val geoidSeparationUnit = fields.getOrNull(12)?.takeIf { it.isNotBlank() }
        val dgpsAgeSeconds = fields.getOrNull(13)?.toDoubleOrNull()
        val dgpsStationId = fields.getOrNull(14)?.takeIf { it.isNotBlank() }

        val (utcHour, utcMinute, utcSecond) = parseUtcTime(utcTimeRaw)
        val utcTimeSecondsOfDay = toUtcSecondsOfDay(utcHour, utcMinute, utcSecond)
        val utcTimeKeyMs = utcTimeSecondsOfDay?.let { (it * 1000.0).roundToLong() }

        val latitudeDegrees = if (latRaw != null && latHemisphere != null) {
            nmeaToDecimalDegrees(latRaw, latHemisphere, isLatitude = true)
        } else {
            null
        }

        val longitudeDegrees = if (lonRaw != null && lonHemisphere != null) {
            nmeaToDecimalDegrees(lonRaw, lonHemisphere, isLatitude = false)
        } else {
            null
        }

        return GgaData(
            rawSentence = trimmed,
            talkerAndType = type,
            utcTimeRaw = utcTimeRaw,
            utcHour = utcHour,
            utcMinute = utcMinute,
            utcSecond = utcSecond,
            utcTimeSecondsOfDay = utcTimeSecondsOfDay,
            utcTimeKeyMs = utcTimeKeyMs,
            latitudeRaw = latRaw,
            latitudeHemisphere = latHemisphere,
            latitudeDegrees = latitudeDegrees,
            longitudeRaw = lonRaw,
            longitudeHemisphere = lonHemisphere,
            longitudeDegrees = longitudeDegrees,
            fixQuality = fixQuality,
            satelliteCount = satelliteCount,
            hdop = hdop,
            altitude = altitude,
            altitudeUnit = altitudeUnit,
            geoidSeparation = geoidSeparation,
            geoidSeparationUnit = geoidSeparationUnit,
            dgpsAgeSeconds = dgpsAgeSeconds,
            dgpsStationId = dgpsStationId,
            checksumProvided = checksumProvided,
            checksumCalculated = checksumCalculated,
            checksumValid = checksumValid,
        )
    }

    fun parseRmcData(sentence: String, strictChecksum: Boolean = true): RmcData {
        val trimmed = sentence.trim()
        require(trimmed.startsWith("$")) { "NMEA sentence must start with '$'." }

        val (payload, checksumProvided, checksumCalculated, checksumValid) = splitAndValidateChecksum(trimmed)
        if (strictChecksum) {
            require(checksumValid) {
                "Checksum mismatch: provided=$checksumProvided, calculated=$checksumCalculated"
            }
        }

        val fields = payload.split(",")
        require(fields.isNotEmpty()) { "Empty NMEA payload." }
        val type = fields[0]
        require(type.endsWith("RMC")) { "Not an RMC sentence: $type" }

        val utcTimeRaw = fields.getOrNull(1)?.takeIf { it.isNotBlank() }
        val status = fields.getOrNull(2)?.trim()?.uppercase()
        val speedKnots = fields.getOrNull(7)?.toDoubleOrNull()
        val courseDegrees = fields.getOrNull(8)?.toDoubleOrNull()
        val dateRaw = fields.getOrNull(9)?.takeIf { it.isNotBlank() }

        val (hour, minute, second) = parseUtcTime(utcTimeRaw)
        val utcTimeSecondsOfDay = toUtcSecondsOfDay(hour, minute, second)
        val utcTimeKeyMs = utcTimeSecondsOfDay?.let { (it * 1000.0).roundToLong() }

        return RmcData(
            rawSentence = trimmed,
            utcTimeRaw = utcTimeRaw,
            dateRaw = dateRaw,
            valid = when (status) {
                "A" -> true
                "V" -> false
                else -> null
            },
            speedKnots = speedKnots,
            courseDegrees = courseDegrees,
            utcTimeSecondsOfDay = utcTimeSecondsOfDay,
            utcTimeKeyMs = utcTimeKeyMs,
            utcEpochSeconds = toUtcEpochSeconds(dateRaw, utcTimeRaw),
            checksumProvided = checksumProvided,
            checksumCalculated = checksumCalculated,
            checksumValid = checksumValid,
        )
    }

    fun parseThsData(sentence: String, strictChecksum: Boolean = true): ThsData {
        val trimmed = sentence.trim()
        require(trimmed.startsWith("$")) { "NMEA sentence must start with '$'." }

        val (payload, checksumProvided, checksumCalculated, checksumValid) = splitAndValidateChecksum(trimmed)
        if (strictChecksum) {
            require(checksumValid) {
                "Checksum mismatch: provided=$checksumProvided, calculated=$checksumCalculated"
            }
        }

        val fields = payload.split(",")
        require(fields.isNotEmpty()) { "Empty NMEA payload." }
        val type = fields[0]
        require(type.endsWith("THS")) { "Not a THS sentence: $type" }

        val heading = fields.getOrNull(1)?.toDoubleOrNull()
        val status = fields.getOrNull(2)?.trim()?.uppercase()
        return ThsData(
            rawSentence = trimmed,
            headingDeg = heading,
            valid = when (status) {
                "A" -> true
                "V" -> false
                else -> null
            },
            checksumProvided = checksumProvided,
            checksumCalculated = checksumCalculated,
            checksumValid = checksumValid,
        )
    }

    fun fixQualityDescription(fixQuality: Int?): String {
        return when (fixQuality) {
            null -> "--"
            0 -> "无效定位"
            1 -> "单点定位"
            2 -> "差分定位"
            3 -> "PPS解"
            4 -> "固定解(RTK)"
            5 -> "浮动解(RTK)"
            6 -> "估算模式"
            7 -> "手动输入模式"
            8 -> "仿真模式"
            9 -> "多星座固定解"
            10 -> "多星座浮点解"
            else -> "未知状态($fixQuality)"
        }
    }

    internal fun parseUtcTime(utc: String?): Triple<Int?, Int?, Double?> {
        if (utc.isNullOrBlank()) return Triple(null, null, null)

        val dotIndex = utc.indexOf('.')
        val mainPart = if (dotIndex >= 0) utc.substring(0, dotIndex) else utc
        if (mainPart.length < 6) return Triple(null, null, null)

        val hh = mainPart.substring(0, 2).toIntOrNull()
        val mm = mainPart.substring(2, 4).toIntOrNull()
        val ss = utc.substring(4).toDoubleOrNull()
        return Triple(hh, mm, ss)
    }

    private fun toUtcSecondsOfDay(hour: Int?, minute: Int?, second: Double?): Double? {
        if (hour == null || minute == null || second == null) return null
        return hour * 3600.0 + minute * 60.0 + second
    }

    private fun splitAndValidateChecksum(trimmed: String): ChecksumResult {
        val starIndex = trimmed.indexOf('*')
        require(starIndex > 0) { "NMEA sentence does not contain checksum separator '*'." }

        val payload = trimmed.substring(1, starIndex)
        val checksumProvided = trimmed.substring(starIndex + 1).uppercase()
        require(checksumProvided.length >= 2) { "Invalid checksum field." }

        val checksumCalculated = calculateChecksumHex(payload)
        val checksumValid = checksumProvided.take(2) == checksumCalculated
        return ChecksumResult(
            payload = payload,
            checksumProvided = checksumProvided.take(2),
            checksumCalculated = checksumCalculated,
            checksumValid = checksumValid,
        )
    }

    private data class ChecksumResult(
        val payload: String,
        val checksumProvided: String,
        val checksumCalculated: String,
        val checksumValid: Boolean,
    )

    private fun nmeaToDecimalDegrees(value: String, hemisphere: Char, isLatitude: Boolean): Double {
        val hemi = hemisphere.uppercaseChar()
        require(hemi == 'N' || hemi == 'S' || hemi == 'E' || hemi == 'W') {
            "Hemisphere must be N/S/E/W, got: $hemisphere"
        }

        val raw = value.toDoubleOrNull()
            ?: throw IllegalArgumentException("Invalid coordinate value: $value")

        require(raw >= 0.0) { "NMEA coordinate must be non-negative: $value" }

        val degrees = kotlin.math.floor(raw / 100.0)
        val minutes = raw - degrees * 100.0

        require(minutes in 0.0..<60.0) {
            "Invalid NMEA minutes: $minutes from value=$value"
        }

        var decimal = degrees + minutes / 60.0
        if (hemi == 'S' || hemi == 'W') {
            decimal = -decimal
        }

        if (isLatitude) {
            require(abs(decimal) <= 90.0) { "Latitude out of range: $decimal" }
        } else {
            require(abs(decimal) <= 180.0) { "Longitude out of range: $decimal" }
        }

        return decimal
    }

    private fun toUtcEpochSeconds(dateRaw: String?, utcTimeRaw: String?): Double? {
        val date = parseRmcDate(dateRaw) ?: return null
        val (hour, minute, second) = parseUtcTime(utcTimeRaw)
        if (hour == null || minute == null || second == null) return null

        val secondInt = second.toInt()
        val fractional = second - secondInt
        val nano = (fractional * 1_000_000_000.0).toInt()

        return try {
            val dateTime = LocalDateTime.of(
                date.year,
                date.month,
                date.day,
                hour,
                minute,
                secondInt,
                nano,
            )
            val instant = dateTime.toInstant(ZoneOffset.UTC)
            instant.epochSecond + instant.nano / 1_000_000_000.0
        } catch (_: Exception) {
            null
        }
    }

    private data class ParsedRmcDate(
        val year: Int,
        val month: Int,
        val day: Int,
    )

    private fun parseRmcDate(dateRaw: String?): ParsedRmcDate? {
        if (dateRaw.isNullOrBlank() || dateRaw.length != 6) return null

        val day = dateRaw.substring(0, 2).toIntOrNull() ?: return null
        val month = dateRaw.substring(2, 4).toIntOrNull() ?: return null
        val yearTwoDigits = dateRaw.substring(4, 6).toIntOrNull() ?: return null
        val year = if (yearTwoDigits in 0..79) 2000 + yearTwoDigits else 1900 + yearTwoDigits
        return ParsedRmcDate(year = year, month = month, day = day)
    }

    private fun calculateChecksumHex(payload: String): String {
        var checksum = 0
        for (ch in payload) {
            checksum = checksum xor ch.code
        }
        return checksum.toString(16).uppercase().padStart(2, '0')
    }
}

class NmeaEpochAssembler(
    private val primaryMatchToleranceMs: Long = 5L,
    private val thsMatchWindowS: Double = 0.020,
) {
    private val buckets = LinkedHashMap<Long, EpochBucket>()

    fun onSentence(sentence: String, arrivalMonotonicS: Double = -1.0): List<GnssFrame> {
        val parsed = runCatching { NmeaParser.parseSentence(sentence) }.getOrNull() ?: return emptyList()
        val frame = when (parsed) {
            is NmeaParser.ParsedSentence.MainGga -> onMainGga(parsed.data, arrivalMonotonicS)
            is NmeaParser.ParsedSentence.AuxGga -> onAuxGga(parsed.data, arrivalMonotonicS)
            is NmeaParser.ParsedSentence.Rmc -> onRmc(parsed.data, arrivalMonotonicS)
            is NmeaParser.ParsedSentence.Ths -> onThs(parsed.data, arrivalMonotonicS)
        }
        Log.d("NmeaEpochAssembler", "Parsed: ${frame?.let(::listOf) ?: emptyList()}")
        return frame?.let(::listOf) ?: emptyList()
    }

    fun clear() {
        buckets.clear()
    }

    private fun onMainGga(gga: NmeaParser.GgaData, arrivalMonotonicS: Double): GnssFrame? {
        Log.d("NmeaEpochAssembler", "Processing main GGA: $gga")
        val key = gga.utcTimeKeyMs ?: return null
        val bucket = findMatchingBucket(key) ?: run {
            discardOlderBuckets(key)
            buckets.getOrPut(key) { EpochBucket(key) }
        }
        bucket.main = gga
        bucket.mainArrivalS = arrivalMonotonicS.takeIf { it >= 0.0 }
        return emitIfReady(bucket)
    }

    private fun onAuxGga(gga: NmeaParser.GgaData, arrivalMonotonicS: Double): GnssFrame? {
        Log.d("NmeaEpochAssembler", "Processing aux GGA: $gga")
        val key = gga.utcTimeKeyMs ?: return null
        val bucket = findMatchingBucket(key) ?: buckets.getOrPut(key) { EpochBucket(key) }
        bucket.aux = gga
        bucket.auxArrivalS = arrivalMonotonicS.takeIf { it >= 0.0 }
        return null
    }

    private fun onRmc(rmc: NmeaParser.RmcData, arrivalMonotonicS: Double): GnssFrame? {
        Log.d("NmeaEpochAssembler", "Processing RMC: $rmc")
        val key = rmc.utcTimeKeyMs ?: return null
        val bucket = findMatchingBucket(key) ?: run {
            discardOlderBuckets(key)
            buckets.getOrPut(key) { EpochBucket(key) }
        }
        bucket.rmc = rmc
        bucket.rmcArrivalS = arrivalMonotonicS.takeIf { it >= 0.0 }
        return emitIfReady(bucket)
    }

    private fun onThs(ths: NmeaParser.ThsData, arrivalMonotonicS: Double): GnssFrame? {
        Log.d("NmeaEpochAssembler", "Processing THS: $ths")
        val rx = arrivalMonotonicS.takeIf { it >= 0.0 } ?: return null
        val candidate = buckets.values
            .filter { bucket ->
                !bucket.published &&
                    bucket.lastPrimaryArrivalS != null &&
                    rx >= bucket.lastPrimaryArrivalS!! &&
                    (rx - bucket.lastPrimaryArrivalS!!) <= thsMatchWindowS

            }
            .minByOrNull { rx - it.lastPrimaryArrivalS!! }
            ?: run {
                return null
            }
        candidate.ths = ths
        candidate.thsArrivalS = rx
        Log.d("NmeaEpochAssembler", "Processing THS: $candidate.thsArrivalS")
        return emitIfReady(candidate)
    }

    private fun discardOlderBuckets(newKey: Long) {
        val iterator = buckets.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key + primaryMatchToleranceMs < newKey) {
                iterator.remove()
            }
        }
    }

    private fun findMatchingBucket(key: Long): EpochBucket? {
        val matchingBuckets = buckets.values
            .filter { !it.published && abs(it.keyMs - key) <= primaryMatchToleranceMs }
            .minByOrNull { abs(it.keyMs - key) }
        return matchingBuckets
    }

    private fun emitIfReady(bucket: EpochBucket): GnssFrame? {
        if (bucket.published || bucket.main == null || bucket.rmc == null || bucket.ths == null) {
            return null
        }
        bucket.published = true
        buckets.remove(bucket.keyMs)
        return GnssFrame(
            main = bucket.main?.toGnssFix(),
            aux = bucket.aux?.toGnssFix(),
            headingDeg = bucket.ths?.headingDeg,
            headingValid = bucket.ths?.valid,
            speedKnots = bucket.rmc?.speedKnots,
            courseOverGroundDeg = bucket.rmc?.courseDegrees,
            rmcValid = bucket.rmc?.valid,
            utcTimeSecondsOfDay = bucket.rmc?.utcTimeSecondsOfDay ?: bucket.main?.utcTimeSecondsOfDay,
            utcEpochSeconds = bucket.rmc?.utcEpochSeconds,
            hostObservedMonotonicS = listOfNotNull(
                bucket.mainArrivalS,
                bucket.rmcArrivalS,
                bucket.thsArrivalS,
            ).minOrNull(),
        )
    }

    private data class EpochBucket(
        val keyMs: Long,
        var main: NmeaParser.GgaData? = null,
        var aux: NmeaParser.GgaData? = null,
        var rmc: NmeaParser.RmcData? = null,
        var ths: NmeaParser.ThsData? = null,
        var mainArrivalS: Double? = null,
        var auxArrivalS: Double? = null,
        var rmcArrivalS: Double? = null,
        var thsArrivalS: Double? = null,
        var published: Boolean = false,
    ) {
        val lastPrimaryArrivalS: Double?
            get() = listOfNotNull(mainArrivalS, rmcArrivalS).maxOrNull()
    }

    private fun NmeaParser.GgaData.toGnssFix(): GnssFix {
        return GnssFix(
            latitudeDeg = latitudeDegrees,
            longitudeDeg = longitudeDegrees,
            altitudeM = altitude,
            geoidSeparationM = geoidSeparation,
            satellitesUsed = satelliteCount,
            fixQualityCode = fixQuality,
            hdop = hdop,
            ageOfDiffS = dgpsAgeSeconds,
        )
    }
}
