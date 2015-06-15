package com.ajjpj.afoundation.concurrent.pool.a;

import com.ajjpj.afoundation.concurrent.pool.a.WorkStealingPoolImpl.ASubmittable;
import sun.misc.Contended;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.concurrent.RejectedExecutionException;


/**
 * @author arno
 */
@Contended
class WorkStealingLocalQueue {
    public void submit (ASubmittable task) {
        if (task == null) {
            throw new NullPointerException ();
        }
        this.push (task);
    }

    /**
     * Mask for the flag to signify shutdown of the entire pool. This flag
     *  is placed in the 'base' field because that field is read with 'volatile'
     *  semantics on every access, so checking for shutdown incurs minimal
     *  overhead.
     */
    static final int FLAG_SHUTDOWN = 1 << 31;

    final boolean lifo; // mode;        // 0: lifo, > 0: fifo, < 0: shared
    volatile int base;                  // index of next slot for poll
    int top;                            // index of next slot for push

    private final int mask;             // bit mask for accessing elements of the array
    private final ASubmittable[] array; // the elements

    WorkStealingLocalQueue (int capacity, boolean lifo) {
        this.lifo = lifo;

        // Place indices in the center of array
        if (Integer.bitCount (capacity) != 1) {
            throw new IllegalArgumentException ("capacity must be a power of two, is " + capacity);
        }
        if (capacity < 8) {
            throw new IllegalArgumentException ("capacity must be at least 8, is " + capacity);
        }
        if (capacity > (1 << 26)) {
            throw new IllegalArgumentException ("capacity must not be bigger than " + (1<<26) + ", is " + capacity);
        }

        // Place indices in the center of array (that is not yet allocated)
        base = top = capacity / 2;
        array = new ASubmittable[capacity];
        mask = capacity-1;
    }

    private int getBase() {
        final int raw = base;
        if ((raw & FLAG_SHUTDOWN) != 0) {
            throw new WorkStealingShutdownException ();
        }
        return raw & (~ FLAG_SHUTDOWN);
    }

    void checkShutdown () {
        getBase ();
    }

    void shutdown() {
        int before;

        do {
            before = base;
        }
        while (! U.compareAndSwapInt (this, QBASE, before, before | FLAG_SHUTDOWN));
    }

    /**
     * Pushes a task. Call only by owner. (The shared-queue version is embedded in method externalPush.)
     *
     * @param task the task. Caller must ensure non-null.
     * @throws java.util.concurrent.RejectedExecutionException if array cannot be resized
     */
    final void push (ASubmittable task) {
        final int s = top;

        // get the value of 'base' early to detect shutdown
        final int base = getBase ();

        U.putOrderedObject (array, unsafeArrayOffset (s), task);
        top = s+1;
        final int n = top - base;
        if (n >= mask) { //TODO is this racy? --> potentially overwriting existing work if capacity is exceeded?
            throw new RejectedExecutionException ();
        }
    }


    /**
     * Takes next task, if one exists, in LIFO order.  Call only
     * by owner in unshared queues.
     */
    final ASubmittable pop() {
//            while (true) {
//                final int s = top-1;
//                if (s - getBase () < 0) {
//                    break;
//                }
//
//                final long j = ((m & s) << ASHIFT) + ABASE;
//                final ASubmittable t = (ASubmittable) U.getObject(a, j);
//                if (t == null) {
//                    break;
//                }
//                if (U.compareAndSwapObject(a, j, t, null)) {
//                    top = s;
//                    return t;
//                }
//            }

        int s;
        while ((s = top - 1) - getBase () >= 0) { //TODO how to simplify this?
            long j = unsafeArrayOffset (s);
            final ASubmittable t = (ASubmittable) U.getObject (array, j);
            if (t == null) {
                break;
            }
            if (U.compareAndSwapObject (array, j, t, null)) {
                top = s;
                return t;
            }
        }
        return null;
    }

    /**
     * Takes next task, if one exists, in FIFO order.
     */
    final ASubmittable poll() {
        int b;

        while ((b = getBase ()) - top < 0) {
            final int j = unsafeArrayOffset (b);
            final ASubmittable t = (ASubmittable) U.getObjectVolatile (array, j);
            if (t != null) {
                if (U.compareAndSwapObject(array, j, t, null)) {
                    U.putOrderedInt(this, QBASE, b + 1);
                    return t;
                }
            }
            else if (getBase () == b) {
                if (b + 1 == top)
                    break;

                Thread.yield(); // wait for lagging update (very rare)
            }
        }
        return null;
    }

    /**
     * Takes next task, if one exists, in order specified by mode.
     */
    final ASubmittable nextLocalTask() {
        return lifo ? pop() : poll();
    }


    private int unsafeArrayOffset (int index) {
        return ((mask & index) << ASHIFT) + ABASE;
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe U;
    private static final long QBASE;
    private static final int ABASE;
    private static final int ASHIFT;
    static {
        try {
            final Field f = sun.misc.Unsafe.class.getDeclaredField ("theUnsafe");
            f.setAccessible (true);
            U = (Unsafe) f.get (null);

            Class<?> k = WorkStealingLocalQueue.class;
            Class<?> ak = ASubmittable[].class;
            QBASE = U.objectFieldOffset (k.getDeclaredField("base"));
            ABASE = U.arrayBaseOffset (ak);
            int scale = U.arrayIndexScale (ak);
            if ((scale & (scale - 1)) != 0) {
                throw new Error ("data type scale not a power of two");
            }
            ASHIFT = 31 - Integer.numberOfLeadingZeros(scale);
        } catch (Exception e) {
            throw new Error(e);
        }
    }
}
