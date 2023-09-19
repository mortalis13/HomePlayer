package org.mortalis.homeplayer.actions;

@FunctionalInterface
public interface DoubleAction<T> {
  public void execute(T arg1, T arg2);
}
