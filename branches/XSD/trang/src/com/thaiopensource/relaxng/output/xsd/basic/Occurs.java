package com.thaiopensource.relaxng.output.xsd.basic;

public class Occurs {
  private final int min;
  private final int max;
  static public final int UNBOUNDED = Integer.MAX_VALUE;

  public Occurs(int min, int max) {
    this.min = min;
    this.max = max;
  }

  public int getMin() {
    return min;
  }

  public int getMax() {
    return max;
  }

  static public Occurs add(Occurs occ1, Occurs occ2) {
    return new Occurs(occ1.min + occ2.min,
                      occ1.max == UNBOUNDED || occ2.max == UNBOUNDED
                      ? UNBOUNDED
                      : occ1.max + occ2.max);
  }
}
