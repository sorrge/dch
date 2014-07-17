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

package org.nyan.dch.node;

import java.util.Collection;
import java.util.Date;
import java.util.Set;
import org.nyan.dch.crypto.SHA256Hash;
import org.nyan.dch.posts.PostData;
import org.nyan.dch.communication.ProtocolException;

/**
 *
 * @author sorrge
 */
public interface IRemoteNode
{
  void PushPost(PostData post) throws ProtocolException;
  void PullPosts(Collection<SHA256Hash> postsNeeded) throws ProtocolException;
  void UpdateThreadsAndGetOthers(String board, Collection<SHA256Hash> myThreads) throws ProtocolException;
  void GetOtherPostsInThread(String board, SHA256Hash threadId, Collection<SHA256Hash> myPosts) throws ProtocolException;
}
