package com.ajjpj.afoundation.concurrent.pool.a;

import com.ajjpj.afoundation.concurrent.pool.a.WorkStealingPoolImpl.ASubmittable;
import sun.misc.Contended;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RejectedExecutionException;


/**
 * @author arno
 */
@Contended
class WorkStealingGlobalQueue {
    volatile int qlock;          // 1: locked, else 0
    volatile int base;           // index of next slot for poll
    int top;                     // index of next slot for push

    final int mask;             // bit mask for accessing the element array
    final ASubmittable[] array; // the elements


    WorkStealingGlobalQueue (int capacity) {
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
        if ((raw & WorkStealingLocalQueue.FLAG_SHUTDOWN) != 0) {
            throw new WorkStealingShutdownException ();
        }
        return raw & (~ WorkStealingLocalQueue.FLAG_SHUTDOWN);
    }

    void shutdown() {
        int before;

        do {
            before = base;
        }
        while (! U.compareAndSwapInt (this, QBASE, before, before | WorkStealingLocalQueue.FLAG_SHUTDOWN));
    }

    /**
     * Unless shutting down, adds the given task to a submission queue
     * at submitter's current queue index (modulo submission
     * range). Only the most common path is directly handled in this
     * method. All others are relayed to fullExternalPush.
     *
     * @param task the task. Caller must ensure non-null.
     */
    final void externalPush(ASubmittable task) {
        if (U.compareAndSwapInt (this, QLOCK, 0, 1)) { // lock
            final ASubmittable[] a = array;
            final int am = a.length - 1;
            final int s = top;
            final int n = s - getBase (); //TODO unlock in finally block? --> shutdown exception?
            if (am > n) {
                int j = ((am & s) << ASHIFT) + ABASE;
                U.putOrderedObject(a, j, task);
                top = s + 1;                     // push on to deque
                qlock = 0;

                return;
            }
            qlock = 0;
        }
        fullExternalPush(task);
    }

    /**
     * Full version of externalPush. This method is called, among
     * other times, upon the first submission of the first task to the
     * pool, so must perform secondary initialization.  It also
     * detects first submission by an external thread by looking up
     * its ThreadLocal, and creates a new shared queue if the one at
     * index if empty or contended. The plock lock body must be
     * exception-free (so no try/finally) so we optimistically
     * allocate new queues outside the lock and throw them away if
     * (very rarely) not needed.
     *
     * Secondary initialization occurs when plock is zero, to create
     * workQueue array and set plock to a valid value.  This lock body
     * must also be exception-free. Because the plock seq value can
     * eventually wrap around zero, this method harmlessly fails to
     * reinitialize if workQueues exists, while still advancing plock.
     */
    private void fullExternalPush (ASubmittable task) {
        for (;;) { //TODO refactor into CAS loop?
            if (U.compareAndSwapInt (this, QLOCK, 0, 1)) {
                int s = top;
                boolean submitted = false;
                try {
                    if (array.length <= s+1 - getBase ()) {
                        throw new RejectedExecutionException ();
                    }

                    int j = unsafeArrayOffset (s);
                    U.putOrderedObject (array, j, task);
                    top = s + 1;
                    submitted = true;
                }
                finally {
                    qlock = 0;  // unlock
                }
                if (submitted) {
                    return;
                }
            }
        }
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
                if (U.compareAndSwapObject (array, j, t, null)) {
                    U.putOrderedInt (this, QBASE, b + 1);
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

    private int unsafeArrayOffset (int index) {
        return ((mask & index) << ASHIFT) + ABASE;
    }

    // Unsafe mechanics
    private static final Unsafe U;
    private static final long QBASE;
    private static final long QLOCK;
    private static final int ABASE;
    private static final int ASHIFT;
    static {
        try {
            final Field f = Unsafe.class.getDeclaredField ("theUnsafe");
            f.setAccessible (true);
            U = (Unsafe) f.get (null);


            Class<?> k = WorkStealingGlobalQueue.class;
            Class<?> ak = ForkJoinTask[].class;
            QBASE = U.objectFieldOffset
                    (k.getDeclaredField("base"));
            QLOCK = U.objectFieldOffset
                    (k.getDeclaredField("qlock"));
            ABASE = U.arrayBaseOffset(ak);
            int scale = U.arrayIndexScale(ak);
            if ((scale & (scale - 1)) != 0)
                throw new Error("data type scale not a power of two");
            ASHIFT = 31 - Integer.numberOfLeadingZeros(scale);
        } catch (Exception e) {
            throw new Error(e);
        }
    }

}
