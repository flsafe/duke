
package no.priv.garshol.duke.matchers;

import no.priv.garshol.duke.Record;

/**
 * Convenience implementation with dummy methods, since most
 * implementations will only implement matches().
 */
public abstract class AbstractMatchListener implements MatchListener {

  public void startRecord(Record r) {
  }

  public void batchReady(int size) {
  }
  
  public void batchDone() {
  }
  
  public void matchesPerhaps(Record r1, Record r2, double confidence) {
  }

  public void noMatchFor(Record record) {
  }
  
  public void endRecord() {
  }

  public void endProcessing() {
  }
  
}