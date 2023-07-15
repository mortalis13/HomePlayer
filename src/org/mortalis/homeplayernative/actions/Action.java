package org.mortalis.homeplayernative.actions;

@FunctionalInterface
public interface Action<T> {
  public void execute(T arg);
}
