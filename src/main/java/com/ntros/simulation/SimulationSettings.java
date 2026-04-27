package com.ntros.simulation;

public record SimulationSettings(
    int generators, int placers, int processors, int pricers, boolean canStore) {}
