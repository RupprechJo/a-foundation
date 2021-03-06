package com.ajjpj.afoundation.collection.immutable;

import java.util.Arrays;


/**
 * @author arno
 */
class IndexNode extends ABTreeMap {
    // one more child than separators, i.e. one separator *between* every pair of adjacent children. The separator contains
    //  the smallest key value for the right child.
    final Object[] separators;
    final ABTreeMap[] children;

    IndexNode (ABTreeSpec spec, Object[] separators, ABTreeMap[] children) {
        super (spec);
        this.separators = separators;
        this.children = children;
    }

    /**
     * returns the index of the first separator that is greater than the key
     */
    @SuppressWarnings ("unchecked")
    private int lookupKey (Object key) {
        return lookupKeyInInterval (key, 0, separators.length);
    }

    /**
     * @param toIdx the index number *after* the last index to search in
     */
    @SuppressWarnings ("unchecked")
    private int lookupKeyInInterval (Object key, int fromIdx, int toIdx) {
        if (toIdx - fromIdx == 1) {
            final int comp = spec.comparator.compare (key, separators[fromIdx]);
            return comp < 0 ? fromIdx : toIdx;
        }
        else { //TODO optimization: this implementation may perform the same comparison twice
            final int medianIdx = (fromIdx + toIdx) / 2;
            if (spec.comparator.compare (key, separators[medianIdx]) < 0) {
                return lookupKeyInInterval (key, fromIdx, medianIdx); //TODO iteration instead of recursion
            }
            else {
                return lookupKeyInInterval (key, medianIdx, toIdx);
            }
        }
    }

    @SuppressWarnings ("unchecked")
    @Override public AOption<Object> get (Object key) {
        return children[lookupKey (key)].get (key);
    }

    @Override UpdateResult merge (ABTreeMap rightNeighbour, Object separator) {
        final IndexNode right = (IndexNode) rightNeighbour;
        final int len = children.length + right.children.length;

        if (len <= spec.maxNumEntries) {
            final Object[] newSeparators = Arrays.copyOf (separators, children.length + right.children.length - 1);
            newSeparators[separators.length] = separator;
            System.arraycopy (right.separators, 0, newSeparators, separators.length + 1, right.separators.length);

            final ABTreeMap[] newChildren = Arrays.copyOf (children, children.length + right.children.length);
            System.arraycopy (right.children, 0, newChildren, children.length, right.children.length);

            return new UpdateResult (new IndexNode (spec, newSeparators, newChildren), null, null);
        }
        else {
            final int idxMedian = len/2;

            if (children.length < idxMedian) {
                final Object[] leftSeparators = Arrays.copyOf (separators, idxMedian - 1);
                leftSeparators[separators.length] = separator;
                System.arraycopy (right.separators, 0, leftSeparators, separators.length + 1, leftSeparators.length - separators.length - 1);

                final ABTreeMap[] leftChildren = Arrays.copyOf (children, idxMedian);
                System.arraycopy (right.children, 0, leftChildren, children.length, idxMedian - children.length);

                final Object[] rightSeparators = Arrays.copyOfRange (right.separators, leftSeparators.length - separators.length, right.separators.length);
                final ABTreeMap[] rightChildren = Arrays.copyOfRange (right.children, leftChildren.length - children.length, right.children.length);

                return new UpdateResult (
                        new IndexNode (spec, leftSeparators, leftChildren),
                        right.separators[idxMedian - separators.length - 2],
                        new IndexNode (spec, rightSeparators, rightChildren));
            }
            else {
                final Object[] leftSeparators = Arrays.copyOf (separators, idxMedian - 1);
                final ABTreeMap[] leftChildren = Arrays.copyOf (children, idxMedian);

                final Object[] rightSeparators = new Object[len - idxMedian - 1];
                System.arraycopy (separators, idxMedian, rightSeparators, 0, separators.length - leftSeparators.length - 1);
                rightSeparators[separators.length - leftSeparators.length-1] = separator;
                System.arraycopy (right.separators, 0, rightSeparators, separators.length - leftSeparators.length, right.separators.length);

                final ABTreeMap[] rightChildren = new ABTreeMap[len - idxMedian];
                System.arraycopy (children, leftChildren.length, rightChildren, 0, children.length - leftChildren.length);
                System.arraycopy (right.children, 0, rightChildren, children.length - leftChildren.length, right.children.length);

                return new UpdateResult (
                        new IndexNode (spec, leftSeparators, leftChildren),
                        separators[idxMedian-1],
                        new IndexNode (spec, rightSeparators, rightChildren)
                );
            }
        }
    }

    @Override RemoveResult _removed (Object key, Object leftSeparator) {
        final int childIdx = lookupKey (key);

        final RemoveResult childResult = children[childIdx]._removed (key, childIdx == 0 ? leftSeparator : separators[childIdx - 1]);

        if (childResult.newNode == children[childIdx]) {
            return new RemoveResult (this, false, leftSeparator);
        }

        if (childResult.underflowed) {
            if (childIdx == children.length-1) {
                final UpdateResult merged = children[childIdx-1].merge (childResult.newNode, childResult.leftSeparator);

                if (merged.optRight == null) {
                    final Object[] newSeparators = Arrays.copyOf (separators, separators.length - 1);
                    final ABTreeMap[] newChildren = Arrays.copyOf (children, children.length - 1);

                    newChildren[childIdx - 1] = merged.left;

                    return new RemoveResult (new IndexNode (spec, newSeparators, newChildren), newChildren.length < spec.minNumEntries, leftSeparator);
                }
                else {
                    final Object[] newSeparators = Arrays.copyOf (separators, separators.length);
                    final ABTreeMap[] newChildren = Arrays.copyOf (children, children.length);

                    newSeparators[newSeparators.length-1] = merged.separator;
                    newChildren[childIdx-1] = merged.left;
                    newChildren[childIdx] = merged.optRight;

                    return new RemoveResult (new IndexNode (spec, newSeparators, newChildren), newChildren.length < spec.minNumEntries, leftSeparator);
                }
            }
            else {
                final UpdateResult merged = childResult.newNode.merge (children[childIdx+1], separators[childIdx]);

                if (merged.optRight == null) {
                    final Object[] newSeparators = new Object[separators.length - 1];
                    final ABTreeMap[] newChildren = new ABTreeMap[children.length - 1];

                    final Object newLeftSeparator;
                    if (childIdx > 0) {
                        System.arraycopy (separators, 0, newSeparators, 0, childIdx - 1);
                        newSeparators[childIdx - 1] = childResult.leftSeparator;
                        newLeftSeparator = leftSeparator;
                    }
                    else {
                        newLeftSeparator = childResult.leftSeparator;
                    }
                    System.arraycopy (separators, childIdx + 1, newSeparators, childIdx, separators.length - childIdx - 1);

                    System.arraycopy (children, 0, newChildren, 0, childIdx);
                    newChildren[childIdx] = merged.left;
                    System.arraycopy (children, childIdx + 2, newChildren, childIdx + 1, children.length - childIdx - 2);

                    return new RemoveResult (new IndexNode (spec, newSeparators, newChildren), newChildren.length < spec.minNumEntries, newLeftSeparator);
                }
                else {
                    final Object[] newSeparators = Arrays.copyOf (separators, separators.length);
                    final ABTreeMap[] newChildren = Arrays.copyOf (children, children.length);

                    final Object newLeftSeparator;
                    if (childIdx == 0) {
                        newLeftSeparator = childResult.leftSeparator;
                    }
                    else {
                        newLeftSeparator = leftSeparator;
                        newSeparators[childIdx-1] = childResult.leftSeparator;
                    }

                    newSeparators[childIdx] = merged.separator;

                    newChildren[childIdx] = merged.left;
                    newChildren[childIdx+1] = merged.optRight;

                    return new RemoveResult (new IndexNode (spec, newSeparators, newChildren), newChildren.length < spec.minNumEntries, newLeftSeparator);
                }
            }
        }
        else {
            final ABTreeMap[] newChildren = Arrays.copyOf (children, children.length);
            newChildren[childIdx] = childResult.newNode;
            if (childIdx == 0) {
                return new RemoveResult (new IndexNode (spec, separators, newChildren), false, childResult.leftSeparator);
            }
            else {
                final Object[] newSeparators = Arrays.copyOf (separators, separators.length);
                newSeparators[childIdx - 1] = childResult.leftSeparator;
                return new RemoveResult (new IndexNode (spec, newSeparators, newChildren), false, leftSeparator);
            }
        }
    }

    @Override UpdateResult _updated (Object key, Object value) {
        final int childIdx = lookupKey (key);
        final UpdateResult childResult = children[childIdx]._updated (key, value);

        if (childResult.optRight == null) {
            final ABTreeMap[] newChildren = Arrays.copyOf (children, children.length);
            newChildren[childIdx] = childResult.left;
            return new UpdateResult (new IndexNode (spec, separators, newChildren), null, null);
        }
        else {
            if (children.length < spec.maxNumEntries) {
                // max size not reached --> make room for an additional child
                final Object[] newSeparators = new Object[separators.length + 1];
                final ABTreeMap[] newChildren = new ABTreeMap[children.length + 1];

                System.arraycopy (separators, 0, newSeparators, 0, childIdx);
                System.arraycopy (children, 0, newChildren, 0, childIdx);

                newSeparators[childIdx] = childResult.separator;
                newChildren[childIdx] = childResult.left;
                newChildren[childIdx + 1] = childResult.optRight;

                System.arraycopy (separators, childIdx, newSeparators, childIdx + 1, separators.length - childIdx);
                System.arraycopy (children, childIdx + 1, newChildren, childIdx + 2, separators.length - childIdx);

                return new UpdateResult (new IndexNode (spec, newSeparators, newChildren), null, null);
            }
            else {
                // max size reached --> split

                final ABTreeMap[] leftChildren;
                final ABTreeMap[] rightChildren;
                final Object[] leftSeparators;
                final Object[] rightSeparators;

                if (childIdx < spec.maxNumEntries/2) {
                    leftChildren  = new ABTreeMap[spec.maxNumEntries/2 + 1];
                    System.arraycopy (children, 0, leftChildren, 0, childIdx);
                    leftChildren[childIdx] = childResult.left;
                    leftChildren[childIdx+1] = childResult.optRight;
                    System.arraycopy (children, childIdx + 1, leftChildren, childIdx + 2, spec.maxNumEntries / 2 - childIdx - 1);

                    leftSeparators = new Object[spec.maxNumEntries/2];
                    System.arraycopy (separators, 0, leftSeparators, 0, childIdx);
                    leftSeparators[childIdx] = childResult.separator;
                    System.arraycopy (separators, childIdx, leftSeparators, childIdx + 1, spec.maxNumEntries / 2 - childIdx - 1);

                    rightChildren = Arrays.copyOfRange (children, spec.maxNumEntries / 2, spec.maxNumEntries);
                    rightSeparators = Arrays.copyOfRange (separators, spec.maxNumEntries / 2, spec.maxNumEntries - 1);
                }
                else {
                    leftChildren  = Arrays.copyOf (children, spec.maxNumEntries / 2);
                    leftSeparators = Arrays.copyOf (separators, spec.maxNumEntries / 2 - 1);

                    rightChildren = new ABTreeMap[spec.maxNumEntries/2 + 1];
                    System.arraycopy (children, spec.maxNumEntries / 2, rightChildren, 0, childIdx - spec.maxNumEntries / 2);
                    rightChildren[childIdx-spec.maxNumEntries/2]   = childResult.left;
                    rightChildren[childIdx-spec.maxNumEntries/2+1] = childResult.optRight;
                    System.arraycopy (children, childIdx + 1, rightChildren, childIdx - spec.maxNumEntries / 2 + 2, spec.maxNumEntries - childIdx - 1);

                    rightSeparators = new Object[spec.maxNumEntries/2];
                    System.arraycopy (separators, spec.maxNumEntries / 2, rightSeparators, 0, childIdx - spec.maxNumEntries / 2);
                    rightSeparators[childIdx-spec.maxNumEntries/2] = childResult.separator;
                    System.arraycopy (separators, childIdx, rightSeparators, childIdx - spec.maxNumEntries / 2 + 1, spec.maxNumEntries - childIdx - 1);
                }

                return new UpdateResult (new IndexNode (spec, leftSeparators, leftChildren), separators[spec.maxNumEntries/2-1], new IndexNode (spec, rightSeparators, rightChildren));
            }
        }
    }

    @Override public int size () {
        int result = 0;
        for (ABTreeMap child: children) {
            result += child.size ();
        }
        return result;
    }

    @Override public boolean isEmpty () {
        return false;
    }
}
