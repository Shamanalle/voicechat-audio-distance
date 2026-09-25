package com.kasper.vcdistance;

/**
 * Where the listener is, acoustically: how echoey the space around them is, whether their head is
 * under water, and the weather over them. Written by the client tick, read by the audio threads.
 */
public final class ListenerEnvironment {

    /** How much of the way to a new room estimate the echo moves per update (every half second). */
    private static final double ROOM_GLIDE = 0.5;

    private volatile RoomEstimate room = RoomEstimate.OPEN;
    private volatile boolean underWater;
    private volatile EnvironmentEffects.Weather weather = EnvironmentEffects.Weather.CLEAR;
    /** The listener's head in world coordinates, for directions to doorways; NaN when unknown. */
    private volatile double[] position = {Double.NaN, Double.NaN, Double.NaN};

    public RoomEstimate room() {
        return room;
    }

    public boolean isUnderWater() {
        return underWater;
    }

    public EnvironmentEffects.Weather weather() {
        return weather;
    }

    /** A new room measurement; the echo glides towards it. */
    public void updateRoom(RoomEstimate measured) {
        room = room.towards(measured, ROOM_GLIDE);
    }

    public void update(boolean underWater, EnvironmentEffects.Weather weather) {
        this.underWater = underWater;
        this.weather = weather == null ? EnvironmentEffects.Weather.CLEAR : weather;
    }

    public double[] position() {
        return position;
    }

    public void setPosition(double x, double y, double z) {
        position = new double[]{x, y, z};
    }

    public void reset() {
        position = new double[]{Double.NaN, Double.NaN, Double.NaN};
        room = RoomEstimate.OPEN;
        underWater = false;
        weather = EnvironmentEffects.Weather.CLEAR;
    }

    /** The stronger of two weathers: a thunderstorm at either end covers the voice. */
    public static EnvironmentEffects.Weather worse(EnvironmentEffects.Weather a, EnvironmentEffects.Weather b) {
        if (a == null) {
            return b == null ? EnvironmentEffects.Weather.CLEAR : b;
        }
        if (b == null) {
            return a;
        }
        return a.ordinal() >= b.ordinal() ? a : b;
    }
}
