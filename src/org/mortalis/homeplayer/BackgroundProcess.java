package org.mortalis.homeplayer;

import android.os.Handler;

import java.util.concurrent.Executor;

import static org.mortalis.homeplayer.Fun.log;


public abstract class BackgroundProcess<T> {
  
  public interface Result<K> {
    void run(K result);
  }
  
  protected final Executor executor;
  protected final Handler handler;
  protected boolean running;
  
  protected Result<T> onFinished;
  
  public BackgroundProcess(Executor executor, Handler handler) {
    this.executor = executor;
    this.handler = handler;
    this.onFinished = result -> {};
  }
  
  protected abstract void run();
  
  public void execute() {
    executor.execute(this::run);
  }
  
  public void execute(Result<T> onFinished) {
    this.onFinished = onFinished;
    this.execute();
  }
  
  public void cancel() {
    running = false;
  }
  
  public boolean isRunning() {
    return running;
  }
  
}
