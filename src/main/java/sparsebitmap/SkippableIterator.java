package sparsebitmap;

public interface SkippableIterator {

	public boolean hasValue();

	public void advance();

	/**
	 * Advance, and keep advancing as long as the word offset is smaller than
	 * min
	 * 
	 * @param min
	 */
	public void advanceUntil(int min);

	/**
	 * have a look at the current word, do not advance
	 * 
	 * @return
	 */
	public int getCurrentWord();

	/**
	 * where is the word we are looking at?
	 * 
	 * @return
	 */
	public int getCurrentWordOffset();

}
