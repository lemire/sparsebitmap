package sparsebitmap;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.PriorityQueue;




public class SparseBitmap implements Iterable<Integer>, BitmapContainer {

 
  /**
   * 
   * @param wo
   *          dirty word to add
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
  
  // same as add but without updating the cardinality counter
  private void fastadd(int wo, int off) {
    if(wordusage+2 > buffer.length)
      buffer = Arrays.copyOf(buffer, buffer.length * 2);
    buffer[wordusage++] = off - sizeinwords;
    buffer[wordusage++] = wo;
    sizeinwords = off + 1;
  }
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

  @Override
  public int hashCode() {
    int buf = 0;
    for (int k = 0; k < this.wordusage; ++k )
      buf = 31 * buf +  this.buffer[k];
    return buf;
  }
  
  
  public int[] toArray() {
    IntIterator i = getIntIterator();
    int[] answer = new int[cardinality];
    for(int k = 0; k < cardinality; ++k)
      answer[k] = i.next();
    return answer;
  }

  public static SparseBitmap bitmapOf(int... k) {
    SparseBitmap s = new SparseBitmap();
    for (int i : k) {
      s.set(i);
    }
    return s;
  }

  public void set(int i) {
    int offset = i - sizeinwords * WORDSIZE;
    if (offset + WORDSIZE < 0) {
      throw new RuntimeException("unsupported write back");
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
  
  public SparseBitmap and(SparseBitmap o) {
    SparseBitmap a = new SparseBitmap();
    and2by2(a,this,o);
    return a;
  }

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

  public SparseBitmap or(SparseBitmap o) {
    SparseBitmap a = new SparseBitmap();
    or2by2(a,this,o);
    return a;
  }

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
      System.out.println("==***= p1 =" + p1 + " p2 = " + p2);
      System.out.println("34%%  p2 = " + p2);
      container.add(bitmap2.buffer[it2 + 1], p2);
      it2 += 2;
      p2 += bitmap2.buffer[it2] + 1;
    }
  }

  public SparseBitmap xor(SparseBitmap o) {
    SparseBitmap a = new SparseBitmap();
    xor2by2(a,this,o);
    return a;
  }
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
  
  static Comparator<SparseBitmap> smallfirst = new Comparator<SparseBitmap>() {   
    public int compare(SparseBitmap a, SparseBitmap b) {
      return a.sizeInBytes() - b.sizeInBytes();
    }
  };
  
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
  
  
  public static void main(String[] args) {
    SparseBitmap sp1 = SparseBitmap.bitmapOf(1, 2, 100, 150, 1000, 123456);

    for (int i : sp1)
      System.out.print(i + " ");
    System.out.println();

    SparseBitmap sp2 = SparseBitmap.bitmapOf(1, 2, 3, 1000, 123456, 1234567);

    for (int i : sp2)
      System.out.print(i + " ");
    System.out.println();

    SparseBitmap sand = sp1.and(sp2);

    System.out.println("and:");

    for (int i : sand)
      System.out.print(i + " ");
    System.out.println();
    
    SparseBitmap sor = sp1.or(sp2);
    
    System.out.println("or:");

    for (int i : sor)
      System.out.print(i + " ");
    System.out.println();

  }
  
  public int sizeInBytes() {
    return wordusage * 4;
  }


  public int sizeinwords;
  public int cardinality;
  public int[] buffer = new int[32];
  public int wordusage = 0;
  public static final int WORDSIZE = 32;

    
}

