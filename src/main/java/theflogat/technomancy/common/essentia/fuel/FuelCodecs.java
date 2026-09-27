package theflogat.technomancy.common.essentia.fuel;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Optional map fields that still report a decode error.
 *
 * <p>DataFixerUpper's own {@code optionalFieldOf} treats a field it cannot decode as absent and
 * throws the reason away. For a fuel table that is the worst possible behaviour: a mistyped
 * condition type turns into "no condition at all", the row silently sits on its base value, and
 * nothing appears in the log - it reads as a balance change rather than as the mistake it is.
 * These variants propagate the failure so the file is rejected and named.</p>
 *
 * <p>1.20.1 has no {@code ExtraCodecs.strictOptionalField}; later versions added one with the
 * same semantics.</p>
 */
final class FuelCodecs {

    private FuelCodecs() {
    }

    /** An absent field yields {@code fallback}; a present but unreadable one is an error. */
    static <A> MapCodec<A> strictOptionalField(Codec<A> codec, String name, A fallback) {
        Objects.requireNonNull(fallback, "fallback");
        return new StrictField<>(codec, name) {
            @Override
            public <T> DataResult<A> decode(DynamicOps<T> ops, MapLike<T> input) {
                T value = input.get(name);
                return value == null ? DataResult.success(fallback) : codec.parse(ops, value);
            }

            @Override
            public <T> RecordBuilder<T> encode(A input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                return fallback.equals(input) ? prefix : prefix.add(name, codec.encodeStart(ops, input));
            }
        };
    }

    /** An absent field yields {@link Optional#empty()}; a present but unreadable one is an error. */
    static <A> MapCodec<Optional<A>> strictOptionalField(Codec<A> codec, String name) {
        return new StrictField<>(codec, name) {
            @Override
            public <T> DataResult<Optional<A>> decode(DynamicOps<T> ops, MapLike<T> input) {
                T value = input.get(name);
                return value == null
                        ? DataResult.success(Optional.empty())
                        : codec.parse(ops, value).map(Optional::of);
            }

            @Override
            public <T> RecordBuilder<T> encode(Optional<A> input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                return input.map(present -> prefix.add(name, codec.encodeStart(ops, present))).orElse(prefix);
            }
        };
    }

    /** Shared {@code keys} and naming; the two factories differ only in decode/encode. */
    private abstract static class StrictField<A, V> extends MapCodec<V> {

        final Codec<A> codec;
        final String name;

        StrictField(Codec<A> codec, String name) {
            this.codec = Objects.requireNonNull(codec, "codec");
            this.name = Objects.requireNonNull(name, "name");
        }

        @Override
        public <T> Stream<T> keys(DynamicOps<T> ops) {
            return Stream.of(ops.createString(name));
        }

        @Override
        public String toString() {
            return "StrictOptionalField[" + name + ": " + codec + "]";
        }
    }
}
