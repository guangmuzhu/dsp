/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Copyright (c) 2013, 2014 by Delphix. All rights reserved.
 */

package com.delphix.session.test;

import com.delphix.appliance.logger.Logger;
import com.delphix.session.control.NexusStats;
import com.delphix.session.net.NetServerConfig;
import com.delphix.session.sasl.*;
import com.delphix.session.service.*;
import com.delphix.session.ssl.*;
import com.delphix.session.util.AsyncFuture;
import com.delphix.session.util.AsyncTracker;
import com.delphix.session.util.ExecutorUtil;
import com.delphix.session.util.ThreadFuture;
import com.google.common.collect.ImmutableList;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;

import static com.delphix.session.service.ServiceOption.*;
import static com.delphix.session.service.ServiceProtocol.PORT;
import static com.delphix.session.service.ServiceProtocol.PROTOCOL;
import static org.testng.Assert.*;

public class ServiceTest extends SessionBaseTest {

    private final Logger logger = Logger.getLogger(ServiceTest.class);

    private static final String SERVER = "server.domain";

    private static final String HELLO_NAME = "hello";
    private static final String HELLO_DESC = "hello service";
    private static final UUID HELLO_UUID = UUID.randomUUID();

    private static final String DELAY_NAME = "delay";
    private static final String DELAY_DESC = "delay service";
    private static final UUID DELAY_UUID = UUID.randomUUID();

    private static final int MB = 1024 * 1024;
    private static final int DATA_SIZE = 3 * MB;
    private static final int MAX_REQUESTS = 64;

    private ServiceType helloService;
    private ServiceType delayService;

    private ServiceTerminus clientTerminus = new ServiceUUID("client");

    @Autowired
    private ServerManager serverManager;

    @Autowired
    private ClientManager clientManager;

    private ExecutorService executor;

    private InetAddress localhost;

    private Semaphore doneSema;

    // Server keystore
    private String keyStore;
    private String keyPass;
    private String storePass;

    // Throughput test parameters
    private String testServer; // Server host name (default localhost)
    private String testData; // Data file path
    private boolean testStats; // Performance stats (default false)
    private boolean multiSessions; // Use multiple sessions (default false)
    private boolean serverMode; // Run in server mode (default false)
    private int testStreams; // Number of data streams (default one)
    private int[] testPorts; // Local ports to bind to (default null)

    // Client truststore
    private String trustStore;
    private String trustPass;

    @BeforeClass
    public void init() throws FileNotFoundException, IOException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Properties testprops = new Properties();
        testprops.load(cl.getResourceAsStream("test.properties"));

        String password = testprops.getProperty("password");

        // Initialize key store
        this.keyStore = cl.getResource(testprops.getProperty("keystore")).getPath();
        this.keyPass = password;
        this.storePass = password;

        // Initialize throughput tests
        this.testServer = testprops.getProperty("server");
        this.testData = cl.getResource(testprops.getProperty("data")).getPath();
        this.testStats = Boolean.parseBoolean(testprops.getProperty("stats"));
        this.multiSessions = Boolean.parseBoolean(testprops.getProperty("multisess"));
        this.serverMode = Boolean.parseBoolean(testprops.getProperty("mode"));
        this.testStreams = Integer.parseInt(testprops.getProperty("streams"));

        // Comma separated port list
        String portList = testprops.getProperty("ports");

        if (portList != null) {
            String[] ports = portList.split(",");

            testPorts = new int[ports.length];
            assertEquals(ports.length, testStreams);

            for (int i = 0; i < ports.length; i++) {
                testPorts[i] = Integer.parseInt(ports[i]);
            }
        }

        // Initialize trust store
        this.trustStore = cl.getResource(testprops.getProperty("truststore")).getPath();
        this.trustPass = password;

        // Initialize service types
        helloService = new ServiceType(HELLO_UUID, HELLO_NAME, HELLO_DESC);
        delayService = new ServiceType(DELAY_UUID, DELAY_NAME, DELAY_DESC);

        try {
            localhost = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            fail("failed to get local host", e);
        }

        clientManager.start();
        serverManager.start();

        executor = Executors.newCachedThreadPool();
    }

    @AfterClass
    public void fini() {
        clientManager.stop();
        serverManager.stop();

        try {
            ExecutorUtil.shutdown(executor);
        } catch (TimeoutException e) {
            e.printStackTrace();
        }
    }

    @BeforeMethod
    public void initTest() {
        // Create a new semaphore to track the completion of each test
        doneSema = new Semaphore(0);

        // Register the services
        ServerConfig config = initServiceConfig(new HelloService(helloService));
        serverManager.register(config);

        config = initServiceConfig(new HelloDelayService(delayService));
        serverManager.register(config);

        // Run in server mode
        if (serverMode) {
            setupThroughputServer();
            sleep(Long.MAX_VALUE);
        }
    }

    @AfterMethod
    public void finiTest() {
        // Close the clients synchronously
        Set<ClientNexus> clients = clientManager.getClients();

        for (ClientNexus client : clients) {
            close(client);
        }

        // Shutdown the services
        Set<Server> servers = serverManager.getServers();

        for (Server server : servers) {
            server.shutdown();
        }

        // We don't expect permits left if the test completed successfully
        int permits = doneSema.availablePermits();

        if (permits > 0) {
            logger.infof("doneSema has %d permits available", permits);
        }
    }

    private void login(ClientNexus client) {
        LoginFuture future = client.login();

        try {
            future.get();
        } catch (InterruptedException e) {
            fail("login interrupted", e);
        } catch (ExecutionException e) {
            fail("login failed", e.getCause());
        }
    }

    private Throwable loginFail(ClientNexus client) {
        Throwable t = null;

        LoginFuture future = client.login();

        try {
            future.get();
            fail("login should have failed");
        } catch (InterruptedException e) {
            fail("login interrupted", e);
        } catch (ExecutionException e) {
            t = e.getCause();
        }

        return t;
    }

    private void close(ClientNexus client) {
        CloseFuture future = client.close();

        try {
            future.get();
        } catch (InterruptedException e) {
            fail("interrupted while closing", e);
        } catch (ExecutionException e) {
            fail("failed to close", e.getCause());
        }
    }

    private void kill(ClientNexus nexus) {
        // Close all the transports to bypass logout
        closeTransports(nexus, 1.0);

        // Close the session
        close(nexus);
    }

    private void closeTransports(ServiceNexus nexus, double chance) {
        Collection<ServiceTransport> xports = nexus.getTransports();
        Iterator<ServiceTransport> iter = xports.iterator();

        while (iter.hasNext()) {
            ServiceTransport xport = iter.next();

            // Randomly victimize a transport
            if (Math.random() < chance) {
                xport.close();
            }
        }

        xports.clear();
    }

    private void sleep(long delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            fail("test interrupted", e);
        }
    }

    /**
     * This is typically invoked at the end of each unit test run to wait for all asynchronous work spawned from the
     * test to complete. Each asynchronous work item, or runnable instance, must call notifyDone upon completion.
     */
    private void awaitDone(int permits) {
        try {
            doneSema.acquire(permits);
        } catch (InterruptedException e) {
            fail("test interrupted");
        }
    }

    private class ThroughputSettings {

        private final boolean encrypt;
        private final boolean digest;
        private final boolean compress;
        private final int bandwidthLimit;

        public ThroughputSettings(boolean encrypt, boolean digest, boolean compress, int bandwidthLimit) {
            this.encrypt = encrypt;
            this.digest = digest;
            this.compress = compress;
            this.bandwidthLimit = bandwidthLimit;
        }

        public List<String> getDigestMethods() {
            return digest ? Arrays.asList("DIGEST_ADLER32") : Arrays.asList("DIGEST_NONE");
        }

        public List<String> getCompressMethods() {
            return compress ? Arrays.asList("COMPRESS_LZ4") : Arrays.asList("COMPRESS_NONE");
        }

        public TransportSecurityLevel getTlsLevel() {
            return encrypt ? TransportSecurityLevel.ENCRYPTION : null;
        }

        public int getBandwidthLimit() {
            return bandwidthLimit;
        }

        @Override
        public String toString() {
            return "encrypt " + encrypt + " digest " + digest + " compress " + compress;
        }
    }

    @DataProvider(name = "throughputSettings")
    private Object[][] getThroughputSettings() {
        return new Object[][] {
                // encryption, header digest, frame digest, payload digest, compression, block
                // group-1:
                { new ThroughputSettings(false, false, false, 0) },
                // group-2:
                { new ThroughputSettings(false, true, false, 0) },
                // group-3:
                { new ThroughputSettings(false, true, true, 0) },
                // group-4:
                { new ThroughputSettings(true, false, false, 0) },
                // group-5:
                { new ThroughputSettings(true, true, true, 0) },
        };
    }

    @DataProvider(name = "bandwidthLimitSettings")
    private Object[][] getBandwidthLimitSettings() {
        return new Object[][] {
                // encryption, header digest, frame digest, payload digest, compression, block
                // group-1:
                { new ThroughputSettings(false, false, false, 25) },
                // group-2:
                { new ThroughputSettings(false, false, false, 50) },
                // group-3:
                { new ThroughputSettings(false, false, false, 100) },
                // group-4:
                { new ThroughputSettings(false, false, false, 200) },
                // group-5:
                { new ThroughputSettings(false, false, true, 25) },
                // group-6:
                { new ThroughputSettings(false, false, true, 50) },
                // group-7:
                { new ThroughputSettings(false, false, true, 100) },
                // group-8:
                { new ThroughputSettings(false, false, true, 200) },
        };
    }

    private byte[] getTestData(String path, int size) throws Exception {
        byte[] data = new byte[size];

        File file = new File(path);
        FileInputStream fis = null;

        fis = new FileInputStream(file);

        try {
            int off = 0;
            int len = data.length;

            while (len > 0) {
                int bytesRead = fis.read(data, off, len);

                if (bytesRead < 0) {
                    fail("file not large enough");
                }

                off += bytesRead;
                len -= bytesRead;
            }
        } finally {
            fis.close();
        }

        return data;
    }

    private long sendData(ServiceNexus[] clients, final byte[] data, final int recordSize, final int count) {
        final AsyncTracker tracker = new AsyncTracker();

        long start = System.nanoTime();

        for (int i = 0; i < clients.length; i++) {
            final ServiceNexus client = clients[i];

            final Runnable stream = new Runnable() {

                @Override
                public void run() {
                    sendStream(client, data, recordSize, count);
                }
            };

            AsyncFuture<?> future = new ThreadFuture<Object>(stream, null) {

                @Override
                public void done() {
                    tracker.done(stream);
                }
            };

            executor.execute(future);

            tracker.track(stream, future);
        }

        tracker.awaitDone();

        return System.nanoTime() - start;
    }

    private void sendStream(ServiceNexus client, byte[] data, int recordSize, int count) {
        final AsyncTracker tracker = new AsyncTracker(MAX_REQUESTS);

        for (int i = 0; i < count; i++) {
            // Take a random record from the test data
            int offset = (int) (Math.random() * (data.length - recordSize));
            ByteBuffer payload = ByteBuffer.wrap(data, offset, recordSize);

            final ServiceRequest request = new HelloRequest(payload);

            ServiceFuture future = client.execute(request, new Runnable() {

                @Override
                public void run() {
                    tracker.done(request);
                }
            });

            tracker.track(request, future);
        }

        tracker.awaitDone();
    }

    private void resetStats(ServiceNexus[] clients) {
        for (ServiceNexus client : clients) {
            client.resetStats();
        }
    }

    private long getStat(ServiceNexus client, String stat) {
        NexusStats stats = client.getStats();
        return Long.parseLong(stats.getStat(stat).toString());
    }

    private long getAggregateStat(ServiceNexus[] clients, String stat) {
        long value = 0;

        if (multiSessions) {
            for (ServiceNexus client : clients) {
                value += getStat(client, stat);
            }
        } else {
            value = getStat(clients[0], stat);
        }

        return value;
    }

    private double getDataThroughput(ServiceNexus[] clients, long timeNS) {
        double dataMB = getAggregateStat(clients, "client.sum.totalBytes") / MB;
        double seconds = (double) timeNS / TimeUnit.SECONDS.toNanos(1);

        return dataMB / seconds;
    }

    private double getCompressedThroughput(ServiceNexus[] clients, long timeNS) {
        double compMB = getAggregateStat(clients, "client.sum.totalCompressedBytes") / MB;
        double seconds = (double) timeNS / TimeUnit.SECONDS.toNanos(1);

        return compMB / seconds;
    }

    private double getCompressionRatio(ServiceNexus[] clients) {
        double dataBytes = getAggregateStat(clients, "client.sum.totalBytes");
        double compBytes = getAggregateStat(clients, "client.sum.totalCompressedBytes");

        return 100 * compBytes / dataBytes;
    }

    private double getPendingRatio(ServiceNexus[] clients) {
        double completed = getAggregateStat(clients, "client.sum.totalCompleted");
        double pending = getAggregateStat(clients, "client.sum.totalPending");

        return 100 * pending / completed;
    }

    private void testDataThroughput(ServiceNexus[] clients, int recordSize) {
        byte[] data = null;

        try {
            data = getTestData(testData, DATA_SIZE);
        } catch (Exception e) {
            fail("", e);
        }

        int count = 10;
        long timeNS;

        if (testStats) {
            System.out.format("-----------\n");
            System.out.format("     block: %d\n", recordSize);

            count = 10000;
        }

        // Warm up 3 times and look for convergence
        for (int j = 0; j < 3; j++) {
            resetStats(clients);
            timeNS = sendData(clients, data, recordSize, count);

            if (testStats) {
                System.out.format("   warm-up: raw %.2f MB/s, compressed %.2f MB/s, compression %.2f%%\n",
                        getDataThroughput(clients, timeNS), getCompressedThroughput(clients, timeNS),
                        getCompressionRatio(clients));
            }
        }

        // Real test
        resetStats(clients);
        timeNS = sendData(clients, data, recordSize, count * 10);

        if (testStats) {
            System.out.format("  test-run: raw %.2f MB/s, compressed %.2f MB/s, compression %.2f%%, pending %.2f%%\n",
                    getDataThroughput(clients, timeNS), getCompressedThroughput(clients, timeNS),
                    getCompressionRatio(clients), getPendingRatio(clients));
        }
    }

    private ServiceNexus setupThroughputClient(ServiceTerminus clientTerminus, ThroughputSettings settings,
            TransportAddress... address) {
        ClientConfig spec = initServiceSpec(clientTerminus, new HelloService(helloService), address);

        // Configure the client with SSL settings
        try {
            SSLClientParams params = new SSLClientParams();

            params.setTrustStorePath(trustStore);
            params.setTrustStorePass(trustPass);
            params.setTlsLevel(settings.getTlsLevel());

            SSLClientContext ssl = SSLContextFactory.getClientContext(params);
            spec.setSslContext(ssl);
        } catch (Exception e) {
            fail("failed to initialize ssl client context", e);
        }

        // Set up the protocol options according to the throughput settings
        ServiceOptions proposal = spec.getOptions();

        proposal.setOption(SYNC_DISPATCH, Boolean.TRUE);
        proposal.setOption(BANDWIDTH_LIMIT, settings.getBandwidthLimit());

        proposal.setOption(HEADER_DIGEST, settings.getDigestMethods());
        proposal.setOption(FRAME_DIGEST, settings.getDigestMethods());
        proposal.setOption(PAYLOAD_DIGEST, settings.getDigestMethods());
        proposal.setOption(DIGEST_DATA, false);

        proposal.setOption(PAYLOAD_COMPRESS, settings.getCompressMethods());

        // Allocate MAX_REQUESTS slots for each transport connection in the session
        proposal.setOption(FORE_QUEUE_DEPTH, MAX_REQUESTS * address.length);
        proposal.setOption(FORE_MAX_REQUEST, 1024 * 256);
        proposal.setOption(SOCKET_SEND_BUFFER, 4 * 1024 * 1024);

        // Create the session
        ClientNexus client = clientManager.create(spec);

        login(client);

        logger.infof("%s: nexus options %s", client, client.getOptions());

        Collection<ServiceTransport> xports = client.getTransports();

        for (ServiceTransport xport : xports) {
            logger.infof("%s: transport options - %s", xport, xport.getOptions());
        }

        return client;
    }

    private ServiceNexus[] setupThroughputClients(ThroughputSettings settings) {
        // Set up the sessions for test streams
        ServiceNexus[] clients = new ServiceNexus[testStreams];

        try {
            if (multiSessions) {
                for (int i = 0; i < testStreams; i++) {
                    ServiceTerminus terminus = new ServiceUUID("throughput-" + i);

                    if (testPorts != null) {
                        clients[i] = setupThroughputClient(terminus, settings,
                                new TransportAddress(testServer, PORT, testPorts[i]));
                    } else {
                        clients[i] = setupThroughputClient(terminus, settings, new TransportAddress(testServer));
                    }
                }
            } else {
                TransportAddress[] addresses = new TransportAddress[testStreams];

                for (int i = 0; i < testStreams; i++) {
                    if (testPorts != null) {
                        addresses[i] = new TransportAddress(testServer, PORT, testPorts[i]);
                    } else {
                        addresses[i] = new TransportAddress(testServer);
                    }
                }

                ServiceNexus client = setupThroughputClient(clientTerminus, settings, addresses);

                for (int i = 0; i < testStreams; i++) {
                    clients[i] = client;
                }
            }
        } catch (UnknownHostException e) {
            fail("failed to resolve address", e);
        }

        return clients;
    }

    private void setupThroughputServer() {
        Server server = serverManager.locate(helloService.getServiceName());
        ServerConfig config = server.getConfig();

        // Configure the server with SSL settings
        try {
            SSLServerParams params = new SSLServerParams(keyStore, keyPass, storePass);
            SSLServerContext ssl = SSLContextFactory.getServerContext(params);
            config.setSslContext(ssl);
        } catch (Exception e) {
            fail("failed to initialize ssl server context", e);
        }

        // Configure the server to accept all digest and compress settings
        ServiceOptions offer = config.getOptions();

        offer.setOption(HEADER_DIGEST, Arrays.asList("DIGEST_CRC32", "DIGEST_ADLER32", "DIGEST_NONE"));
        offer.setOption(FRAME_DIGEST, Arrays.asList("DIGEST_CRC32", "DIGEST_ADLER32", "DIGEST_NONE"));
        offer.setOption(PAYLOAD_DIGEST, Arrays.asList("DIGEST_CRC32", "DIGEST_ADLER32", "DIGEST_NONE"));
        offer.setOption(PAYLOAD_COMPRESS, Arrays.asList("COMPRESS_LZ4", "COMPRESS_DEFLATE", "COMPRESS_NONE"));

        // Set the maximum allowed slot table size and let the client negotiate down
        offer.setOption(FORE_QUEUE_DEPTH, 4096);
        offer.setOption(FORE_MAX_REQUEST, 1024 * 256);
        offer.setOption(SOCKET_RECEIVE_BUFFER, 4 * 1024 * 1024);
    }

    @Test(dataProvider = "throughputSettings")
    public void testDataThroughput(ThroughputSettings settings) {
        // Display the throughput test settings
        logger.infof("%s", settings);

        // Set up the throughput server settings
        setupThroughputServer();

        // Set up the throughput client(s)
        ServiceNexus[] clients = setupThroughputClients(settings);

        if (testStats) {
            System.out.format("\n=== start throughout test ===\n");
            System.out.format("   encrypt: %s\n", settings.getTlsLevel());
            System.out.format("    digest: %s\n", settings.getDigestMethods());
            System.out.format("  compress: %s\n", settings.getCompressMethods());
        }

        // Throughput test block sizes
        int[] sizes = { 1024, 4096, 8192, 16384, 32768, 65536 };

        for (int i = 0; i < sizes.length; i++) {
            testDataThroughput(clients, sizes[i]);
        }
    }

    @Test(dataProvider = "bandwidthLimitSettings")
    public void testBandwidthLimit(ThroughputSettings settings) {
        // Display the throughput test settings
        logger.infof("%s", settings);

        // Set up the throughput server settings
        setupThroughputServer();

        // Set up the throughput client(s)
        ServiceNexus[] clients = setupThroughputClients(settings);

        if (testStats) {
            System.out.format("\n=== start bandwidth limit test ===\n");
            System.out.format("  bandwidth limit: %d\n", settings.getBandwidthLimit());
            System.out.format("         compress: %s\n", settings.getCompressMethods());
        }

        // Throughput test block sizes
        int[] sizes = { 32768 };

        for (int i = 0; i < sizes.length; i++) {
            testDataThroughput(clients, sizes[i]);
        }
    }

    @Test
    public void testRegister() {
        // Dump a list of registered services
        Set<ServiceTerminus> termini = serverManager.getTermini();

        logger.info("registered termini with the service manager: ");

        for (ServiceTerminus terminus : termini) {
            logger.info(terminus);
        }
    }

    @Test
    public void testLoginDigest() {
        // Create the session
        ClientConfig spec = initServiceSpec(new HelloService(helloService));

        ClientNexus client = clientManager.create(spec);

        login(client);
    }

    @Test
    public void testLoginPlain() {
        // Create the session
        ClientConfig spec = initServiceSpec(new HelloService(helloService));

        PlainClient sasl = new PlainClient(TEST_USERNAME, TEST_PASSWORD);
        spec.setSaslMechanism(sasl);

        ClientNexus client = clientManager.create(spec);

        login(client);
    }

    @Test
    public void testLoginMaxConnLimit() {
        // Create the session
        ClientConfig spec = initServiceSpec(new HelloService(helloService), 3);

        ServiceOptions options = spec.getOptions();
        options.setOption(MAX_TRANSPORTS, 2);

        ClientNexus client = clientManager.create(spec);

        login(client);

        // Wait a little bit for the non-leading connections to login
        sleep(500);

        /*
         * We must never exceed the negotiated MCS limit of 2, which implies that the session should be in a
         * connected but degraded state.
         */
        assertTrue(client.isConnected());
        assertTrue(client.isDegraded());
    }

    /**
     * Scan for an unused port.
     */
    private int portScan() {
        int localPort = (int) (Math.random() * 1000) + 62626;

        do {
            Socket socket = new Socket();

            try {
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress(localPort));
                assertTrue(socket.isBound());
                break;
            } catch (IOException e) {
                logger.infof(e, "failed to bind to port %d - try next", localPort);
                localPort++;
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    fail("failed to close socket", e);
                }
            }
        } while (localPort < 65536);

        if (localPort >= 65536) {
            fail("failed to find unused port");
        }

        logger.infof("unused local port %d found", localPort);

        return localPort;
    }

    @Test
    public void testLoginLocalBinding() {
        int localPort = portScan();

        // Create the session
        ClientConfig spec = initServiceSpec(new HelloService(helloService));

        PlainClient sasl = new PlainClient(TEST_USERNAME, TEST_PASSWORD);
        spec.setSaslMechanism(sasl);

        // Connect to an address with the specified local binding
        List<TransportAddress> addresses = new ArrayList<TransportAddress>();
        addresses.add(new TransportAddress(localhost, PORT, localPort));
        spec.setAddresses(addresses);

        ClientNexus client = clientManager.create(spec);

        login(client);

        // Validate the local binding
        Collection<ServiceTransport> xports = client.getTransports();
        assertEquals(xports.size(), 1);

        for (ServiceTransport xport : xports) {
            InetSocketAddress address = (InetSocketAddress) xport.getLocalAddress();
            assertEquals(address.getPort(), localPort);
        }
    }

    @Test
    public void testLoginPlainWithTLS() {
        // Configure the server
        Server server = serverManager.locate(helloService.getServiceName());
        ServerConfig config = server.getConfig();

        try {
            SSLServerParams params = new SSLServerParams(keyStore, keyPass, storePass);
            params.setTlsLevel(TransportSecurityLevel.AUTHENTICATION);
            SSLServerContext ssl = SSLContextFactory.getServerContext(params);
            config.setSslContext(ssl);
        } catch (Exception e) {
            fail("failed to initialize ssl server context", e);
        }

        // Create the session
        ClientConfig spec = initServiceSpec(new HelloService(helloService));

        try {
            SSLClientParams params = new SSLClientParams();

            params.setTrustStorePath(trustStore);
            params.setTrustStorePass(trustPass);
            params.setTlsLevel(TransportSecurityLevel.AUTHENTICATION);

            SSLClientContext ssl = SSLContextFactory.getClientContext(params);
            spec.setSslContext(ssl);
        } catch (Exception e) {
            fail("failed to initialize ssl client context", e);
        }

        PlainClient sasl = new PlainClient(TEST_USERNAME, TEST_PASSWORD);
        spec.setSaslMechanism(sasl);

        ClientNexus client = clientManager.create(spec);

        login(client);
    }

    @Test
    public void testNegotiate() {
        // Configure the server
        Server server = serverManager.locate(helloService.getServiceName());
        ServiceOptions offer = server.getConfig().getOptions();

        offer.setOption(HEADER_DIGEST, Arrays.asList("DIGEST_CRC32", "DIGEST_ADLER32"));
        offer.setOption(FRAME_DIGEST, Arrays.asList("DIGEST_CRC32", "DIGEST_ADLER32", "DIGEST_NONE"));
        offer.setOption(PAYLOAD_DIGEST, Arrays.asList("DIGEST_CRC32", "DIGEST_ADLER32"));
        offer.setOption(FORE_QUEUE_DEPTH, 2);

        // Configure the client
        ClientConfig spec = initServiceSpec(new HelloService(helloService));
        ServiceOptions proposal = spec.getOptions();

        proposal.setOption(HEADER_DIGEST, Arrays.asList("DIGEST_CRC32"));
        proposal.setOption(PAYLOAD_DIGEST, Arrays.asList("DIGEST_ADLER32"));
        proposal.setOption(DIGEST_DATA, true);
        proposal.setOption(FORE_QUEUE_DEPTH, 4);

        // Create the session
        ClientNexus client = clientManager.create(spec);

        login(client);

        // Verify service options
        Set<ServiceOption<?>> supported = ServiceOption.clientOptions();

        ServiceOptions result = client.getOptions();

        for (ServiceOption<?> option : supported) {
            if (option == FORE_QUEUE_DEPTH) {
                assertEquals(result.getOption(FORE_QUEUE_DEPTH).intValue(), 2);
            } else if (option == HEADER_DIGEST) {
                assertEquals(result.getOption(HEADER_DIGEST), Arrays.asList("DIGEST_CRC32"));
            } else if (option == PAYLOAD_DIGEST) {
                assertEquals(result.getOption(PAYLOAD_DIGEST), Arrays.asList("DIGEST_ADLER32"));
            } else if (option == DIGEST_DATA) {
                assertTrue(result.getOption(DIGEST_DATA));
            } else if (option.isNexus()) {
                assertEquals(result.getOption(option), option.getDefault());
            }
        }
    }

    @Test
    public void testNegotiateFailure() {
        // Configure the server
        Server server = serverManager.locate(helloService.getServiceName());
        ServiceOptions offer = server.getConfig().getOptions();
        offer.setOption(HEADER_DIGEST, Arrays.asList("DIGEST_ADLER32"));

        // Negotiate an unsupported digest mechanism
        ClientConfig spec = initServiceSpec(new HelloService(helloService));
        ServiceOptions proposal = spec.getOptions();
        proposal.setOption(HEADER_DIGEST, Arrays.asList("DIGEST_CRC32"));

        // Create the session
        ClientNexus client = clientManager.create(spec);

        Throwable t = loginFail(client);

        assertTrue(t instanceof ParameterNegotiationException);
    }

    @Test
    public void testLoginFailure() {
        // Configure the client
        ClientConfig spec = initServiceSpec(new HelloService(helloService));

        // Authenticate using an invalid password
        DigestClient sasl = new DigestClient("username", "invalid", "server.realm");
        spec.setSaslMechanism(sasl);

        // Create the session
        ClientNexus client = clientManager.create(spec);

        Throwable t = loginFail(client);

        assertTrue(t instanceof AuthFailedException);
    }

    @Test
    public void testServiceUnavailable() {
        // Shutdown the service
        Server server = serverManager.locate(helloService.getServiceName());
        server.shutdown();

        // Configure the client
        ClientConfig spec = initServiceSpec(new HelloService(helloService));

        // Create the session
        ClientNexus client = clientManager.create(spec);

        Throwable t = loginFail(client);

        assertTrue(t instanceof ServiceUnavailableException);
    }

    @Test
    public void testServiceUnreachable() {
        // Block all incoming addresses
        Server server = serverManager.locate(helloService.getServiceName());
        ServerConfig config = server.getConfig();

        config.setNetConfig(new NetServerConfig());

        // Configure the client
        ClientConfig spec = initServiceSpec(new HelloService(helloService));

        // Create the session
        ClientNexus client = clientManager.create(spec);

        Throwable t = loginFail(client);

        assertTrue(t instanceof ServiceUnreachableException);
    }

    @Test
    public void testAuthNotSupported() {
        // Configure the client
        ClientConfig spec = initServiceSpec(new HelloService(helloService));

        // Authenticate using an unsupported sasl mechanism
        AnonymousClient sasl = new AnonymousClient();
        spec.setSaslMechanism(sasl);

        // Create the session
        ClientNexus client = clientManager.create(spec);

        Throwable t = loginFail(client);

        assertTrue(t instanceof AuthNotSupportedException);
    }

    @Test
    public void testLoginAborted() {
        // Create the session
        ClientConfig spec = initServiceSpec(new HelloService(helloService));

        // Connect to an invalid address
        try {
            List<TransportAddress> addresses = new ArrayList<TransportAddress>();
            addresses.add(new TransportAddress(InetAddress.getByName("169.0.0.1")));
            spec.setAddresses(addresses);
        } catch (UnknownHostException e) {
            fail("failed to get invalid address", e);
        }

        final ClientNexus client = clientManager.create(spec);

        // Schedule a timer to close the nexus before it is established
        Timer timer = new Timer();

        timer.schedule(new TimerTask() {

            @Override
            public void run() {
                client.close();
            }
        }, 1000);

        Throwable t = loginFail(client);

        assertTrue(t instanceof LoginAbortedException);

        timer.cancel();
    }

    @Test
    public void testSecurityNegotiationFailure() {
        // Configure the client
        ClientConfig spec = initServiceSpec(new HelloService(helloService));

        // Require TLS that the server doesn't support
        try {
            SSLClientParams params = new SSLClientParams();

            params.setTrustStorePath(trustStore);
            params.setTrustStorePass(trustPass);
            params.setTlsLevel(TransportSecurityLevel.ENCRYPTION);

            SSLClientContext ssl = SSLContextFactory.getClientContext(params);
            spec.setSslContext(ssl);
        } catch (Exception e) {
            fail("failed to initialize ssl client context", e);
        }

        // Create the session
        ClientNexus client = clientManager.create(spec);

        Throwable t = loginFail(client);

        assertTrue(t instanceof SecurityNegotiationException);
    }

    @Test
    public void testLoginTimeout() {
        // Create the session
        ClientConfig spec = initServiceSpec(new HelloService(helloService));
        spec.setLoginTimeout(5);

        // Connect to an invalid address
        try {
            List<TransportAddress> addresses = new ArrayList<TransportAddress>();
            addresses.add(new TransportAddress(InetAddress.getByName("169.0.0.1")));
            spec.setAddresses(addresses);
        } catch (UnknownHostException e) {
            fail("failed to get invalid address", e);
        }

        ClientNexus client = clientManager.create(spec);

        Throwable t = loginFail(client);

        assertTrue(t instanceof LoginTimeoutException);
    }

    @Test
    public void testCommand() {
        int numThreads = 1;

        // Create the session
        ClientConfig spec = initServiceSpec(new HelloService(helloService));

        ClientNexus client = clientManager.create(spec);

        login(client);

        // Issue commands over the fore channel
        issueCommands(client, numThreads, 1, 0);

        // Wait for the test to complete
        awaitDone(numThreads);
    }

    @Test
    public void testReinstatement() {
        ClientNexus[] clients = new ClientNexus[2];

        // Create the session
        ClientConfig spec = initServiceSpec(new HelloService(helloService));
        clients[0] = clientManager.create(spec);

        login(clients[0]);

        kill(clients[0]);

        // Initiate new session to trigger reinstatement
        spec = initServiceSpec(new HelloService(helloService));
        clients[1] = clientManager.create(spec);

        login(clients[1]);
    }

    @Test
    public void testSessionReset() {
        int numThreads = 4;

        // Configure the server
        Server server = serverManager.locate(helloService.getServiceName());

        final Timer timer = new Timer();

        server.addListener(new SessionEventListener(new SessionEventRunnable() {

            @Override
            public void work() {
                timer.schedule(new TimerTask() {

                    @Override
                    public void run() {
                        nexus.close();
                    }
                }, 500);
            }
        }));

        // Create the session
        ClientConfig spec = initServiceSpec(new HelloService(helloService));
        ClientNexus client = clientManager.create(spec);

        login(client);

        // Issue commands over the fore channel
        issueCommands(client, numThreads, 32, 0);

        // Wait for the login to complete
        awaitDone(numThreads + 1);

        timer.cancel();
    }

    @Test
    public void testServiceShutdown() {
        int numThreads = 4;

        // Create the session
        ClientConfig spec = initServiceSpec(new HelloService(helloService));
        ClientNexus client = clientManager.create(spec);

        login(client);

        issueCommands(client, numThreads, 64, 0);

        // Shutdown service
        Server server = serverManager.locate(helloService.getServiceName());
        server.shutdown();

        // Wait for the test to complete
        awaitDone(numThreads);
    }

    @Test
    public void testLoginRecovery() {
        // Configure the server
        Server server = serverManager.locate(helloService.getServiceName());

        server.addListener(new SessionErrorInjector());

        // Create the session
        ClientConfig spec = initServiceSpec(new HelloService(helloService));
        ClientNexus client = clientManager.create(spec);

        login(client);

        // Keep the service registered for 30 seconds to test recovery
        sleep(30000);
    }

    @Test
    public void testForeChannel() {
        int numThreads = 8;

        // Create the session
        ClientConfig spec = initServiceSpec(new HelloService(helloService), 2);
        ClientNexus client = clientManager.create(spec);

        login(client);

        // Issue commands over the fore channel
        issueCommands(client, numThreads, 64, 0);

        // Wait for the test to complete
        awaitDone(numThreads);
    }

    @Test
    public void testLatency() {
        int numThreads = 1;

        // Create the session
        ClientConfig spec = initServiceSpec(new HelloService(helloService), 2);
        ClientNexus client = clientManager.create(spec);

        login(client);

        // Issue commands over the fore channel
        issueCommands(client, numThreads, 81920, 0);

        // Wait for the test to complete
        awaitDone(numThreads);

        displayStats(client);

        Server server = serverManager.locate(helloService.getServiceName());
        ServerNexus session = server.locate(clientTerminus);

        if (session != null) {
            displayStats(session);
        }
    }

    @Test
    public void testDataLatency() {
        int numThreads = 1;

        // Create the session
        ClientConfig spec = initServiceSpec(new HelloService(helloService), 2);
        ClientNexus client = clientManager.create(spec);

        login(client);

        // Issue commands over the fore channel
        issueCommands(client, numThreads, 81920, 0, true);

        // Wait for the test to complete
        awaitDone(numThreads);

        displayStats(client);

        Server server = serverManager.locate(helloService.getServiceName());
        ServerNexus session = server.locate(clientTerminus);

        if (session != null) {
            displayStats(session);
        }
    }

    @Test
    public void testThroughput() {
        int numThreads = 8;

        // Create the session
        ClientConfig spec = initServiceSpec(new HelloService(helloService), 2);
        ClientNexus client = clientManager.create(spec);

        login(client);

        // Issue commands over the fore channel
        issueCommands(client, numThreads, 12000, 0);

        // Wait for the test to complete
        awaitDone(numThreads);

        displayStats(client);

        Server server = serverManager.locate(helloService.getServiceName());
        ServerNexus session = server.locate(clientTerminus);

        if (session != null) {
            displayStats(session);
        }
    }

    @Test
    public void testLeastQueue() {
        int numThreads = 8;

        // Create the session
        ClientConfig spec = initServiceSpec(new HelloService(helloService), 2);

        // Set the scheduler to LEAST_QUEUE instead of the default ROUND_ROBIN
        ServiceOptions proposal = spec.getOptions();
        proposal.setOption(XPORT_SCHEDULER, "LEAST_QUEUE");

        ClientNexus client = clientManager.create(spec);

        login(client);

        // Issue commands over the fore channel
        issueCommands(client, numThreads, 12000, 0);

        // Wait for the test to complete
        awaitDone(numThreads);

        displayStats(client);

        Server server = serverManager.locate(helloService.getServiceName());
        ServerNexus session = server.locate(clientTerminus);

        if (session != null) {
            displayStats(session);
        }
    }

    @Test
    public void testBackChannel() {
        final int numThreads = 8;

        // Configure the server
        Server server = serverManager.locate(helloService.getServiceName());

        server.addListener(new SessionEventListener(new SessionEventRunnable() {

            @Override
            public void work() {
                // Issue commands over the fore channel
                issueCommands(nexus, numThreads, 64, 0);
            }
        }));

        // Create the session
        ClientConfig spec = initServiceSpec(new HelloService(helloService), 2);
        ClientNexus client = clientManager.create(spec);

        login(client);

        // Wait for the test to complete
        awaitDone(numThreads + 1);
    }

    @Test
    public void testDualChannels() {
        final int numThreads = 8;

        // Configure the server
        Server server = serverManager.locate(helloService.getServiceName());

        server.addListener(new SessionEventListener(new SessionEventRunnable() {

            @Override
            public void work() {
                // Issue commands over the back channel
                issueCommands(nexus, numThreads, 64, 0);
            }
        }));

        // Create the session
        ClientConfig spec = initServiceSpec(new HelloService(helloService), 2);
        ClientNexus client = clientManager.create(spec);

        client.addListener(new SessionEventListener(new SessionEventRunnable() {

            @Override
            public void work() {
                // Issue commands over the fore channel
                issueCommands(nexus, numThreads, 64, 0);
            }
        }));

        login(client);

        // Wait for the test to complete
        awaitDone(2 * numThreads + 2);
    }

    @Test
    public void testCommandRecovery() {
        int numThreads = 1;

        // Configure the server
        Server server = serverManager.locate(delayService.getServiceName());

        server.addListener(new SessionErrorInjector(500, 20000, 1.0));

        // Create the session
        ClientConfig spec = initServiceSpec(new HelloDelayService(delayService));
        ClientNexus client = clientManager.create(spec);

        login(client);

        // Issue commands over the fore channel
        issueCommands(client, numThreads, 1, 0);

        // Wait for the test to complete
        awaitDone(numThreads);
    }

    @Test
    public void testChannelRecovery() {
        int numThreads = 4;

        // Configure the server
        Server server = serverManager.locate(delayService.getServiceName());

        HelloDelayService delay = (HelloDelayService) server.getService();
        delay.setDelay(500);

        server.addListener(new SessionErrorInjector(100, 5000, 0.5));

        // Create the session
        ClientConfig spec = initServiceSpec(new HelloDelayService(delayService), 2);
        ClientNexus client = clientManager.create(spec);

        login(client);

        // Issue commands over the fore channel
        issueCommands(client, numThreads, 128, 0);

        // Wait for the test to complete
        awaitDone(numThreads);

        displayStats(client);

        ServerNexus session = server.locate(clientTerminus);

        if (session != null) {
            displayStats(session);
        }
    }

    @Test
    public void testCommandTimeout() {
        int numThreads = 1;

        // Create the session
        ClientConfig spec = initServiceSpec(new HelloDelayService(delayService));
        ClientNexus client = clientManager.create(spec);

        login(client);

        // Issue commands over the fore channel
        issueCommands(client, numThreads, 1, 500);

        // Wait for the test to complete
        awaitDone(numThreads);
    }

    @Test
    public void testCommandAbort() {
        int numThreads = 1;

        // Create the session
        ClientConfig spec = initServiceSpec(new HelloDelayService(delayService));
        ClientNexus client = clientManager.create(spec);

        login(client);

        // Issue commands over the fore channel
        abortCommands(client, numThreads, 1, 0);

        // Wait for the test to complete
        awaitDone(numThreads);
    }

    @Test
    public void testChannelAbort() {
        int numThreads = 8;

        // Create the session
        ClientConfig spec = initServiceSpec(new HelloService(helloService), 2);
        ClientNexus client = clientManager.create(spec);

        login(client);

        // Issue commands over the fore channel
        abortCommands(client, numThreads, 1024, 0);

        // Wait for the test to complete
        awaitDone(numThreads);

        displayStats(client);

        Server server = serverManager.locate(helloService.getServiceName());
        ServerNexus session = server.locate(clientTerminus);

        if (session != null) {
            displayStats(session);
        }
    }

    @Test
    public void testResetActive() {
        int numThreads = 1;

        // Configure the server
        Server server = serverManager.locate(delayService.getServiceName());

        HelloDelayService delay = (HelloDelayService) server.getService();
        delay.setDelay(2000);

        server.addListener(new SessionErrorInjector(100, 10000, 1.0));

        // Create the session
        ClientConfig spec = initServiceSpec(new HelloDelayService(delayService), 2);
        ClientNexus client = clientManager.create(spec);

        login(client);

        // Issue commands over the fore channel
        issueCommands(client, numThreads, 1, 0);

        // Wait for the test to complete
        awaitDone(numThreads);
    }

    @Test
    public void testCommandAbortRecovery() {
        int numThreads = 1;

        // Configure the server
        Server server = serverManager.locate(delayService.getServiceName());

        server.addListener(new SessionErrorInjector(500, 20000, 1.0));

        // Create the session
        ClientConfig spec = initServiceSpec(new HelloDelayService(delayService));
        ClientNexus client = clientManager.create(spec);

        login(client);

        // Issue commands over the fore channel
        issueCommands(client, numThreads, 1, 750);

        // Wait for the test to complete
        awaitDone(numThreads);
    }

    @Test
    public void testChannelAbortRecovery() {
        int numThreads = 4;

        // Configure the server
        Server server = serverManager.locate(delayService.getServiceName());

        HelloDelayService delay = (HelloDelayService) server.getService();
        delay.setDelay(500);

        server.addListener(new SessionErrorInjector(100, 5000, 0.5));

        // Create the session
        ClientConfig spec = initServiceSpec(new HelloDelayService(delayService), 2);
        ClientNexus client = clientManager.create(spec);

        login(client);

        // Issue commands over the fore channel
        abortCommands(client, numThreads, 128, 0);

        // Wait for the test to complete
        awaitDone(numThreads);
    }

    @Test
    public void testResetBusy() {
        int numThreads = 32;

        // Configure the server
        Server server = serverManager.locate(delayService.getServiceName());

        HelloDelayService delay = (HelloDelayService) server.getService();
        delay.setDelay(50);

        server.addListener(new SessionErrorInjector(100, 10000, 1.0));

        // Create the session
        ClientConfig spec = initServiceSpec(new HelloDelayService(delayService), 2);
        ClientNexus client = clientManager.create(spec);

        login(client);

        // Issue commands over the fore channel
        issueCommands(client, numThreads, 4, 0);

        // Wait for the test to complete
        awaitDone(numThreads);
    }

    @Test
    public void testResetAbortBusy() {
        int numThreads = 32;

        // Configure the server
        Server server = serverManager.locate(delayService.getServiceName());

        HelloDelayService delay = (HelloDelayService) server.getService();
        delay.setDelay(50);

        server.addListener(new SessionErrorInjector(100, 10000, 1.0));

        // Create the session
        ClientConfig spec = initServiceSpec(new HelloDelayService(delayService), 2);
        ClientNexus client = clientManager.create(spec);

        login(client);

        // Issue commands over the fore channel
        abortCommands(client, numThreads, 4, 0);

        // Wait for the test to complete
        awaitDone(numThreads);
    }

    @Test
    public void testNonIdempotent() {
        int numThreads = 1;

        // Configure the server
        Server server = serverManager.locate(delayService.getServiceName());

        HelloDelayService delay = (HelloDelayService) server.getService();
        delay.setDelay(1000);

        server.addListener(new SessionErrorInjector(100, 10000, 1.0));

        // Create the session
        ClientConfig spec = initServiceSpec(new HelloDelayService(delayService));
        ClientNexus client = clientManager.create(spec);

        login(client);

        issueNonIdempotent(client);

        // Wait for the test to complete
        awaitDone(numThreads);
    }

    @Test
    public void testIdempotent() {
        int numThreads = 1;

        // Configure the server
        Server server = serverManager.locate(delayService.getServiceName());

        HelloDelayService delay = (HelloDelayService) server.getService();
        delay.setDelay(1000);

        server.addListener(new SessionErrorInjector(100, 10000, 1.0));

        // Create the session
        ClientConfig spec = initServiceSpec(new HelloDelayService(delayService));
        ClientNexus client = clientManager.create(spec);

        login(client);

        issueIdempotent(client);

        // Wait for the test to complete
        awaitDone(numThreads);
    }

    @Test
    public void testSyncDispatch() {
        int numThreads = 8;

        // Create the session
        ClientConfig spec = initServiceSpec(new HelloService(helloService));

        // Set a small fore channel queue depth to test sync dispatch
        ServiceOptions proposal = spec.getOptions();
        proposal.setOption(FORE_QUEUE_DEPTH, 2);
        proposal.setOption(SYNC_DISPATCH, true);

        ClientNexus client = clientManager.create(spec);

        login(client);

        // Issue commands over the fore channel
        issueCommands(client, numThreads, 4096, 0);

        // Wait for the test to complete
        awaitDone(numThreads);
    }

    @Test
    public void testSyncDispatchReset() {
        int numThreads = 8;

        // Configure the server
        Server server = serverManager.locate(delayService.getServiceName());

        HelloDelayService delay = (HelloDelayService) server.getService();
        delay.setDelay(1000);

        final Timer timer = new Timer();

        server.addListener(new SessionEventListener(new SessionEventRunnable() {

            @Override
            public void work() {
                timer.schedule(new TimerTask() {

                    @Override
                    public void run() {
                        nexus.close();
                    }
                }, 500);
            }
        }));

        // Create the session
        ClientConfig spec = initServiceSpec(new HelloDelayService(delayService));

        // Set a small fore channel queue depth to test sync dispatch
        ServiceOptions proposal = spec.getOptions();
        proposal.setOption(FORE_QUEUE_DEPTH, 2);
        proposal.setOption(SYNC_DISPATCH, true);

        ClientNexus client = clientManager.create(spec);

        login(client);

        // Issue commands over the fore channel
        issueCommands(client, numThreads, 4096, 0);

        // Wait for the test to complete
        awaitDone(numThreads + 1);

        timer.cancel();
    }

    @Test
    public void testSsl() {
        int numThreads = 8;

        // Configure the server
        Server server = serverManager.locate(helloService.getServiceName());
        ServerConfig config = server.getConfig();

        try {
            SSLServerParams params = new SSLServerParams(keyStore, keyPass, storePass);
            params.setTlsLevel(TransportSecurityLevel.ENCRYPTION);
            SSLServerContext ssl = SSLContextFactory.getServerContext(params);
            config.setSslContext(ssl);
        } catch (Exception e) {
            fail("failed to initialize ssl server context", e);
        }

        // Create the session
        ClientConfig spec = initServiceSpec(new HelloService(helloService), 2);

        try {
            SSLClientParams params = new SSLClientParams();

            params.setTrustStorePath(trustStore);
            params.setTrustStorePass(trustPass);

            SSLClientContext ssl = SSLContextFactory.getClientContext(params);
            spec.setSslContext(ssl);
        } catch (Exception e) {
            fail("failed to initialize ssl client context", e);
        }

        ClientNexus client = clientManager.create(spec);

        login(client);

        // Issue commands over the fore channel
        issueCommands(client, numThreads, 12000, 0);

        // Wait for the test to complete
        awaitDone(numThreads);

        displayStats(client);

        ServerNexus session = server.locate(clientTerminus);

        if (session != null) {
            displayStats(session);
        }
    }

    @Test
    public void testSslRecovery() {
        int numThreads = 8;

        // Configure the server
        Server server = serverManager.locate(helloService.getServiceName());
        ServerConfig config = server.getConfig();

        try {
            SSLServerParams params = new SSLServerParams(keyStore, keyPass, storePass);
            params.setTlsLevel(TransportSecurityLevel.AUTHENTICATION);
            SSLServerContext ssl = SSLContextFactory.getServerContext(params);
            config.setSslContext(ssl);
        } catch (Exception e) {
            fail("failed to initialize ssl server context", e);
        }

        server.addListener(new SessionErrorInjector());

        // Create the session
        ClientConfig spec = initServiceSpec(new HelloService(helloService), 2);

        try {
            SSLClientParams params = new SSLClientParams();

            params.setTrustStorePath(trustStore);
            params.setTrustStorePass(trustPass);

            SSLClientContext ssl = SSLContextFactory.getClientContext(params);
            spec.setSslContext(ssl);
        } catch (Exception e) {
            fail("failed to initialize ssl client context", e);
        }

        ClientNexus client = clientManager.create(spec);

        login(client);

        // Issue commands over the fore channel
        issueCommands(client, numThreads, 128, 0);

        // Keep the service registered for 30 seconds to test recovery
        sleep(30000);

        // Wait for the test to complete
        awaitDone(numThreads);
    }

    @Test
    public void testSessionTimeout() {
        // Configure the server
        Server server = serverManager.locate(helloService.getServiceName());
        server.addListener(new SessionEventListener() {

            @Override
            public void nexusClosed(ServiceNexus nexus) {
                logger.infof("%s: closed", nexus);

                if (runnable != null) {
                    runnable.setNexus(nexus);
                    runnable.run();
                }
            }
        });

        // Configure the client
        ClientConfig spec = initServiceSpec(new HelloService(helloService));
        ServiceOptions proposal = spec.getOptions();

        // Negotiate a one second timeout on the session state
        proposal.setOption(MIN_KEEPALIVE_TIME, 1);

        // Create the session
        ClientNexus client = clientManager.create(spec);

        login(client);

        kill(client);

        // Wait for the test to complete
        awaitDone(2);
    }

    @Test
    public void testLogoutGrace() {
        final int numThreads = 4;

        // Configure the server
        Server server = serverManager.locate(delayService.getServiceName());
        ServiceOptions offer = server.getConfig().getOptions();
        offer.setOption(LOGOUT_TIMEOUT, 10);

        // Start a few commands on the back channel just to make it interesting
        SessionEventRunnable task = new SessionEventRunnable() {

            @Override
            public void work() {
                // Issue commands over the fore channel
                issueCommands(nexus, numThreads, 1, 0);
            }
        };

        server.addListener(new SessionEventListener(task) {

            @Override
            public void nexusLogout(ServiceNexus nexus) {
                final ServerNexus serverNexus = (ServerNexus) nexus;

                logger.infof("%s: logout requested", nexus);

                executor.execute(new Runnable() {

                    @Override
                    public void run() {
                        // Wait for command to complete on the back channel
                        awaitDone(numThreads + 1);

                        // Proceed to logout
                        serverNexus.logout();
                    }
                });
            }
        });

        // Configure the client
        ClientConfig spec = initServiceSpec(new HelloDelayService(delayService, 2000));
        ServiceOptions proposal = spec.getOptions();
        proposal.setOption(LOGOUT_TIMEOUT, 30);

        // Create the session
        ClientNexus client = clientManager.create(spec);

        login(client);

        // Let the server fire up some commands
        sleep(1000);

        close(client);
    }

    @Test
    public void testLogoutTimeout() {
        // Configure the server
        Server server = serverManager.locate(helloService.getServiceName());
        ServiceOptions offer = server.getConfig().getOptions();
        offer.setOption(LOGOUT_TIMEOUT, 3);

        // Configure the client
        ClientConfig spec = initServiceSpec(new HelloService(helloService));
        ServiceOptions proposal = spec.getOptions();
        proposal.setOption(LOGOUT_TIMEOUT, 30);

        // Create the session
        ClientNexus client = clientManager.create(spec);

        login(client);

        // Close will take a 3 second timeout waiting for logout
        close(client);
    }

    private void executeNonIdempotent(ServiceNexus nexus) {
        HelloRequest hello = new HelloRequest();
        hello.setMessage(HelloRequest.NON_IDEMPOTENT_TEST);

        ServiceFuture future = nexus.execute(hello);

        try {
            future.get();
        } catch (InterruptedException e) {
            fail("non-idempotent command interrupted");
        } catch (ExecutionException e) {
            fail("non-idempotent command failed", e);
        }
    }

    private void issueNonIdempotent(ServiceNexus nexus) {
        Runnable runnable = new SessionTestRunnable(nexus) {

            @Override
            protected void work() {
                executeNonIdempotent(nexus);
            }
        };

        executor.execute(runnable);
    }

    private void executeIdempotent(ServiceNexus nexus) {
        HelloRequest hello = new HelloRequest();
        hello.setIdempotent(true);

        ServiceFuture future = nexus.execute(hello);

        try {
            future.get();
            fail("idempotent command succeeded");
        } catch (InterruptedException e) {
            fail("idempotent command interrupted");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();

            if (!(cause instanceof IdempotentRetryException)) {
                fail("idempotent command failed", e);
            }
        }

        future = nexus.execute(hello);

        try {
            future.get();
        } catch (InterruptedException e) {
            fail("idempotent command interrupted");
        } catch (ExecutionException e) {
            fail("idempotent command failed", e);
        }
    }

    private void issueIdempotent(ServiceNexus nexus) {
        Runnable runnable = new SessionTestRunnable(nexus) {

            @Override
            public void work() {
                executeIdempotent(nexus);
            }
        };

        executor.execute(runnable);
    }

    private void executeCommand(ServiceNexus nexus, long timeout, boolean hasData) {
        ServiceRequest request;

        if (hasData) {
            request = new HelloRequest(new byte[128]);
        } else {
            request = new HelloRequest();
        }

        ServiceFuture future = nexus.execute(request, null, timeout);

        try {
            future.get();
        } catch (InterruptedException e) {
            fail("command interrupted", e);
        } catch (ExecutionException e) {
            fail("command failed", e.getCause());
        } catch (CancellationException e) {
            // Do nothing
        }
    }

    private void abortCommand(ServiceNexus nexus, long timeout) {
        ServiceRequest request = new HelloRequest();

        ServiceFuture future = nexus.execute(request, null, timeout);

        future.cancel(true, true);
    }

    private void abortCommands(ServiceNexus nexus, int numThreads, final int numCommands, final long timeout) {
        for (int i = 0; i < numThreads; i++) {
            Runnable runnable = new SessionTestRunnable(nexus) {

                @Override
                public void work() {
                    for (int i = 0; i < numCommands; i++) {
                        abortCommand(nexus, timeout);
                    }
                }
            };

            executor.execute(runnable);
        }
    }

    private void issueCommands(ServiceNexus nexus, int numThreads, int numCommands, long timeout) {
        issueCommands(nexus, numThreads, numCommands, timeout, false);
    }

    private void issueCommands(ServiceNexus nexus, int numThreads, final int numCommands,
            final long timeout, final boolean hasData) {
        for (int i = 0; i < numThreads; i++) {
            Runnable runnable = new SessionTestRunnable(nexus) {

                @Override
                public void work() {
                    for (int i = 0; i < numCommands; i++) {
                        executeCommand(nexus, timeout, hasData);
                    }
                }
            };

            executor.execute(runnable);
        }
    }

    private void displayStats(ServiceNexus nexus) {
        logger.infof("%s: command stats\n%s", nexus, nexus.getStats().format());
    }

    private <T extends Service & ProtocolHandler<?>> ClientConfig initServiceSpec(ServiceTerminus client,
            T service, TransportAddress... address) {
        ServiceTerminus terminus = service.getType().getServiceName();
        ClientConfig spec = new ClientConfig(client, terminus, service,
                ImmutableList.<ProtocolHandler<?>> of(service));

        // Initialize SASL
        DigestClient sasl = new DigestClient("username", "password", "server.realm");
        spec.setSaslMechanism(sasl);

        // Initialize transport addresses
        spec.setAddresses(Arrays.asList(address));

        spec.setServerHost("server.domain");

        return spec;
    }

    private <T extends Service & ProtocolHandler<?>> ClientConfig initServiceSpec(ServiceTerminus client,
            T service, InetAddress... address) {
        // Initialize transport addresses
        TransportAddress[] addresses = new TransportAddress[address.length];

        for (int i = 0; i < address.length; i++) {
            addresses[i] = new TransportAddress(address[i]);
        }

        return initServiceSpec(client, service, addresses);
    }

    private <T extends Service & ProtocolHandler<?>> ClientConfig initServiceSpec(T service) {
        return initServiceSpec(service, 1);
    }

    private <T extends Service & ProtocolHandler<?>> ClientConfig initServiceSpec(T service, int numAddrs) {
        InetAddress[] addresses = new InetAddress[numAddrs];
        Arrays.fill(addresses, localhost);
        return initServiceSpec(clientTerminus, service, addresses);
    }

    private <T extends Service & ProtocolHandlerFactory> ServerConfig initServiceConfig(T service) {
        // Initialize SASL
        SaslServerConfig sasl = new SaslServerConfig(PROTOCOL, SERVER);

        UserDatabase userDB = new UserDatabase();

        PasswordAuthenticator authenticator = new PasswordAuthenticator() {

            @Override
            public boolean authenticate(String username, String password) {
                return username.equals(TEST_USERNAME) && password.equals(TEST_PASSWORD);
            }
        };

        sasl.addMechanism(new DigestServer(userDB, userDB, userDB.getRealms()));
        sasl.addMechanism(new CramServer(userDB, userDB));
        sasl.addMechanism(new PlainServer(authenticator, userDB));

        // Initialize network
        NetServerConfig net = new NetServerConfig();

        try {
            net.addAll();
        } catch (SocketException e) {
            e.printStackTrace();
        }

        // Initialize service configuration
        ServiceTerminus terminus = service.getType().getServiceName();
        ServerConfig config = new ServerConfig(terminus, service, service);

        config.setSaslConfig(sasl);
        config.setNetConfig(net);

        return config;
    }

    /**
     * A SessionTestRunnable runs a specific task required for a test, such as to create a command runner or a timer
     * and to execute a set of commands. It is either created in the test by the main context or during session event
     * processing by the event manager. A reference is acquired here on the semaphore created earlier to track the
     * completion of the current test. The semaphore will be released upon termination of this runnable. The clients
     * and services are shutdown during test termination and the event queue synchronously flushed. So we don't run
     * any risk of ever releasing a semaphore that doesn't belong to the test since no new SessionTestRunnables are
     * created beyond test termination.
     */
    private abstract class SessionTestRunnable implements Runnable {

        protected ServiceNexus nexus;
        protected Semaphore doneSema;

        public SessionTestRunnable() {
            this(null);
        }

        public SessionTestRunnable(ServiceNexus nexus) {
            this.doneSema = ServiceTest.this.doneSema;
            this.nexus = nexus;
        }

        public void setNexus(ServiceNexus nexus) {
            this.nexus = nexus;
        }

        protected void notifyDone() {
            doneSema.release();
        }

        // Override
        protected void work() {

        }

        protected void handleException(Throwable t) {
            logger.errorf("%s: execute failed", nexus);
        }

        @Override
        public void run() {
            try {
                work();
            } catch (Throwable t) {
                handleException(t);
            } finally {
                notifyDone();
            }
        }
    }

    private class SessionEventRunnable extends SessionTestRunnable {

        @Override
        protected void handleException(Throwable t) {
            fail("event failed", t);
        }
    }

    private class SessionEventListener extends DefaultNexusListener {

        protected final SessionEventRunnable runnable;

        public SessionEventListener() {
            this.runnable = new SessionEventRunnable();
        }

        public SessionEventListener(SessionEventRunnable runnable) {
            this.runnable = runnable;
        }

        @Override
        public void nexusEstablished(ServiceNexus nexus) {
            logger.infof("%s: established", nexus);

            if (runnable != null) {
                runnable.setNexus(nexus);
                runnable.run();
            }
        }

        @Override
        public void nexusClosed(ServiceNexus nexus) {
            logger.infof("%s: closed", nexus);
        }

        @Override
        public void nexusRestored(ServiceNexus nexus) {
            logger.infof("%s: restored", nexus);
        }

        @Override
        public void nexusLost(ServiceNexus nexus) {
            logger.infof("%s: lost", nexus);
        }

        @Override
        public void nexusReinstated(ServiceNexus existing, ServiceNexus replacement) {
            logger.infof("%s: reinstated by %s", existing, replacement);
        }

        @Override
        public void nexusLogout(ServiceNexus nexus) {
            logger.infof("%s: logout requested", nexus);
        }
    }

    private class SessionErrorInjector extends DefaultNexusListener {

        private Timer timer = new Timer();

        private long delay;
        private long period;
        private double chance;

        public SessionErrorInjector() {
            this(5000, 10000, 1.0);
        }

        public SessionErrorInjector(long delay, long period, double chance) {
            this.delay = delay;
            this.period = period;
            this.chance = chance;
        }

        @Override
        public void nexusEstablished(final ServiceNexus nexus) {
            logger.infof("%s: established", nexus);

            // Start the timer to periodically close transports for error injection
            timer.schedule(new TimerTask() {

                @Override
                public void run() {
                    closeTransports(nexus, chance);
                }
            }, delay, period);
        }

        @Override
        public void nexusClosed(ServiceNexus nexus) {
            logger.infof("%s: closed", nexus);
            timer.cancel();
        }

        @Override
        public void nexusRestored(ServiceNexus nexus) {
            logger.infof("%s: restored", nexus);
        }

        @Override
        public void nexusLost(ServiceNexus nexus) {
            logger.infof("%s: lost", nexus);
        }

        @Override
        public void nexusReinstated(ServiceNexus existing, ServiceNexus replacement) {
            logger.infof("%s: reinstated by %s", existing, replacement);
        }

        @Override
        public void nexusLogout(ServiceNexus nexus) {
            logger.infof("%s: logout requested", nexus);
        }
    }
}
