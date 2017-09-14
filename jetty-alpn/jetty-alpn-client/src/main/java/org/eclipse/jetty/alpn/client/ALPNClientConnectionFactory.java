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

package org.eclipse.jetty.alpn.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.Executor;

import javax.net.ssl.SSLEngine;

import org.eclipse.jetty.io.ClientConnectionFactory;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.io.NegotiatingClientConnectionFactory;
import org.eclipse.jetty.io.ssl.ALPNProcessor;
import org.eclipse.jetty.io.ssl.ALPNProcessor.Client;
import org.eclipse.jetty.io.ssl.SslClientConnectionFactory;
import org.eclipse.jetty.io.ssl.SslConnection.DecryptedEndPoint;
import org.eclipse.jetty.io.ssl.SslHandshakeListener;
import org.eclipse.jetty.util.MultiException;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public class ALPNClientConnectionFactory extends NegotiatingClientConnectionFactory implements SslHandshakeListener
{
    private static final Logger LOG = Log.getLogger(ALPNClientConnectionFactory.class);

    private final Executor executor;
    private final List<String> protocols;
    private final List<Client> processors = new ArrayList<>();

    public ALPNClientConnectionFactory(Executor executor, ClientConnectionFactory connectionFactory, List<String> protocols)
    {
        super(connectionFactory);
        if (protocols.isEmpty())
            throw new IllegalArgumentException("ALPN protocol list cannot be empty");
        this.executor = executor;
        this.protocols = protocols;

        MultiException me = new MultiException();
        for (Iterator<Client> i = ServiceLoader.load(Client.class).iterator(); i.hasNext();)
        {
            ALPNProcessor.Client processor;
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
                processor.init(LOG.isDebugEnabled());
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
            LOG.debug("protocols: {}", Arrays.asList(protocols));
            LOG.debug("processors: {}",processors);
        }
        if (processors.isEmpty())
        {
            IllegalStateException ise = new IllegalStateException("No Client ALPNProcessors!");
            for (Throwable th : me.getThrowables())
                ise.addSuppressed(th);
            throw ise;
        }
    }

    @Override
    public Connection newConnection(EndPoint endPoint, Map<String, Object> context) throws IOException
    {
        SSLEngine engine = (SSLEngine)context.get(SslClientConnectionFactory.SSL_ENGINE_CONTEXT_KEY);

        Client c = null;
        for (Client p: processors)
        {
            if (p.appliesTo(engine))
            {
                c = p;
                break;
            }
        }
        if (c==null)
        {
            LOG.warn("No application processor {} {} {}", processors, engine.getClass(), endPoint);
            throw new IllegalStateException("No ALPN processor for "+engine);
        }
        if (LOG.isDebugEnabled())
            LOG.debug("configure {} {} {}", c, engine, endPoint);

        final Client processor = c;

        ALPNClientConnection connection = new ALPNClientConnection(endPoint, executor, getClientConnectionFactory(),
                engine, context, protocols);

        processor.configure(engine, connection);

        return customize(connection, context);
    }

}
