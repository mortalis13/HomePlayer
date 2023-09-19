package org.mortalis.homeplayer.actions;

@FunctionalInterface
public interface SingleAction<T> {
  public void execute(T arg);
}
