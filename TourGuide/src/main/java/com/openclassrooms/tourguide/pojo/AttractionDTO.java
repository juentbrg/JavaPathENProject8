package com.openclassrooms.tourguide.pojo;

import gpsUtil.location.Location;

import java.util.Objects;

public class AttractionDTO {
    private String name;
    private Location attractionLocation;
    private Location userLocation;
    private Double distance;
    private int rewardPoint;

    public AttractionDTO(String attractionName, Location attractionLocation, Location userLocation, double distance, int attractionRewardPoints) {
        this.name = attractionName;
        this.attractionLocation = attractionLocation;
        this.userLocation = userLocation;
        this.distance = distance;
        this.rewardPoint = attractionRewardPoints;
    }

    public Location getAttractionLocation() {
        return attractionLocation;
    }

    public void setAttractionLocation(Location attractionLocation) {
        this.attractionLocation = attractionLocation;
    }

    public Double getDistance() {
        return distance;
    }

    public void setDistance(Double distance) {
        this.distance = distance;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getRewardPoint() {
        return rewardPoint;
    }

    public void setRewardPoint(int rewardPoint) {
        this.rewardPoint = rewardPoint;
    }

    public Location getUserLocation() {
        return userLocation;
    }

    public void setUserLocation(Location userLocation) {
        this.userLocation = userLocation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AttractionDTO that = (AttractionDTO) o;
        return rewardPoint == that.rewardPoint && Objects.equals(name, that.name) && Objects.equals(attractionLocation, that.attractionLocation) && Objects.equals(userLocation, that.userLocation) && Objects.equals(distance, that.distance);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, attractionLocation, userLocation, distance, rewardPoint);
    }

    @Override
    public String toString() {
        return "AttractionDTO{" +
                "attractionLocation=" + attractionLocation +
                ", name='" + name + '\'' +
                ", userLocation=" + userLocation +
                ", distance=" + distance +
                ", rewardPoint=" + rewardPoint +
                '}';
    }
}
