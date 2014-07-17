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

package org.nyan.dch.communication.transport.simulator;

import org.nyan.dch.communication.IAcceptance;

/**
 *
 * @author sorrge
 */
public class Acceptance implements IAcceptance
{
  private boolean canAccept = true, acceptanceConfirmed = false;
  private int requestCountdown = -1;
  
  @Override
  public void AcceptanceConfirmationRequested()
  {
    requestCountdown = 10;
  }
  
  public boolean Step()
  {
    if(requestCountdown == -1)
      return false;
    
    --requestCountdown;
    if(requestCountdown == -1)
    {
      if(!acceptanceConfirmed)
      {
        acceptanceConfirmed = true;
        canAccept = false;
      }
    }
    
    return true;
  }

  @Override
  public void AcceptanceConfirmed()
  {
    acceptanceConfirmed = true;
    canAccept = true;
  }

  @Override
  public boolean CanAcceptConnections()
  {
    return canAccept;
  }

  @Override
  public boolean NeedToConfirmAcceptance()
  {
    return !acceptanceConfirmed;
  }  
}
