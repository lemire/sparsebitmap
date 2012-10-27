package sparsebitmap;

public interface BitmapContainer {
  /**
   * 
   * @param wo
   *          dirty word to add
   * @param off
   *          position at (total size will be off+1)
   */
  public void add(int wo, int off);

  public int sizeInBytes();
}
