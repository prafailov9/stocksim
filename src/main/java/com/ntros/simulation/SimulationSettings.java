package com.ntros.simulation;

public record SimulationSettings(
    int seeders, int placers, int settlers, int pricers, boolean canStore) {}
