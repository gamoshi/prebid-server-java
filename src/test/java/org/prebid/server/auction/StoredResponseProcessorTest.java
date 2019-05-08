package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.auction.model.StoredResponseResult;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.proto.openrtb.ext.request.ExtImp;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredAuctionResponse;
import org.prebid.server.proto.openrtb.ext.request.ExtStoredSeatBid;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.StoredResponseDataResult;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyZeroInteractions;

public class StoredResponseProcessorTest extends VertxTest {

    private static final long DEFAULT_TIMEOUT = 500;

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private ApplicationSettings applicationSettings;

    @Mock
    private BidderCatalog bidderCatalog;

    private StoredResponseProcessor storedResponseProcessor;

    @Before
    public void setUp() {
        final TimeoutFactory timeoutFactory = new TimeoutFactory(Clock.fixed(Instant.now(), ZoneId.systemDefault()));
        storedResponseProcessor = new StoredResponseProcessor(applicationSettings, bidderCatalog, timeoutFactory, DEFAULT_TIMEOUT);
    }

    @Test
    public void getStoredResponseResultShouldReturnSeatBidsForAuctionResponseId() throws JsonProcessingException {
        // given
        final List<Imp> imps = singletonList(Imp.builder()
                .ext(Json.mapper.valueToTree(ExtImp.of(ExtImpPrebid.of(null, ExtStoredAuctionResponse.of("1"), null))))
                .build());

        given(applicationSettings.getStoredResponse(any(), any()))
                .willReturn(Future.succeededFuture(StoredResponseDataResult.of(Collections.singletonMap("responseId",
                        Json.mapper.writeValueAsString(singletonList(SeatBid.builder().seat("rubicon")
                                .bid(singletonList(Bid.builder().id("id").build())).build()))),
                        Collections.emptyList())));

        // when
        final Future<StoredResponseResult> result = storedResponseProcessor.getStoredResponseResult(imps,
                DEFAULT_TIMEOUT, Collections.emptyMap());

        // then
        assertThat(result.result()).isEqualTo(StoredResponseResult.of(Collections.emptyList(),
                singletonList(SeatBid.builder().seat("rubicon")
                        .bid(singletonList(Bid.builder().id("id").build())).build())));
    }

    @Test
    public void getStoredResponseResultShouldNotChangeImpsAndReturnSeatBidsWhenThereAreNoStoredIds()
            throws JsonProcessingException {
        // given
        final List<Imp> imps = singletonList(Imp.builder().ext(Json.mapper.createObjectNode().put("rubicon", 1)).build());
        given(bidderCatalog.isValidName(any())).willReturn(true);

        // when
        final Future<StoredResponseResult> result = storedResponseProcessor.getStoredResponseResult(imps,
                DEFAULT_TIMEOUT, Collections.emptyMap());

        // then
        assertThat(result.result()).isEqualTo(StoredResponseResult.of(
                singletonList(Imp.builder().ext(Json.mapper.createObjectNode().put("rubicon", 1)).build()),
                Collections.emptyList()));
        verifyZeroInteractions(applicationSettings);
    }

    @Test
    public void getStoredResponseResultShouldReturnFailedFutureWhenErrorHappenedDuringRetrievingStoredResponse() {
        // given
        final List<Imp> imps = singletonList(Imp.builder()
                .ext(Json.mapper.valueToTree(ExtImp.of(ExtImpPrebid.of(null, ExtStoredAuctionResponse.of("1"), null))))
                .build());

        given(applicationSettings.getStoredResponse(any(), any()))
                .willReturn(Future.failedFuture(new PreBidException("Failed.")));

        // when
        final Future<StoredResponseResult> result = storedResponseProcessor.getStoredResponseResult(imps,
                DEFAULT_TIMEOUT, Collections.emptyMap());

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(InvalidRequestException.class)
                .hasMessage("Stored response fetching failed with reason: Failed.");
    }

    @Test
    public void getStoredResponseResultShouldReturnSeatBidsForBidStoredResponseId() throws JsonProcessingException {
        // given
        final List<Imp> imps = singletonList(Imp.builder()
                .ext(Json.mapper.valueToTree(ExtImp.of(ExtImpPrebid.of(null, null,
                        asList(ExtStoredSeatBid.of("rubicon", "storedBidResponseId1"),
                                ExtStoredSeatBid.of("appnexus", "storedBidResponseId2"))))))
                .build());

        final Map<String, String> storedResponse = new HashMap<>();
        storedResponse.put("storedBidResponseId1", Json.mapper.writeValueAsString(singletonList(
                SeatBid.builder().seat("rubicon").bid(singletonList(Bid.builder().id("id1").build()))
                        .build())));
        storedResponse.put("storedBidResponseId2", Json.mapper.writeValueAsString(singletonList(
                SeatBid.builder().seat("appnexus").bid(singletonList(Bid.builder().id("id2").build()))
                        .build())));


        given(applicationSettings.getStoredResponse(any(), any())).willReturn(
                Future.succeededFuture(StoredResponseDataResult.of(storedResponse, Collections.emptyList())));

        // when
        final Future<StoredResponseResult> result = storedResponseProcessor.getStoredResponseResult(imps,
                DEFAULT_TIMEOUT, Collections.emptyMap());

        // then
        assertThat(result.result()).isEqualTo(StoredResponseResult.of(Collections.emptyList(),
                asList(
                        SeatBid.builder().seat("appnexus").bid(singletonList(Bid.builder().id("id2")
                                .build())).build(),
                        SeatBid.builder().seat("rubicon").bid(singletonList(Bid.builder().id("id1")
                                .build())).build())));
    }

    @Test
    public void getStoredResponseResultShouldReturnSeatBidsForBidAndAuctionStoredResponseId() throws JsonProcessingException {
        // given
        final List<Imp> imps = asList(
                Imp.builder()
                        .ext(Json.mapper.valueToTree(ExtImp.of(ExtImpPrebid.of(null,
                                ExtStoredAuctionResponse.of("storedAuctionRequest"), null)))).build(),
                Imp.builder()
                        .ext(Json.mapper.valueToTree(ExtImp.of(ExtImpPrebid.of(
                                null, null, singletonList(ExtStoredSeatBid.of("rubicon", "storedBidRequest"))))))
                        .build());

        final Map<String, String> storedResponse = new HashMap<>();
        storedResponse.put("storedAuctionRequest", Json.mapper.writeValueAsString(singletonList(
                SeatBid.builder().seat("appnexus").bid(singletonList(Bid.builder().id("id1").build()))
                        .build())));
        storedResponse.put("storedBidRequest", Json.mapper.writeValueAsString(singletonList(
                SeatBid.builder().seat("rubicon").bid(singletonList(Bid.builder().id("id2").build()))
                        .build())));

        given(applicationSettings.getStoredResponse(any(), any())).willReturn(
                Future.succeededFuture(StoredResponseDataResult.of(storedResponse, Collections.emptyList())));

        // when
        final Future<StoredResponseResult> result = storedResponseProcessor.getStoredResponseResult(imps,
                DEFAULT_TIMEOUT, Collections.emptyMap());

        // then
        assertThat(result.result()).isEqualTo(StoredResponseResult.of(Collections.emptyList(),
                asList(
                        SeatBid.builder().seat("appnexus").bid(singletonList(Bid.builder().id("id1")
                                .build())).build(),
                        SeatBid.builder().seat("rubicon").bid(singletonList(Bid.builder().id("id2")
                                .build())).build())));
    }

    @Test
    public void getStoredResponseResultShouldRemoveMockedBiddersFromImps() throws JsonProcessingException {
        final ObjectNode impExt = Json.mapper.valueToTree(ExtImp.of(ExtImpPrebid.of(null, null,
                singletonList(ExtStoredSeatBid.of("rubicon", "storedBidResponseId1")))));
        impExt.put("rubicon", 1);
        impExt.put("appnexus", 2);

        given(bidderCatalog.isValidName(any())).willReturn(true);

        final List<Imp> imps = singletonList(Imp.builder().ext(impExt).build());

        final Map<String, String> storedResponse = new HashMap<>();
        storedResponse.put("storedBidResponseId1", Json.mapper.writeValueAsString(singletonList(
                SeatBid.builder().seat("rubicon").bid(singletonList(Bid.builder().id("id1").build()))
                        .build())));

        given(applicationSettings.getStoredResponse(any(), any())).willReturn(
                Future.succeededFuture(StoredResponseDataResult.of(storedResponse, Collections.emptyList())));

        // when
        final Future<StoredResponseResult> result = storedResponseProcessor.getStoredResponseResult(imps,
                DEFAULT_TIMEOUT, Collections.emptyMap());

        // then
        final ObjectNode impExtResult = Json.mapper.valueToTree(ExtImp.of(ExtImpPrebid.of(null, null,
                singletonList(ExtStoredSeatBid.of("rubicon", "storedBidResponseId1")))));
        impExtResult.put("appnexus", 2);

        assertThat(result.result()).isEqualTo(StoredResponseResult.of(singletonList(Imp.builder().ext(impExtResult).build()),
                singletonList(SeatBid.builder().seat("rubicon").bid(singletonList(Bid.builder()
                        .id("id1").build())).build())));
    }

    @Test
    public void getStoredResponseResultShouldMergeStoredSeatBidsForTheSameBidder() throws JsonProcessingException {
        // given
        final List<Imp> imps = asList(
                Imp.builder()
                        .ext(Json.mapper.valueToTree(ExtImp.of(ExtImpPrebid.of(null,
                                ExtStoredAuctionResponse.of("storedAuctionRequest"), null)))).build(),
                Imp.builder()
                        .ext(Json.mapper.valueToTree(ExtImp.of(ExtImpPrebid.of(
                                null, null, singletonList(ExtStoredSeatBid.of("rubicon", "storedBidRequest"))))))
                        .build());

        final Map<String, String> storedResponse = new HashMap<>();
        storedResponse.put("storedAuctionRequest", Json.mapper.writeValueAsString(asList(
                SeatBid.builder().seat("appnexus").bid(singletonList(Bid.builder().id("id1").build()))
                        .build(), SeatBid.builder().seat("rubicon").bid(singletonList(Bid.builder().id("id3").build()))
                        .build())));
        storedResponse.put("storedBidRequest", Json.mapper.writeValueAsString(singletonList(
                SeatBid.builder().seat("rubicon").bid(singletonList(Bid.builder().id("id2").build()))
                        .build())));

        given(applicationSettings.getStoredResponse(any(), any())).willReturn(
                Future.succeededFuture(StoredResponseDataResult.of(storedResponse, Collections.emptyList())));

        // when
        final Future<StoredResponseResult> result = storedResponseProcessor.getStoredResponseResult(imps,
                DEFAULT_TIMEOUT, Collections.emptyMap());

        // then
        assertThat(result.result()).isEqualTo(StoredResponseResult.of(Collections.emptyList(),
                asList(
                        SeatBid.builder().seat("appnexus").bid(singletonList(Bid.builder().id("id1")
                                .build())).build(),
                        SeatBid.builder().seat("rubicon").bid(asList(Bid.builder().id("id3").build(),
                                Bid.builder().id("id2").build())).build())));
    }

    @Test
    public void getStoredResponseResultShouldSupportAliasesWhenDecidingIfImpRequiredRequestToExchange()
            throws JsonProcessingException {
        final ObjectNode impExt = Json.mapper.valueToTree(ExtImp.of(ExtImpPrebid.of(null, null,
                singletonList(ExtStoredSeatBid.of("rubicon", "storedBidResponseId1")))));
        impExt.put("rubicon", 1);
        impExt.put("appnexusAlias", 2);

        given(bidderCatalog.isValidName(any())).willReturn(false);

        final List<Imp> imps = singletonList(Imp.builder().ext(impExt).build());

        final Map<String, String> storedResponse = new HashMap<>();
        storedResponse.put("storedBidResponseId1", Json.mapper.writeValueAsString(singletonList(
                SeatBid.builder().seat("rubicon").bid(singletonList(Bid.builder().id("id1").build()))
                        .build())));

        given(applicationSettings.getStoredResponse(any(), any())).willReturn(
                Future.succeededFuture(StoredResponseDataResult.of(storedResponse, Collections.emptyList())));

        // when
        final Future<StoredResponseResult> result = storedResponseProcessor.getStoredResponseResult(imps,
                DEFAULT_TIMEOUT, Collections.singletonMap("appnexusAlias", "appnexus"));

        // then
        final ObjectNode impExtResult = Json.mapper.valueToTree(ExtImp.of(ExtImpPrebid.of(null, null,
                singletonList(ExtStoredSeatBid.of("rubicon", "storedBidResponseId1")))));
        impExtResult.put("appnexusAlias", 2);

        assertThat(result.result()).isEqualTo(StoredResponseResult.of(singletonList(Imp.builder().ext(impExtResult).build()),
                singletonList(SeatBid.builder().seat("rubicon").bid(singletonList(Bid.builder()
                        .id("id1").build())).build())));
    }

    @Test
    public void getStoredResponseResultShouldReturnFailedFutureWhenImpExtIsNotValid()
            throws JsonProcessingException {
        // given
        final List<Imp> imps = singletonList(Imp.builder().id("impId").ext(Json.mapper.createObjectNode()
                .put("prebid", 5)).build());

        // when
        final Future<StoredResponseResult> result = storedResponseProcessor.getStoredResponseResult(imps,
                DEFAULT_TIMEOUT, Collections.emptyMap());

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).hasMessageContaining("Error decoding bidRequest.imp.ext for impId = impId :");
    }

    @Test
    public void getStoredResponseResultShouldReturnFailedFutureWhenBidderIsMissedInStoredBidResponse()
            throws JsonProcessingException {
        // given
        final ObjectNode impExt = Json.mapper.valueToTree(ExtImp.of(ExtImpPrebid.of(null, null,
                singletonList(ExtStoredSeatBid.of(null, "storedBidResponseId1")))));
        final List<Imp> imps = singletonList(Imp.builder().id("impId").ext(impExt).build());

        // when
        final Future<StoredResponseResult> result = storedResponseProcessor.getStoredResponseResult(imps,
                DEFAULT_TIMEOUT, Collections.emptyMap());

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .hasMessage("Bidder was not defined for imp.ext.prebid.storedBidResponse for imp with id impId");
    }

    @Test
    public void getStoredResponseResultShouldReturnFailedFutureWhenIdIsMissedInStoredBidResponse()
            throws JsonProcessingException {
        // given
        final ObjectNode impExt = Json.mapper.valueToTree(ExtImp.of(ExtImpPrebid.of(null, null,
                singletonList(ExtStoredSeatBid.of("rubicon", null)))));
        final List<Imp> imps = singletonList(Imp.builder().id("impId").ext(impExt).build());

        // when
        final Future<StoredResponseResult> result = storedResponseProcessor.getStoredResponseResult(imps,
                DEFAULT_TIMEOUT, Collections.emptyMap());

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .hasMessage("Id was not defined for imp.ext.prebid.storedBidResponse for imp with id impId");
    }

    @Test
    public void getStoredResponseResultShouldReturnFailedFutureWhenSeatIsEmptyInStoredSeatBid()
            throws JsonProcessingException {
        // given
        final List<Imp> imps = singletonList(Imp.builder()
                .ext(Json.mapper.valueToTree(ExtImp.of(ExtImpPrebid.of(null, ExtStoredAuctionResponse.of("1"), null))))
                .build());

        given(applicationSettings.getStoredResponse(any(), any()))
                .willReturn(Future.succeededFuture(StoredResponseDataResult.of(Collections.singletonMap("responseId",
                        Json.mapper.writeValueAsString(singletonList(SeatBid.builder().bid(singletonList(
                                Bid.builder().id("id").build())).build()))),
                        Collections.emptyList())));

        // when
        final Future<StoredResponseResult> result = storedResponseProcessor.getStoredResponseResult(imps,
                DEFAULT_TIMEOUT, Collections.emptyMap());

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause())
                .hasMessage("Seat can't be empty in stored response seatBid");
    }

    @Test
    public void mergeWithBidderResponsesShouldReturnMergedStoredSeatWithResponse() {
        // given
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("rubicon", BidderSeatBid.of(
                singletonList(BidderBid.of(Bid.builder().id("bid1").build(), BidType.banner, "USD")), emptyList(),
                emptyList()), 100));

        final List<SeatBid> seatBid = singletonList(SeatBid.builder()
                .seat("rubicon").bid(singletonList(Bid.builder().id("bid2").build())).build());

        // when
        final List<BidderResponse> result = storedResponseProcessor.mergeWithBidderResponses(bidderResponses, seatBid);

        // then
        assertThat(result).contains(BidderResponse.of("rubicon", BidderSeatBid.of(
                asList(BidderBid.of(Bid.builder().id("bid2").build(), BidType.banner, "USD"),
                        BidderBid.of(Bid.builder().id("bid1").build(), BidType.banner, "USD")), emptyList(),
                emptyList()), 100));
    }

    @Test
    public void mergeWithBidderResponsesShouldMergeBidderResponsesWithoutCorrespondingStoredSeatBid() {
        // given
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("rubicon", BidderSeatBid.of(
                singletonList(BidderBid.of(Bid.builder().id("bid1").build(), BidType.banner, "USD")), emptyList(),
                emptyList()), 100));

        final List<SeatBid> seatBid = singletonList(SeatBid.builder()
                .seat("appnexus").bid(singletonList(Bid.builder().id("bid2").build())).build());

        // when
        final List<BidderResponse> result = storedResponseProcessor.mergeWithBidderResponses(bidderResponses, seatBid);

        // then
        assertThat(result).contains(
                BidderResponse.of("rubicon", BidderSeatBid.of(
                        singletonList(BidderBid.of(Bid.builder().id("bid1").build(), BidType.banner, "USD")), emptyList(),
                        emptyList()), 100),
                BidderResponse.of("appnexus", BidderSeatBid.of(
                        singletonList(BidderBid.of(Bid.builder().id("bid2").build(), BidType.banner, "USD")), emptyList(),
                        emptyList()), 0));
    }

    @Test
    public void mergeWithBidderResponsesShouldMergeStoredSeatBidsWithoutBidderResponses() {
        // given
        final List<SeatBid> seatBid = singletonList(SeatBid.builder()
                .seat("rubicon").bid(singletonList(Bid.builder().id("bid2").build())).build());

        // when
        final List<BidderResponse> result = storedResponseProcessor.mergeWithBidderResponses(emptyList(), seatBid);

        // then
        assertThat(result).contains(BidderResponse.of("rubicon", BidderSeatBid.of(
                singletonList(BidderBid.of(Bid.builder().id("bid2").build(), BidType.banner, "USD")), emptyList(),
                emptyList()), 0));
    }

    @Test
    public void mergeWithBidderResponsesShouldResolveCurrencyFromBidderResponse() {
        // given
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("rubicon", BidderSeatBid.of(
                singletonList(BidderBid.of(Bid.builder().id("bid1").build(), BidType.banner, "EUR")), emptyList(),
                emptyList()), 100));

        final List<SeatBid> seatBid = singletonList(SeatBid.builder()
                .seat("rubicon").bid(singletonList(Bid.builder().id("bid2").build())).build());

        // when
        final List<BidderResponse> result = storedResponseProcessor.mergeWithBidderResponses(bidderResponses, seatBid);

        // then
        assertThat(result).contains(BidderResponse.of("rubicon", BidderSeatBid.of(
                asList(BidderBid.of(Bid.builder().id("bid2").build(), BidType.banner, "EUR"),
                        BidderBid.of(Bid.builder().id("bid1").build(), BidType.banner, "EUR")), emptyList(),
                emptyList()), 100));
    }

    @Test
    public void mergeWithBidderResponsesShouldResolveBidTypeFromStoredBidExt() throws JsonProcessingException {
        // given
        final List<BidderResponse> bidderResponses = singletonList(BidderResponse.of("rubicon", BidderSeatBid.of(
                singletonList(BidderBid.of(Bid.builder().id("bid1").build(), BidType.banner, "USD")), emptyList(),
                emptyList()), 100));

        final ExtBidPrebid extBidPrebid = ExtBidPrebid.of(BidType.video, null, null, null);

        final List<SeatBid> seatBid = singletonList(SeatBid.builder()
                .seat("rubicon").bid(singletonList(Bid.builder().ext((ObjectNode) Json.mapper.createObjectNode()
                        .set("prebid", Json.mapper.valueToTree(extBidPrebid))).id("bid2").build())).build());

        // when
        final List<BidderResponse> result = storedResponseProcessor.mergeWithBidderResponses(bidderResponses, seatBid);

        // then
        assertThat(result).contains(BidderResponse.of("rubicon", BidderSeatBid.of(
                asList(BidderBid.of(Bid.builder().id("bid2").ext((ObjectNode) Json.mapper.createObjectNode()
                                .set("prebid", Json.mapper.valueToTree(extBidPrebid))).build(), BidType.video, "USD"),
                        BidderBid.of(Bid.builder().id("bid1").build(), BidType.banner, "USD")), emptyList(),
                emptyList()), 100));
    }

    @Test
    public void mergeWithBidderResponsesShouldThrowPrebidExceptionWhenExtBidPrebidInStoredBidIsNotValid() {
        // given
        final ObjectNode extBidPrebid = Json.mapper.createObjectNode().put("type", "invalid");

        final List<SeatBid> seatBid = singletonList(SeatBid.builder()
                .seat("rubicon").bid(singletonList(Bid.builder().ext((ObjectNode) Json.mapper.createObjectNode()
                        .set("prebid", extBidPrebid)).id("bid2").build())).build());

        // when and then
        assertThatThrownBy(() -> storedResponseProcessor.mergeWithBidderResponses(emptyList(), seatBid))
                .isInstanceOf(PreBidException.class).hasMessage("Error decoding stored response bid.ext.prebid");
    }
}