package org.roaringbitmap.art;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.roaringbitmap.ArrayContainer;
import org.roaringbitmap.BitmapContainer;
import org.roaringbitmap.Container;
import org.roaringbitmap.RoaringBitmap;
import org.roaringbitmap.RunContainer;
import org.roaringbitmap.longlong.LongUtils;

/**
 *To support the largest 2^48 different keys,we almost need 2^18 Container
 *arrays which holds 2^31 - 8 Container
 */
public class Containers {

  private List<Container[]> containerArrays = new ArrayList<>(0);
  private long containerSize = 0;
  private int firstLevelIdx = -1;
  private int secondLevelIdx = 0;
  private static final int MAX_JVM_ARRAY_LENGTH = Integer.MAX_VALUE - 8;
  private static final int MAX_JVM_ARRAY_OFFSET = MAX_JVM_ARRAY_LENGTH - 1;
  private static final byte NULL_MARK = 0;
  private static final byte NOT_NULL_MARK = 1;
  //TODO: when the containerArrays is dense enough we hold the remained not null
  //containerIdx in this dense bitmap,and shrink the real containerArrays to hold
  //all the not null containers.
  private List<RoaringBitmap> denseContainerIdxList;
  private static final byte TRIMMED_MARK = -1;
  private static final byte NOT_TRIMMED_MARK = -2;

  /**
   * Constructor
   */
  public Containers() {
    reset();
  }

  private void reset() {
    containerSize = 0;
    firstLevelIdx = -1;
    secondLevelIdx = 0;
  }

  /**
   * remove the container index Container
   * @param containerIdx the container index
   */
  public void remove(long containerIdx) {
    int firstDimIdx = (int) (containerIdx >>> 32);
    int secondDimIdx = (int) containerIdx;
    containerArrays.get(firstDimIdx)[secondDimIdx] = null;
    containerSize--;
  }

  /**
   * get the Container with the corresponding container index
   * @param idx the container index
   * @return the corresponding Container
   */
  public Container getContainer(long idx) {
    //split the idx into two part
    int firstDimIdx = (int) (idx >>> 32);
    int secondDimIdx = (int) idx;
    Container[] containers = containerArrays.get(firstDimIdx);
    return containers[secondDimIdx];
  }

  /**
   * add a Container
   * @param container a Container
   * @return the container index
   */
  public long addContainer(Container container) {
    if (secondLevelIdx + 1 == MAX_JVM_ARRAY_OFFSET || firstLevelIdx == -1) {
      containerArrays.add(new Container[1]);
      this.firstLevelIdx++;
      this.secondLevelIdx = 0;
    } else {
      secondLevelIdx++;
    }
    int firstDimIdx = firstLevelIdx;
    int secondDimIdx = secondLevelIdx;
    grow(secondDimIdx + 1, firstLevelIdx);
    containerArrays.get(firstDimIdx)[secondDimIdx] = container;
    this.containerSize++;
    return toContainerIdx(firstLevelIdx, secondLevelIdx);
  }

  /**
   * a iterator of the Containers
   * @return a iterator
   */
  public ContainerIterator iterator() {
    return new ContainerIterator(this);
  }

  /**
   * replace the container index one with a fresh Container
   * @param containerIdx the container index to replace
   * @param freshContainer the fresh one
   */
  public void replace(long containerIdx, Container freshContainer) {
    int firstDimIdx = (int) (containerIdx >>> 32);
    int secondDimIdx = (int) containerIdx;
    containerArrays.get(firstDimIdx)[secondDimIdx] = freshContainer;
  }

  /**
   * replace with a fresh Container
   * @param firstLevelIdx the first level array index
   * @param secondLevelIdx the second level array index
   * @param freshContainer a fresh container
   */
  public void replace(int firstLevelIdx, int secondLevelIdx, Container freshContainer) {
    containerArrays.get(firstLevelIdx)[secondLevelIdx] = freshContainer;
  }

  /**
   * the number of all the holding containers
   * @return the container number
   */
  public long getContainerSize() {
    return containerSize;
  }

  List<Container[]> getContainerArrays() {
    return containerArrays;
  }

  static long toContainerIdx(int firstLevelIdx, int secondLevelIdx) {
    long firstLevelIdxL = firstLevelIdx;
    return firstLevelIdxL << 32 | secondLevelIdx;
  }

  /**
   * increases the capacity to ensure that it can hold at least the number of elements specified by
   * the minimum capacity argument.
   *
   * @param minCapacity the desired minimum capacity
   */
  private void grow(int minCapacity, int firstLevelIdx) {
    Container[] elementData = containerArrays.get(firstLevelIdx);
    int oldCapacity = elementData.length;
    if (minCapacity - oldCapacity <= 0) {
      return;
    }
    // overflow-conscious code
    int newCapacity = oldCapacity + (oldCapacity >> 1);
    if (newCapacity - minCapacity < 0) {
      newCapacity = minCapacity;
    }
    if (newCapacity - MAX_JVM_ARRAY_LENGTH > 0) {
      newCapacity = hugeCapacity(minCapacity);
    }
    // minCapacity is usually close to size, so this is a win:
    Container[] freshElementData = Arrays.copyOf(elementData, newCapacity);
    containerArrays.set(firstLevelIdx, freshElementData);
  }

  private static int hugeCapacity(int minCapacity) {
    if (minCapacity < 0) // overflow
    {
      throw new OutOfMemoryError();
    }
    return (minCapacity > MAX_JVM_ARRAY_LENGTH) ? Integer.MAX_VALUE : MAX_JVM_ARRAY_LENGTH;
  }

  /**
   * Report the number of bytes required for serialization.
   * @return The size in bytes
   */
  public long serializedSizeInBytes() {
    long totalSize = 0l;
    totalSize += 4;
    int firstLevelSize = containerArrays.size();
    for (int i = 0; i < firstLevelSize; i++) {
      Container[] containers = containerArrays.get(i);
      totalSize += 5;
      for (int j = 0; j < containers.length; j++) {
        Container container = containers[j];
        if (container != null) {
          totalSize += 2;
          totalSize += container.serializedSizeInBytes();
        } else {
          totalSize += 1;
        }
      }
    }
    totalSize += 16;
    return totalSize;
  }

  /**
   * Serialize the Containers
   * @param dataOutput The destination DataOutput
   * @throws IOException Signals that an I/O exception has occurred.
   */
  public void serialize(DataOutput dataOutput) throws IOException {
    int firstLevelSize = containerArrays.size();
    dataOutput.writeInt(Integer.reverse(firstLevelSize));
    for (int i = 0; i < firstLevelSize; i++) {
      Container[] containers = containerArrays.get(i);
      int secondLevelSize = containers.length;
      dataOutput.writeByte(NOT_TRIMMED_MARK);
      //TODO:serialize the trimmed related data
      dataOutput.writeInt(Integer.reverse(secondLevelSize));
      for (int j = 0; j < containers.length; j++) {
        Container container = containers[j];
        if (container != null) {
          dataOutput.writeByte(NOT_NULL_MARK);
          byte containerType = containerType(container);
          dataOutput.writeByte(containerType);
          container.serialize(dataOutput);
        } else {
          dataOutput.writeByte(NULL_MARK);
        }
      }
    }
    dataOutput.write(LongUtils.toLDBytes(containerSize));
    dataOutput.writeInt(Integer.reverse(this.firstLevelIdx));
    dataOutput.writeInt(Integer.reverse(this.secondLevelIdx));
  }

  /**
   * Deserialize the byte stream to init this Containers
   * @param dataInput The DataInput
   * @throws IOException Signals that an I/O exception has occurred.
   */
  public void deserialize(DataInput dataInput) throws IOException {
    int firstLevelSize = Integer.reverse(dataInput.readInt());
    ArrayList<Container[]> containersArray = new ArrayList<>(firstLevelSize);
    for (int i = 0; i < firstLevelSize; i++) {
      //TODO:deserialize the trimmed related data
      byte trimTag = dataInput.readByte();
      int secondLevelSize = Integer.reverse(dataInput.readInt());
      Container[] containers = new Container[secondLevelSize];
      for (int j = 0; j < secondLevelSize; j++) {
        byte nullTag = dataInput.readByte();
        if (nullTag == NULL_MARK) {
          containers[j] = null;
        } else if (nullTag == NOT_NULL_MARK) {
          byte containerType = dataInput.readByte();
          Container container = instanceContainer(containerType);
          container.deserialize(dataInput);
          containers[j] = container;
        } else {
          throw new RuntimeException("the null tag byte value:" + nullTag + " is not right!");
        }
      }
      containersArray.add(containers);
    }
    this.containerArrays = containersArray;
    byte[] littleEndianL = new byte[8];
    dataInput.readFully(littleEndianL);
    this.containerSize = LongUtils.fromLDBytes(littleEndianL);
    this.firstLevelIdx = Integer.reverse(dataInput.readInt());
    this.secondLevelIdx = Integer.reverse(dataInput.readInt());
  }

  //TODO: remove allocated unused memory
  public void trim() {

  }

  private byte containerType(Container container) {
    if (container instanceof RunContainer) {
      return 0;
    } else if (container instanceof BitmapContainer) {
      return 1;
    } else if (container instanceof ArrayContainer) {
      return 2;
    } else {
      throw new UnsupportedOperationException("Not supported container type");
    }
  }

  private Container instanceContainer(byte containerType) {
    if (containerType == 0) {
      return new RunContainer();
    } else if (containerType == 1) {
      return new BitmapContainer();
    } else if (containerType == 2) {
      return new ArrayContainer();
    } else {
      throw new UnsupportedOperationException("Not supported container type:" + containerType);
    }
  }
}
