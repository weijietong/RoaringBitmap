package org.roaringbitmap.longlong;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.roaringbitmap.art.Art;
import org.roaringbitmap.art.LeafNode;
import org.roaringbitmap.art.LeafNodeIterator;
import org.roaringbitmap.art.Node;

public class ArtTest {

  //one leaf node
  @Test
  public void test1() {
    byte[] key1 = new byte[]{1, 2, 3, 4, 5, 0};
    Art art = new Art();
    insert5PrefixCommonBytesIntoArt(art, 1);
    LeafNodeIterator leafNodeIterator = art.leafNodeIterator(false, null);
    boolean hasNext = leafNodeIterator.hasNext();
    Assertions.assertTrue(hasNext);
    LeafNode leafNode = leafNodeIterator.next();
    Assertions.assertTrue(BytesUtil.same(leafNode.getKeyBytes(), key1));
    Assertions.assertTrue(leafNode.getContainerIdx() == 0);
    hasNext = leafNodeIterator.hasNext();
    Assertions.assertTrue(!hasNext);
    art.remove(key1);
    Assertions.assertTrue(art.findByKey(key1) == Node.ILLEGAL_IDX);
  }

  //one node4 with two leaf nodes
  @Test
  public void test2() {
    byte[] key1 = new byte[]{1, 2, 3, 4, 5, 0};
    byte[] key2 = new byte[]{1, 2, 3, 4, 5, 1};
    Art art = new Art();
    insert5PrefixCommonBytesIntoArt(art, 2);
    LeafNodeIterator leafNodeIterator = art.leafNodeIterator(false, null);
    boolean hasNext = leafNodeIterator.hasNext();
    Assertions.assertTrue(hasNext);
    LeafNode leafNode = leafNodeIterator.next();
    Assertions.assertTrue(BytesUtil.same(leafNode.getKeyBytes(), key1));
    Assertions.assertTrue(leafNode.getContainerIdx() == 0);
    hasNext = leafNodeIterator.hasNext();
    Assertions.assertTrue(hasNext);
    leafNode = leafNodeIterator.next();
    Assertions.assertTrue(BytesUtil.same(leafNode.getKeyBytes(), key2));
    Assertions.assertTrue(leafNode.getContainerIdx() == 1);
    hasNext = leafNodeIterator.hasNext();
    Assertions.assertTrue(!hasNext);
    art.remove(key1);
    //shrink to leaf node
    long containerIdx2 = art.findByKey(key2);
    Assertions.assertTrue(containerIdx2 == 1);
  }

  //1 node16
  @Test
  public void test3() {
    byte[] key1 = new byte[]{1, 2, 3, 4, 5, 0};
    byte[] key2 = new byte[]{1, 2, 3, 4, 5, 1};
    byte[] key3 = new byte[]{1, 2, 3, 4, 5, 2};
    byte[] key4 = new byte[]{1, 2, 3, 4, 5, 3};
    byte[] key5 = new byte[]{1, 2, 3, 4, 5, 4};
    Art art = new Art();
    insert5PrefixCommonBytesIntoArt(art, 5);
    LeafNodeIterator leafNodeIterator = art.leafNodeIterator(false, null);
    boolean hasNext = leafNodeIterator.hasNext();
    Assertions.assertTrue(hasNext);
    LeafNode leafNode = leafNodeIterator.next();
    Assertions.assertTrue(BytesUtil.same(leafNode.getKeyBytes(), key1));
    Assertions.assertEquals(0, leafNode.getContainerIdx());
    hasNext = leafNodeIterator.hasNext();
    Assertions.assertTrue(hasNext);
    leafNode = leafNodeIterator.next();
    Assertions.assertTrue(BytesUtil.same(leafNode.getKeyBytes(), key2));
    Assertions.assertEquals(1, leafNode.getContainerIdx());
    hasNext = leafNodeIterator.hasNext();
    Assertions.assertTrue(hasNext);
    long containerIdx = art.findByKey(key4);
    Assertions.assertEquals(3, containerIdx);
    containerIdx = art.findByKey(key5);
    Assertions.assertEquals(4, containerIdx);
    art.remove(key5);
    art.remove(key4);
    //shrink to node4
    long containerIdx4 = art.findByKey(key3);
    Assertions.assertEquals(2, containerIdx4);
  }

  //node48
  @Test
  public void test4() {
    Art art = new Art();
    insert5PrefixCommonBytesIntoArt(art, 13);
    byte[] key = new byte[]{1, 2, 3, 4, 5, 0};
    long containerIdx = art.findByKey(key);
    Assertions.assertTrue(containerIdx == 0);
    key = new byte[]{1, 2, 3, 4, 5, 10};
    containerIdx = art.findByKey(key);
    Assertions.assertTrue(containerIdx == 10);
    key = new byte[]{1, 2, 3, 4, 5, 12};
    containerIdx = art.findByKey(key);
    Assertions.assertTrue(containerIdx == 12);
    byte[] key13 = new byte[]{1, 2, 3, 4, 5, 12};
    //shrink to node16
    art.remove(key13);
    byte[] key12 = new byte[]{1, 2, 3, 4, 5, 11};
    long containerIdx16 = art.findByKey(key12);
    Assertions.assertTrue(containerIdx16 == 11);
  }

  //node256
  @Test
  public void test5() throws IOException {
    Art art = new Art();
    insert5PrefixCommonBytesIntoArt(art, 37);
    byte[] key = new byte[]{1, 2, 3, 4, 5, 0};
    long containerIdx = art.findByKey(key);
    Assertions.assertTrue(containerIdx == 0);
    key = new byte[]{1, 2, 3, 4, 5, 10};
    containerIdx = art.findByKey(key);
    Assertions.assertEquals(10, containerIdx);
    key = new byte[]{1, 2, 3, 4, 5, 16};
    containerIdx = art.findByKey(key);
    Assertions.assertTrue(containerIdx == 16);
    key = new byte[]{1, 2, 3, 4, 5, 36};
    containerIdx = art.findByKey(key);
    Assertions.assertTrue(containerIdx == 36);
    key = new byte[]{1, 2, 3, 4, 5, 50};
    containerIdx = art.findByKey(key);
    Assertions.assertTrue(containerIdx == Node.ILLEGAL_IDX);
    long sizeInBytesL = art.serializeSizeInBytes();
    int sizeInBytesI = (int) sizeInBytesL;
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(sizeInBytesI);
    DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);
    art.serializeArt(dataOutputStream);
    Assertions.assertEquals(sizeInBytesI, byteArrayOutputStream.toByteArray().length);
    Art deserArt = new Art();
    DataInputStream dataInputStream = new DataInputStream(
        new ByteArrayInputStream(byteArrayOutputStream.toByteArray()));
    deserArt.deserializeArt(dataInputStream);
    key = new byte[]{1, 2, 3, 4, 5, 36};
    containerIdx = deserArt.findByKey(key);
    Assertions.assertTrue(containerIdx == 36);
    //shrink to node48
    deserArt.remove(key);
    key = new byte[]{1, 2, 3, 4, 5, 10};
    containerIdx = deserArt.findByKey(key);
    Assertions.assertTrue(containerIdx == 10);
  }

  private void insert5PrefixCommonBytesIntoArt(Art art, int keyNum) {
    byte[] key = new byte[]{1, 2, 3, 4, 5, 0};
    byte b = 0;
    long containerIdx = 0;
    for (int i = 0; i < keyNum; i++) {
      key[5] = b;
      art.insert(key, containerIdx);
      key = new byte[]{1, 2, 3, 4, 5, 0};
      b++;
      containerIdx++;
    }
  }
}