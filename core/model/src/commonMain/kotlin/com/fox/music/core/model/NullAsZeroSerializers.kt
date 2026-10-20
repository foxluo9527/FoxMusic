package com.fox.music.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * 后端数值计数字段（play_count / view_count / duration 等）偶发返回显式 `null`，
 * 而 DTO 字段是非空 Int 且带默认值 —— kotlinx.serialization 的 coerceInputValues
 * 只对可空属性生效，非空字段遇到 null 仍会直接抛
 * "Unexpected symbol 'n' in numeric literal"，把整个列表页打挂（iOS 推荐专辑已现网命中）。
 *
 * 该序列化器在解码端把 null（或缺失）兜底为 0；非数字脏值（字符串数字）交给 isLenient。
 * Android / iOS 行为一致，对已存储数据无影响。
 */
object NullAsZeroIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("NullAsZeroInt", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: Int) {
        encoder.encodeInt(value)
    }

    override fun deserialize(decoder: Decoder): Int {
        // 标准 nullable 读取姿势：先判 null 标记，为 null 时消费掉 null token 并返回默认值。
        return if (decoder.decodeNotNullMark()) decoder.decodeInt() else {
            decoder.decodeNull()
            0
        }
    }
}

/** 同 [NullAsZeroIntSerializer] 的 Long 版本（duration / 大数 id 等）。 */
object NullAsZeroLongSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("NullAsZeroLong", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Long) {
        encoder.encodeLong(value)
    }

    override fun deserialize(decoder: Decoder): Long {
        return if (decoder.decodeNotNullMark()) decoder.decodeLong() else {
            decoder.decodeNull()
            0L
        }
    }
}

/** 同 [NullAsZeroIntSerializer] 的 Float 版本（avg_play_progress / completion_rate 等）。 */
object NullAsZeroFloatSerializer : KSerializer<Float> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("NullAsZeroFloat", PrimitiveKind.FLOAT)

    override fun serialize(encoder: Encoder, value: Float) {
        encoder.encodeFloat(value)
    }

    override fun deserialize(decoder: Decoder): Float {
        return if (decoder.decodeNotNullMark()) decoder.decodeFloat() else {
            decoder.decodeNull()
            0f
        }
    }
}
