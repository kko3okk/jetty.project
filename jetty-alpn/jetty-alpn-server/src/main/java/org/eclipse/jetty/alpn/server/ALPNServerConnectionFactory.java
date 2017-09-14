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

package org.eclipse.jetty.alpn.server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;

import javax.net.ssl.SSLEngine;

import org.eclipse.jetty.io.AbstractConnection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.io.ssl.ALPNProcessor;
import org.eclipse.jetty.io.ssl.ALPNProcessor.Server;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.NegotiatingServerConnectionFactory;
import org.eclipse.jetty.util.MultiException;
import org.eclipse.jetty.util.annotation.Name;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public class ALPNServerConnectionFactory extends NegotiatingServerConnectionFactory
{
    private static final Logger LOG = Log.getLogger(ALPNServerConnectionFactory.class);

    private final List<Server> processors = new ArrayList<>();

    public ALPNServerConnectionFactory(String protocols)
    {
        this(false, protocols);
    }

    public ALPNServerConnectionFactory(@Name("debug") boolean debug, @Name("protocols") String protocols)
    {
        this(debug, protocols.trim().split(",", 0));
    }
    
    public ALPNServerConnectionFactory(@Name("protocols") String... protocols)
    {
        this(false,protocols);
    }

    public ALPNServerConnectionFactory(@Name("debug") boolean debug, @Name("protocols") String... protocols)
    {
        super("alpn", protocols);
        MultiException me = new MultiException();
        for (Iterator<Server> i = ServiceLoader.load(Server.class).iterator(); i.hasNext();)
        {
            Server processor;
            try
            {
                processor = i.next();
            }
            catch(Throwable th)
            {
                LOG.debug("{}",th.toString());
                me.add(th);
                continue;
            }

            try
            {
                processor.init(debug || LOG.isDebugEnabled());
                processors.add(processor);
            }
            catch(Throwable th)
            {
                LOG.debug("{} -> {}",processor,th.toString());
                me.add(th);
            }
        }
        
        if (LOG.isDebugEnabled())
        {
            LOG.debug("protocols: {}",Arrays.asList(protocols));
            LOG.debug("processors: {}",processors);
        }
        if (processors.isEmpty())
        {
            IllegalStateException ise = new IllegalStateException("No Server ALPNProcessors!");
            for (Throwable th : me.getThrowables())
                ise.addSuppressed(th);
            throw ise;
        }
    }

    @Override
    protected AbstractConnection newServerConnection(Connector connector, EndPoint endPoint, SSLEngine engine, List<String> protocols, String defaultProtocol)
    {
        ALPNServerConnection connection = new ALPNServerConnection(connector, endPoint, engine, protocols, defaultProtocol);
        Server processor = null;
        for (Server p: processors)
        {
            if (p.appliesTo(engine))
            {
                processor = p;
                break;
            }
        }
        if (processor==null)
        {
            LOG.warn("No application processor {} {} {}", processors, engine.getClass(), connection.getEndPoint());
            throw new IllegalStateException("No ALPN processor for "+engine);
        }
        if (LOG.isDebugEnabled())
            LOG.debug("configure {} {} {}", processor, engine, connection.getEndPoint());
        processor.configure(engine, connection);
        return connection;
    }
}
