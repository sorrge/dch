/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.nyan.dch.crypto;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.Serializable;
import org.nyan.dch.misc.BitcoinUtils;

/**
 *
 * @author sorrge
 */
public class SHA256Hash extends Hash
{
  public static final int BytesLength = 32;

  public SHA256Hash(DataInputStream stream) throws IOException
  {
    super(stream);
  }
  
  public SHA256Hash(byte[] rawHashBytes)
  {
    super(rawHashBytes);
  }
  
  public SHA256Hash(String hashString)
  {
    super(hashString);
  }  
  
  @Override
  protected int BytesLength()
  {
    return BytesLength;
  }
  
  public static SHA256Hash Digest(byte[] message)
  {
    return new SHA256Hash(BitcoinUtils.singleDigest(message, 0, message.length));
  }
}
