package io.retel.ariproxy.boundary.commandsandresponses.auxiliary;

import static io.retel.ariproxy.boundary.commandsandresponses.auxiliary.Companion.NON_DEPENDANT_COMMAND_URI_MAX_SIZE;
import static io.retel.ariproxy.boundary.commandsandresponses.auxiliary.Companion.RESOURCE_ID_POSITION;
import static io.retel.ariproxy.boundary.commandsandresponses.auxiliary.Companion.RESOURCE_ID_POSITION_ON_ANOTHER_RESOURCE;
import static io.vavr.API.None;
import static io.vavr.API.Set;
import static io.vavr.API.Some;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import io.vavr.collection.List;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;

public enum AriCommandType {

    BRIDGE(
            uri -> uri.endsWith("/bridges") || (uri.contains("/bridges/") && List.of(uri.split("/")).size() <= NON_DEPENDANT_COMMAND_URI_MAX_SIZE),
            resourceIdFromUri(RESOURCE_ID_POSITION),
            resourceIdFromBody("/bridgeId")
    ),
    CHANNEL(
            uri -> uri.endsWith("/channels") || (uri.contains("/channels/") && List.of(uri.split("/")).size() <= NON_DEPENDANT_COMMAND_URI_MAX_SIZE),
            resourceIdFromUri(RESOURCE_ID_POSITION),
            resourceIdFromBody("/channelId")
    ),
    PLAYBACK(
            uri -> uri.contains("/play/") || uri.endsWith("/play"),
            resourceIdFromUri(RESOURCE_ID_POSITION_ON_ANOTHER_RESOURCE),
            resourceIdFromBody("/playbackId")
    ),
    RECORDING(
            uri -> uri.endsWith("/record"),
            AriCommandType::notAvailable,
            resourceIdFromBody("/name")
    ),
    SNOOPING(
            uri -> uri.contains("/snoop/") || uri.endsWith("/snoop"),
            resourceIdFromUri(RESOURCE_ID_POSITION_ON_ANOTHER_RESOURCE),
            resourceIdFromBody("/snoopId")
    ),
    COMMAND_NOT_CREATING_A_NEW_RESOURCE(
            uri -> false,
            uri -> None(),
            body -> None()
    );

    private static final ObjectReader reader = new ObjectMapper().reader();

    private final Function<String, Boolean> identifierPredicate;
    private final Function<String, Option<Try<String>>> resourceIdUriExtractor;
    private final Function<String, Option<Try<String>>> resourceIdBodyExtractor;

    AriCommandType(
            final Function<String, Boolean> identifierPredicate,
            final Function<String, Option<Try<String>>> resourceIdUriExtractor,
            final Function<String, Option<Try<String>>> resourceIdBodyExtractor
    ) {
        this.identifierPredicate = identifierPredicate;
        this.resourceIdUriExtractor = resourceIdUriExtractor;
        this.resourceIdBodyExtractor = resourceIdBodyExtractor;
    }

    public Option<Try<String>> extractResourceIdFromUri(final String uri) {
        return resourceIdUriExtractor.apply(uri);
    }

    public Option<Try<String>> extractResourceIdFromBody(final String body) {
        return resourceIdBodyExtractor.apply(body);
    }

    public static AriCommandType fromRequestUri(String candidateUri) {
        return Set(AriCommandType.values())
                .find(ariCommandType -> ariCommandType.identifierPredicate.apply(candidateUri))
                .getOrElse(COMMAND_NOT_CREATING_A_NEW_RESOURCE);
    }

    private static Function<String, Option<Try<String>>> resourceIdFromUri(final int resourceIdPosition) {
        return uri -> Some(Try.of(() -> List.of(uri.split("/")).get(resourceIdPosition)));
    }

    private static Function<String, Option<Try<String>>> resourceIdFromBody(final String resourceIdXPath) {
        return body -> Some(Try.of(() -> reader.readTree(body))
                .toOption()
                .flatMap(root -> Option.of(root.at(resourceIdXPath)))
                .map(JsonNode::asText)
                .flatMap(type -> StringUtils.isBlank(type) ? None() : Some(type))
                .toTry(() -> new Throwable(String.format("Failed to extract resourceId at path=%s from body=%s", resourceIdXPath, body))));
    }

    private static Option<Try<String>> notAvailable(final String bodyOrUri) {
        return Some(Try.failure(new ExtractorNotAvailable(bodyOrUri)));
    }
}

class Companion {
    static final int RESOURCE_ID_POSITION = 2;
    static final int RESOURCE_ID_POSITION_ON_ANOTHER_RESOURCE = 4;
    static final int NON_DEPENDANT_COMMAND_URI_MAX_SIZE = 3;
}
