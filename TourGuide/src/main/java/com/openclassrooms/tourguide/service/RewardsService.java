package com.openclassrooms.tourguide.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

@Service
public class RewardsService {
    private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;

	// proximity in miles
    private int defaultProximityBuffer = 10;
	private int proximityBuffer = defaultProximityBuffer;
	private int attractionProximityRange = 200;
	private final GpsUtil gpsUtil;
	private final RewardCentral rewardsCentral;
	private final ExecutorService executor = Executors.newFixedThreadPool(32);

	public ExecutorService getExecutor() {
		return executor;
	}

	public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
		this.gpsUtil = gpsUtil;
		this.rewardsCentral = rewardCentral;
	}
	
	public void setProximityBuffer(int proximityBuffer) {
		this.proximityBuffer = proximityBuffer;
	}
	
	public void setDefaultProximityBuffer() {
		proximityBuffer = defaultProximityBuffer;
	}

	/**
	 * Calculates rewards for a user asynchronously based on their visited locations and nearby attractions.
	 *
	 * <p>This method was refactored to return a {@link CompletableFuture} to enable asynchronous
	 * processing of reward calculations. Previously, it synchronously retrieved attractions and calculated
	 * rewards for each visited location, which could be time-consuming. The new implementation performs these
	 * steps asynchronously, allowing for improved performance and responsiveness.
	 *
	 * <ul>
	 *   <li>Attractions are retrieved asynchronously using {@code gpsUtil.getAttractions}.</li>
	 *   <li>For each visited location, the method iterates through the attractions and checks if a reward
	 *       already exists for each attraction.</li>
	 *   <li>If no reward exists and the attraction is near the visited location, the method calculates reward
	 *       points and adds a new {@link UserReward} to the user's rewards.</li>
	 * </ul>
	 *
	 * <p>The asynchronous approach allows for concurrent retrieval of attractions and reward calculations,
	 * making the method more efficient for scenarios with multiple locations and attractions.
	 *
	 * @param user The user for whom rewards are being calculated.
	 * @return A {@link CompletableFuture} that completes when all reward calculations are done.
	 */
	public CompletableFuture<Void> calculateRewards(User user) {
		List<VisitedLocation> userLocations = user.getVisitedLocations();
		System.out.println("ICI: " + userLocations.size());

		return CompletableFuture.supplyAsync(gpsUtil::getAttractions, executor)
				.thenAcceptAsync(attractions -> {
					userLocations.forEach(visitedLocation -> {
						attractions.forEach(attraction -> {
							boolean alreadyRewarded = user.getUserRewards().stream()
									.anyMatch(r -> r.attraction.attractionName.equals(attraction.attractionName));

							if (!alreadyRewarded && nearAttraction(visitedLocation, attraction)) {
								int rewardPoints = getRewardPoints(attraction, user);
								user.addUserReward(new UserReward(visitedLocation, attraction, rewardPoints));
							}
						});
					});
				}, executor);
	}


	/**
	 * Determines if a specified location is within proximity to a given attraction.
	 *
	 * <p>This method was simplified by removing a redundant ternary operation.
	 * Previously, it used a ternary to return `false` or `true` based on the result of
	 * a comparison. Now, it directly returns the negation of the comparison, achieving the
	 * same result with improved readability and performance.
	 *
	 * @param attraction The attraction to compare against.
	 * @param location The location to check proximity for.
	 * @return {@code true} if the location is within the attraction's proximity range, {@code false} otherwise.
	 */
	public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
		return !(getDistance(attraction, location) > attractionProximityRange);
	}

	/**
	 * Checks if a given visited location is near a specific attraction.
	 *
	 * <p>As with {@link #isWithinAttractionProximity}, this method was optimized by removing
	 * an unnecessary ternary operation. The negation of the distance comparison directly
	 * returns the required boolean value, simplifying the logic.
	 *
	 * @param visitedLocation The visited location to check.
	 * @param attraction The attraction to compare against.
	 * @return {@code true} if the visited location is near the attraction, {@code false} otherwise.
	 */
	private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
		return !(getDistance(attraction, visitedLocation.location) > proximityBuffer);
	}


	private int getRewardPoints(Attraction attraction, User user) {
		return rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
	}
	
	public double getDistance(Location loc1, Location loc2) {
        double lat1 = Math.toRadians(loc1.latitude);
        double lon1 = Math.toRadians(loc1.longitude);
        double lat2 = Math.toRadians(loc2.latitude);
        double lon2 = Math.toRadians(loc2.longitude);

        double angle = Math.acos(Math.sin(lat1) * Math.sin(lat2)
                               + Math.cos(lat1) * Math.cos(lat2) * Math.cos(lon1 - lon2));

        double nauticalMiles = 60 * Math.toDegrees(angle);
        double statuteMiles = STATUTE_MILES_PER_NAUTICAL_MILE * nauticalMiles;
        return statuteMiles;
	}

}
