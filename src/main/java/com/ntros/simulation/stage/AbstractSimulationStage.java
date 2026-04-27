package com.ntros.simulation.stage;

import com.ntros.simulation.SimulationContext;

public abstract class AbstractSimulationStage implements Stage {

  protected final SimulationContext context;

  protected AbstractSimulationStage(SimulationContext context) {
    this.context = context;
  }

  public SimulationContext getContext() {
    return context;
  }
}
