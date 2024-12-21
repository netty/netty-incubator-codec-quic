/*
 * Copyright 2022 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.incubator.codec.quic;

import java.util.Objects;

// Fork from https://gist.github.com/franz1981/6277af990ccfe0a5da8542a75d66ce5e
/**
 * It's a growable ring buffer that allows to move tail/head sequences, clear, append, set/replace at specific positions.
 */
final class ArrayRingBuffer<T> {

    private static final Object[] EMPTY = new Object[0];
    // it points to the next slot after the last element
    private int tailSequence;
    // it points to the slot of the first element
    private int headSequence;
    private T[] elements;
    private int size;

    public ArrayRingBuffer() {
        this(0, 0);
    }

    public ArrayRingBuffer(final int headSequence) {
        this(0, headSequence);
    }

    public ArrayRingBuffer(final int initialSize, final int headSequence) {
        this.elements = allocate(initialSize);
        this.headSequence = headSequence;
        this.tailSequence = headSequence;
        if (headSequence < 0) {
            throw new IllegalArgumentException("headSequence cannot be negative");
        }
        size = 0;
    }

    public int usedCapacity() {
        return tailSequence - headSequence;
    }

    public int size() {
        return size;
    }

    public int availableCapacityWithoutResizing() {
        return elements.length - usedCapacity();
    }

    int bufferOffset(final long index) {
        return bufferOffset(index, elements.length);
    }

    private static int bufferOffset(final long index, final int elementsLength) {
        return (int) (index & (elementsLength - 1));
    }

    public T get(final int index) {
        if (index < 0) {
            throw new IllegalArgumentException("index cannot be negative");
        }
        if (index >= headSequence && index < tailSequence) {
            return elements[bufferOffset(index)];
        }
        return null;
    }

    public T put(final int index, final T e) {
        Objects.requireNonNull(e);
        if (index < headSequence) {
            throw new IllegalArgumentException("index cannot be less then " + headSequence);
        }
        if (index < tailSequence) {
            final int offset = bufferOffset(index);
            final T oldValue = elements[offset];
            elements[offset] = e;
            if (oldValue == null) {
                size++;
            }
            return oldValue;
        }
        final int requiredCapacity = (index - tailSequence) + 1;
        final int missingCapacity = requiredCapacity - availableCapacityWithoutResizing();
        if (missingCapacity > 0) {
            growCapacity(missingCapacity);
        }
        assert elements[bufferOffset(index)] == null;
        elements[bufferOffset(index)] = e;
        if (index >= tailSequence) {
            tailSequence = index + 1;
        }
        size++;
        return null;
    }

    public T remove(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("index cannot be negative");
        }
        if (index < headSequence || index >= tailSequence) {
            return null;
        }
        final T[] elements = this.elements;
        final int offset = bufferOffset(index);
        final T e = elements[offset];
        elements[offset] = null;
        if (e != null) {
            size--;
        }
        if (index == headSequence) {
            int toRemove = 1;
            for (int sequence = headSequence + 1; sequence < tailSequence; sequence++) {
                if (elements[bufferOffset(sequence)] != null) {
                    break;
                }
                toRemove++;
            }
            headSequence += toRemove;
        }
        return e;
    }

    private void growCapacity(int delta) {
        assert delta > 0;
        final T[] oldElements = this.elements;
        final int newCapacity = findNextPositivePowerOfTwo(oldElements.length + delta);
        if (newCapacity < 0) {
            // see ArrayList::newCapacity
            throw new OutOfMemoryError();
        }
        final T[] newElements = allocate(newCapacity);
        final int usedCapacity = usedCapacity();
        final long headSequence = this.headSequence;
        long oldIndex = headSequence;
        long newIndex = headSequence;
        int remaining = usedCapacity;
        while (remaining > 0) {
            final int fromOldIndex = bufferOffset(oldIndex, oldElements.length);
            final int fromNewIndex = bufferOffset(newIndex, newCapacity);
            final int toOldEnd = oldElements.length - fromOldIndex;
            final int toNewEnd = newElements.length - fromNewIndex;
            final int bytesToCopy = Math.min(Math.min(remaining, toOldEnd), toNewEnd);
            System.arraycopy(oldElements, fromOldIndex, newElements, fromNewIndex, bytesToCopy);
            oldIndex += bytesToCopy;
            newIndex += bytesToCopy;
            remaining -= bytesToCopy;
        }
        this.elements = newElements;
    }

    private static int findNextPositivePowerOfTwo(final int value) {
        return 1 << (Integer.SIZE - Integer.numberOfLeadingZeros(value - 1));
    }

    @SuppressWarnings("unchecked")
    private static <T> T[] allocate(int capacity) {
        return (T[]) (capacity == 0? EMPTY : new Object[capacity]);
    }

    public ReadonlyCursor cursor() {
        return new ReadonlyCursor();
    }

    public class ReadonlyCursor {

        private int next = headSequence;

        public T next() {
            final T[] elements = ArrayRingBuffer.this.elements;
            if (next >= tailSequence) {
                return null;
            }
            for (int next = this.next; next < tailSequence; next++) {
                T e = elements[bufferOffset(next)];
                if (e != null) {
                    // point this to the next past to this element
                    this.next = next + 1;
                    return e;
                }
            }
            // let's point this to the tailSequence since there's nothing left and we speed up the next call to cursor
            next = tailSequence;
            return null;
        }
    }
}
