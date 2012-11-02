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

import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.PriorityQueue;



/**
 * The purpose of this class is to provide a compressed alternative to the
 * Java BitSet class that can scale to much larger bit ranges. It also
 * offers good processing performance while remaining simple.
 * 
 * @author Daniel Lemire
 */
public class SparseBitmap implements Iterable<Integer>, BitmapContainer, Cloneable {

 
  /**
   * For expert use: add a literal bitmap word so that
   * the resulting bitmap will cover off+1 words. This
   * function does minimal checking: to input data in the
   * bitmap, you might be better off with the set method.
   * 
   * @param wo
   *          literal bitmap word to add
   * @param off
   *          position at (total size will be off+1)
   */
  public void add(int wo, int off) {
    if(wordusage+2 > buffer.length)
      buffer = Arrays.copyOf(buffer, buffer.length * 2);
    buffer[wordusage++] = off - sizeinwords;
    buffer[wordusage++] = wo;
    sizeinwords = off + 1;
    cardinality += Integer.bitCount(wo);
  }
  
  /**
   *  same as add but without updating the cardinality counter,
   *  strictly for internal use.
   *  
   * @param wo
   * @param off
   */
  private void fastadd(int wo, int off) {
    if(wordusage+2 > buffer.length)
      buffer = Arrays.copyOf(buffer, buffer.length * 2);
    buffer[wordusage++] = off - sizeinwords;
    buffer[wordusage++] = wo;
    sizeinwords = off + 1;
  }
  
  /**
   * Checks whether two SparseBitmap have the same bit sets. Return true
   * if so.
   * 
   * @return whether the two objects have the same set bits
   */
  @Override
  public boolean equals(Object o) {
    if (o instanceof SparseBitmap) {
      for (int k = 0; k < this.wordusage; ++k) {
        if (this.buffer[k] != ((SparseBitmap) o).buffer[k])
          return false;
      }
      return true;
    }
    return false;
  }

  /**
   * Return a hash value for this object. Uses a Karp-Rabin hash function.
   * @return the hash value
   */
  @Override
  public int hashCode() {
    int buf = 0;
    for (int k = 0; k < this.wordusage; ++k )
      buf = 31 * buf +  this.buffer[k];
    return buf;
  }
  
  /**
   * Convenience method: returns an array containing the set
   * bits.
   * @return array corresponding to the position of the set bits.
   */
  public int[] toArray() {
    IntIterator i = getIntIterator();
    int[] answer = new int[cardinality];
    for(int k = 0; k < cardinality; ++k)
      answer[k] = i.next();
    return answer;
  }

  /**
   * Convenience method: will construct a bitmap with the 
   * specified bit sets. Note that the list of integers should
   * be sorted in increasing order.
   * @param k the list of bits to set
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
   * Set the bit at position i to true. The SparseBitmap will
   * automatically expand. Note that you need to set the bits
   * in sorted order (e.g., 1,2,5,6 and not 6,4,1,2). If the
   * bit cannot be set, an IllegalArgumentException is thrown. 
   * @param i
   */
  public void set(int i) {
    int offset = i - sizeinwords * WORDSIZE;
    if (offset + WORDSIZE < 0) {
      throw new IllegalArgumentException("unsupported write back");
    } else if ((offset + WORDSIZE >= 0) && (offset < 0)) {
      final int before = buffer[wordusage - 1];
      buffer[wordusage - 1] |= 1 << (offset + WORDSIZE);
      if(before != buffer[wordusage - 1]) ++ cardinality;
    } else {
      final int numberofemptywords = offset / WORDSIZE;
      offset -= numberofemptywords * WORDSIZE;
      fastadd(1 << offset,sizeinwords+numberofemptywords);
      ++cardinality;
    }

  }

  
  /**
   * Allow you to iterate over the set bits
   * 
   * @return Iterator over the set bits 
   */
  public Iterator<Integer> iterator() {
    final IntIterator under = this.getIntIterator();
    return new Iterator<Integer>() {
      public boolean hasNext() {
        return under.hasNext();
      }

      public Integer next() {
        return new Integer(under.next());
      }

      public void remove() {
        throw new UnsupportedOperationException("bitsets do not support remove");
      }

    };
  }

  /**
   * Build a fast iterator over the set bits
   * 
   * @return the iterator over the set bits
   */
  public IntIterator getIntIterator() {
    return new IntIterator() {
      int wordindex ;
      int i = 0;
      int[] buf;
      int max ;
      int currentword;

      public IntIterator init(int[] b,int m) {
        buf = b;
        max = m;
        wordindex = buf[i];
        currentword = buf[i+1];
        return this;
      }

      public boolean hasNext() {
        return currentword != 0;
      }
      public int next() {
        final int offset = Integer.numberOfTrailingZeros(currentword);
        currentword ^= 1 << offset;
        final int answer = wordindex * WORDSIZE + offset;
        if (currentword == 0) {
          i+= 2;
          if(i<max) {
            currentword = buf[i+1];
            wordindex += buf[i] + 1;
          }
        }
        return answer;
      }

    }.init(buffer,this.wordusage);
  }
  
  
  /**
   * Compute the bit-wise logical and with another bitmap.
   * 
   * @param o another bitmap
   * @return the result of the bit-wise logical and
   */
  public SparseBitmap and(SparseBitmap o) {
    SparseBitmap a = new SparseBitmap();
    and2by2(a,this,o);
    return a;
  }

  /**
   * Computes the bit-wise logical exclusive and of two bitmaps.
   * 
   * @param container where the data will be stored
   * @param bitmap1 the first bitmap
   * @param bitmap2 the second bitmap
   */
  public static void and2by2(BitmapContainer container, SparseBitmap bitmap1, SparseBitmap bitmap2) {
    int it1 = 0;
    int it2 = 0;
    int p1 = bitmap1.buffer[it1], p2 = bitmap2.buffer[it2];
    int buff;
    while (true) {
      if (p1 < p2) {
        if (it1 + 2 >= bitmap1.wordusage)
          break;
        it1 += 2;
        p1 += bitmap1.buffer[it1] + 1;
      } else if (p1 > p2) {
        if (it2 + 2 >= bitmap2.wordusage)
          break;
        it2 += 2;
        p2 += bitmap2.buffer[it2] + 1;
      } else {
        if ((buff = bitmap1.buffer[it1+1] & bitmap2.buffer[it2+1]) != 0) {
          container.add(buff, p1);
        }

        if ((it1 + 2 >= bitmap1.wordusage) || (it2 + 2 >= bitmap2.wordusage))
          break;

        it1 += 2;
        it2 += 2;
        p1 += bitmap1.buffer[it1] + 1;
        p2 += bitmap2.buffer[it2] + 1;
      }
    }
  }

  /**
   * Computes the bit-wise logical or with another bitmap.
   * 
   * @param o another bitmap
   * @return the result of the bit-wise logical or
   */
  public SparseBitmap or(SparseBitmap o) {
    SparseBitmap a = new SparseBitmap();
    or2by2(a,this,o);
    return a;
  }

  /**
   * Computes the bit-wise logical or of two bitmaps.
   * 
   * @param container where the data will be stored
   * @param bitmap1 the first bitmap
   * @param bitmap2 the second bitmap
   */
  public static void or2by2(BitmapContainer container, SparseBitmap bitmap1,
    SparseBitmap bitmap2) {
    int it1 = 0;
    int it2 = 0;
    int p1 = bitmap1.buffer[it1];
    int p2 = bitmap2.buffer[it2];
    if ((it1 < bitmap1.wordusage) && (it2 < bitmap2.wordusage))
      while (true) {
        if (p1 < p2) {
          container.add(bitmap1.buffer[it1 + 1], p1);
          it1 += 2;
          if (it1 >= bitmap1.wordusage)
            break;
          p1 += bitmap1.buffer[it1] + 1;
        } else if (p1 > p2) {
          container.add(bitmap2.buffer[it2 + 1], p2);
          it2 += 2;
          if (it2 >= bitmap2.wordusage)
            break;
          p2 += bitmap2.buffer[it2] + 1;
        } else {
          container.add(bitmap1.buffer[it1 + 1] | bitmap2.buffer[it2 + 1], p1);
          it1 += 2;
          it2 += 2;
          if (it1 < bitmap1.wordusage)
            p1 += bitmap1.buffer[it1] + 1;
          if (it2 < bitmap2.wordusage)
            p2 += bitmap2.buffer[it2] + 1;
          if ((it1 >= bitmap1.wordusage) || (it2 >= bitmap2.wordusage))
            break;
        }
      }

    if (it1 < bitmap1.wordusage) {
      while (true) {
        container.add(bitmap1.buffer[it1 + 1], p1);
        it1 += 2;
        if (it1 == bitmap1.wordusage)
          break;
        p1 += bitmap1.buffer[it1] + 1;
      }
    }
    if (it2 < bitmap2.wordusage) {
      while (true) {
        container.add(bitmap2.buffer[it2 + 1], p2);
        it2 += 2;
        if (it2 == bitmap2.wordusage)
          break;
        p2 += bitmap2.buffer[it2] + 1;
      }
    }
    while (it2 < bitmap2.wordusage) {
      container.add(bitmap2.buffer[it2 + 1], p2);
      it2 += 2;
      p2 += bitmap2.buffer[it2] + 1;
    }
  }

  /**
   * Computes the bit-wise logical exclusive or with another bitmap.
   * 
   * @param o another bitmap
   * @return the result of the bti-wise logical exclusive or
   */
  public SparseBitmap xor(SparseBitmap o) {
    SparseBitmap a = new SparseBitmap();
    xor2by2(a,this,o);
    return a;
  }
  
  /**
   * Computes the bit-wise logical exclusive or of two bitmaps.
   * 
   * @param container where the data will be stored
   * @param bitmap1 the first bitmap
   * @param bitmap2 the second bitmap
   */
  public static void xor2by2(BitmapContainer container, SparseBitmap bitmap1,
    SparseBitmap bitmap2) {
    int it1 = 0;
    int it2 = 0;
    int p1 = bitmap1.buffer[it1];
    int p2 = bitmap2.buffer[it2];
    if ((it1 < bitmap1.wordusage) && (it2 < bitmap2.wordusage))
      while (true) {
        if (p1 < p2) {
          container.add(bitmap1.buffer[it1 + 1], p1);
          it1 += 2;
          if (it1 >= bitmap1.wordusage)
            break;
          p1 += bitmap1.buffer[it1] + 1;
        } else if (p1 > p2) {
          container.add(bitmap2.buffer[it2 + 1], p2);
          it2 += 2;
          if (it2 >= bitmap2.wordusage)
            break;
          p2 += bitmap2.buffer[it2] + 1;
        } else {
          if(bitmap1.buffer[it1+1] != bitmap2.buffer[it2+1])
            container.add(bitmap1.buffer[it1 + 1] ^ bitmap2.buffer[it2 + 1], p1);
          it1 += 2;
          it2 += 2;
          if (it1 < bitmap1.wordusage)
            p1 += bitmap1.buffer[it1] + 1;
          if (it2 < bitmap2.wordusage)
            p2 += bitmap2.buffer[it2] + 1;
          if ((it1 >= bitmap1.wordusage) || (it2 >= bitmap2.wordusage))
            break;
        }
      }

    if (it1 < bitmap1.wordusage) {
      while (true) {
        container.add(bitmap1.buffer[it1 + 1], p1);
        it1 += 2;
        if (it1 == bitmap1.wordusage)
          break;
        p1 += bitmap1.buffer[it1] + 1;
      }
    }
    if (it2 < bitmap2.wordusage) {
      while (true) {
        container.add(bitmap2.buffer[it2 + 1], p2);
        it2 += 2;
        if (it2 == bitmap2.wordusage)
          break;
        p2 += bitmap2.buffer[it2] + 1;
      }
    }
    while (it2 < bitmap2.wordusage) {
      System.out.println("==***= p1 =" + p1 + " p2 = " + p2);
      System.out.println("34%%  p2 = " + p2);
      container.add(bitmap2.buffer[it2 + 1], p2);
      it2 += 2;
      p2 += bitmap2.buffer[it2] + 1;
    }
  }
  
  /**
   * Used with the priority queues used for aggregating several bitmaps.
   */
  private static Comparator<SparseBitmap> smallfirst = new Comparator<SparseBitmap>() {   
    public int compare(SparseBitmap a, SparseBitmap b) {
      return a.sizeInBytes() - b.sizeInBytes();
    }
  };
  
  /**
   * Computes the bit-wise and aggregate over several bitmaps.
   * @param bitmaps the bitmaps to aggregate
   * @return the resulting bitmap
   */
  public static SparseBitmap and(SparseBitmap...bitmaps) {
    if(bitmaps.length == 0) 
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
      pq.add(  x1.and(x2));
    }
    return pq.poll();
  }
  /**
   * Computes the bit-wise or aggregate over several bitmaps.
   * @param bitmaps the bitmaps to aggregate
   * @return the resulting bitmap
   */  
  public static SparseBitmap or(SparseBitmap...bitmaps) {
    if(bitmaps.length == 0) 
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
      pq.add( x1.or(x2) );
    }
    return pq.poll();
  }
  /**
   * Computes the bit-wise exclusive or aggregate over several bitmaps.
   * @param bitmaps the bitmaps to aggregate
   * @return the resulting bitmap
   */
  public static SparseBitmap xor(SparseBitmap...bitmaps) {
    if(bitmaps.length == 0) 
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
      pq.add( x1.xor(x2));
    }
    return pq.poll();
  }
  
    
  /**
   * Return how much space is used by data  (in bytes).
   * 
   * 
   * @return the 
   */
  public int sizeInBytes() {
    return wordusage * 4;
  }
  
  /**
   * Minimizes the memory usage by copying over the data on 
   * a smaller array.
   * 
   * @return new memory usage for the internal array (in bytes)
   */
  public int compact() {
    if(wordusage>=2)
      buffer = Arrays.copyOf(buffer, wordusage);
    return buffer.length * 4;
  }
  
  /**
   * Constructs a SparseBitmap object with default parameters.
   */
  public SparseBitmap() {
    this(MINSTORAGEUSAGE);
  }
  
  /**
   * Constructs a SparseBitmap object.
   * 
   * @param expectedstoragesize this parameter corresponds to the initial memory allocation
   */
  public SparseBitmap(int expectedstoragesize) {
    buffer = new int[expectedstoragesize];
  }


  @Override
  public Object clone() throws java.lang.CloneNotSupportedException {
    SparseBitmap b = (SparseBitmap) super.clone();
    b.buffer = Arrays.copyOf(this.buffer, this.buffer.length);
    b.wordusage = this.wordusage;
    b.sizeinwords = this.sizeinwords;
    b.cardinality = this.cardinality;
    return b;
  }
  
  /**
   * sizeinwords*32 is the the number of bits represented by this
   * bitmap.
   */
  public int sizeinwords;
  /**
   * Number of bits set to true.
   */
  public int cardinality;
  /**
   * MINSTORAGEUSAGE determines how big the initial array is, by default.
   */
  public final static int MINSTORAGEUSAGE = 32;
  
  /**
   * buffer is where the data is store. The format is 
   * 32-bit for the offset, 32-bit for a literal bitmap
   */
  public int[] buffer;
  /**
   * How many words in buffer do we actually use?
   */
  public int wordusage = 0;
  /**
   * We have a 32-bit implementation (ints are 32-bit in Java).
   */
  public static final int WORDSIZE = 32;

}

