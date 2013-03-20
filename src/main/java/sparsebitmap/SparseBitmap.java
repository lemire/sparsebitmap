/**
 * Copyright 2012 Daniel Lemire
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package sparsebitmap;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Comparator;
import java.util.Iterator;
import java.util.PriorityQueue;

// TODO: Auto-generated Javadoc
/**
 * The purpose of this class is to provide a compressed alternative to the Java
 * BitSet class that can scale to much larger bit ranges. It also offers good
 * processing performance while remaining simple.
 * 
 * @author Daniel Lemire
 */
public class SparseBitmap implements Iterable<Integer>, BitmapContainer,
		Cloneable, Externalizable {

	/**
	 * For expert use: add a literal bitmap word so that the resulting bitmap
	 * will cover off+1 words. This function does minimal checking: to input
	 * data in the bitmap, you might be better off with the set method.
	 * 
	 * @param wo
	 *            literal bitmap word to add
	 * @param off
	 *            position at (total size will be off+1)
	 */
	@Override
	public void add(int wo, int off) {
		fastadd(wo, off);
		// cardinality += Integer.bitCount(wo);
	}

	/**
	 * same as add but without updating the cardinality counter, strictly for
	 * internal use.
	 * 
	 * @param wo
	 *            the wo
	 * @param off
	 *            the off
	 */
	private void fastadd(int wo, int off) {
		this.buffer.add(off - this.sizeinwords);
		this.buffer.add(wo);
		this.sizeinwords = off + 1;
	}

	/**
	 * Checks whether two SparseBitmap have the same bit sets. Return true if
	 * so.
	 * 
	 * @param o
	 *            the o
	 * @return whether the two objects have the same set bits
	 */
	@Override
	public boolean equals(Object o) {
		if (o instanceof SparseBitmap) {
			return this.buffer.equals(((SparseBitmap) o).buffer);
		}
		return false;
	}

	/**
	 * Return a hash value for this object. Uses a Karp-Rabin hash function.
	 * 
	 * @return the hash value
	 */
	@Override
	public int hashCode() {
		return this.buffer.hashCode();
	}

	/**
	 * Convenience method: returns an array containing the set bits.
	 * 
	 * @return array corresponding to the position of the set bits.
	 */
	public int[] toArray() {
		IntIterator i = getIntIterator();
		final int cardinality = this.cardinality();
		int[] answer = new int[cardinality];
		for (int k = 0; k < cardinality; ++k)
			answer[k] = i.next();
		return answer;
	}

	/**
	 * A string describing the bitmap.
	 * 
	 * @return the string
	 */
	@Override
	public String toString() {
		StringBuffer answer = new StringBuffer();
		IntIterator i = getIntIterator();
		answer.append("{");
		if (i.hasNext())
			answer.append(i.next());
		while (i.hasNext()) {
			answer.append(",");
			answer.append(i.next());
		}
		answer.append("}");
		return answer.toString();
	}

	/**
	 * Convenience method: will construct a bitmap with the specified bit sets.
	 * Note that the list of integers should be sorted in increasing order.
	 * 
	 * @param k
	 *            the list of bits to set
	 * @return the corresponding SparseBitmap object.
	 */
	public static SparseBitmap bitmapOf(int... k) {
		SparseBitmap s = new SparseBitmap();
		for (int i : k) {
			s.set(i);
		}
		return s;
	}

	/**
	 * Set the bit at position i to true. The SparseBitmap will automatically
	 * expand. Note that you need to set the bits in sorted order (e.g., 1,2,5,6
	 * and not 6,4,1,2). If the bit cannot be set, an IllegalArgumentException
	 * is thrown.
	 * 
	 * @param i
	 *            the i
	 */
	public void set(int i) {
		int offset = i - this.sizeinwords * WORDSIZE;
		if (offset + WORDSIZE < 0) {
			throw new IllegalArgumentException("unsupported write back");
		} else if ((offset + WORDSIZE >= 0) && (offset < 0)) {
			final int before = this.buffer.get(this.buffer.size() - 1);
			this.buffer.set(this.buffer.size() - 1, before
					| (1 << (offset + WORDSIZE)));
			// if(before != buffer.get(buffer.size()-1)) ++ cardinality;
		} else {
			final int numberofemptywords = offset / WORDSIZE;
			offset -= numberofemptywords * WORDSIZE;
			fastadd(1 << offset, this.sizeinwords + numberofemptywords);
			// ++cardinality;
		}

	}

	/**
	 * Allow you to iterate over the set bits.
	 * 
	 * @return Iterator over the set bits
	 */
	@Override
	public Iterator<Integer> iterator() {
		final IntIterator under = this.getIntIterator();
		return new Iterator<Integer>() {
			@Override
			public boolean hasNext() {
				return under.hasNext();
			}

			@Override
			public Integer next() {
				return new Integer(under.next());
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException(
						"bitsets do not support remove");
			}

		};
	}

	/**
	 * Build a fast iterator over the set bits.
	 * 
	 * @return the iterator over the set bits
	 */
	public IntIterator getIntIterator() {
		return new IntIterator() {
			int wordindex;
			int i = 0;
			IntArray buf;
			int currentword;

			public IntIterator init(IntArray b) {
				this.buf = b;
				this.wordindex = this.buf.get(this.i);
				this.currentword = this.buf.get(this.i + 1);
				return this;
			}

			@Override
			public boolean hasNext() {
				return this.currentword != 0;
			}

			@Override
			public int next() {
				final int offset = Integer
						.numberOfTrailingZeros(this.currentword);
				this.currentword ^= 1 << offset;
				final int answer = this.wordindex * WORDSIZE + offset;
				if (this.currentword == 0) {
					this.i += 2;
					if (this.i < this.buf.size()) {
						this.currentword = this.buf.get(this.i + 1);
						this.wordindex += this.buf.get(this.i) + 1;
					}
				}
				return answer;
			}

		}.init(this.buffer);
	}

	/**
	 * Compute the bit-wise logical and with another bitmap.
	 * 
	 * @param o
	 *            another bitmap
	 * @return the result of the bit-wise logical and
	 */
	public SparseBitmap and(SparseBitmap o) {
		SparseBitmap a = new SparseBitmap();
		and2by2(a, this, o);
		return a;
	}

	/**
	 * Computes the bit-wise logical exclusive and of two bitmaps.
	 * 
	 * @param container
	 *            where the data will be stored
	 * @param bitmap1
	 *            the first bitmap
	 * @param bitmap2
	 *            the second bitmap
	 */
	public static void and2by2(BitmapContainer container, SparseBitmap bitmap1,
			SparseBitmap bitmap2) {
		int it1 = 0;
		int it2 = 0;
		int p1 = bitmap1.buffer.get(it1), p2 = bitmap2.buffer.get(it2);
		int buff;
		while (true) {
			if (p1 < p2) {
				if (it1 + 2 >= bitmap1.buffer.size())
					break;
				it1 += 2;
				p1 += bitmap1.buffer.get(it1) + 1;
			} else if (p1 > p2) {
				if (it2 + 2 >= bitmap2.buffer.size())
					break;
				it2 += 2;
				p2 += bitmap2.buffer.get(it2) + 1;
			} else {
				if ((buff = bitmap1.buffer.get(it1 + 1)
						& bitmap2.buffer.get(it2 + 1)) != 0) {
					container.add(buff, p1);
				}

				if ((it1 + 2 >= bitmap1.buffer.size())
						|| (it2 + 2 >= bitmap2.buffer.size()))
					break;

				it1 += 2;
				it2 += 2;
				p1 += bitmap1.buffer.get(it1) + 1;
				p2 += bitmap2.buffer.get(it2) + 1;
			}
		}
	}

	/**
	 * And.
	 * 
	 * @param bitmap
	 *            the bitmap
	 * @return the skippable iterator
	 */
	public static SkippableIterator and(final SkippableIterator... bitmap) {
		if (bitmap.length == 0)
			throw new RuntimeException("nothing to process");

		return new SkippableIterator() {

			int maxval;
			int wordval;
			boolean hasvalue = false;

			@Override
			public boolean hasValue() {
				return this.hasvalue;
			}

			public SkippableIterator init() {
				this.hasvalue = false;
				for (SkippableIterator i : bitmap)
					if (!i.hasValue())
						return this;
				this.maxval = bitmap[0].getCurrentWordOffset();
				for (int k = 1; k < bitmap.length; ++k)
					if (this.maxval < bitmap[k].getCurrentWordOffset())
						this.maxval = bitmap[k].getCurrentWordOffset();
				movetonext();
				return this;
			}

			public void movetonext() {
				this.hasvalue = false;
				boolean stable;
				do {
					stable = true;
					for (int k = 0; k < bitmap.length; ++k) {
						if (bitmap[k].getCurrentWordOffset() < this.maxval) {
							bitmap[k].advanceUntil(this.maxval);
							if (!bitmap[k].hasValue()) {
								return;
							}
							this.maxval = bitmap[k].getCurrentWordOffset();
							stable = false;
						}
					}
				} while (!stable);
				this.wordval = bitmap[0].getCurrentWord();
				for (int k = 1; k < bitmap.length; ++k) {
					this.wordval &= bitmap[k].getCurrentWord();
				}
				if (this.wordval == 0)
					advance();
				else {
					this.hasvalue = true;
				}
			}

			@Override
			public void advance() {
				for (SkippableIterator b : bitmap) {
					b.advanceUntil(this.maxval);
					if (!b.hasValue()) {
						this.hasvalue = false;
						return;
					}
					this.maxval = b.getCurrentWordOffset();
				}
				movetonext();
			}

			@Override
			public void advanceUntil(int min) {
				bitmap[bitmap.length - 1].advanceUntil(min);
				if (!bitmap[bitmap.length - 1].hasValue()) {
					this.hasvalue = false;
					return;
				}
				this.maxval = bitmap[bitmap.length - 1].getCurrentWordOffset();
				movetonext();
			}

			@Override
			public int getCurrentWord() {
				return this.wordval;
			}

			@Override
			public int getCurrentWordOffset() {
				return this.maxval;
			}

		}.init();
	}

	/**
	 * Fastand.
	 * 
	 * @param bitmap
	 *            the bitmap
	 * @return the skippable iterator
	 */
	public static SkippableIterator fastand(final SkippableIterator... bitmap) {
		if (bitmap.length == 0)
			throw new RuntimeException("nothing to process");

		return new SkippableIterator() {

			int maxval;
			int wordval;
			boolean hasvalue = false;
			int sbscardinality = 0;

			@Override
			public boolean hasValue() {
				return this.hasvalue;
			}

			public SkippableIterator init() {
				this.hasvalue = false;
				for (SkippableIterator i : bitmap)
					if (!i.hasValue())
						return this;
				this.maxval = bitmap[0].getCurrentWordOffset();
				this.sbscardinality = 1;

				for (int k = 1; k < bitmap.length; ++k) {
					if (this.maxval < bitmap[k].getCurrentWordOffset()) {
						this.maxval = bitmap[k].getCurrentWordOffset();
						this.sbscardinality = 1;
					} else
						this.sbscardinality += 1;
				}

				while (this.sbscardinality < bitmap.length) {
					for (int k = 0; k < bitmap.length; ++k)
						if (bitmap[k].getCurrentWordOffset() == this.maxval) {
							++this.sbscardinality;
							if (this.sbscardinality == bitmap.length)
								break;
						} else if (bitmap[k].getCurrentWordOffset() < this.maxval) {
							bitmap[k].advanceUntil(this.maxval);
							if (!bitmap[k].hasValue())
								return this;
							this.maxval = bitmap[k].getCurrentWordOffset();
							this.sbscardinality = 1;
						}
					this.wordval = bitmap[0].getCurrentWord();
					for (int k = 1; k < bitmap.length; ++k) {
						this.wordval &= bitmap[k].getCurrentWord();
					}
					if (this.wordval == 0)
						advance();
					else {
						this.hasvalue = true;
					}

				}
				return this;
			}

			public void movetonext() {
				while (this.sbscardinality < bitmap.length)
					for (int i = 0; i < bitmap.length; ++i) {
						bitmap[i].advanceUntil(this.maxval);
						if (!bitmap[i].hasValue()) {
							this.hasvalue = false;
							return;
						}
						if (bitmap[i].getCurrentWordOffset() > this.maxval) {
							this.maxval = bitmap[i].getCurrentWordOffset();
							this.sbscardinality = 1;
							break;
						}
						++this.sbscardinality;

					}
				this.wordval = bitmap[0].getCurrentWord();
				for (int k = 1; k < bitmap.length; ++k) {
					this.wordval &= bitmap[k].getCurrentWord();
				}
				if (this.wordval == 0)
					advance();
				else {
					this.hasvalue = true;
				}
			}

			@Override
			public void advance() {
				bitmap[0].advance();
				if (!bitmap[0].hasValue()) {
					this.hasvalue = false;
					return;
				}
				this.sbscardinality = 1;
				this.maxval = bitmap[0].getCurrentWordOffset();

				for (int k = 1; k < bitmap.length; ++k) {
					SkippableIterator b = bitmap[k];
					b.advanceUntil(this.maxval);
					if (!b.hasValue()) {
						this.hasvalue = false;
						return;
					}
					if (b.getCurrentWordOffset() > this.maxval) {
						this.maxval = b.getCurrentWordOffset();
						this.sbscardinality = 1;
					} else {
						++this.sbscardinality;
					}
				}
				movetonext();
			}

			@Override
			public void advanceUntil(int min) {
				throw new InternalError("not implemented");
			}

			@Override
			public int getCurrentWord() {
				return this.wordval;
			}

			@Override
			public int getCurrentWordOffset() {
				return this.maxval;
			}

		}.init();
	}

	/**
	 * Treeand.
	 * 
	 * @param bitmap
	 *            the bitmap
	 * @return the skippable iterator
	 */
	public static SkippableIterator treeand(SkippableIterator... bitmap) {
		if (bitmap.length == 0)
			throw new RuntimeException("nothing to process");
		if (bitmap.length == 1)
			return bitmap[0];
		if (bitmap.length == 2)
			return and2by2(bitmap[0], bitmap[1]);
		if ((bitmap.length & 1) == 0) {// even
			SkippableIterator[] si = new SkippableIterator[bitmap.length / 2];
			for (int i = 0; i < si.length; ++i)
				si[i] = and2by2(bitmap[2 * i], bitmap[2 * i + 1]);
			return treeand(si);
		}
		// odd
		SkippableIterator[] si = new SkippableIterator[bitmap.length / 2 + 1];
		for (int i = 0; i < si.length - 1; ++i)
			si[i] = and2by2(bitmap[2 * i], bitmap[2 * i + 1]);
		si[si.length - 1] = bitmap[bitmap.length - 1];
		return treeand(si);
	}

	/**
	 * Flatand.
	 * 
	 * @param bitmap
	 *            the bitmap
	 * @return the skippable iterator
	 */
	public static SkippableIterator flatand(SkippableIterator... bitmap) {
		if (bitmap.length == 0)
			throw new RuntimeException("nothing to process");
		SkippableIterator answer = bitmap[0];
		for (int k = 1; k < bitmap.length; ++k) {
			answer = and2by2(answer, bitmap[k]);
		}
		return answer;
	}

	/**
	 * Reverseflatand.
	 * 
	 * @param bitmap
	 *            the bitmap
	 * @return the skippable iterator
	 */
	public static SkippableIterator reverseflatand(SkippableIterator... bitmap) {
		if (bitmap.length == 0)
			throw new RuntimeException("nothing to process");
		SkippableIterator answer = bitmap[bitmap.length - 1];
		for (int k = bitmap.length - 1; k > 0; --k) {
			answer = and2by2(answer, bitmap[k]);
		}
		return answer;
	}

	/**
	 * Materialize.
	 * 
	 * @param i
	 *            the i
	 * @return the sparse bitmap
	 */
	public static SparseBitmap materialize(SkippableIterator i) {
		SparseBitmap answer = new SparseBitmap();
		while (i.hasValue()) {
			answer.add(i.getCurrentWord(), i.getCurrentWordOffset());
			i.advance();
		}
		return answer;
	}

	/**
	 * Cardinality.
	 * 
	 * @param i
	 *            the i
	 * @return the int
	 */
	public static int cardinality(SkippableIterator i) {
		int card = 0;
		while (i.hasValue()) {
			card += Integer.bitCount(i.getCurrentWord());
			i.advance();
		}
		return card;
	}

	/**
	 * And2by2.
	 * 
	 * @param bitmap1
	 *            the bitmap1
	 * @param bitmap2
	 *            the bitmap2
	 * @return the skippable iterator
	 */
	public static SkippableIterator and2by2(final SkippableIterator bitmap1,
			final SkippableIterator bitmap2) {
		return new SkippableIterator() {

			boolean hasvalue = false;
			int currentword = 0;

			@Override
			public boolean hasValue() {
				return this.hasvalue;
			}

			public SkippableIterator init() {
				movetonext();
				return this;
			}

			public void movetonext() {
				this.hasvalue = false;
				if (bitmap1.hasValue() && bitmap2.hasValue()) {
					while (true) {
						if (bitmap1.getCurrentWordOffset() < bitmap2
								.getCurrentWordOffset()) {
							bitmap1.advanceUntil(bitmap2.getCurrentWordOffset());
							if (!bitmap1.hasValue()) {
								return;
							}
						} else if (bitmap1.getCurrentWordOffset() > bitmap2
								.getCurrentWordOffset()) {
							bitmap2.advanceUntil(bitmap1.getCurrentWordOffset());
							if (!bitmap2.hasValue()) {
								return;
							}
						} else {
							this.currentword = bitmap1.getCurrentWord()
									& bitmap2.getCurrentWord();
							if (this.currentword != 0) {
								this.hasvalue = true;
								return;
							}
							bitmap1.advance();
							if (bitmap1.hasValue()) {
								bitmap2.advanceUntil(bitmap1
										.getCurrentWordOffset());
								if (bitmap2.hasValue()) {
									continue;
								}
							}
							return;
						}
					}
				}
			}

			@Override
			public void advance() {
				bitmap1.advance();
				if (bitmap1.hasValue()) {
					bitmap2.advanceUntil(bitmap1.getCurrentWordOffset());
					movetonext();
				} else
					this.hasvalue = false;
			}

			@Override
			public void advanceUntil(int min) {
				bitmap1.advanceUntil(min);
				if (bitmap1.hasValue()) {
					bitmap2.advanceUntil(bitmap1.getCurrentWordOffset());
					movetonext();
				} else
					this.hasvalue = false;
			}

			@Override
			public int getCurrentWord() {
				return this.currentword;
			}

			@Override
			public int getCurrentWordOffset() {
				return bitmap1.getCurrentWordOffset();// could be bitmap2, they
														// must be equal
			}

		}.init();
	}

	/**
	 * Computes the bit-wise logical or with another bitmap.
	 * 
	 * @param o
	 *            another bitmap
	 * @return the result of the bit-wise logical or
	 */
	public SparseBitmap or(SparseBitmap o) {
		SparseBitmap a = new SparseBitmap();
		or2by2(a, this, o);
		return a;
	}

	/**
	 * Computes the bit-wise logical or of two bitmaps.
	 * 
	 * @param container
	 *            where the data will be stored
	 * @param bitmap1
	 *            the first bitmap
	 * @param bitmap2
	 *            the second bitmap
	 */
	public static void or2by2(BitmapContainer container, SparseBitmap bitmap1,
			SparseBitmap bitmap2) {
		int it1 = 0;
		int it2 = 0;
		int p1 = bitmap1.buffer.get(it1);
		int p2 = bitmap2.buffer.get(it2);
		if ((it1 < bitmap1.buffer.size()) && (it2 < bitmap2.buffer.size()))
			while (true) {
				if (p1 < p2) {
					container.add(bitmap1.buffer.get(it1 + 1), p1);
					it1 += 2;
					if (it1 >= bitmap1.buffer.size())
						break;
					p1 += bitmap1.buffer.get(it1) + 1;
				} else if (p1 > p2) {
					container.add(bitmap2.buffer.get(it2 + 1), p2);
					it2 += 2;
					if (it2 >= bitmap2.buffer.size())
						break;
					p2 += bitmap2.buffer.get(it2) + 1;
				} else {
					container.add(
							bitmap1.buffer.get(it1 + 1)
									| bitmap2.buffer.get(it2 + 1), p1);
					it1 += 2;
					it2 += 2;
					if (it1 < bitmap1.buffer.size())
						p1 += bitmap1.buffer.get(it1) + 1;
					if (it2 < bitmap2.buffer.size())
						p2 += bitmap2.buffer.get(it2) + 1;
					if ((it1 >= bitmap1.buffer.size())
							|| (it2 >= bitmap2.buffer.size()))
						break;
				}
			}

		if (it1 < bitmap1.buffer.size()) {
			while (true) {
				container.add(bitmap1.buffer.get(it1 + 1), p1);
				it1 += 2;
				if (it1 == bitmap1.buffer.size())
					break;
				p1 += bitmap1.buffer.get(it1) + 1;
			}
		}
		if (it2 < bitmap2.buffer.size()) {
			while (true) {
				container.add(bitmap2.buffer.get(it2 + 1), p2);
				it2 += 2;
				if (it2 == bitmap2.buffer.size())
					break;
				p2 += bitmap2.buffer.get(it2) + 1;
			}
		}
		while (it2 < bitmap2.buffer.size()) {
			container.add(bitmap2.buffer.get(it2 + 1), p2);
			it2 += 2;
			p2 += bitmap2.buffer.get(it2) + 1;
		}
	}

	/**
	 * Computes the bit-wise logical exclusive or with another bitmap.
	 * 
	 * @param o
	 *            another bitmap
	 * @return the result of the bti-wise logical exclusive or
	 */
	public SparseBitmap xor(SparseBitmap o) {
		SparseBitmap a = new SparseBitmap();
		xor2by2(a, this, o);
		return a;
	}

	/**
	 * Computes the bit-wise logical exclusive or of two bitmaps.
	 * 
	 * @param container
	 *            where the data will be stored
	 * @param bitmap1
	 *            the first bitmap
	 * @param bitmap2
	 *            the second bitmap
	 */
	public static void xor2by2(BitmapContainer container, SparseBitmap bitmap1,
			SparseBitmap bitmap2) {
		int it1 = 0;
		int it2 = 0;
		int p1 = bitmap1.buffer.get(it1);
		int p2 = bitmap2.buffer.get(it2);
		if ((it1 < bitmap1.buffer.size()) && (it2 < bitmap2.buffer.size()))
			while (true) {
				if (p1 < p2) {
					container.add(bitmap1.buffer.get(it1 + 1), p1);
					it1 += 2;
					if (it1 >= bitmap1.buffer.size())
						break;
					p1 += bitmap1.buffer.get(it1) + 1;
				} else if (p1 > p2) {
					container.add(bitmap2.buffer.get(it2 + 1), p2);
					it2 += 2;
					if (it2 >= bitmap2.buffer.size())
						break;
					p2 += bitmap2.buffer.get(it2) + 1;
				} else {
					if (bitmap1.buffer.get(it1 + 1) != bitmap2.buffer
							.get(it2 + 1))
						container.add(bitmap1.buffer.get(it1 + 1)
								^ bitmap2.buffer.get(it2 + 1), p1);
					it1 += 2;
					it2 += 2;
					if (it1 < bitmap1.buffer.size())
						p1 += bitmap1.buffer.get(it1) + 1;
					if (it2 < bitmap2.buffer.size())
						p2 += bitmap2.buffer.get(it2) + 1;
					if ((it1 >= bitmap1.buffer.size())
							|| (it2 >= bitmap2.buffer.size()))
						break;
				}
			}

		if (it1 < bitmap1.buffer.size()) {
			while (true) {
				container.add(bitmap1.buffer.get(it1 + 1), p1);
				it1 += 2;
				if (it1 == bitmap1.buffer.size())
					break;
				p1 += bitmap1.buffer.get(it1) + 1;
			}
		}
		if (it2 < bitmap2.buffer.size()) {
			while (true) {
				container.add(bitmap2.buffer.get(it2 + 1), p2);
				it2 += 2;
				if (it2 == bitmap2.buffer.size())
					break;
				p2 += bitmap2.buffer.get(it2) + 1;
			}
		}
		while (it2 < bitmap2.buffer.size()) {
			System.out.println("==***= p1 =" + p1 + " p2 = " + p2);
			System.out.println("34%%  p2 = " + p2);
			container.add(bitmap2.buffer.get(it2 + 1), p2);
			it2 += 2;
			p2 += bitmap2.buffer.get(it2) + 1;
		}
	}

	/**
	 * Used with the priority queues used for aggregating several bitmaps.
	 */
	private static Comparator<SparseBitmap> smallfirst = new Comparator<SparseBitmap>() {
		@Override
		public int compare(SparseBitmap a, SparseBitmap b) {
			return a.sizeInBytes() - b.sizeInBytes();
		}
	};

	/**
	 * Computes the bit-wise and aggregate over several bitmaps.
	 * 
	 * @param bitmaps
	 *            the bitmaps to aggregate
	 * @return the resulting bitmap
	 */
	public static SparseBitmap and(SparseBitmap... bitmaps) {
		if (bitmaps.length == 0)
			return new SparseBitmap();
		else if (bitmaps.length == 1)
			return bitmaps[0];
		else if (bitmaps.length == 2)
			return bitmaps[0].and(bitmaps[1]);
		// for "and" a priority queue is not needed, but
		// overhead ought to be small
		PriorityQueue<SparseBitmap> pq = new PriorityQueue<SparseBitmap>(
				bitmaps.length, smallfirst);
		for (SparseBitmap x : bitmaps)
			pq.add(x);
		while (pq.size() > 1) {
			SparseBitmap x1 = pq.poll();
			SparseBitmap x2 = pq.poll();
			pq.add(x1.and(x2));
		}
		return pq.poll();
	}

	public static SkippableIterator fastand(SparseBitmap... bitmaps) {
		SkippableIterator[] si = new SkippableIterator[bitmaps.length];
		for (int k = 0; k < bitmaps.length; ++k)
			si[k] = bitmaps[k].getSkippableIterator();
		return fastand(si);
	}

	/**
	 * Computes the bit-wise or aggregate over several bitmaps.
	 * 
	 * @param bitmaps
	 *            the bitmaps to aggregate
	 * @return the resulting bitmap
	 */
	public static SparseBitmap or(SparseBitmap... bitmaps) {
		if (bitmaps.length == 0)
			return new SparseBitmap();
		else if (bitmaps.length == 1)
			return bitmaps[0];
		else if (bitmaps.length == 2)
			return bitmaps[0].or(bitmaps[1]);
		PriorityQueue<SparseBitmap> pq = new PriorityQueue<SparseBitmap>(
				bitmaps.length, smallfirst);
		for (SparseBitmap x : bitmaps) {
			pq.add(x);
		}
		while (pq.size() > 1) {
			SparseBitmap x1 = pq.poll();
			SparseBitmap x2 = pq.poll();
			pq.add(x1.or(x2));
		}
		return pq.poll();
	}

	/**
	 * Computes the bit-wise exclusive or aggregate over several bitmaps.
	 * 
	 * @param bitmaps
	 *            the bitmaps to aggregate
	 * @return the resulting bitmap
	 */
	public static SparseBitmap xor(SparseBitmap... bitmaps) {
		if (bitmaps.length == 0)
			return new SparseBitmap();
		else if (bitmaps.length == 1)
			return bitmaps[0];
		else if (bitmaps.length == 2)
			return bitmaps[0].or(bitmaps[1]);
		PriorityQueue<SparseBitmap> pq = new PriorityQueue<SparseBitmap>(
				bitmaps.length, smallfirst);
		for (SparseBitmap x : bitmaps)
			pq.add(x);
		while (pq.size() > 1) {
			SparseBitmap x1 = pq.poll();
			SparseBitmap x2 = pq.poll();
			pq.add(x1.xor(x2));
		}
		return pq.poll();
	}

	/**
	 * Return how much space is used by data (in bytes).
	 * 
	 * 
	 * @return the
	 */
	public int sizeInBytes() {
		return this.buffer.size() * 4;
	}

	/**
	 * Minimizes the memory usage by copying over the data on a smaller array.
	 * 
	 * @return new memory usage for the internal array (in bytes)
	 */
	public int trim() {
		return this.buffer.trim();
	}

	/**
	 * Constructs a SparseBitmap object with default parameters.
	 */
	public SparseBitmap() {
		this.buffer = new IntArray();
	}

	/**
	 * Constructs a SparseBitmap object.
	 * 
	 * @param expectedstoragesize
	 *            this parameter corresponds to the initial memory allocation
	 */
	public SparseBitmap(int expectedstoragesize) {
		this.buffer = new IntArray(expectedstoragesize);
	}

	/**
	 * Gets the skippable iterator.
	 * 
	 * @return the skippable iterator
	 */
	public SkippableIterator getSkippableIterator() {
		return new SkippableIterator() {
			int pos = 0;
			int p = 0;

			public SkippableIterator init() {
				this.p = SparseBitmap.this.buffer.get(0);
				return this;
			}

			@Override
			public void advance() {
				this.pos += 2;
				if (this.pos < SparseBitmap.this.buffer.size())
					this.p += SparseBitmap.this.buffer.get(this.pos) + 1;
			}

			@Override
			public void advanceUntil(int min) {
				advance();
				while (hasValue() && (getCurrentWordOffset() < min)) {
					advance();
				}
			}

			@Override
			public int getCurrentWord() {
				return SparseBitmap.this.buffer.get(this.pos + 1);
			}

			@Override
			public int getCurrentWordOffset() {
				return this.p;
			}

			@Override
			public boolean hasValue() {
				return this.pos < SparseBitmap.this.buffer.size();
			}
		}.init();

	}

	/**
	 * Synchronize two iterators
	 * 
	 * @param o1
	 *            the first iterator
	 * @param o2
	 *            the second iterator
	 * @return true, if successful
	 */
	public static boolean match(SkippableIterator o1, SkippableIterator o2) {
		while (o1.getCurrentWordOffset() != o2.getCurrentWordOffset()) {
			if (o1.getCurrentWordOffset() < o2.getCurrentWordOffset()) {
				o1.advanceUntil(o2.getCurrentWordOffset());
				if (!o1.hasValue())
					return false;
			}
			if (o1.getCurrentWordOffset() > o2.getCurrentWordOffset()) {
				o2.advanceUntil(o1.getCurrentWordOffset());
				if (!o2.hasValue())
					return false;
			}
		}
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.io.Externalizable#readExternal(java.io.ObjectInput)
	 */
	@Override
	public void readExternal(ObjectInput in) throws IOException,
			ClassNotFoundException {
		deserialize(in);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.io.Externalizable#writeExternal(java.io.ObjectOutput)
	 */
	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		serialize(out);
	}

	/**
	 * Serialize.
	 * 
	 * @param out
	 *            the stream
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	public void serialize(DataOutput out) throws IOException {
		this.buffer.serialize(out);
	}

	/**
	 * Deserialize.
	 * 
	 * @param in
	 *            the stream
	 * @throws IOException
	 *             Signals that an I/O exception has occurred.
	 */
	public void deserialize(DataInput in) throws IOException {
		this.buffer.deserialize(in);
		for (int k = 0; k < this.buffer.size(); k += 2) {
			this.sizeinwords += this.buffer.get(k) + 1;
			// cardinality += Integer.bitCount(this.buffer.get(k+1));
		}
	}

	/**
	 * Compute the cardinality.
	 * 
	 * @return the cardinality
	 */
	public int cardinality() {
		int answer = 0;
		for (int k = 0; k < this.buffer.size(); k += 2) {
			answer += Integer.bitCount(this.buffer.get(k + 1));
		}
		return answer;
	}

	/**
	 * Reinitialize this bitmap.
	 */
	public void clear() {
		this.buffer.clear();
		this.sizeinwords = 0;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#clone()
	 */
	@Override
	public Object clone() throws java.lang.CloneNotSupportedException {
		SparseBitmap b = (SparseBitmap) super.clone();
		b.buffer = this.buffer.clone();
		b.sizeinwords = this.sizeinwords;
		return b;
	}

	/**
	 * sizeinwords*32 is the the number of bits represented by this bitmap.
	 */
	public int sizeinwords;

	/**
	 * buffer is where the data is store. The format is 32-bit for the offset,
	 * 32-bit for a literal bitmap
	 */
	public IntArray buffer;

	/**
	 * We have a 32-bit implementation (ints are 32-bit in Java).
	 */
	public static final int WORDSIZE = 32;

}
