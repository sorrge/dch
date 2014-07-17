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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.Date;
import org.nyan.dch.communication.ProtocolException;
import org.nyan.dch.crypto.SHA256Hash;

/**
 *
 * @author sorrge
 */
public class PostData
{
  public static final int MaxTitleLength = 50, MaxBodyLength = 10000;
  
  private final SHA256Hash threadId;
  private final String board, title, body;
  private final Date sentAt; 
  
  public PostData(String board, SHA256Hash threadId, String title, String body, Date sentAt)
  {
    this.board = board;
    this.threadId = threadId;
    this.title = title;
    this.body = body;
    this.sentAt = sentAt;
  }  
  
  public SHA256Hash GetThreadId()
  {
    return threadId;
  }

  public String GetBoard()
  {
    return board;
  }

  public String GetTitle()
  {
    return title;
  }

  public String GetBody()
  {
    return body;
  }

  public Date GetSentAt()
  {
    return sentAt;
  }  
  
  public void Write(DataOutputStream stream) throws IOException
  {
    stream.writeBoolean(threadId == null);    
    if(threadId != null)
      threadId.Write(stream);
    
    stream.writeUTF(board);
    stream.writeUTF(title);
    stream.writeUTF(body);
    stream.writeLong(sentAt.getTime());
  }
  
  public PostData(DataInputStream stream) throws IOException, ProtocolException
  {
    boolean OP = stream.readBoolean();
    if(OP)
      threadId = null;
    else
      threadId = new SHA256Hash(stream);
    
    board = stream.readUTF();
    if(board.length() > Board.MaxBoardNameLength)
      throw new ProtocolException("Board name too long");
    
    title = stream.readUTF();
    if(title.length() > MaxTitleLength)
      throw new ProtocolException("Post title too long");

    body = stream.readUTF();
    if(body.length() > MaxBodyLength)
      throw new ProtocolException("Post body too long");
    
    sentAt = new Date(stream.readLong());
  }
}
