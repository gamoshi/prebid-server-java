package org.prebid.server.auction;

import lombok.NoArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.EnumUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.request.ExtGranularityRange;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

/**
 * Describes the behavior for price granularity feature.
 */
@NoArgsConstructor
public class PriceGranularity {

    enum PriceGranularityType {
        low, medium, med, high, auto, dense
    }

    private static final EnumMap<PriceGranularityType, PriceGranularity> STRING_TO_CUSTOM_PRICE_GRANULARITY =
            new EnumMap<>(PriceGranularityType.class);

    static {
        putStringPriceGranularity(PriceGranularityType.low, 2, range(5, 0.5));
        final ExtGranularityRange medRange = range(20, 0.1);
        putStringPriceGranularity(PriceGranularityType.medium, 2, medRange);
        putStringPriceGranularity(PriceGranularityType.med, 2, medRange);
        putStringPriceGranularity(PriceGranularityType.high, 2, range(20, 0.01));
        putStringPriceGranularity(PriceGranularityType.auto, 2,
                range(5, 0.05),
                range(10, 0.1),
                range(20, 0.5));
        putStringPriceGranularity(PriceGranularityType.dense, 2,
                range(3, 0.01),
                range(8, 0.05),
                range(20, 0.5));
    }

    public static final PriceGranularity DEFAULT = STRING_TO_CUSTOM_PRICE_GRANULARITY.get(PriceGranularityType.med);

    private List<ExtGranularityRange> ranges;
    private BigDecimal rangesMax;
    private Integer precision;

    private PriceGranularity(List<ExtGranularityRange> ranges, BigDecimal rangesMax, Integer precision) {
        this.ranges = ranges;
        this.rangesMax = rangesMax;
        this.precision = precision;
    }

    /**
     * Creates {@link PriceGranularity} from {@link ExtPriceGranularity}.
     */
    public static PriceGranularity createFromExtPriceGranularity(ExtPriceGranularity extPriceGranularity) {
        return createFromRanges(extPriceGranularity.getPrecision(), extPriceGranularity.getRanges());
    }

    /**
     * Returns {@link PriceGranularity} by string representation if it is present in map, otherwise returns null.
     */
    public static PriceGranularity createFromString(String stringPriceGranularity) {
        if (isValidStringPriceGranularityType(stringPriceGranularity)) {
            return STRING_TO_CUSTOM_PRICE_GRANULARITY.get(PriceGranularityType.valueOf(stringPriceGranularity));
        } else {
            throw new PreBidException("Invalid string price granularity with value: " + stringPriceGranularity);
        }
    }

    public static PriceGranularity createFromStringOrDefault(String stringPriceGranularity) {
        return isValidStringPriceGranularityType(stringPriceGranularity)
                ? STRING_TO_CUSTOM_PRICE_GRANULARITY.get(PriceGranularityType.valueOf(stringPriceGranularity))
                : PriceGranularity.DEFAULT;
    }

    /**
     * Returns list of {@link ExtGranularityRange}s.
     */
    public List<ExtGranularityRange> getRanges() {
        return ranges;
    }

    /**
     * Returns max value among all ranges.
     */
    BigDecimal getRangesMax() {
        return rangesMax;
    }

    /**
     * Returns {@link PriceGranularity} precision.
     */
    public Integer getPrecision() {
        return precision;
    }

    /**
     * Creates {@link PriceGranularity} for string representation and puts it to
     * {@link EnumMap<PriceGranularityType, PriceGranularity>}.
     */
    private static void putStringPriceGranularity(PriceGranularityType type,
                                                  Integer precision,
                                                  ExtGranularityRange... ranges) {

        STRING_TO_CUSTOM_PRICE_GRANULARITY.put(type,
                PriceGranularity.createFromRanges(precision, Arrays.asList(ranges)));
    }

    /**
     * Creates {@link PriceGranularity} from list of {@link ExtGranularityRange}s and validates it.
     */
    public static PriceGranularity createFromRanges(Integer precision, List<ExtGranularityRange> ranges) {

        final BigDecimal rangeMax = CollectionUtils.emptyIfNull(ranges).stream()
                .filter(Objects::nonNull)
                .map(ExtGranularityRange::getMax)
                .filter(Objects::nonNull)
                .max(BigDecimal::compareTo)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Price granularity error: "
                                + "Max value among all ranges was not found. Please check if ranges are valid"));

        return new PriceGranularity(ranges, rangeMax, precision);
    }

    /**
     * Creates {@link ExtGranularityRange`} from given precision, min, max and increment parameters.
     */
    private static ExtGranularityRange range(int max, double increment) {
        return ExtGranularityRange.of(BigDecimal.valueOf(max), BigDecimal.valueOf(increment));
    }

    /**
     * Checks if string price granularity is valid type.
     */
    private static boolean isValidStringPriceGranularityType(String stringPriceGranularity) {
        return EnumUtils.isValidEnum(PriceGranularityType.class, stringPriceGranularity);
    }
}
