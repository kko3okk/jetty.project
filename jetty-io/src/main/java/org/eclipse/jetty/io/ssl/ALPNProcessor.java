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

package org.eclipse.jetty.io.ssl;

import java.util.List;

import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLEngine;

import org.eclipse.jetty.io.Connection;

public interface ALPNProcessor
{
    /**
     * Initialize Processor
     * @param debug True if the underlying ALPN implementation should produce debug output
     * @throws Exception Throws if this processor is unavailable (eg missing dependencies or wrong JVM)
     */
    public default void init(boolean debug) throws Exception
    {
    }

    /**
     * Test if this processor can be applied to a specific SSLEngine
     * @param sslEngine The SSLEngine to check
     * @return True if the processor can be applied
     */
    public default boolean appliesTo(SSLEngine sslEngine)
    {
        return true;
    }

    /**
     * Configure the SSLEngine and connection for ALPN
     * @param sslEngine The SSLEngine to configure
     * @param connection The connection to configure
     */
    public default void configure(SSLEngine sslEngine, Connection connection)
    {
    }

    public interface Server extends ALPNProcessor
    {
    }

    public interface Client extends ALPNProcessor
    {
    }

}
