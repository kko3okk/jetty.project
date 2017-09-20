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

package org.eclipse.jetty.alpn.conscrypt.client;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.List;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

import org.conscrypt.OpenSSLProvider;
import org.eclipse.jetty.alpn.client.ALPNClientConnection;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.ssl.ALPNProcessor;
import org.eclipse.jetty.io.ssl.SslConnection;
import org.eclipse.jetty.io.ssl.SslHandshakeListener;
import org.eclipse.jetty.util.JavaVersion;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

public class ConscryptClientALPNProcessor implements ALPNProcessor.Client
{
    private static final Logger LOG = Log.getLogger(ConscryptClientALPNProcessor.class);

    @Override
    public void init(boolean debug)
    {
        if (Security.getProvider("Conscrypt")==null)
        {
            LOG.debug("Security.addProvider(\"Conscrypt\")");
            Security.addProvider(new OpenSSLProvider());
        }

        if (debug)
            LOG.setDebugEnabled(true);
    }

    @Override
    public boolean appliesTo(SSLEngine sslEngine)
    {
        return sslEngine.getClass().getName().startsWith("org.conscrypt.");
    }

    @Override
    public void configure(SSLEngine sslEngine, Connection connection)
    {
        try
        {
            ALPNClientConnection alpn = (ALPNClientConnection)connection;

            SSLParameters sslParameters = sslEngine.getSSLParameters();
            Method method = sslParameters.getClass().getMethod("setApplicationProtocols", String[].class);
            method.setAccessible(true);
            List<String> protocols = alpn.getProtocols();
            method.invoke(sslParameters, protocols.toArray(new String[protocols.size()]));
            sslEngine.setSSLParameters(sslParameters);
            ((SslConnection.DecryptedEndPoint)connection.getEndPoint()).getSslConnection()
                    .addHandshakeListener(new ALPNListener(alpn));
        }
        catch(Throwable e)
        {
            throw new RuntimeException(e);
        }
    }

    private final class ALPNListener implements SslHandshakeListener
    {
        private final ALPNClientConnection alpnConnection;

        private ALPNListener(ALPNClientConnection connection)
        {
            alpnConnection = connection;
        }

        @Override
        public void handshakeSucceeded(Event event)
        {
            String protocol = null;
            try
            {
                SSLEngine sslEngine = alpnConnection.getSSLEngine();
                Method method = sslEngine.getClass().getMethod("getAlpnSelectedProtocol");
                protocol = new String((byte[])method.invoke(sslEngine), StandardCharsets.US_ASCII);
            }
            catch (Throwable e)
            {
                LOG.warn(e);
            }

            alpnConnection.selected(protocol);
        }
    }
}
