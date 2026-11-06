package simplifiedsimulator.network;

/**
 * One arm of the crossing, named after the compass direction it comes from.
 *
 * @param id      {@code N}, {@code E}, {@code S} or {@code W}
 * @param name    street name
 * @param heading direction of travel of traffic entering from this arm
 */
public record Approach(String id, String name, String heading) {
}
