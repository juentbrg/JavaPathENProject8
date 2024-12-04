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
	private final List<Attraction> attractions;
	private final ExecutorService executor = Executors.newFixedThreadPool(64);

	public ExecutorService getExecutor() {
		return executor;
	}

	public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
		this.gpsUtil = gpsUtil;
		this.rewardsCentral = rewardCentral;
        this.attractions = gpsUtil.getAttractions();
    }
	
	public void setProximityBuffer(int proximityBuffer) {
		this.proximityBuffer = proximityBuffer;
	}
	
	public void setDefaultProximityBuffer() {
		proximityBuffer = defaultProximityBuffer;
	}

	/**
	 * Retrieves the list of rewards for a user asynchronously.
	 *
	 * <p>The method has been refactored to improve performance and scalability by implementing
	 * asynchronous processing, caching of attractions, and using a thread pool executor for concurrent computations.
	 * Previously, the method directly accessed the user's rewards synchronously, but the new implementation
	 * now leverages the following features:
	 *
	 * <ul>
	 *   <li>**Asynchronous Processing**: The calculation of rewards is handled using {@link CompletableFuture},
	 *       allowing non-blocking operations and better responsiveness in multi-user scenarios.</li>
	 *   <li>**Thread Pool Executor**: A dedicated thread pool with a fixed number of threads ({@code 64})
	 *       is used to efficiently manage concurrent reward calculations.</li>
	 *   <li>**Cached Attraction List**: The list of attractions is retrieved once and stored in memory,
	 *       reducing redundant calls to {@code gpsUtil.getAttractions()} and improving performance.</li>
	 * </ul>
	 *
	 * <p>**Key Behavior:**
	 * <ul>
	 *   <li>The method checks each visited location of the user against all cached attractions.</li>
	 *   <li>For each attraction, it determines if the attraction is near the visited location using proximity checks.</li>
	 *   <li>If the attraction is nearby and no reward has already been assigned for it, a new {@link UserReward}
	 *       is created with the corresponding reward points and added to the user's rewards list.</li>
	 * </ul>
	 *
	 * <p>The asynchronous implementation ensures that the rewards are calculated without blocking the main thread,
	 * making the system more responsive in high-load environments.
	 *
	 * @param user The {@link User} whose rewards are to be calculated.
	 * @return A {@link CompletableFuture} that completes with the updated list of rewards for the user.
	 */
	public CompletableFuture<Void> calculateRewards(User user) {
		List<VisitedLocation> userLocations = user.getVisitedLocations();

		return CompletableFuture.runAsync(() -> {
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

	public List<Attraction> getAttractions() {
		return attractions;
	}
}
