package com.openclassrooms.tourguide;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.lang3.time.StopWatch;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;
import org.springframework.beans.factory.annotation.Autowired;
import rewardCentral.RewardCentral;
import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.service.RewardsService;
import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;

import static org.junit.jupiter.api.Assertions.*;

public class TestPerformance {
	/*
	 * A note on performance improvements:
	 * 
	 * The number of users generated for the high volume tests can be easily
	 * adjusted via this method:
	 * 
	 * InternalTestHelper.setInternalUserNumber(100000);
	 * 
	 * 
	 * These tests can be modified to suit new solutions, just as long as the
	 * performance metrics at the end of the tests remains consistent.
	 * 
	 * These are performance metrics that we are trying to hit:
	 * 
	 * highVolumeTrackLocation: 100,000 users within 15 minutes:
	 * assertTrue(TimeUnit.MINUTES.toSeconds(15) >=
	 * TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	 *
	 * highVolumeGetRewards: 100,000 users within 20 minutes:
	 * assertTrue(TimeUnit.MINUTES.toSeconds(20) >=
	 * TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));
	 */

	@Test
	public void highVolumeTrackLocation() {
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());

		// Setting up for 100,000 users
		InternalTestHelper.setInternalUserNumber(100000);
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService, new RewardCentral());

		List<User> allUsers = tourGuideService.getAllUsers();

		StopWatch stopWatch = new StopWatch();
		stopWatch.start();

		// List to store all CompletableFutures
		List<CompletableFuture<VisitedLocation>> futures = new ArrayList<>();

		for (User user : allUsers) {
			// Launch each user location tracking asynchronously and store the CompletableFuture
			CompletableFuture<VisitedLocation> future = tourGuideService.trackUserLocation(user);
			futures.add(future);
		}

		// Wait for all futures to complete
		CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
		allOf.join(); // Block until all futures are completed

		stopWatch.stop();
		tourGuideService.tracker.stopTracking();

		System.out.println("highVolumeTrackLocation: Time Elapsed: "
				+ TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()) + " seconds.");
		assertTrue(TimeUnit.MINUTES.toSeconds(15) >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));

		tourGuideService.getExecutor().shutdownNow();
	}

	@Test
	public void highVolumeGetRewards() {
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());

		// Users should be incremented up to 100,000, and test finishes within 20 minutes
		InternalTestHelper.setInternalUserNumber(10000);

		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService, new RewardCentral());

		StopWatch stopWatch = new StopWatch();
		stopWatch.start();

		Attraction attraction = gpsUtil.getAttractions().get(0);
		List<User> allUsers = tourGuideService.getAllUsers();
		allUsers.forEach(u -> u.addToVisitedLocations(new VisitedLocation(u.getUserId(), attraction, new Date())));

		// Collect all CompletableFutures from calculateRewards calls
		List<CompletableFuture<Void>> futures = allUsers.stream()
				.map(rewardsService::calculateRewards)
				.toList();

		// Wait for all calculateRewards calls to complete
		CompletableFuture<Void> completableFuture = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

		assertDoesNotThrow(() -> completableFuture.get(20, TimeUnit.MINUTES));

		allUsers.forEach(user -> assertFalse(user.getUserRewards().isEmpty()));

		stopWatch.stop();
		tourGuideService.tracker.stopTracking();

		System.out.println("highVolumeGetRewards: Time Elapsed: " + TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime())
				+ " seconds.");
		assertTrue(TimeUnit.MINUTES.toSeconds(20) >= TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()));

		rewardsService.getExecutor().shutdownNow();
	}
}
