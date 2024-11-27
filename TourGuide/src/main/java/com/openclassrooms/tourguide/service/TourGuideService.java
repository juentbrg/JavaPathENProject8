package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.pojo.AttractionDTO;
import com.openclassrooms.tourguide.tracker.Tracker;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;

import rewardCentral.RewardCentral;
import tripPricer.Provider;
import tripPricer.TripPricer;

@Service
public class TourGuideService {
	private Logger logger = LoggerFactory.getLogger(TourGuideService.class);
	private final ExecutorService executor = Executors.newFixedThreadPool(32);
	private final GpsUtil gpsUtil;
	private final RewardsService rewardsService;
	private final TripPricer tripPricer = new TripPricer();
	private final RewardCentral rewardCentral;
	public final Tracker tracker;
	boolean testMode = true;

	public ExecutorService getExecutor() {
		return executor;
	}

	public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService, RewardCentral rewardCentral) {
		this.gpsUtil = gpsUtil;
		this.rewardsService = rewardsService;
        this.rewardCentral = rewardCentral;

        Locale.setDefault(Locale.US);

		if (testMode) {
			logger.info("TestMode enabled");
			logger.debug("Initializing users");
			initializeInternalUsers();
			logger.debug("Finished initializing users");
		}
		tracker = new Tracker(this);
		addShutDownHook();
	}

	public List<UserReward> getUserRewards(User user) {
		return user.getUserRewards();
	}

	/**
	 * Retrieves the last known location of the specified user asynchronously.
	 *
	 * <p>This method was refactored to return a {@link CompletableFuture} to support asynchronous
	 * operations when determining the user's location. Previously, the method returned a
	 * {@link VisitedLocation} directly. However, as {@code trackUserLocation(user)} already
	 * returns a {@code CompletableFuture<VisitedLocation>}, this method now leverages asynchronous
	 * behavior based on the availability of user location data.
	 *
	 * <ul>
	 *   <li>If the user has previously visited locations, the method returns a
	 *       {@code CompletableFuture} immediately completed with the user's last known location.</li>
	 *   <li>If no locations are recorded for the user, it initiates an asynchronous operation
	 *       using {@code trackUserLocation(user)}.</li>
	 * </ul>
	 *
	 * <p>This approach improves efficiency by avoiding unnecessary asynchronous wrapping and
	 * maintaining a consistent return type across both cases.
	 *
	 * @param user The user whose location is to be retrieved.
	 * @return A {@link CompletableFuture} containing the user's last visited location if it exists,
	 *         or the result of tracking the user's location asynchronously.
	 */
	public CompletableFuture<VisitedLocation> getUserLocation(User user) {
		if (user.getVisitedLocations().isEmpty())
			return trackUserLocation(user);
		else
			return CompletableFuture.completedFuture(user.getLastVisitedLocation());
	}


	public User getUser(String userName) {
		return internalUserMap.get(userName);
	}

	public List<User> getAllUsers() {
		return internalUserMap.values().stream().collect(Collectors.toList());
	}

	public void addUser(User user) {
		if (!internalUserMap.containsKey(user.getUserName())) {
			internalUserMap.put(user.getUserName(), user);
		}
	}

	public List<Provider> getTripDeals(User user) {
		int cumulatativeRewardPoints = user.getUserRewards().stream().mapToInt(i -> i.getRewardPoints()).sum();
		List<Provider> providers = tripPricer.getPrice(tripPricerApiKey, user.getUserId(),
				user.getUserPreferences().getNumberOfAdults(), user.getUserPreferences().getNumberOfChildren(),
				user.getUserPreferences().getTripDuration(), cumulatativeRewardPoints);
		user.setTripDeals(providers);
		return providers;
	}

	/**
	 * Tracks the location of the specified user asynchronously and updates their visited locations.
	 *
	 * <p>This method was refactored to return a {@link CompletableFuture} in order to handle
	 * the location tracking and reward calculation processes asynchronously. Previously, the method
	 * was synchronous, directly fetching the user's location, updating their visited locations, and
	 * calculating rewards. Now, it leverages asynchronous operations to improve performance, particularly
	 * in environments where location retrieval and reward calculations may be time-consuming.
	 *
	 * <ul>
	 *   <li>The user's location is retrieved asynchronously using {@code gpsUtil.getUserLocation} with a
	 *       custom {@code ExecutorService}.</li>
	 *   <li>Once the location is obtained, it is added to the user's visited locations, and reward
	 *       calculations are triggered asynchronously by calling {@code rewardsService.calculateRewards}.</li>
	 * </ul>
	 *
	 * <p>This approach allows the method to return immediately, providing a {@code CompletableFuture}
	 * that completes once both the location tracking and reward calculation processes are finished.
	 *
	 * @param user The user whose location is being tracked.
	 * @return A {@link CompletableFuture} containing the tracked {@link VisitedLocation} once
	 *         all processing is complete.
	 */
	public CompletableFuture<VisitedLocation> trackUserLocation(User user) {
		CompletableFuture<VisitedLocation> completableFuture =
				CompletableFuture.supplyAsync(() -> gpsUtil.getUserLocation(user.getUserId()), executor);

		completableFuture.thenComposeAsync(visitedLocation -> {
			user.addToVisitedLocations(visitedLocation);
			return rewardsService.calculateRewards(user);
		}, executor);

		return completableFuture;
	}


	/**
	 * Retrieves the five closest tourist attractions to the user's visited location.
	 *
	 * This method returns a list of {@link AttractionDTO} objects, each containing
	 * the attraction's name, coordinates, the user's location, the distance in miles
	 * from the user, and reward points for visiting the attraction.
	 *
	 * @param visitedLocation the user's current location.
	 * @return a list of the five closest {@link AttractionDTO} objects.
	 */
	public List<AttractionDTO> getNearByAttractions(VisitedLocation visitedLocation) {
		return gpsUtil.getAttractions().stream()
				.sorted(Comparator.comparingDouble(location -> rewardsService.getDistance(location, visitedLocation.location)))
				.limit(5)
				.map(attraction -> new AttractionDTO(
						attraction.attractionName,
						new Location(attraction.latitude, attraction.longitude),
						new Location(visitedLocation.location.latitude, visitedLocation.location.longitude),
						rewardsService.getDistance(new Location(attraction.latitude, attraction.longitude), visitedLocation.location),
						rewardCentral.getAttractionRewardPoints(attraction.attractionId, visitedLocation.userId)
				))
				.toList();
	}

	private void addShutDownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				tracker.stopTracking();
			}
		});
	}

	/**********************************************************************************
	 * 
	 * Methods Below: For Internal Testing
	 * 
	 **********************************************************************************/
	private static final String tripPricerApiKey = "test-server-api-key";
	// Database connection will be used for external users, but for testing purposes
	// internal users are provided and stored in memory
	private final Map<String, User> internalUserMap = new HashMap<>();

	private void initializeInternalUsers() {
		IntStream.range(0, InternalTestHelper.getInternalUserNumber()).forEach(i -> {
			String userName = "internalUser" + i;
			String phone = "000";
			String email = userName + "@tourGuide.com";
			User user = new User(UUID.randomUUID(), userName, phone, email);
			generateUserLocationHistory(user);

			internalUserMap.put(userName, user);
		});
		logger.debug("Created " + InternalTestHelper.getInternalUserNumber() + " internal test users.");
	}

	private void generateUserLocationHistory(User user) {
		IntStream.range(0, 3).forEach(i -> {
			user.addToVisitedLocations(new VisitedLocation(user.getUserId(),
					new Location(generateRandomLatitude(), generateRandomLongitude()), getRandomTime()));
		});
	}

	private double generateRandomLongitude() {
		double leftLimit = -180;
		double rightLimit = 180;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private double generateRandomLatitude() {
		double leftLimit = -85.05112878;
		double rightLimit = 85.05112878;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private Date getRandomTime() {
		LocalDateTime localDateTime = LocalDateTime.now().minusDays(new Random().nextInt(30));
		return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
	}

}
