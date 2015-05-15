package org.jivesoftware.smackx.pubsub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import org.igniterealtime.smack.inttest.AbstractSmackIntegrationTest;
import org.igniterealtime.smack.inttest.SmackIntegrationTest;
import org.igniterealtime.smack.inttest.SmackIntegrationTestEnvironment;
import org.jivesoftware.smack.PacketCollector;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.SmackException.NoResponseException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.XMPPException.XMPPErrorException;
import org.jivesoftware.smack.filter.AndFilter;
import org.jivesoftware.smack.filter.FromMatchesFilter;
import org.jivesoftware.smack.filter.IQTypeFilter;
import org.jivesoftware.smack.filter.MessageTypeFilter;
import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.IQ.Type;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smackx.disco.packet.DiscoverInfo;
import org.jivesoftware.smackx.disco.packet.DiscoverItems;
import org.jivesoftware.smackx.pubsub.packet.PubSub;
import org.jivesoftware.smackx.pubsub.packet.PubSubNamespace;
import org.jivesoftware.smackx.xdata.FormField;
import org.jivesoftware.smackx.xdata.packet.DataForm;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.jxmpp.jid.DomainBareJid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;

public class PubSubIntegrationTest extends AbstractSmackIntegrationTest {

    private static final int WAIT_TIME = 10000;

    private static String testNodeId;
    private static String templateNodeId;
    private static String submittedNodeId;

    private static DomainBareJid pubsubJid;

    public PubSubIntegrationTest(SmackIntegrationTestEnvironment environment) throws XmppStringprepException {
        super(environment);

        pubsubJid = JidCreate.domainBareFrom("pubsub." + environment.configuration.service);
        testNodeId = "testNode-" + environment.testRunId;
        templateNodeId = "fdp/template/" + testNodeId;
        submittedNodeId = "fdp/submitted/" + testNodeId;
    }

    @BeforeClass
    public static void setup() throws KeyManagementException, NoSuchAlgorithmException, SmackException, IOException,
                    XMPPException, InterruptedException {
    }

    @AfterClass
    public static void tearDown() throws NoResponseException, XMPPErrorException, NotConnectedException,
                    InterruptedException {

    }

    /**
     * @throws InterruptedException
     * @throws XMPPException
     * @throws IOException
     * @throws SmackException
     * @throws NoSuchAlgorithmException
     * @throws KeyManagementException
     */
    // @SmackIntegrationTest
    public void testCreateAndDeleteNodes() throws SmackException, IOException, XMPPException, InterruptedException,
                    KeyManagementException, NoSuchAlgorithmException {

        PacketCollector conOneResult = conOne.createPacketCollector(new AndFilter(IQTypeFilter.RESULT,
                        FromMatchesFilter.createBare(pubsubJid)));

        PacketCollector conOneError = conOne.createPacketCollector(new AndFilter(IQTypeFilter.ERROR,
                        FromMatchesFilter.createBare(pubsubJid)));

        PacketCollector conTwoError = conTwo.createPacketCollector(new AndFilter(IQTypeFilter.ERROR,
                        FromMatchesFilter.createBare(pubsubJid)));

        // create node with user One, expect success
        createNode(conOne, testNodeId);
        assertCollection(conOneResult);

        // attempt to create same node again, expect failure
        createNode(conOne, testNodeId);
        assertCollection(conOneError);

        // attempt to create same node with user Two, expect failure
        createNode(conTwo, testNodeId);
        assertCollection(conTwoError);

        // attempt to delete node with user Two, expect failure
        deleteNode(conTwo, testNodeId);
        assertCollection(conTwoError);

        // delete node with user One, expect success
        deleteNode(conOne, testNodeId);
        assertCollection(conOneResult);

    }

    @SmackIntegrationTest
    public void testPublishAndSubscribe() throws SmackException, IOException, XMPPException, InterruptedException,
                    KeyManagementException, NoSuchAlgorithmException {

        PacketCollector conOneResult = conOne.createPacketCollector(new AndFilter(IQTypeFilter.RESULT,
                        FromMatchesFilter.createBare(pubsubJid)));

        PacketCollector conTwoResult = conTwo.createPacketCollector(new AndFilter(IQTypeFilter.RESULT,
                        FromMatchesFilter.createBare(pubsubJid)));

        PacketCollector conTwoMessage = conTwo.createPacketCollector(new AndFilter(MessageTypeFilter.NORMAL,
                        FromMatchesFilter.createBare(pubsubJid)));

        // create node with user One, expect success
        createNode(conOne, testNodeId);
        assertCollection(conOneResult);

        // subscribe with user Two, expect success
        subscribeToNode(conTwo, testNodeId);
        assertCollection(conTwoResult);

        // publish with user One, expect success and receipt by user Two
        publishToNode(conOne, testNodeId);
        assertCollection(conOneResult);
        assertCollection(conTwoMessage);

        // publish with user One, expect success and receipt by user Two
        publishToNode(conOne, testNodeId);
        assertCollection(conOneResult);
        assertCollection(conTwoMessage);

        queryPersistedItems(conTwo, testNodeId, 1);
        assertCollection(conTwoResult);

        // un-subscribe with user Two, expect success
        unsubscribeToNode(conTwo, testNodeId);
        assertCollection(conTwoResult);

        // publish with user One, expect success, no message received by user Two
        publishToNode(conOne, testNodeId);
        assertCollection(conOneResult);
        assertNoPacket(conTwoMessage);

        // delete node with user One, expect success
        deleteNode(conOne, testNodeId);
        assertCollection(conOneResult);
    }

    @SmackIntegrationTest
    public void testFormDiscoveryAndPublishing() throws SmackException, IOException, XMPPException,
                    InterruptedException, KeyManagementException, NoSuchAlgorithmException {

        PacketCollector conOneResult = conOne.createPacketCollector(new AndFilter(IQTypeFilter.RESULT,
                        FromMatchesFilter.createBare(pubsubJid)));

        PacketCollector conTwoResult = conTwo.createPacketCollector(new AndFilter(IQTypeFilter.RESULT,
                        FromMatchesFilter.createBare(pubsubJid)));

        PacketCollector conTwoMessage = conTwo.createPacketCollector(new AndFilter(MessageTypeFilter.NORMAL,
                        FromMatchesFilter.createBare(pubsubJid)));

        discoFeatures(conOne);
        assertCollection(conOneResult);

        // create node with user One, expect success
        createNode(conOne, templateNodeId);
        assertCollection(conOneResult);

        // create node with user One, expect success
        createNode(conOne, submittedNodeId);
        assertCollection(conOneResult);

        // subscribe with user Two, expect success
        subscribeToNode(conTwo, templateNodeId);
        assertCollection(conTwoResult);

        // subscribe with user Two, expect success
        subscribeToNode(conOne, submittedNodeId);
        assertCollection(conOneResult);

        // discover items in templates
        discoItemsOnNode(conTwo, templateNodeId);
        assertCollection(conTwoResult);

        publishTemplateToNode(conOne, templateNodeId);
        assertCollection(conOneResult);
        assertCollection(conTwoMessage);

        publishTemplateToNode(conOne, templateNodeId);
        assertCollection(conOneResult);
        assertCollection(conTwoMessage);

        // discover items in templates
        discoItemsOnNode(conTwo, templateNodeId);
        assertCollection(conTwoResult);

        queryPersistedItems(conOne, templateNodeId, 5);
        assertCollection(conOneResult);

        // TODO: submit forms to submitted node.

        // delete node with user One, expect success
        deleteNode(conOne, templateNodeId);
        assertCollection(conOneResult);

        // delete node with user One, expect success
        deleteNode(conOne, submittedNodeId);
        assertCollection(conOneResult);
    }

    private static void assertNoPacket(PacketCollector collector) throws InterruptedException {
        Stanza stanza = collector.pollResult();
        assertEquals(null, stanza);
    }

    private static void assertCollection(PacketCollector collector) throws InterruptedException {
        Stanza stanza = collector.nextResult();
        assertNotNull(stanza);
    }

    private static void createNode(XMPPConnection from, String nodeId) throws NotConnectedException,
                    InterruptedException {

        PubSub iq = new PubSub();

        iq.setFrom(from.getUser());
        iq.setTo(pubsubJid);
        iq.setType(Type.set);

        ExtensionElement pubsub = new NodeExtension(PubSubElementType.CREATE, nodeId);
        iq.addExtension(pubsub);
        from.sendStanza(iq);
    }

    private static void deleteNode(XMPPConnection from, String nodeId) throws NotConnectedException,
                    InterruptedException {

        PubSub iq = new PubSub(PubSubNamespace.OWNER);

        iq.setFrom(from.getUser());
        iq.setTo(pubsubJid);
        iq.setType(Type.set);

        ExtensionElement pubsub = new NodeExtension(PubSubElementType.DELETE, nodeId);
        iq.addExtension(pubsub);
        from.sendStanza(iq);
    }

    private static void subscribeToNode(XMPPConnection from, String nodeId) throws NotConnectedException,
                    InterruptedException {
        PubSub iq = new PubSub();

        iq.setFrom(from.getUser());
        iq.setTo(pubsubJid);
        iq.setType(Type.set);

        ExtensionElement pubsub = new SubscribeExtension(from.getUser().asBareJidString(), nodeId);
        iq.addExtension(pubsub);
        from.sendStanza(iq);
    }

    private static void unsubscribeToNode(XMPPConnection from, String nodeId) throws NotConnectedException,
                    InterruptedException {
        PubSub iq = new PubSub();

        iq.setFrom(from.getUser());
        iq.setTo(pubsubJid);
        iq.setType(Type.set);

        ExtensionElement pubsub = new UnsubscribeExtension(from.getUser().asBareJidString(), nodeId);// new

        iq.addExtension(pubsub);
        from.sendStanza(iq);
    }

    private static void publishToNode(XMPPConnection from, String nodeId) throws NotConnectedException,
                    InterruptedException {
        PubSub iq = new PubSub();

        iq.setFrom(from.getUser());
        iq.setTo(pubsubJid);
        iq.setType(Type.set);

        Item item = new PayloadItem<ExtensionElement>(new Item());

        ExtensionElement pubsub = new PublishItem<Item>(nodeId, item);

        iq.addExtension(pubsub);
        from.sendStanza(iq);
    }

    private static void publishTemplateToNode(XMPPConnection from, String nodeId) throws NotConnectedException,
                    InterruptedException {
        PubSub iq = new PubSub();

        iq.setFrom(from.getUser());
        iq.setTo(pubsubJid);
        iq.setType(Type.set);

        DataForm dataForm = new DataForm(DataForm.Type.form);
        dataForm.setTitle("Test form");

        dataForm.addField(new FormField("field name"));

        Item item = new PayloadItem<ExtensionElement>(dataForm);

        ExtensionElement pubsub = new PublishItem<Item>(nodeId, item);

        iq.addExtension(pubsub);
        from.sendStanza(iq);
    }

    private static void queryPersistedItems(XMPPConnection from, String nodeId, int maxItemsToReturn)
                    throws NotConnectedException, InterruptedException {
        PubSub iq = new PubSub();

        iq.setFrom(from.getUser());
        iq.setTo(pubsubJid);
        iq.setType(Type.get);

        ExtensionElement pubsub = new GetItemsRequest(nodeId, maxItemsToReturn);

        iq.addExtension(pubsub);
        from.sendStanza(iq);
    }

    private static void discoItemsOnNode(XMPPConnection from, String nodeId) throws NotConnectedException,
                    InterruptedException {
        DiscoverItems iq = new DiscoverItems();
        iq.setFrom(from.getUser());
        iq.setTo(pubsubJid);
        iq.setNode(nodeId);
        from.sendStanza(iq);
    }

    private static void discoFeatures(XMPPConnection from) throws NotConnectedException, InterruptedException {
        DiscoverInfo iq = new DiscoverInfo();
        iq.setFrom(from.getUser());
        iq.setTo(pubsubJid);
        from.sendStanza(iq);
    }

}
