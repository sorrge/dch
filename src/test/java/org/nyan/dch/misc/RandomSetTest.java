/*
 * The MIT License
 *
 * Copyright 2014 sorrge.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.nyan.dch.misc;

import java.util.HashSet;
import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author sorrge
 */
public class RandomSetTest
{
  /**
   * Test of add method, of class RandomSet.
   */
  @Test
  public void testAdd()
  {
    RandomSet<String> s = new RandomSet<>();
    HashSet<String> ss = new HashSet<>();
    Random rand = new Random(12);
    
    for(int i = 0; i < 10000; ++i)
    {
      String str = String.valueOf(rand.nextDouble());
      s.add(str);
      ss.add(str);
      
      if(rand.nextInt(4) == 0)
        assert(ss.remove(s.pollRandom(rand)));
    }
    
    assert(!s.isEmpty());
    
    assert(ss.equals(s));
    for(String str : s)
    {
      assert(s.contains(str));
      assert(ss.contains(str));
    }
    
    for(int i = 0; i < 10000; ++i)
      assert(ss.contains(s.get(i % s.size())));
    
    while(!s.isEmpty())
      assert(ss.remove(s.pollRandom(rand)));
    
    assert(ss.isEmpty());
  } 
}
