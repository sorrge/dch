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

import com.google.common.collect.BoundType;
import com.google.common.collect.SortedMultiset;
import com.google.common.collect.TreeMultiset;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.nyan.dch.crypto.SHA256Hash;

/**
 *
 * @author sorrge
 */
public class Storage implements Serializable
{
  private final Map<SHA256Hash, Post> allPosts = new HashMap<>();
  private final TreeMultiset<Comparable<Object>> postsHistory = TreeMultiset.create();
  private final Chan chan;
  private final ArrayList<IPostAddedListener> postAdded = new ArrayList<>();
  
  public Storage(String... boardNames)
  {
    chan = new Chan(this, boardNames);
  }
  
  public synchronized void Add(Post post)
  {
    if(Contains(post))
      return;
    
    if(post.GetBoard() != null)
      chan.Add(post);
    else
      throw new IllegalArgumentException("post without a board");
  }
  
  void FinalizeAdd(Post post)
  {
    allPosts.put(post.GetId(), post);
    postsHistory.add(post);
    for(IPostAddedListener l : postAdded)
      l.PostAdded(post);
  }
  
  void FinalizeRemove(Post post)
  {
    allPosts.remove(post.GetId());
    postsHistory.remove(post);
  }

  public boolean Contains(Post post)
  {
    return allPosts.containsKey(post.GetId());
  }
  
  public boolean Contains(SHA256Hash id)
  {
    return allPosts.containsKey(id);
  }  

  public void AddPostAddedListener(IPostAddedListener postAdded)
  {
    this.postAdded.add(postAdded);
  }
  
  public int NumPosts()
  {
    return allPosts.size();
  }
  
  public void Wipe()
  {
    chan.Wipe();
    allPosts.clear();
    postsHistory.clear();
  }
  
  public List<Post> GetPostsAfter(Date begin)
  {
    SortedMultiset<Comparable<Object>> tail = postsHistory.tailMultiset(new Post.PostDate(begin), BoundType.CLOSED);
    ArrayList<Post> res = new ArrayList(tail.size());
    for(Comparable<Object> p : tail)
      res.add((Post)p);
    
    return res;
  }
  
  public Chan GetChan()
  {
    return chan;
  }
  
  public Post GetPost(SHA256Hash id)
  {
    return allPosts.get(id);
  }
}
