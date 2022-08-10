package org.mortalis.homeplayer.actions;

@FunctionalInterface
public interface Action<T> {
  public void execute(T arg);
}
