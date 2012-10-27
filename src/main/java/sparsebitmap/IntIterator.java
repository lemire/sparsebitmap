package sparsebitmap;

public interface IntIterator {
  
  /**
   * Is there more?
   *
   * @return true, if there is more, false otherwise
   */
  public boolean hasNext();

  /**
   * Return the next integer
   *
   * @return the integer
   */
  public int next();

}
