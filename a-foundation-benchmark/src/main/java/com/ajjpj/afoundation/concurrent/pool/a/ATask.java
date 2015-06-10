package com.ajjpj.afoundation.concurrent.pool.a;

import com.ajjpj.afoundation.collection.immutable.AList;
import com.ajjpj.afoundation.concurrent.pool.AFuture;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;


/**
 * @author arno
 */
public class ATask<T> implements AFuture<T> {
    //TODO make original Callable extractable --> resubmit
    private final AtomicReference<AList<Thread>> waiters = new AtomicReference<> (AList.nil());
    private final AtomicReference<Result> result = new AtomicReference<> ();

    void set(T o) {
        doFinish (new Result (o, null));
    }

    private void doFinish (Result r) {
        if (result.compareAndSet (null, r)) {
            AList<Thread> w;
            while ((w = waiters.getAndSet (AList.nil ())).nonEmpty ()) {
                for (Thread thread: w) {
                    LockSupport.unpark (thread);
                }
            }
        }
    }

    void setException (Throwable th) {
        doFinish (new Result (null, th));
    }

    @Override public boolean isDone () {
        return result.get () != null;
    }

    @Override public T get () throws ExecutionException, InterruptedException {
        await (false, 0);
        return doGet();
    }

    @SuppressWarnings ("unchecked")
    private T doGet() throws ExecutionException {
        final Result res = result.get ();
        if (res.th != null) {
            throw new ExecutionException (res.th);
        }
        return (T) res.value;
    }

//    @Override public T get (long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
//        await (true, unit.toNanos (timeout));
//        if (!isDone ()) {
//            throw new TimeoutException ();
//        }
//        return doGet();
//    }

    void await (boolean timed, long nanos) throws InterruptedException {
        final long deadline = timed ? System.nanoTime() + nanos : 0L;

        {
            AList<Thread> before, after;
            do {
                before = waiters.get ();
                after = before.cons (Thread.currentThread ());
            }
            while (! waiters.compareAndSet (before, after));
        }

        while (true) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }

            final boolean isDone = isDone ();

            if (isDone) {
                return;
            }
            //TODO if (completing) Thread.yield(); --> do not time out (?)

            if (timed) {
                long remaining = deadline - System.nanoTime ();
                if (remaining <= 0) {
                    return;
                }
                LockSupport.parkNanos (this, remaining);
            }
            else {
                LockSupport.park (this);
            }
        }
    }

    static class Result {
        final Object value;
        final Throwable th;

        public Result (Object value, Throwable th) {
            this.value = value;
            this.th = th;
        }
    }
}