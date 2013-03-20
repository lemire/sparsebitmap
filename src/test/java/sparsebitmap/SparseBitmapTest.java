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
import java.util.PriorityQueue;
import org.junit.Test;

public class SparseBitmapTest {
  
  
  public static int[] unite(int[]... set) {
    if(set.length == 0) throw new RuntimeException("nothing");
    PriorityQueue<int[]> pq = new PriorityQueue<int[]>(set.length,
      new Comparator<int[]>(){
        @Override
		public int compare(int[] a, int[] b) {
          return a.length - b.length;
        }}
     );
    int[] buffer = new int[32];
    for(int[] x : set) 
      pq.add(x);
    while(pq.size()>1) {
    int[] x1 = pq.poll();
    int[] x2 = pq.poll();
    if(buffer.length<x1.length+x2.length)
      buffer =  new int[x1.length+x2.length];
    int [] a = unite2by2(x1,x2,buffer);
    pq.add(a);
    } 
    return pq.poll();
  }
  
  static public int[] unite2by2(final int[] set1, final int[] set2, final int[] buffer) {
    int pos = 0;
    int k1 = 0, k2 = 0;
    if(0==set1.length)
      return Arrays.copyOf(set2, set2.length);
    if(0==set2.length)
      return Arrays.copyOf(set1, set1.length);
    while(true) {
      if(set1[k1]<set2[k2]) {
        buffer[pos++] = set1[k1];
        ++k1;
        if(k1>=set1.length) {
          for(; k2<set2.length;++k2)
            buffer[pos++] = set2[k2];
          break;
        }
      } else if (set1[k1]==set2[k2]) {
        buffer[pos++] = set1[k1];
        ++k1;
        ++k2;
        if(k1>=set1.length) {
          for(; k2<set2.length;++k2)
            buffer[pos++] = set2[k2];
          break;
        }
        if(k2>=set2.length) {
          for(; k1<set1.length;++k1)
            buffer[pos++] = set1[k1];
          break;
        }
      } else {//if (set1[k1]>set2[k2]) {
        buffer[pos++] = set2[k2];
        ++k2;
        if(k2>=set2.length) {
          for(; k1<set1.length;++k1)
            buffer[pos++] = set1[k1];
          break;
        }
      }
    }
    return Arrays.copyOf(buffer, pos);
  }
  public static int[] exclusiveunite(int[]... set) {
    if(set.length == 0) throw new RuntimeException("nothing");
    PriorityQueue<int[]> pq = new PriorityQueue<int[]>(set.length,
      new Comparator<int[]>(){
        @Override
		public int compare(int[] a, int[] b) {
          return a.length - b.length;
        }}
     );
    int[] buffer = new int[32];
    for(int[] x : set) 
      pq.add(x);
    while(pq.size()>1) {
    int[] x1 = pq.poll();
    int[] x2 = pq.poll();
    if(buffer.length<x1.length+x2.length)
      buffer =  new int[x1.length+x2.length];
    int [] a = exclusiveunite2by2(x1,x2,buffer);
    pq.add(a);
    } 
    return pq.poll();
  }

  static public int[] exclusiveunite2by2(final int[] set1, final int[] set2, final int[] buffer) {
    int pos = 0;
    int k1 = 0, k2 = 0;
    if(0==set1.length)
      return Arrays.copyOf(set2, set2.length);
    if(0==set2.length)
      return Arrays.copyOf(set1, set1.length);
    while(true) {
      if(set1[k1]<set2[k2]) {
        buffer[pos++] = set1[k1];
        ++k1;
        if(k1>=set1.length) {
          for(; k2<set2.length;++k2)
            buffer[pos++] = set2[k2];
          break;
        }
      } else if (set1[k1]==set2[k2]) {
        ++k1;
        ++k2;
        if(k1>=set1.length) {
          for(; k2<set2.length;++k2)
            buffer[pos++] = set2[k2];
          break;
        }
        if(k2>=set2.length) {
          for(; k1<set1.length;++k1)
            buffer[pos++] = set1[k1];
          break;
        }
      } else {//if (set1[k1]>set2[k2]) {
        buffer[pos++] = set2[k2];
        ++k2;
        if(k2>=set2.length) {
          for(; k1<set1.length;++k1)
            buffer[pos++] = set1[k1];
          break;
        }
      }
    }
    return Arrays.copyOf(buffer, pos);
  }
  
  public static int[] intersect(int[]... set) {
    if(set.length == 0) throw new RuntimeException("nothing");
    int[] answer = set[0];
    int[] buffer = new int[32];
    for(int k = 1; k<set.length;++k) {
      if(buffer.length<answer.length+set[k].length)
        buffer =  new int[answer.length+set[k].length];
      answer = intersect2by2(answer, set[k], buffer);
    }
    return answer;
  }

  public static int[] intersect2by2(final int[] set1, final int[] set2, final int[] buffer) {
    int pos = 0;
    int k1 = 0, k2 = 0;
    if((set1.length == 0) || (set2.length == 0)) return new int[0];
    while(true) {
      if(set1[k1]<set2[k2]) {
        ++k1; 
        if(k1==set1.length) break;
      } else if(set1[k1]>set2[k2]) {
        ++k2; 
        if(k2==set2.length) break;
      } else {
        buffer[pos++] = set1[k1];
        ++k1; ++k2;
        if((k1==set1.length) || (k2==set2.length)) break;
      }
    }
    return Arrays.copyOf(buffer, pos);
  }

  
  @SuppressWarnings("static-method")
  @Test
  public  void testOps() {
    System.out.println("Testing AND/OR/XOR");
    final int N = 40;
    final int M = 100;
    
//    for(int skip = 1; skip <=M; ++skip) {
    for(int skip = 4; skip <=4; ++skip) {
    	      int[] array = new int[N];
      for(int k = 0; k< N ; ++k )
        array[k] = k*skip +skip;
      final SparseBitmap b1 = SparseBitmap.bitmapOf(array);
      if(!Arrays.equals(array, b1.toArray())) {
          throw new RuntimeException("basic bug");
      }
      //for(int skip2 = 1; skip2 <=M; ++skip2) {
      for(int skip2 = M; skip2 <=M; ++skip2) {
        int[] array2 = new int[N];
        for(int k = 0; k< N ; ++k )
          array2[k] = k*skip2 +skip2;
        int[] answeror = unite(array,array2);
        int[] answerxor = exclusiveunite(array,array2);
        int[] answerand = intersect(array,array2);
        final SparseBitmap b2 = SparseBitmap.bitmapOf(array2);
        if(!Arrays.equals(array2, b2.toArray())) {
            throw new RuntimeException("basic bug");
        }
        if(!Arrays.equals(answeror, b1.or(b2).toArray())) {
          throw new RuntimeException("bug or");
        }
        if(!Arrays.equals(answerand, b1.and(b2).toArray())) {
          throw new RuntimeException("bug and");
        }
        if(!Arrays.equals(answerand, SparseBitmap.materialize(SparseBitmap.and(b1.getSkippableIterator(),b2.getSkippableIterator())).toArray())) {
            throw new RuntimeException("bug and");
          }
        if(!Arrays.equals(answerand, SparseBitmap.materialize(SparseBitmap.fastand(b1.getSkippableIterator(),b2.getSkippableIterator())).toArray())) {
            throw new RuntimeException("bug and");
          }

        if(!Arrays.equals(answerxor, b1.xor(b2).toArray())) {
          throw new RuntimeException("bug xor");
        }
      }

    }
  }
}
