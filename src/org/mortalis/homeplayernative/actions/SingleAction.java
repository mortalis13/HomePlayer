package org.mortalis.homeplayernative.actions;

@FunctionalInterface
public interface SingleAction<T> {
  public void execute(T arg);
}
