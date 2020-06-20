package org.roaringbitmap.art;

public interface Shuttle {

  /**
   * should be called firstly before calling other methods
   */
  public void initShuttle();

  /**
   *
   * @return true: has a LeafNode ,false: has no LeafNode
   */
  public boolean moveToNextLeaf();

  /**
   * get the current LeafNode after calling the method moveToNextLeaf
   * @return
   */
  public LeafNode getCurrentLeafNode();

  public void remove();
}
