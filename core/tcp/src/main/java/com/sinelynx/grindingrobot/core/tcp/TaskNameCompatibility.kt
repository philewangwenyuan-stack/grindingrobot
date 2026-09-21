package com.sinelynx.grindingrobot.core.tcp

import com.google.protobuf.ByteString
import com.google.protobuf.UnknownFieldSet
import sl_link.SlLink

/**
 * The board protocol keeps task_name as a wire-compatible extension used by
 * this app, while the canonical generated SDK omits the field from the
 * current schema. Keep it in protobuf unknown fields so existing boards and
 * task-name UI behaviour continue to work after syncing the new SDK.
 */
private fun taskNameUnknownFields(fieldNumber: Int, value: String): UnknownFieldSet =
    UnknownFieldSet.newBuilder()
        .addField(
            fieldNumber,
            UnknownFieldSet.Field.newBuilder()
                .addLengthDelimited(ByteString.copyFromUtf8(value))
                .build()
        )
        .build()

fun SlLink.TaskConfig.Builder.setTaskName(value: String): SlLink.TaskConfig.Builder = apply {
    mergeUnknownFields(taskNameUnknownFields(11, value))
}

val SlLink.TaskConfig.taskName: String
    get() = unknownFields.getField(11)?.lengthDelimitedList
        ?.lastOrNull()
        ?.toStringUtf8()
        .orEmpty()

fun SlLink.TaskConfigResponse.Builder.setTaskName(value: String): SlLink.TaskConfigResponse.Builder = apply {
    mergeUnknownFields(taskNameUnknownFields(4, value))
}

val SlLink.TaskConfigResponse.taskName: String
    get() = unknownFields.getField(4)?.lengthDelimitedList
        ?.lastOrNull()
        ?.toStringUtf8()
        .orEmpty()
