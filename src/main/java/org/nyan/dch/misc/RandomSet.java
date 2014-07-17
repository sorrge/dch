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

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Pasted from http://stackoverflow.com/questions/124671/picking-a-random-element-from-a-set
 * @author fandrew
 */
public class RandomSet<E> extends AbstractSet<E> 
{
  List<E> dta = new ArrayList<>();
  Map<E, Integer> idx = new HashMap<>();

  public RandomSet() 
  {
  }

  public RandomSet(Collection<E> items) 
  {
    for (E item : items) 
    {
      idx.put(item, dta.size());
      dta.add(item);
    }
  }

  @Override
  public boolean add(E item) 
  {
    if (idx.containsKey(item))
      return false;

    idx.put(item, dta.size());
    dta.add(item);
    return true;
  }

  /**
   * Override element at position <code>id</code> with last element.
   * @param id
   */
  public E removeAt(int id) 
  {
    if (id >= dta.size())
      return null;

    E res = dta.get(id);
    idx.remove(res);
    E last = dta.remove(dta.size() - 1);
    // skip filling the hole if last is removed
    if (id < dta.size())
    {
      idx.put(last, id);
      dta.set(id, last);
    }

    return res;
  }

  @Override
  public boolean remove(Object item) 
  {
    @SuppressWarnings(value = "element-type-mismatch")
    Integer id = idx.get(item);
    if (id == null)
      return false;

    removeAt(id);
    return true;
  }

  public E get(int i) 
  {
    return dta.get(i);
  }

  public E pollRandom(Random rnd) 
  {
    if (dta.isEmpty())
      return null;

    int id = rnd.nextInt(dta.size());
    return removeAt(id);
  }

  @Override
  public int size() 
  {
    return dta.size();
  }

  @Override
  public Iterator<E> iterator() 
  {
    return dta.iterator();
  }
}
