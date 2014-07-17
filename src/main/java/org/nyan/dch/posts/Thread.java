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

package org.nyan.dch.posts;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.nyan.dch.crypto.SHA256Hash;

/**
 *
 * @author sorrge
 */
public class Thread implements Serializable
{
  public static final int MaxPosts = 500;
  
  static class BumpedComparator implements Comparator<Thread>
  {
    @Override
    public int compare(Thread t1, Thread t2)
    {
      if(t1.bumpedAt.equals(t2.bumpedAt))
        return Integer.compare(t1.hashCode(), t2.hashCode());
      
      return t1.bumpedAt.compareTo(t2.bumpedAt);
    }
  }
  
  private final SHA256Hash id;
  private final SortedSet<Post> posts = new TreeSet<>(new Post.DateIdComparator());
  private final HashSet<SHA256Hash> postsIDs = new HashSet<>();
  private Date bumpedAt;
  
  public Thread(Post post)
  {
    id = post.GetId();
    posts.add(post);
    postsIDs.add(id);
    bumpedAt = post.GetSentAt();
  }
  
  public void Add(Post post)
  {
    posts.add(post);
    postsIDs.add(post.GetId());
    if(posts.size() <= MaxPosts && post.GetSentAt().after(bumpedAt))
      bumpedAt = post.GetSentAt();
  }

  public SHA256Hash GetId()
  {
    return id;
  }

  public SortedSet<Post> GetPosts()
  {
    return posts;
  }
  
  public Set<SHA256Hash> GetPostIds()
  {
    return postsIDs;
  }
}
