//
//  ========================================================================
//  Copyright (c) 1995-2017 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.util.thread;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

import org.eclipse.jetty.util.annotation.ManagedAttribute;
import org.eclipse.jetty.util.annotation.ManagedObject;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

/**
 * An Executor using preallocated/reserved Threads from a wrapped Executor.
 * <p>Calls to {@link #execute(Runnable)} on a {@link ReservedThreadExecutor} will either succeed
 * with a Thread immediately being assigned the Runnable task, or fail if no Thread is
 * available.
 * <p>Threads are reserved lazily, with a new reserved thread being allocated from a
 * wrapped {@link Executor} when an execution fails.  If the {@link #setIdleTimeout(long, TimeUnit)}
 * is set to non zero (default 1 minute), then the reserved thread pool will shrink by 1 thread
 * whenever it has been idle for that period.
 */
@ManagedObject("A pool for reserved threads")
public class ReservedThreadExecutor extends AbstractLifeCycle implements Executor
{
    private static final Logger LOG = Log.getLogger(ReservedThreadExecutor.class);

    private final Executor _executor;
    private final Locker _locker = new Locker();
    private final ReservedThread[] _stack;
    private int _size;
    private int _pending;
    private long _idleTime = 1L;
    private TimeUnit _idleTimeUnit = TimeUnit.MINUTES;

    public ReservedThreadExecutor(Executor executor)
    {
        this(executor,1);
    }

    /**
     * @param executor The executor to use to obtain threads
     * @param capacity The number of threads to preallocate. If less than 0 then capacity
     * is calculated based on a heuristic from the number of available processors and
     * thread pool size.
     */
    public ReservedThreadExecutor(Executor executor,int capacity)
    {
        _executor = executor;

        if (capacity < 0)
        {
            int cpus = Runtime.getRuntime().availableProcessors();
            if (executor instanceof ThreadPool.SizedThreadPool)
            {
                int threads = ((ThreadPool.SizedThreadPool)executor).getMaxThreads();
                capacity = Math.max(1, Math.min(cpus, threads / 8));
            }
            else
            {
                capacity = cpus;
            }
        }

        _stack = new ReservedThread[capacity];
    }

    public Executor getExecutor()
    {
        return _executor;
    }

    @ManagedAttribute(value = "max number of reserved threads", readonly = true)
    public int getCapacity()
    {
        return _stack.length;
    }

    @ManagedAttribute(value = "available reserved threads", readonly = true)
    public int getAvailable()
    {
        try (Locker.Lock lock = _locker.lock())
        {
            return _size;
        }
    }

    @ManagedAttribute(value = "pending reserved threads", readonly = true)
    public int getPending()
    {
        try (Locker.Lock lock = _locker.lock())
        {
            return _pending;
        }
    }

    /**
     * Set the idle timeout for shrinking the reserved thread pool
     * @param idleTime Time to wait before shrinking
     * @param idleTimeUnit Time units for idle timeout
     */
    public void setIdleTimeout(long idleTime, TimeUnit idleTimeUnit)
    {
        if (isRunning())
            throw new IllegalStateException();
        _idleTime = idleTime;
        _idleTimeUnit = idleTimeUnit;
    }

    @Override
    public void doStop() throws Exception
    {
        try (Locker.Lock lock = _locker.lock())
        {
            while (_size>0)
            {
                ReservedThread thread = _stack[--_size];
                _stack[_size] = null;
                thread._wakeup.signal();
            }
        }
    }

    @Override
    public void execute(Runnable task) throws RejectedExecutionException
    {
        if (!tryExecute(task))
            throw new RejectedExecutionException();
    }

    /**
     * @param task The task to run
     * @return True iff a reserved thread was available and has been assigned the task to run.
     */
    public boolean tryExecute(Runnable task)
    {
        if (task==null)
            return false;

        try (Locker.Lock lock = _locker.lockIfNotHeld())
        {
            if (_size==0)
            {
                if (_pending<_stack.length)
                {
                    _executor.execute(new ReservedThread());
                    _pending++;
                }
                return false;
            }

            ReservedThread thread = _stack[--_size];
            _stack[_size] = null;

            if (_size==0 && _pending<_stack.length)
            {
                _executor.execute(new ReservedThread());
                _pending++;
            }

            thread._task = task;
            thread._wakeup.signal();

            return true;
        }
        catch(RejectedExecutionException e)
        {
            LOG.ignore(e);
            return false;
        }
    }

    @Override
    public String toString()
    {
        try (Locker.Lock lock = _locker.lock())
        {
            return String.format("%s{s=%d,p=%d}",super.toString(),_size,_pending);
        }
    }

    private class ReservedThread implements Runnable
    {
        private Condition _wakeup = null;
        private Runnable _task = null;

        private Runnable reservedWait()
        {
            while (_task==null)
            {
                if (!isRunning())
                    return null;
                try
                {
                    if (_idleTime==0)
                        _wakeup.await();
                    else
                    {
                        if (!_wakeup.await(_idleTime, _idleTimeUnit))
                        {
                            // We are idle, so we are no longer needed
                            return null;
                        }
                    }
                }
                catch(InterruptedException e)
                {
                    LOG.ignore(e);
                }
            }

            Runnable task = _task;
            _task = null;
            return task;
        }

        @Override
        public void run()
        {
            while (true)
            {
                Runnable task = null;
                try (Locker.Lock lock = _locker.lock())
                {
                    // if this is our first loop, decrement pending count
                    if (_wakeup==null)
                    {
                        _pending--;
                        _wakeup = _locker.newCondition();
                    }

                    // Exit if no longer running or there now too many preallocated threads
                    if (!isRunning() || _size>=_stack.length)
                        break;

                    // Insert ourselves in the stack
                    _stack[_size++] = this;

                    // Wait for a task
                    if (LOG.isDebugEnabled())
                        LOG.debug("{} waiting", this);
                    task = reservedWait();
                    if (LOG.isDebugEnabled())
                        LOG.debug("{} woken up {}", this, task);

                    // If no task, we are not needed anymore
                    if (task==null && isRunning())
                    {
                        for (int i=0; i<_size; i++)
                        {
                            if (_stack[i]==this)
                            {
                                if (LOG.isDebugEnabled())
                                    LOG.debug("{} shrink {}", this, i);
                                System.arraycopy(_stack, i + 1, _stack, i, --_size);
                                return;
                            }
                        }
                        throw new IllegalStateException();
                    }
                }

                if (task!=null)
                {
                    try
                    {
                        task.run();
                    }
                    catch (Throwable e)
                    {
                        LOG.warn(e);
                    }
                }
            }
        }
    }
}
