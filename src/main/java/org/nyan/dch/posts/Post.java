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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.Comparator;
import java.util.Date;
import java.util.Objects;
import org.nyan.dch.crypto.SHA256Hash;
import org.nyan.dch.misc.Utils;

/**
 *
 * @author sorrge
 */
public class Post implements Serializable, Comparable<Object>
{
  static class DateIdComparator implements Comparator<Post>
  {
    @Override
    public int compare(Post p1, Post p2)
    {
      if(p1.GetSentAt().equals(p2.GetSentAt()))
        return p1.id.compareTo(p2.id);
      
      return p1.GetSentAt().compareTo(p2.GetSentAt());
    }
  }
  
  static class PostDate implements Comparable<Object>
  {
    Date date;

    public PostDate(Date date)
    {
      this.date = date;
    }

    @Override
    public int compareTo(Object t)
    {
      if(t instanceof PostDate)
        return date.compareTo(((PostDate)t).date);
      else if(t instanceof Post)
        return date.compareTo(((Post)t).GetSentAt());

      throw new IllegalArgumentException();
    }
  }  
  
  private final SHA256Hash id;
  private final PostData data;
    
  public Post(PostData data)
  {
    this.data = data;
    
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try
    {
      DataOutputStream stream = new DataOutputStream(bos);
      stream.writeUTF("dch post");
      data.Write(stream);
    }
    catch(IOException ex)
    {
    }

    id = SHA256Hash.Digest(bos.toByteArray());
  }

  public SHA256Hash GetThreadId()
  {
    return data.GetThreadId();
  }

  public String GetBoard()
  {
    return data.GetBoard();
  }

  public String GetTitle()
  {
    return data.GetTitle();
  }

  public String GetBody()
  {
    return data.GetBody();
  }

  public Date GetSentAt()
  {
    return data.GetSentAt();
  }

  public SHA256Hash GetId()
  {
    return id;
  }

  public PostData GetData()
  {
    return data;
  }  
  
  @Override
  public int compareTo(Object t)
  {
    if(t instanceof PostDate)
      return data.GetSentAt().compareTo(((PostDate)t).date);
    else if(t instanceof Post)
      return data.GetSentAt().compareTo(((Post)t).GetSentAt());
    
    throw new IllegalArgumentException();
  }

  @Override
  public int hashCode()
  {
    return id.hashCode();
  }

  @Override
  public boolean equals(Object obj)
  {
    if (obj == null)
      return false;
    
    if (getClass() != obj.getClass())
      return false;
    
    final Post other = (Post) obj;
    return Objects.equals(this.id, other.id);
  }
}
